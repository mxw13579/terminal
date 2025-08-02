package com.fufu.terminal.exception;

import lombok.Getter;

import java.util.Map;

/**
 * Exception thrown when SSH connection operations fail
 * Includes specific connection configuration and retry attempt information
 */
@Getter
public class ConnectionException extends ScriptExecutionException {
    
    private final String host;
    private final int port;
    private final String username;
    private final int retryAttempt;
    
    public ConnectionException(String message, String host, int port, String username) {
        this(message, host, port, username, 0);
    }
    
    public ConnectionException(String message, String host, int port, String username, int retryAttempt) {
        super(message, "CONNECTION_ERROR");
        this.host = host;
        this.port = port;
        this.username = username;
        this.retryAttempt = retryAttempt;
        
        // Add connection context
        addContext("host", host);
        addContext("port", String.valueOf(port));
        addContext("username", username);
        addContext("retryAttempt", String.valueOf(retryAttempt));
    }
    
    public ConnectionException(String message, Throwable cause, String host, int port, String username, int retryAttempt) {
        super(message, cause, "CONNECTION_ERROR");
        this.host = host;
        this.port = port;
        this.username = username;
        this.retryAttempt = retryAttempt;
        
        // Add connection context
        addContext("host", host);
        addContext("port", String.valueOf(port));
        addContext("username", username);
        addContext("retryAttempt", String.valueOf(retryAttempt));
    }
    
    /**
     * Get user-friendly error message with connection details
     */
    public String getUserFriendlyMessage() {
        return String.format("Failed to connect to %s@%s:%d - %s", username, host, port, getMessage());
    }
    
    /**
     * Get suggested recovery actions
     */
    public String getRecoverySuggestion() {
        if (retryAttempt > 0) {
            return "Connection failed after " + retryAttempt + " attempts. Please check your SSH credentials, network connectivity, and ensure the target server is accessible.";
        } else {
            return "Please verify your SSH connection settings and ensure the target server is accessible.";
        }
    }
}