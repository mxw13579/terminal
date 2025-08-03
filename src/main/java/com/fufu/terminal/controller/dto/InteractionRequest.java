package com.fufu.terminal.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;


/**
 * DTO for user interaction requests
 * Supports real-time user input during script execution
 */
@Data
@Builder
public class InteractionRequest {
    @NotBlank(message = "Interaction value is required")
    private String value;
    private String interactionId;
    private Long timestamp;
}
