package com.fufu.terminal.service.validation;

import com.fufu.terminal.config.properties.ScriptExecutionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Security validation service for input sanitization and injection attack prevention
 * Provides comprehensive security checks for parameter values
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityValidationService {
    
    private final ScriptExecutionProperties properties;
    
    // Dangerous patterns that could indicate injection attacks
    private static final List<String> COMMAND_INJECTION_PATTERNS = Arrays.asList(
        ";", "&&", "||", "|", "`", "$(", "${", "/../", "..\\\\",
        "rm -rf", "format c:", "del /f", "sudo", "su -", "chmod", "chown",
        "/bin/", "/usr/bin/", "cmd.exe", "powershell", "bash", "sh -c"
    );
    
    private static final List<String> XSS_PATTERNS = Arrays.asList(
        "<script", "</script>", "javascript:", "onload=", "onerror=", "onclick=",
        "onmouseover=", "onfocus=", "onblur=", "onchange=", "onsubmit=",
        "eval(", "document.cookie", "window.location", "innerHTML", "outerHTML"
    );
    
    private static final List<String> SQL_INJECTION_PATTERNS = Arrays.asList(
        "' or '1'='1", "' or 1=1", "union select", "drop table", "delete from",
        "insert into", "update ", "alter table", "create table", "--", "/*", "*/"
    );
    
    private static final List<String> PATH_TRAVERSAL_PATTERNS = Arrays.asList(
        "../", "..\\", "/..", "\\..", "/etc/passwd", "/etc/shadow", 
        "c:\\windows", "c:/windows", "%systemroot%", "$home"
    );
    
    // Compiled patterns for performance
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("(?i)<script[^>]*>.*?</script>");
    private static final Pattern DANGEROUS_CHARS_PATTERN = Pattern.compile("[;&|`$<>{}\\[\\]()\\\\]");
    
    /**
     * Check if input contains suspicious content
     */
    public boolean containsSuspiciousContent(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        
        String normalizedInput = input.toLowerCase().trim();
        
        // Check for command injection patterns
        if (containsAnyPattern(normalizedInput, COMMAND_INJECTION_PATTERNS)) {
            log.warn("Potential command injection detected in input: {}", input.substring(0, Math.min(input.length(), 50)));
            return true;
        }
        
        // Check for XSS patterns
        if (containsAnyPattern(normalizedInput, XSS_PATTERNS)) {
            log.warn("Potential XSS attack detected in input: {}", input.substring(0, Math.min(input.length(), 50)));
            return true;
        }
        
        // Check for SQL injection patterns
        if (containsAnyPattern(normalizedInput, SQL_INJECTION_PATTERNS)) {
            log.warn("Potential SQL injection detected in input: {}", input.substring(0, Math.min(input.length(), 50)));
            return true;
        }
        
        // Check for path traversal patterns
        if (containsAnyPattern(normalizedInput, PATH_TRAVERSAL_PATTERNS)) {
            log.warn("Potential path traversal attack detected in input: {}", input.substring(0, Math.min(input.length(), 50)));
            return true;
        }
        
        // Check for suspicious character combinations
        if (containsSuspiciousCharacterCombinations(input)) {
            log.warn("Suspicious character combinations detected in input: {}", input.substring(0, Math.min(input.length(), 50)));
            return true;
        }
        
        return false;
    }
    
    /**
     * Sanitize input by removing or escaping dangerous content
     */
    public String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        
        String sanitized = input;
        
        // Remove HTML/script tags
        sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = HTML_TAG_PATTERN.matcher(sanitized).replaceAll("");
        
        // Escape dangerous characters
        sanitized = sanitized.replace("&", "&amp;")
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                            .replace("\"", "&quot;")
                            .replace("'", "&#x27;")
                            .replace("/", "&#x2F;");
        
        // Remove null bytes and control characters
        sanitized = sanitized.replaceAll("\\x00", "")
                            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        
        // Limit length if configured
        if (properties.getValidation().getMaxParameterLength() > 0 && 
            sanitized.length() > properties.getValidation().getMaxParameterLength()) {
            sanitized = sanitized.substring(0, properties.getValidation().getMaxParameterLength());
            log.warn("Input truncated to maximum allowed length: {}", properties.getValidation().getMaxParameterLength());
        }
        
        // Trim whitespace
        sanitized = sanitized.trim();
        
        if (!input.equals(sanitized)) {
            log.debug("Input sanitized: original length {}, sanitized length {}", input.length(), sanitized.length());
        }
        
        return sanitized;
    }
    
    /**
     * Validate that input is safe for shell execution
     */
    public boolean isSafeForShellExecution(String input) {
        if (input == null || input.trim().isEmpty()) {
            return true;
        }
        
        // Check for dangerous shell characters
        if (DANGEROUS_CHARS_PATTERN.matcher(input).find()) {
            return false;
        }
        
        // Check for command injection patterns
        String normalized = input.toLowerCase();
        for (String pattern : COMMAND_INJECTION_PATTERNS) {
            if (normalized.contains(pattern.toLowerCase())) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validate that input is safe for file path operations
     */
    public boolean isSafeFilePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return true;
        }
        
        String normalizedPath = path.toLowerCase().replace("\\", "/");
        
        // Check for path traversal attempts
        if (normalizedPath.contains("../") || normalizedPath.contains("/..") ||
            normalizedPath.contains("~") || normalizedPath.startsWith("/etc/") ||
            normalizedPath.startsWith("/root/") || normalizedPath.contains("/proc/")) {
            return false;
        }
        
        // Check for Windows system paths
        if (normalizedPath.contains("c:/windows") || normalizedPath.contains("c:\\windows") ||
            normalizedPath.contains("system32") || normalizedPath.contains("%")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Get security assessment for input
     */
    public SecurityAssessment assessInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return SecurityAssessment.builder()
                .safe(true)
                .riskLevel(RiskLevel.NONE)
                .threats(Arrays.asList())
                .recommendation("Input is empty")
                .build();
        }
        
        SecurityAssessment.SecurityAssessmentBuilder builder = SecurityAssessment.builder();
        List<String> threats = new java.util.ArrayList<>();
        RiskLevel maxRisk = RiskLevel.NONE;
        
        String normalized = input.toLowerCase();
        
        // Check for various threat types
        if (containsAnyPattern(normalized, COMMAND_INJECTION_PATTERNS)) {
            threats.add("Command injection indicators");
            maxRisk = RiskLevel.HIGH;
        }
        
        if (containsAnyPattern(normalized, XSS_PATTERNS)) {
            threats.add("Cross-site scripting indicators");
            maxRisk = maxRisk.ordinal() < RiskLevel.HIGH.ordinal() ? RiskLevel.HIGH : maxRisk;
        }
        
        if (containsAnyPattern(normalized, SQL_INJECTION_PATTERNS)) {
            threats.add("SQL injection indicators");
            maxRisk = maxRisk.ordinal() < RiskLevel.HIGH.ordinal() ? RiskLevel.HIGH : maxRisk;
        }
        
        if (containsAnyPattern(normalized, PATH_TRAVERSAL_PATTERNS)) {
            threats.add("Path traversal indicators");
            maxRisk = maxRisk.ordinal() < RiskLevel.MEDIUM.ordinal() ? RiskLevel.MEDIUM : maxRisk;
        }
        
        if (containsSuspiciousCharacterCombinations(input)) {
            threats.add("Suspicious character patterns");
            maxRisk = maxRisk.ordinal() < RiskLevel.LOW.ordinal() ? RiskLevel.LOW : maxRisk;
        }
        
        boolean safe = threats.isEmpty();
        String recommendation = safe ? "Input appears safe" : 
            "Input contains potentially dangerous content and should be sanitized or rejected";
        
        return builder
            .safe(safe)
            .riskLevel(maxRisk)
            .threats(threats)
            .recommendation(recommendation)
            .originalLength(input.length())
            .build();
    }
    
    /**
     * Check if input contains any of the given patterns
     */
    private boolean containsAnyPattern(String input, List<String> patterns) {
        for (String pattern : patterns) {
            if (input.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check for suspicious character combinations
     */
    private boolean containsSuspiciousCharacterCombinations(String input) {
        // Check for multiple consecutive dangerous characters
        if (input.matches(".*[;&|`$]{2,}.*")) {
            return true;
        }
        
        // Check for encoded dangerous sequences
        if (input.contains("%3B") || input.contains("%26") || input.contains("%7C") ||
            input.contains("&#59;") || input.contains("&#38;") || input.contains("&#124;")) {
            return true;
        }
        
        // Check for suspicious Unicode characters
        if (input.matches(".*[\\u0000-\\u001F\\u007F-\\u009F\\u2000-\\u200F\\uFEFF].*")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Security assessment result
     */
    @lombok.Data
    @lombok.Builder
    public static class SecurityAssessment {
        private boolean safe;
        private RiskLevel riskLevel;
        private List<String> threats;
        private String recommendation;
        private int originalLength;
        private String sanitizedValue;
    }
    
    /**
     * Risk level enumeration
     */
    public enum RiskLevel {
        NONE("No risk detected"),
        LOW("Low risk - suspicious patterns detected"),
        MEDIUM("Medium risk - potentially dangerous content"),
        HIGH("High risk - dangerous content detected");
        
        private final String description;
        
        RiskLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}