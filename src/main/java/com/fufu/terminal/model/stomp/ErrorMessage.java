package com.fufu.terminal.model.stomp;

import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.NotBlank;

/**
 * STOMP error message for communicating errors to clients.
 * 
 * @author lizelin
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ErrorMessage extends StompMessage {
    
    /**
     * Error code for categorizing the error type.
     */
    private String errorCode;
    
    /**
     * Human-readable error message.
     */
    @NotBlank
    private String message;
    
    /**
     * Additional error details or stack trace (for debugging).
     */
    private String details;
    
    /**
     * Constructor for creating error messages.
     */
    public ErrorMessage(String errorCode, String message) {
        super("error");
        this.errorCode = errorCode;
        this.message = message;
    }
    
    /**
     * Constructor with details.
     */
    public ErrorMessage(String errorCode, String message, String details) {
        super("error");
        this.errorCode = errorCode;
        this.message = message;
        this.details = details;
    }
    
    /**
     * Default constructor for JSON deserialization.
     */
    public ErrorMessage() {
        super("error");
    }
}