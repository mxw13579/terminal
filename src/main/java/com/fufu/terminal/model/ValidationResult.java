package com.fufu.terminal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.ArrayList;

/**
 * Validation Result
 * 
 * Contains the result of parameter validation with error details
 * and suggestions for recovery.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    
    private boolean valid;
    private String errorMessage;
    private List<String> suggestions;
    private String fieldName;
    private Object providedValue;
    private String errorCode;
    
    /**
     * Create a successful validation result
     */
    public static ValidationResult valid() {
        return ValidationResult.builder()
            .valid(true)
            .suggestions(new ArrayList<>())
            .build();
    }
    
    /**
     * Create a validation failure result
     */
    public static ValidationResult invalid(String errorMessage) {
        return ValidationResult.builder()
            .valid(false)
            .errorMessage(errorMessage)
            .suggestions(new ArrayList<>())
            .build();
    }
    
    /**
     * Create a validation failure result with field information
     */
    public static ValidationResult invalid(String fieldName, String errorMessage) {
        return ValidationResult.builder()
            .valid(false)
            .fieldName(fieldName)
            .errorMessage(errorMessage)
            .suggestions(new ArrayList<>())
            .build();
    }
    
    /**
     * Create a validation failure result with error code
     */
    public static ValidationResult invalid(String fieldName, String errorMessage, String errorCode) {
        return ValidationResult.builder()
            .valid(false)
            .fieldName(fieldName)
            .errorMessage(errorMessage)
            .errorCode(errorCode)
            .suggestions(new ArrayList<>())
            .build();
    }
    
    /**
     * Add a suggestion for fixing the validation error
     */
    public ValidationResult withSuggestion(String suggestion) {
        if (suggestions == null) {
            suggestions = new ArrayList<>();
        }
        suggestions.add(suggestion);
        return this;
    }
    
    /**
     * Add multiple suggestions for fixing the validation error
     */
    public ValidationResult withSuggestions(List<String> suggestions) {
        if (this.suggestions == null) {
            this.suggestions = new ArrayList<>();
        }
        this.suggestions.addAll(suggestions);
        return this;
    }
    
    /**
     * Set the provided value that failed validation
     */
    public ValidationResult withProvidedValue(Object providedValue) {
        this.providedValue = providedValue;
        return this;
    }
    
    /**
     * Check if there are suggestions available
     */
    public boolean hasSuggestions() {
        return suggestions != null && !suggestions.isEmpty();
    }
    
    /**
     * Get a formatted error message including field name
     */
    public String getFormattedErrorMessage() {
        if (fieldName != null && !fieldName.isEmpty()) {
            return String.format("%s: %s", fieldName, errorMessage);
        }
        return errorMessage;
    }
    
    /**
     * Get a user-friendly display message
     */
    public String getUserFriendlyMessage() {
        StringBuilder message = new StringBuilder();
        
        if (valid) {
            return "Validation successful";
        }
        
        message.append(getFormattedErrorMessage());
        
        if (hasSuggestions()) {
            message.append("\n\nSuggestions:");
            for (String suggestion : suggestions) {
                message.append("\n• ").append(suggestion);
            }
        }
        
        return message.toString();
    }
}