package com.fufu.terminal.script.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Validation result for script parameters and execution prerequisites
 * Provides detailed feedback for validation failures
 */
@Data
@Builder
public class ValidationResult {
    private boolean valid;
    private String errorMessage;
    private List<String> errors;
    private List<String> warnings;
    
    public static ValidationResult valid() {
        return ValidationResult.builder()
                .valid(true)
                .errors(new ArrayList<>())
                .warnings(new ArrayList<>())
                .build();
    }
    
    public static ValidationResult invalid(String errorMessage) {
        return ValidationResult.builder()
                .valid(false)
                .errorMessage(errorMessage)
                .errors(List.of(errorMessage))
                .warnings(new ArrayList<>())
                .build();
    }
    
    public static ValidationResult withErrors(List<String> errors) {
        return ValidationResult.builder()
                .valid(false)
                .errorMessage(String.join("; ", errors))
                .errors(errors)
                .warnings(new ArrayList<>())
                .build();
    }
    
    public static ValidationResult withWarnings(List<String> warnings) {
        return ValidationResult.builder()
                .valid(true)
                .errors(new ArrayList<>())
                .warnings(warnings)
                .build();
    }
    
    public ValidationResult addError(String error) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(error);
        this.valid = false;
        this.errorMessage = String.join("; ", this.errors);
        return this;
    }
    
    public ValidationResult addWarning(String warning) {
        if (this.warnings == null) {
            this.warnings = new ArrayList<>();
        }
        this.warnings.add(warning);
        return this;
    }
    
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
    
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
}