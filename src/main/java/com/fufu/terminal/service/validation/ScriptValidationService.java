package com.fufu.terminal.service.validation;

import com.fufu.terminal.entity.enums.InteractionType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Script validation and input sanitization service
 * Ensures security and proper handling of user inputs and script content
 */
@Slf4j
@Service
public class ScriptValidationService {

    // Response constants for confirmation inputs
    private static final String RESPONSE_YES = "yes";
    private static final String RESPONSE_NO = "no";
    private static final String RESPONSE_Y = "y";
    private static final String RESPONSE_N = "n";

    // Pattern to detect potentially dangerous commands in scripts
    private static final Pattern DANGEROUS_PATTERNS = Pattern.compile(
        "(?i)(rm\\s+-rf|;\\s*rm|&&\\s*rm|\\|\\s*rm|`.*`|\\$\\(.*\\))"
    );

    // Valid responses for confirmation type interactions
    private static final Pattern CONFIRMATION_PATTERN = Pattern.compile("^(yes|no|y|n)$", Pattern.CASE_INSENSITIVE);

    /**
     * Validates script content for security issues
     */
    public ValidationResult validateScriptContent(String scriptContent) {
        ValidationResult result = new ValidationResult();

        if (scriptContent == null || scriptContent.trim().isEmpty()) {
            result.addError("Script content cannot be empty");
            return result;
        }

        // Check for dangerous commands
        if (DANGEROUS_PATTERNS.matcher(scriptContent).find()) {
            result.addError("Script contains potentially dangerous commands");
        }

        // Validate script syntax (basic check)
        if (!isValidShellScript(scriptContent)) {
            result.addWarning("Script syntax may be invalid");
        }

        return result;
    }

    /**
     * Sanitizes user input based on interaction type
     */
    public String sanitizeUserInput(String input, InteractionType type) {
        if (input == null) {
            return null;
        }

        return switch (type) {
            case INPUT_PASSWORD -> input; // Don't modify passwords
            case INPUT_TEXT -> StringEscapeUtils.escapeHtml4(input.trim());
            case CONFIRMATION -> sanitizeConfirmationInput(input);
            case CONFIRM_YES_NO -> sanitizeConfirmationInput(input);
            case CONFIRM_RECOMMENDATION -> sanitizeConfirmationInput(input);
            default -> input.trim();
        };
    }

    /**
     * Validates user input based on interaction type
     */
    public ValidationResult validateUserInput(String input, InteractionType type) {
        ValidationResult result = new ValidationResult();

        if (input == null || input.trim().isEmpty()) {
            result.addError("Input cannot be empty");
            return result;
        }

        switch (type) {
            case CONFIRMATION, CONFIRM_YES_NO, CONFIRM_RECOMMENDATION -> {
                if (!CONFIRMATION_PATTERN.matcher(input.trim()).matches()) {
                    result.addError("Confirmation input must be yes/no/y/n");
                }
            }
            case INPUT_PASSWORD -> {
                if (input.length() < 1) {
                    result.addError("Password cannot be empty");
                }
            }
            case INPUT_TEXT -> {
                if (input.trim().length() < 1) {
                    result.addError("Text input cannot be empty");
                }
                // Check for potentially dangerous input
                if (containsSuspiciousContent(input)) {
                    result.addWarning("Input contains potentially suspicious content");
                }
            }
            default -> {
                // No specific validation for other types
            }
        }

        return result;
    }

    /**
     * Sanitizes confirmation input to ensure valid responses
     */
    private String sanitizeConfirmationInput(String input) {
        String trimmed = input.toLowerCase().trim();
        if (CONFIRMATION_PATTERN.matcher(trimmed).matches()) {
            // Normalize to yes/no
            return switch (trimmed) {
                case RESPONSE_Y, RESPONSE_YES -> RESPONSE_YES;
                case RESPONSE_N, RESPONSE_NO -> RESPONSE_NO;
                default -> RESPONSE_NO; // Default to no for safety
            };
        }
        return RESPONSE_NO; // Default to no for invalid input
    }

    /**
     * Basic shell script syntax validation
     */
    private boolean isValidShellScript(String scriptContent) {
        // Basic checks for shell script validity
        String trimmed = scriptContent.trim();

        // Check for unbalanced quotes
        int singleQuotes = 0;
        int doubleQuotes = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (char c : trimmed.toCharArray()) {
            if (c == '\'' && !inDoubleQuote) {
                if (inSingleQuote) {
                    singleQuotes--;
                    inSingleQuote = false;
                } else {
                    singleQuotes++;
                    inSingleQuote = true;
                }
            } else if (c == '"' && !inSingleQuote) {
                if (inDoubleQuote) {
                    doubleQuotes--;
                    inDoubleQuote = false;
                } else {
                    doubleQuotes++;
                    inDoubleQuote = true;
                }
            }
        }

        // Check if quotes are balanced
        return singleQuotes == 0 && doubleQuotes == 0;
    }

    /**
     * Checks if input contains suspicious content
     */
    private boolean containsSuspiciousContent(String input) {
        // Check for common injection patterns
        String lowerInput = input.toLowerCase();
        return lowerInput.contains("<script") ||
               lowerInput.contains("javascript:") ||
               lowerInput.contains("onload=") ||
               lowerInput.contains("onerror=") ||
               DANGEROUS_PATTERNS.matcher(input).find();
    }

    /**
     * Validation result container
     */
    public static class ValidationResult {
        private final java.util.List<String> errors = new java.util.ArrayList<>();
        private final java.util.List<String> warnings = new java.util.ArrayList<>();

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public java.util.List<String> getErrors() {
            return java.util.Collections.unmodifiableList(errors);
        }

        public java.util.List<String> getWarnings() {
            return java.util.Collections.unmodifiableList(warnings);
        }

        public boolean isValid() {
            return !hasErrors();
        }
    }
}
