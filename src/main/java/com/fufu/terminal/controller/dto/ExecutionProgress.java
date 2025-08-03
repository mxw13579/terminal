package com.fufu.terminal.controller.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO for execution progress information
 * Provides real-time progress updates to the frontend
 */
@Data
@Builder
public class ExecutionProgress {
    private String sessionId;
    private String status;
    private String currentStep;
    private Integer currentStepNumber;
    private Integer totalSteps;
    private Integer percentage;
    private String message;
    private Long startTime;
    private Long estimatedEndTime;
    private Object resultData;
    private String errorMessage;
}