package com.fufu.terminal.exception;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base exception for script execution errors
 * Provides comprehensive error information with correlation tracking
 */
@Getter
public class ScriptExecutionException extends Exception {
    
    private final String errorCode;
    private final String correlationId;
    private final Map<String, String> context;
    
    public ScriptExecutionException(String message) {
        this(message, "GENERAL_ERROR", UUID.randomUUID().toString(), new HashMap<>());
    }
    
    public ScriptExecutionException(String message, String errorCode) {
        this(message, errorCode, UUID.randomUUID().toString(), new HashMap<>());
    }
    
    public ScriptExecutionException(String message, String errorCode, String correlationId) {
        this(message, errorCode, correlationId, new HashMap<>());
    }
    
    public ScriptExecutionException(String message, String errorCode, String correlationId, Map<String, String> context) {
        super(message);
        this.errorCode = errorCode;
        this.correlationId = correlationId;
        this.context = context;
    }
    
    public ScriptExecutionException(String message, Throwable cause, String errorCode) {
        this(message, cause, errorCode, UUID.randomUUID().toString(), new HashMap<>());
    }
    
    public ScriptExecutionException(String message, Throwable cause, String errorCode, String correlationId, Map<String, String> context) {
        super(message, cause);
        this.errorCode = errorCode;
        this.correlationId = correlationId;
        this.context = context;
    }
    
    /**
     * Add context information to the exception
     */
    public void addContext(String key, String value) {
        this.context.put(key, value);
    }
    
    /**
     * Get formatted error message with correlation ID
     */
    public String getFormattedMessage() {
        return String.format("[%s] %s (Error: %s)", correlationId, getMessage(), errorCode);
    }
}