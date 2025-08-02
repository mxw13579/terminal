package com.fufu.terminal.validation;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured validation result with field-specific error reporting
 * Provides comprehensive error information with actionable guidance
 */
@Data
@NoArgsConstructor
public class ValidationResult {
    
    private boolean valid = true;
    private Map<String, String> fieldErrors = new HashMap<>();
    private Map<String, String> fieldWarnings = new HashMap<>();
    private List<String> globalErrors = new ArrayList<>();
    private List<String> globalWarnings = new ArrayList<>();
    private Map<String, Object> metadata = new HashMap<>();
    
    /**
     * Add field-specific error
     */
    public void addFieldError(String fieldName, String errorMessage) {
        this.valid = false;
        this.fieldErrors.put(fieldName, errorMessage);
    }
    
    /**
     * Add field-specific warning
     */
    public void addFieldWarning(String fieldName, String warningMessage) {
        this.fieldWarnings.put(fieldName, warningMessage);
    }
    
    /**
     * Add global error
     */
    public void addGlobalError(String errorMessage) {
        this.valid = false;
        this.globalErrors.add(errorMessage);
    }
    
    /**
     * Add global warning
     */
    public void addGlobalWarning(String warningMessage) {
        this.globalWarnings.add(warningMessage);
    }
    
    /**
     * Add metadata information
     */
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    /**
     * Check if field has errors
     */
    public boolean hasFieldError(String fieldName) {
        return fieldErrors.containsKey(fieldName);
    }
    
    /**
     * Check if field has warnings
     */
    public boolean hasFieldWarning(String fieldName) {
        return fieldWarnings.containsKey(fieldName);
    }
    
    /**
     * Check if there are any warnings
     */
    public boolean hasWarnings() {
        return !fieldWarnings.isEmpty() || !globalWarnings.isEmpty();
    }
    
    /**
     * Get total error count
     */
    public int getErrorCount() {
        return fieldErrors.size() + globalErrors.size();
    }
    
    /**
     * Get total warning count
     */
    public int getWarningCount() {
        return fieldWarnings.size() + globalWarnings.size();
    }
    
    /**
     * Merge another validation result into this one
     */
    public void merge(ValidationResult other) {
        if (other == null) {
            return;
        }
        
        if (!other.isValid()) {
            this.valid = false;
        }
        
        this.fieldErrors.putAll(other.getFieldErrors());
        this.fieldWarnings.putAll(other.getFieldWarnings());
        this.globalErrors.addAll(other.getGlobalErrors());
        this.globalWarnings.addAll(other.getGlobalWarnings());
        this.metadata.putAll(other.getMetadata());
    }
    
    /**
     * Get all error messages as a single list
     */
    public List<String> getAllErrorMessages() {
        List<String> allErrors = new ArrayList<>();
        allErrors.addAll(globalErrors);
        fieldErrors.forEach((field, error) -> 
            allErrors.add(String.format("%s: %s", field, error))
        );
        return allErrors;
    }
    
    /**
     * Get all warning messages as a single list
     */
    public List<String> getAllWarningMessages() {
        List<String> allWarnings = new ArrayList<>();
        allWarnings.addAll(globalWarnings);
        fieldWarnings.forEach((field, warning) -> 
            allWarnings.add(String.format("%s: %s", field, warning))
        );
        return allWarnings;
    }
    
    /**
     * Get formatted summary message
     */
    public String getSummaryMessage() {
        if (valid && !hasWarnings()) {
            return "Validation passed successfully";
        }
        
        StringBuilder summary = new StringBuilder();
        
        if (!valid) {
            summary.append(String.format("Validation failed with %d error(s)", getErrorCount()));
            if (hasWarnings()) {
                summary.append(String.format(" and %d warning(s)", getWarningCount()));
            }
        } else if (hasWarnings()) {
            summary.append(String.format("Validation passed with %d warning(s)", getWarningCount()));
        }
        
        return summary.toString();
    }
    
    /**
     * Create successful validation result
     */
    public static ValidationResult success() {
        return new ValidationResult();
    }
    
    /**
     * Create failed validation result with message
     */
    public static ValidationResult failure(String errorMessage) {
        ValidationResult result = new ValidationResult();
        result.addGlobalError(errorMessage);
        return result;
    }
    
    /**
     * Create failed validation result with field error
     */
    public static ValidationResult fieldFailure(String fieldName, String errorMessage) {
        ValidationResult result = new ValidationResult();
        result.addFieldError(fieldName, errorMessage);
        return result;
    }
    
    @Override
    public String toString() {
        return String.format("ValidationResult{valid=%s, errors=%d, warnings=%d, summary='%s'}", 
                valid, getErrorCount(), getWarningCount(), getSummaryMessage());
    }
}