package com.fufu.terminal.controller.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for script execution initiation
 */
@Data
@Builder
public class ExecutionResponse {
    private String sessionId;
    private String status;
    private String message;
    private Long timestamp;
    
    public static ExecutionResponse started(String sessionId) {
        return ExecutionResponse.builder()
                .sessionId(sessionId)
                .status("STARTED")
                .message("Script execution initiated")
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    public static ExecutionResponse failed(String message) {
        return ExecutionResponse.builder()
                .status("FAILED")
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}