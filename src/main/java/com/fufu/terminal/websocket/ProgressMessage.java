package com.fufu.terminal.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Progress Message for WebSocket Communication
 * 
 * Contains progress information for script execution that is sent
 * via WebSocket to connected clients.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ProgressMessage {
    
    private ProgressType type;
    private String sessionId;
    private String scriptName;
    private String stage;
    private Integer percentage;
    private String details;
    private Long estimatedRemainingSeconds;
    private Instant timestamp;
    private Long elapsedSeconds;
    private ScriptExecutionResult result;
    private ErrorDetails error;
    
    /**
     * Create a start message
     */
    public static ProgressMessage start(String sessionId, String scriptName) {
        return ProgressMessage.builder()
            .type(ProgressType.STARTED)
            .sessionId(sessionId)
            .scriptName(scriptName)
            .stage("Starting")
            .percentage(0)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create a progress update message
     */
    public static ProgressMessage progress(String sessionId, String stage, int percentage) {
        return ProgressMessage.builder()
            .type(ProgressType.PROGRESS)
            .sessionId(sessionId)
            .stage(stage)
            .percentage(percentage)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create a completion message
     */
    public static ProgressMessage completed(String sessionId, ScriptExecutionResult result) {
        return ProgressMessage.builder()
            .type(ProgressType.COMPLETED)
            .sessionId(sessionId)
            .stage("Completed")
            .percentage(100)
            .result(result)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create an error message
     */
    public static ProgressMessage error(String sessionId, ErrorDetails error) {
        return ProgressMessage.builder()
            .type(ProgressType.ERROR)
            .sessionId(sessionId)
            .stage("Error")
            .error(error)
            .timestamp(Instant.now())
            .build();
    }
}

/**
 * Progress Type Enumeration
 */
enum ProgressType {
    STARTED,
    PROGRESS,
    COMPLETED,
    FAILED,
    ERROR,
    TIMEOUT,
    CANCELLED
}

/**
 * Script Execution Result for Progress Reporting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ScriptExecutionResult {
    private boolean success;
    private String message;
    private String output;
    private String errorOutput;
    private java.util.Map<String, Object> variables;
    private long executionTimeMs;
}

/**
 * Error Details for Progress Reporting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ErrorDetails {
    private String type;
    private String message;
    private String userMessage;
    private java.util.List<String> suggestions;
    private boolean recoverable;
    private String stackTrace;
}