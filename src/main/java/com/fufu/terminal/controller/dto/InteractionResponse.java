package com.fufu.terminal.controller.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO for interaction response
 * Confirms receipt and processing of user input
 */
@Data
@Builder
public class InteractionResponse {
    private String status;
    private String message;
    private boolean accepted;
    private String validationError;
    private Long timestamp;
    
    public static InteractionResponse accepted() {
        return InteractionResponse.builder()
                .status("ACCEPTED")
                .message("Input received and accepted")
                .accepted(true)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    public static InteractionResponse rejected(String validationError) {
        return InteractionResponse.builder()
                .status("REJECTED")
                .message("Input validation failed")
                .accepted(false)
                .validationError(validationError)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}