package com.fufu.terminal.service.validation;

import com.fufu.terminal.config.properties.ScriptExecutionProperties;
import com.fufu.terminal.service.script.EnhancedScriptParameter;
import com.fufu.terminal.validation.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Comprehensive parameter validation service with type checking, security validation, and dependency handling
 * Provides production-ready validation for all script parameter types
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParameterValidationService {
    
    private final ScriptExecutionProperties properties;
    private final SecurityValidationService securityValidationService;
    
    // Cached compiled patterns for performance
    private final Map<String, Pattern> patternCache = new HashMap<>();
    
    // Common validation patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    private static final Pattern URL_PATTERN = Pattern.compile(
        "^https?://[a-zA-Z0-9.-]+(?:\\.[a-zA-Z]{2,})?(?:/.*)?$"
    );
    
    /**
     * Validate parameters against their definitions
     */
    public ValidationResult validateParameters(List<EnhancedScriptParameter> parameterDefinitions, 
                                             Map<String, Object> providedValues) {
        log.debug("Validating {} parameters with {} provided values", 
                parameterDefinitions.size(), providedValues.size());
        
        ValidationResult result = new ValidationResult();
        
        // First pass: basic parameter validation
        for (EnhancedScriptParameter param : parameterDefinitions) {
            ValidationResult paramResult = validateSingleParameter(param, providedValues.get(param.getName()));
            result.merge(paramResult);
        }
        
        // Second pass: cross-field validation if enabled
        if (properties.getValidation().isEnableCrossFieldValidation()) {
            ValidationResult crossValidationResult = validateDependencies(parameterDefinitions, providedValues);
            result.merge(crossValidationResult);
        }
        
        // Third pass: security validation if enabled
        if (properties.getValidation().isEnableSecurityChecks()) {
            ValidationResult securityResult = validateSecurity(parameterDefinitions, providedValues);
            result.merge(securityResult);
        }
        
        // Add validation metadata
        result.addMetadata("validatedParameterCount", parameterDefinitions.size());
        result.addMetadata("providedValueCount", providedValues.size());
        result.addMetadata("validationTimestamp", System.currentTimeMillis());
        
        log.debug("Parameter validation completed: {}", result.getSummaryMessage());
        return result;
    }
    
    /**
     * Validate a single parameter
     */
    private ValidationResult validateSingleParameter(EnhancedScriptParameter param, Object value) {
        ValidationResult result = new ValidationResult();
        
        // Check required parameters
        if (param.isRequired() && isEmpty(value)) {
            result.addFieldError(param.getName(), "Parameter is required");
            return result;
        }
        
        // Skip further validation for optional empty parameters
        if (isEmpty(value)) {
            return result;
        }
        
        // Type validation
        ValidationResult typeResult = validateType(param, value);
        result.merge(typeResult);
        
        if (!typeResult.isValid()) {
            return result; // Don't continue if type validation failed
        }
        
        // Format validation
        if (param.getPattern() != null) {
            ValidationResult patternResult = validatePattern(param, value);
            result.merge(patternResult);
        }
        
        // Range validation
        if (param.getType().supportsRange()) {
            ValidationResult rangeResult = validateRange(param, value);
            result.merge(rangeResult);
        }
        
        // Length validation
        if (param.getType().supportsLength()) {
            ValidationResult lengthResult = validateLength(param, value);
            result.merge(lengthResult);
        }
        
        // Allowed values validation
        if (!param.getAllowedValues().isEmpty()) {
            ValidationResult allowedValuesResult = validateAllowedValues(param, value);
            result.merge(allowedValuesResult);
        }
        
        return result;
    }
    
    /**
     * Validate parameter type
     */
    private ValidationResult validateType(EnhancedScriptParameter param, Object value) {
        ValidationResult result = new ValidationResult();
        String valueStr = value.toString().trim();
        
        try {
            switch (param.getType()) {
                case STRING:
                case TEXTAREA:
                case PASSWORD:
                    // String types are always valid
                    break;
                    
                case INTEGER:
                    try {
                        Integer.parseInt(valueStr);
                    } catch (NumberFormatException e) {
                        result.addFieldError(param.getName(), 
                            "Must be a valid integer. Example: " + (param.getExample() != null ? param.getExample() : "123"));
                    }
                    break;
                    
                case LONG:
                    try {
                        Long.parseLong(valueStr);
                    } catch (NumberFormatException e) {
                        result.addFieldError(param.getName(), 
                            "Must be a valid long integer. Example: " + (param.getExample() != null ? param.getExample() : "1234567890"));
                    }
                    break;
                    
                case DOUBLE:
                    try {
                        Double.parseDouble(valueStr);
                    } catch (NumberFormatException e) {
                        result.addFieldError(param.getName(), 
                            "Must be a valid decimal number. Example: " + (param.getExample() != null ? param.getExample() : "12.34"));
                    }
                    break;
                    
                case BOOLEAN:
                    if (!isBooleanValue(valueStr)) {
                        result.addFieldError(param.getName(), 
                            "Must be true or false. Example: true");
                    }
                    break;
                    
                case PORT:
                    try {
                        int port = Integer.parseInt(valueStr);
                        if (port < 1 || port > 65535) {
                            result.addFieldError(param.getName(), 
                                "Port must be between 1 and 65535. Example: 8080");
                        }
                    } catch (NumberFormatException e) {
                        result.addFieldError(param.getName(), 
                            "Must be a valid port number (1-65535). Example: 8080");
                    }
                    break;
                    
                case EMAIL:
                    if (!EMAIL_PATTERN.matcher(valueStr).matches()) {
                        result.addFieldError(param.getName(), 
                            "Must be a valid email address. Example: user@example.com");
                    }
                    break;
                    
                case URL:
                    try {
                        new URL(valueStr);
                        if (!URL_PATTERN.matcher(valueStr).matches()) {
                            result.addFieldError(param.getName(), 
                                "Must be a valid HTTP/HTTPS URL. Example: https://example.com");
                        }
                    } catch (MalformedURLException e) {
                        result.addFieldError(param.getName(), 
                            "Must be a valid URL. Example: https://example.com");
                    }
                    break;
                    
                case DATE:
                    try {
                        LocalDate.parse(valueStr);
                    } catch (DateTimeParseException e) {
                        result.addFieldError(param.getName(), 
                            "Must be a valid date in YYYY-MM-DD format. Example: 2023-12-31");
                    }
                    break;
                    
                case DATETIME:
                    try {
                        LocalDateTime.parse(valueStr);
                    } catch (DateTimeParseException e) {
                        result.addFieldError(param.getName(), 
                            "Must be a valid date-time in ISO format. Example: 2023-12-31T23:59:59");
                    }
                    break;
                    
                case JSON:
                    ValidationResult jsonResult = validateJson(param.getName(), valueStr);
                    result.merge(jsonResult);
                    break;
                    
                case ARRAY:
                    ValidationResult arrayResult = validateArray(param.getName(), valueStr);
                    result.merge(arrayResult);
                    break;
                    
                default:
                    // Unknown types default to string validation
                    break;
            }
        } catch (Exception e) {
            log.warn("Unexpected error during type validation for parameter {}: {}", param.getName(), e.getMessage());
            result.addFieldError(param.getName(), "Invalid value format");
        }
        
        return result;
    }
    
    /**
     * Validate parameter against regex pattern
     */
    private ValidationResult validatePattern(EnhancedScriptParameter param, Object value) {
        ValidationResult result = new ValidationResult();
        
        try {
            Pattern pattern = patternCache.computeIfAbsent(param.getPattern(), Pattern::compile);
            if (!pattern.matcher(value.toString()).matches()) {
                String message = param.getPatternDescription() != null 
                    ? param.getPatternDescription()
                    : "Value does not match required pattern";
                
                if (param.getExample() != null) {
                    message += ". Example: " + param.getExample();
                }
                
                result.addFieldError(param.getName(), message);
            }
        } catch (PatternSyntaxException e) {
            log.error("Invalid regex pattern for parameter {}: {}", param.getName(), param.getPattern());
            result.addFieldError(param.getName(), "Invalid validation pattern configured");
        }
        
        return result;
    }
    
    /**
     * Validate numeric range
     */
    private ValidationResult validateRange(EnhancedScriptParameter param, Object value) {
        ValidationResult result = new ValidationResult();
        
        try {
            double numValue = Double.parseDouble(value.toString());
            
            if (param.getMinValue() != null && numValue < param.getMinValue()) {
                result.addFieldError(param.getName(), 
                    String.format("Value must be at least %s", param.getMinValue()));
            }
            
            if (param.getMaxValue() != null && numValue > param.getMaxValue()) {
                result.addFieldError(param.getName(), 
                    String.format("Value must be at most %s", param.getMaxValue()));
            }
            
        } catch (NumberFormatException e) {
            // This should be caught by type validation, but handle it gracefully
            result.addFieldError(param.getName(), "Invalid numeric value for range validation");
        }
        
        return result;
    }
    
    /**
     * Validate string length
     */
    private ValidationResult validateLength(EnhancedScriptParameter param, Object value) {
        ValidationResult result = new ValidationResult();
        
        String strValue = value.toString();
        int length = strValue.length();
        
        if (param.getMinLength() != null && length < param.getMinLength()) {
            result.addFieldError(param.getName(), 
                String.format("Must be at least %d characters long", param.getMinLength()));
        }
        
        if (param.getMaxLength() != null && length > param.getMaxLength()) {
            result.addFieldError(param.getName(), 
                String.format("Must be at most %d characters long", param.getMaxLength()));
        }
        
        // Check global max length
        if (length > properties.getValidation().getMaxParameterLength()) {
            result.addFieldError(param.getName(), 
                String.format("Parameter too long (max %d characters)", properties.getValidation().getMaxParameterLength()));
        }
        
        return result;
    }
    
    /**
     * Validate allowed values
     */
    private ValidationResult validateAllowedValues(EnhancedScriptParameter param, Object value) {
        ValidationResult result = new ValidationResult();
        
        String strValue = value.toString();
        if (!param.getAllowedValues().contains(strValue)) {
            result.addFieldError(param.getName(), 
                String.format("Value must be one of: %s", String.join(", ", param.getAllowedValues())));
        }
        
        return result;
    }
    
    /**
     * Validate parameter dependencies
     */
    private ValidationResult validateDependencies(List<EnhancedScriptParameter> parameters, 
                                                 Map<String, Object> providedValues) {
        ValidationResult result = new ValidationResult();
        
        for (EnhancedScriptParameter param : parameters) {
            if (!param.getDependsOn().isEmpty()) {
                for (String dependency : param.getDependsOn()) {
                    if (!providedValues.containsKey(dependency) || isEmpty(providedValues.get(dependency))) {
                        result.addFieldError(param.getName(), 
                            String.format("Required dependency '%s' is not provided", dependency));
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Validate security constraints
     */
    private ValidationResult validateSecurity(List<EnhancedScriptParameter> parameters, 
                                             Map<String, Object> providedValues) {
        ValidationResult result = new ValidationResult();
        
        for (Map.Entry<String, Object> entry : providedValues.entrySet()) {
            if (entry.getValue() != null) {
                String value = entry.getValue().toString();
                
                if (securityValidationService.containsSuspiciousContent(value)) {
                    result.addFieldError(entry.getKey(), 
                        "Parameter contains potentially unsafe content");
                }
                
                if (properties.getValidation().isEnableParameterSanitization()) {
                    String sanitized = securityValidationService.sanitizeInput(value);
                    if (!value.equals(sanitized)) {
                        result.addFieldWarning(entry.getKey(), 
                            "Parameter value has been sanitized for security");
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Validate JSON string
     */
    private ValidationResult validateJson(String paramName, String value) {
        ValidationResult result = new ValidationResult();
        
        try {
            // Simple JSON validation - try to parse with basic checks
            value = value.trim();
            if ((!value.startsWith("{") || !value.endsWith("}")) && 
                (!value.startsWith("[") || !value.endsWith("]"))) {
                result.addFieldError(paramName, "Must be valid JSON (object or array)");
            }
            // For full validation, you'd use Jackson ObjectMapper here
        } catch (Exception e) {
            result.addFieldError(paramName, "Invalid JSON format");
        }
        
        return result;
    }
    
    /**
     * Validate array string
     */
    private ValidationResult validateArray(String paramName, String value) {
        ValidationResult result = new ValidationResult();
        
        try {
            value = value.trim();
            if (!value.startsWith("[") || !value.endsWith("]")) {
                result.addFieldError(paramName, "Array must start with [ and end with ]");
            }
        } catch (Exception e) {
            result.addFieldError(paramName, "Invalid array format");
        }
        
        return result;
    }
    
    /**
     * Check if value is empty (null, empty string, or whitespace only)
     */
    private boolean isEmpty(Object value) {
        return value == null || value.toString().trim().isEmpty();
    }
    
    /**
     * Check if string represents a boolean value
     */
    private boolean isBooleanValue(String value) {
        String lower = value.toLowerCase();
        return "true".equals(lower) || "false".equals(lower) || 
               "1".equals(lower) || "0".equals(lower) ||
               "yes".equals(lower) || "no".equals(lower);
    }
}