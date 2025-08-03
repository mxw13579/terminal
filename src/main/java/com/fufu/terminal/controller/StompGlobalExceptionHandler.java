package com.fufu.terminal.controller;

import com.fufu.terminal.model.stomp.ErrorMessage;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindException;

import jakarta.validation.ConstraintViolationException;
import java.io.IOException;

/**
 * Global exception handler for STOMP message processing.
 * Handles exceptions thrown by STOMP message mapping methods and converts them
 * to standardized error messages for the client.
 *
 * @author lizelin
 */
@Slf4j
@Controller
public class StompGlobalExceptionHandler {

    /**
     * Handle general exceptions thrown during STOMP message processing.
     */
    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/error")
    public ErrorMessage handleGenericException(Exception e) {
        log.error("STOMP message processing error", e);
        return new ErrorMessage(
            "PROCESSING_ERROR",
            "An error occurred while processing your request: " + e.getMessage(),
            getStackTraceAsString(e)
        );
    }

    /**
     * Handle SSH connection related exceptions.
     */
    @MessageExceptionHandler(JSchException.class)
    @SendToUser("/queue/error")
    public ErrorMessage handleSshException(JSchException e) {
        log.error("SSH connection error", e);
        return new ErrorMessage(
            "SSH_ERROR",
            "SSH connection failed: " + e.getMessage()
        );
    }

    /**
     * Handle SFTP operation related exceptions.
     */
    @MessageExceptionHandler(SftpException.class)
    @SendToUser("/queue/error")
    public ErrorMessage handleSftpException(SftpException e) {
        log.error("SFTP operation error", e);
        String errorMessage = switch (e.id) {
            case ChannelSftp.SSH_FX_NO_SUCH_FILE -> "File or directory not found";
            case ChannelSftp.SSH_FX_PERMISSION_DENIED -> "Permission denied";
            case ChannelSftp.SSH_FX_BAD_MESSAGE -> "Invalid SFTP request";
            case ChannelSftp.SSH_FX_NO_CONNECTION -> "SFTP connection lost";
            case ChannelSftp.SSH_FX_CONNECTION_LOST -> "SFTP connection lost";
            case ChannelSftp.SSH_FX_OP_UNSUPPORTED -> "Operation not supported";
            default -> "SFTP operation failed: " + e.getMessage();
        };

        return new ErrorMessage("SFTP_ERROR", errorMessage);
    }

    /**
     * Handle I/O exceptions (file operations, network errors).
     */
    @MessageExceptionHandler(IOException.class)
    @SendToUser("/queue/error")
    public ErrorMessage handleIOException(IOException e) {
        log.error("I/O error during STOMP message processing", e);
        return new ErrorMessage(
            "IO_ERROR",
            "I/O operation failed: " + e.getMessage()
        );
    }

    /**
     * Handle validation errors for STOMP message payloads.
     */
    @MessageExceptionHandler(ConstraintViolationException.class)
    @SendToUser("/queue/error")
    public ErrorMessage handleValidationException(ConstraintViolationException e) {
        log.warn("Validation error in STOMP message", e);
        StringBuilder errorMsg = new StringBuilder("Validation failed: ");
        e.getConstraintViolations().forEach(violation ->
            errorMsg.append(violation.getPropertyPath())
                   .append(" ")
                   .append(violation.getMessage())
                   .append("; "));

        return new ErrorMessage("VALIDATION_ERROR", errorMsg.toString());
    }

    /**
     * Handle binding exceptions (message deserialization errors).
     */
    @MessageExceptionHandler(BindException.class)
    @SendToUser("/queue/error")
    public ErrorMessage handleBindException(BindException e) {
        log.warn("Message binding error", e);
        StringBuilder errorMsg = new StringBuilder("Message format error: ");
        e.getBindingResult().getFieldErrors().forEach(error ->
            errorMsg.append(error.getField())
                   .append(" ")
                   .append(error.getDefaultMessage())
                   .append("; "));

        return new ErrorMessage("BINDING_ERROR", errorMsg.toString());
    }

    /**
     * Handle illegal argument exceptions (invalid parameters).
     */
    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser("/queue/error")
    public ErrorMessage handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal argument in STOMP message", e);
        return new ErrorMessage(
            "INVALID_ARGUMENT",
            "Invalid request parameter: " + e.getMessage()
        );
    }

    /**
     * Handle security related exceptions.
     */
    @MessageExceptionHandler(SecurityException.class)
    @SendToUser("/queue/error")
    public ErrorMessage handleSecurityException(SecurityException e) {
        log.error("Security error in STOMP message processing", e);
        return new ErrorMessage(
            "SECURITY_ERROR",
            "Access denied: " + e.getMessage()
        );
    }

    /**
     * Handle interrupted operations (cancelled tasks).
     */
    @MessageExceptionHandler(InterruptedException.class)
    @SendToUser("/queue/error")
    public ErrorMessage handleInterruptedException(InterruptedException e) {
        log.info("Operation was interrupted", e);
        Thread.currentThread().interrupt(); // Restore interrupt status
        return new ErrorMessage(
            "OPERATION_CANCELLED",
            "Operation was cancelled: " + e.getMessage()
        );
    }

    /**
     * Get stack trace as string for debugging (only in development).
     */
    private String getStackTraceAsString(Exception e) {
        // Only include stack trace in development/debug mode
        // In production, this should return null for security reasons
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            for (StackTraceElement element : e.getStackTrace()) {
                sb.append(element.toString()).append("\n");
            }
            return sb.toString();
        }
        return null;
    }
}
