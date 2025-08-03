package com.fufu.terminal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.List;

/**
 * Script Execution Result
 * 
 * Contains the result of script execution including output data,
 * status, and any error information.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ScriptResult {
    
    private boolean success;
    private String message;
    private Map<String, Object> outputVariables;
    private String output;
    private String errorOutput;
    private int exitCode;
    private long executionTimeMs;
    private Instant startTime;
    private Instant endTime;
    private List<String> executedCommands;
    private Map<String, Object> metadata;
    private Throwable exception;
    
    /**
     * Create a successful result
     */
    public static ScriptResult success(String message) {
        return ScriptResult.builder()
            .success(true)
            .message(message)
            .exitCode(0)
            .endTime(Instant.now())
            .build();
    }
    
    /**
     * Create a successful result with output variables
     */
    public static ScriptResult success(String message, Map<String, Object> outputVariables) {
        return ScriptResult.builder()
            .success(true)
            .message(message)
            .outputVariables(outputVariables)
            .exitCode(0)
            .endTime(Instant.now())
            .build();
    }
    
    /**
     * Create a failure result
     */
    public static ScriptResult failure(String message) {
        return ScriptResult.builder()
            .success(false)
            .message(message)
            .exitCode(1)
            .endTime(Instant.now())
            .build();
    }
    
    /**
     * Create a failure result with exception
     */
    public static ScriptResult failure(String message, Throwable exception) {
        return ScriptResult.builder()
            .success(false)
            .message(message)
            .exception(exception)
            .exitCode(1)
            .endTime(Instant.now())
            .build();
    }
    
    /**
     * Create a failure result with exit code
     */
    public static ScriptResult failure(String message, int exitCode) {
        return ScriptResult.builder()
            .success(false)
            .message(message)
            .exitCode(exitCode)
            .endTime(Instant.now())
            .build();
    }
    
    /**
     * Get formatted output combining stdout and stderr
     */
    public String getFormattedOutput() {
        StringBuilder result = new StringBuilder();
        
        if (output != null && !output.trim().isEmpty()) {
            result.append("Output:\n").append(output);
        }
        
        if (errorOutput != null && !errorOutput.trim().isEmpty()) {
            if (result.length() > 0) {
                result.append("\n\n");
            }
            result.append("Error Output:\n").append(errorOutput);
        }
        
        return result.toString();
    }
    
    /**
     * Check if the result has any output
     */
    public boolean hasOutput() {
        return (output != null && !output.trim().isEmpty()) ||
               (errorOutput != null && !errorOutput.trim().isEmpty());
    }
    
    /**
     * Check if the result has output variables
     */
    public boolean hasOutputVariables() {
        return outputVariables != null && !outputVariables.isEmpty();
    }
    
    /**
     * Get execution duration in seconds
     */
    public long getExecutionTimeSeconds() {
        return executionTimeMs / 1000;
    }
    
    /**
     * Add metadata entry
     */
    public ScriptResult withMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = Map.of(key, value);
        } else {
            metadata.put(key, value);
        }
        return this;
    }
    
    /**
     * Set execution timing
     */
    public ScriptResult withTiming(Instant startTime, Instant endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
        if (startTime != null && endTime != null) {
            this.executionTimeMs = endTime.toEpochMilli() - startTime.toEpochMilli();
        }
        return this;
    }
}