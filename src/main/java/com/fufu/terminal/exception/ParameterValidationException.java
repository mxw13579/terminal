package com.fufu.terminal.exception;

import lombok.Getter;

import java.util.Map;

/**
 * Exception thrown when parameter validation fails
 * Includes field-specific error information for user guidance
 */
@Getter
public class ParameterValidationException extends ScriptExecutionException {
    
    private final Map<String, String> fieldErrors;
    
    public ParameterValidationException(String message, Map<String, String> fieldErrors) {
        super(message, "VALIDATION_ERROR");
        this.fieldErrors = fieldErrors;
        
        // Add field error count to context
        addContext("fieldErrorCount", String.valueOf(fieldErrors.size()));
        addContext("fields", String.join(", ", fieldErrors.keySet()));
    }
    
    public ParameterValidationException(String message, Map<String, String> fieldErrors, String correlationId) {
        super(message, "VALIDATION_ERROR", correlationId);
        this.fieldErrors = fieldErrors;
        
        // Add field error count to context
        addContext("fieldErrorCount", String.valueOf(fieldErrors.size()));
        addContext("fields", String.join(", ", fieldErrors.keySet()));
    }
    
    /**
     * Get user-friendly validation summary
     */
    public String getValidationSummary() {
        if (fieldErrors.isEmpty()) {
            return getMessage();
        }
        
        StringBuilder summary = new StringBuilder(getMessage());
        summary.append(". Issues found in fields: ");
        
        fieldErrors.forEach((field, error) -> 
            summary.append(String.format("%s (%s), ", field, error))
        );
        
        // Remove last comma and space
        if (summary.length() > 2) {
            summary.setLength(summary.length() - 2);
        }
        
        return summary.toString();
    }
    
    /**
     * Check if a specific field has validation errors
     */
    public boolean hasFieldError(String fieldName) {
        return fieldErrors.containsKey(fieldName);
    }
    
    /**
     * Get error message for a specific field
     */
    public String getFieldError(String fieldName) {
        return fieldErrors.get(fieldName);
    }
    
    /**
     * Get recovery suggestion based on validation errors
     */
    public String getRecoverySuggestion() {
        return "Please correct the highlighted fields and try again. Ensure all required fields are filled and values meet the specified format requirements.";
    }
}