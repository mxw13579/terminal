package com.fufu.terminal.script.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Script execution result with comprehensive output data and metadata
 * Supports variable passing between scripts and detailed error reporting
 */
@Data
@Builder
public class ScriptResult {
    private boolean success;
    private String message;
    private String errorMessage;
    private String errorCode;
    private Map<String, Object> outputVariables;
    private Map<String, Object> outputData;
    private long executionTimeMs;
    private Instant startTime;
    private Instant endTime;
    private int exitCode;
    private String stdOut;
    private String stdErr;
    
    // Additional metadata
    private String sessionId;
    private String scriptId;
    private String scriptVersion;
    private Map<String, Object> metadata;
    
    public static ScriptResult success(String message) {
        return ScriptResult.builder()
                .success(true)
                .message(message)
                .exitCode(0)
                .build();
    }
    
    public static ScriptResult failure(String errorMessage) {
        return ScriptResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .exitCode(1)
                .build();
    }
    
    public static ScriptResult failure(String errorMessage, String errorCode) {
        return ScriptResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .errorCode(errorCode)
                .exitCode(1)
                .build();
    }
    
    public boolean hasOutputVariables() {
        return outputVariables != null && !outputVariables.isEmpty();
    }
    
    public boolean hasOutputData() {
        return outputData != null && !outputData.isEmpty();
    }
    
    public boolean hasStdOut() {
        return stdOut != null && !stdOut.trim().isEmpty();
    }
    
    public boolean hasStdErr() {
        return stdErr != null && !stdErr.trim().isEmpty();
    }
    
    public ScriptResult withOutputVariable(String name, Object value) {
        if (this.outputVariables == null) {
            this.outputVariables = new java.util.HashMap<>();
        }
        this.outputVariables.put(name, value);
        return this;
    }
    
    public ScriptResult withOutputData(String key, Object value) {
        if (this.outputData == null) {
            this.outputData = new java.util.HashMap<>();
        }
        this.outputData.put(key, value);
        return this;
    }
    
    public ScriptResult withMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new java.util.HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }
}