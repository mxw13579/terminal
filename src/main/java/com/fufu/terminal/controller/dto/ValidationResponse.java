package com.fufu.terminal.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO for script validation response
 * Provides validation results before script execution
 */
@Data
@Builder
public class ValidationResponse {
    private boolean valid;
    private String message;
    private List<String> errors;
    private List<String> warnings;
    private List<String> suggestions;
    
    public static ValidationResponse valid() {
        return ValidationResponse.builder()
                .valid(true)
                .message("Validation passed")
                .build();
    }
    
    public static ValidationResponse invalid(String message, List<String> errors) {
        return ValidationResponse.builder()
                .valid(false)
                .message(message)
                .errors(errors)
                .build();
    }
    
    public static ValidationResponse withWarnings(List<String> warnings) {
        return ValidationResponse.builder()
                .valid(true)
                .message("Validation passed with warnings")
                .warnings(warnings)
                .build();
    }
}