package com.fufu.terminal.service.error;

import com.fufu.terminal.exception.ConnectionException;
import com.fufu.terminal.exception.ParameterValidationException;
import com.fufu.terminal.exception.ScriptExecutionException;
import com.fufu.terminal.service.script.ScriptExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Comprehensive error handler for script execution with categorization and recovery suggestions
 * Provides structured error responses with correlation IDs for debugging
 */
@Slf4j
@Component
public class ScriptExecutionErrorHandler {
    
    /**
     * Handle script execution error and create appropriate response
     */
    public ScriptExecutionResult handleExecutionError(Exception exception, String scriptId, String executionContext) {
        String correlationId = UUID.randomUUID().toString();
        
        // Log error with correlation ID and context
        log.error("Script execution failed [{}] for script '{}' in context '{}': {}", 
                correlationId, scriptId, executionContext, exception.getMessage(), exception);
        
        // Determine error type and create appropriate response
        if (exception instanceof ConnectionException) {
            return createConnectionErrorResponse((ConnectionException) exception, correlationId, scriptId);
        } else if (exception instanceof ParameterValidationException) {
            return createValidationErrorResponse((ParameterValidationException) exception, correlationId, scriptId);
        } else if (exception instanceof TimeoutException) {
            return createTimeoutErrorResponse(exception, correlationId, scriptId);
        } else if (exception instanceof SecurityException) {
            return createSecurityErrorResponse(exception, correlationId, scriptId);
        } else if (exception instanceof ScriptExecutionException) {
            return createScriptExecutionErrorResponse((ScriptExecutionException) exception, correlationId, scriptId);
        } else {
            return createGenericErrorResponse(exception, correlationId, scriptId);
        }
    }
    
    /**
     * Create response for SSH connection errors
     */
    private ScriptExecutionResult createConnectionErrorResponse(ConnectionException exception, 
                                                              String correlationId, 
                                                              String scriptId) {
        String mainMessage = String.format("SSH connection failed: %s", exception.getMessage());
        String suggestion = exception.getRecoverySuggestion();
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("host", exception.getHost());
        errorDetails.put("port", exception.getPort());
        errorDetails.put("username", exception.getUsername());
        errorDetails.put("retryAttempt", exception.getRetryAttempt());
        errorDetails.put("errorType", "CONNECTION_ERROR");
        
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setSuccess(false);
        result.setErrorMessage(mainMessage);
        result.setErrorCode("CONNECTION_ERROR");
        result.setSuggestion(suggestion);
        result.setCorrelationId(correlationId);
        result.setScriptId(scriptId);
        result.setStartTime(LocalDateTime.now());
        result.setEndTime(LocalDateTime.now());
        result.setDuration(0L);
        result.setDisplayToUser(true);
        result.setErrorDetails(errorDetails);
        
        return result;
    }
    
    /**
     * Create response for parameter validation errors
     */
    private ScriptExecutionResult createValidationErrorResponse(ParameterValidationException exception, 
                                                               String correlationId, 
                                                               String scriptId) {
        String mainMessage = "Parameter validation failed";
        String suggestion = exception.getRecoverySuggestion();
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("fieldErrors", exception.getFieldErrors());
        errorDetails.put("validationSummary", exception.getValidationSummary());
        errorDetails.put("errorType", "VALIDATION_ERROR");
        
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setSuccess(false);
        result.setErrorMessage(mainMessage);
        result.setErrorCode("VALIDATION_ERROR");
        result.setSuggestion(suggestion);
        result.setCorrelationId(correlationId);
        result.setScriptId(scriptId);
        result.setStartTime(LocalDateTime.now());
        result.setEndTime(LocalDateTime.now());
        result.setDuration(0L);
        result.setDisplayToUser(true);
        result.setErrorDetails(errorDetails);
        result.setFieldErrors(exception.getFieldErrors());
        
        return result;
    }
    
    /**
     * Create response for timeout errors
     */
    private ScriptExecutionResult createTimeoutErrorResponse(Exception exception, 
                                                            String correlationId, 
                                                            String scriptId) {
        String mainMessage = "Script execution timed out";
        String suggestion = "The script took too long to execute. This may be due to network issues, " +
                          "server load, or a script that requires more time. Try running the script again, " +
                          "or contact your administrator if the problem persists.";
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("errorType", "TIMEOUT_ERROR");
        errorDetails.put("timeoutReason", exception.getMessage());
        
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setSuccess(false);
        result.setErrorMessage(mainMessage);
        result.setErrorCode("TIMEOUT_ERROR");
        result.setSuggestion(suggestion);
        result.setCorrelationId(correlationId);
        result.setScriptId(scriptId);
        result.setStartTime(LocalDateTime.now());
        result.setEndTime(LocalDateTime.now());
        result.setDuration(0L);
        result.setDisplayToUser(true);
        result.setErrorDetails(errorDetails);
        
        return result;
    }
    
    /**
     * Create response for security errors
     */
    private ScriptExecutionResult createSecurityErrorResponse(Exception exception, 
                                                             String correlationId, 
                                                             String scriptId) {
        String mainMessage = "Security validation failed";
        String suggestion = "The script execution was blocked due to security concerns. " +
                          "Please review your input parameters and ensure they don't contain " +
                          "potentially dangerous content.";
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("errorType", "SECURITY_ERROR");
        errorDetails.put("securityReason", exception.getMessage());
        
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setSuccess(false);
        result.setErrorMessage(mainMessage);
        result.setErrorCode("SECURITY_ERROR");
        result.setSuggestion(suggestion);
        result.setCorrelationId(correlationId);
        result.setScriptId(scriptId);
        result.setStartTime(LocalDateTime.now());
        result.setEndTime(LocalDateTime.now());
        result.setDuration(0L);
        result.setDisplayToUser(true);
        result.setErrorDetails(errorDetails);
        
        return result;
    }
    
    /**
     * Create response for script execution specific errors
     */
    private ScriptExecutionResult createScriptExecutionErrorResponse(ScriptExecutionException exception, 
                                                                    String correlationId, 
                                                                    String scriptId) {
        String mainMessage = exception.getMessage();
        String suggestion = determineSuggestionFromErrorCode(exception.getErrorCode());
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("errorType", "SCRIPT_EXECUTION_ERROR");
        errorDetails.put("originalErrorCode", exception.getErrorCode());
        errorDetails.put("context", exception.getContext());
        
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setSuccess(false);
        result.setErrorMessage(mainMessage);
        result.setErrorCode(exception.getErrorCode());
        result.setSuggestion(suggestion);
        result.setCorrelationId(exception.getCorrelationId() != null ? exception.getCorrelationId() : correlationId);
        result.setScriptId(scriptId);
        result.setStartTime(LocalDateTime.now());
        result.setEndTime(LocalDateTime.now());
        result.setDuration(0L);
        result.setDisplayToUser(true);
        result.setErrorDetails(errorDetails);
        
        return result;
    }
    
    /**
     * Create response for generic/unexpected errors
     */
    private ScriptExecutionResult createGenericErrorResponse(Exception exception, 
                                                            String correlationId, 
                                                            String scriptId) {
        String mainMessage = "An unexpected error occurred during script execution";
        String suggestion = "An unexpected system error occurred. Please try again later. " +
                          "If the problem persists, contact your system administrator with " +
                          "correlation ID: " + correlationId;
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("errorType", "SYSTEM_ERROR");
        errorDetails.put("exceptionClass", exception.getClass().getSimpleName());
        errorDetails.put("originalMessage", exception.getMessage());
        
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setSuccess(false);
        result.setErrorMessage(mainMessage);
        result.setErrorCode("SYSTEM_ERROR");
        result.setSuggestion(suggestion);
        result.setCorrelationId(correlationId);
        result.setScriptId(scriptId);
        result.setStartTime(LocalDateTime.now());
        result.setEndTime(LocalDateTime.now());
        result.setDuration(0L);
        result.setDisplayToUser(true);
        result.setErrorDetails(errorDetails);
        
        return result;
    }
    
    /**
     * Determine appropriate suggestion based on error code
     */
    private String determineSuggestionFromErrorCode(String errorCode) {
        if (errorCode == null) {
            return "Please try again or contact support if the problem persists.";
        }
        
        switch (errorCode.toUpperCase()) {
            case "CONNECTION_ERROR":
                return "Please check your SSH connection settings and ensure the target server is accessible.";
                
            case "VALIDATION_ERROR":
                return "Please correct the highlighted fields and try again.";
                
            case "TIMEOUT_ERROR":
                return "The operation timed out. Try again or contact your administrator.";
                
            case "SECURITY_ERROR":
                return "Security validation failed. Please review your input parameters.";
                
            case "PERMISSION_ERROR":
                return "You don't have sufficient permissions to perform this operation.";
                
            case "RESOURCE_ERROR":
                return "System resources are currently unavailable. Please try again later.";
                
            case "SCRIPT_NOT_FOUND":
                return "The requested script was not found. Please verify the script ID.";
                
            case "CONFIGURATION_ERROR":
                return "System configuration error. Please contact your administrator.";
                
            default:
                return "Please try again or contact support if the problem persists.";
        }
    }
    
    /**
     * Log error for monitoring and alerting systems
     */
    public void logErrorForMonitoring(String correlationId, String scriptId, String errorCode, 
                                     String errorMessage, Map<String, Object> context) {
        Map<String, Object> monitoringData = new HashMap<>();
        monitoringData.put("correlationId", correlationId);
        monitoringData.put("scriptId", scriptId);
        monitoringData.put("errorCode", errorCode);
        monitoringData.put("errorMessage", errorMessage);
        monitoringData.put("timestamp", LocalDateTime.now());
        monitoringData.putAll(context != null ? context : new HashMap<>());
        
        // Log structured data for monitoring systems
        log.error("SCRIPT_EXECUTION_ERROR: {}", monitoringData);
        
        // Additional logging for specific error patterns that might need immediate attention
        if ("CONNECTION_ERROR".equals(errorCode) || "SYSTEM_ERROR".equals(errorCode)) {
            log.error("HIGH_PRIORITY_ERROR [{}]: {} - {}", correlationId, scriptId, errorMessage);
        }
    }
    
    /**
     * Check if error should trigger reconnection attempt
     */
    public boolean shouldAttemptReconnection(Exception exception) {
        if (exception instanceof ConnectionException) {
            ConnectionException connEx = (ConnectionException) exception;
            return connEx.getRetryAttempt() < 3; // Max 3 retry attempts
        }
        
        // Check if error message indicates connection issues
        String message = exception.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            return lowerMessage.contains("connection") && 
                   (lowerMessage.contains("lost") || lowerMessage.contains("broken") || 
                    lowerMessage.contains("reset") || lowerMessage.contains("timeout"));
        }
        
        return false;
    }
    
    /**
     * Extract actionable error information for user display
     */
    public ErrorDisplayInfo extractDisplayInfo(ScriptExecutionResult errorResult) {
        return ErrorDisplayInfo.builder()
            .title(getErrorTitle(errorResult.getErrorCode()))
            .message(errorResult.getErrorMessage())
            .suggestion(errorResult.getSuggestion())
            .correlationId(errorResult.getCorrelationId())
            .severity(getErrorSeverity(errorResult.getErrorCode()))
            .canRetry(canRetryError(errorResult.getErrorCode()))
            .supportInfo(String.format("Reference ID: %s", errorResult.getCorrelationId()))
            .build();
    }
    
    private String getErrorTitle(String errorCode) {
        switch (errorCode != null ? errorCode.toUpperCase() : "") {
            case "CONNECTION_ERROR": return "Connection Failed";
            case "VALIDATION_ERROR": return "Invalid Parameters";
            case "TIMEOUT_ERROR": return "Operation Timed Out";
            case "SECURITY_ERROR": return "Security Check Failed";
            case "PERMISSION_ERROR": return "Access Denied";
            default: return "Script Execution Failed";
        }
    }
    
    private ErrorSeverity getErrorSeverity(String errorCode) {
        switch (errorCode != null ? errorCode.toUpperCase() : "") {
            case "VALIDATION_ERROR": return ErrorSeverity.WARNING;
            case "SECURITY_ERROR": 
            case "PERMISSION_ERROR": return ErrorSeverity.ERROR;
            case "SYSTEM_ERROR": return ErrorSeverity.CRITICAL;
            default: return ErrorSeverity.ERROR;
        }
    }
    
    private boolean canRetryError(String errorCode) {
        switch (errorCode != null ? errorCode.toUpperCase() : "") {
            case "CONNECTION_ERROR":
            case "TIMEOUT_ERROR":
            case "RESOURCE_ERROR":
                return true;
            case "VALIDATION_ERROR":
            case "SECURITY_ERROR":
            case "PERMISSION_ERROR":
                return false;
            default:
                return true;
        }
    }
    
    /**
     * Error display information for frontend
     */
    @lombok.Data
    @lombok.Builder
    public static class ErrorDisplayInfo {
        private String title;
        private String message;
        private String suggestion;
        private String correlationId;
        private ErrorSeverity severity;
        private boolean canRetry;
        private String supportInfo;
    }
    
    /**
     * Error severity levels
     */
    public enum ErrorSeverity {
        INFO("info"),
        WARNING("warning"), 
        ERROR("error"),
        CRITICAL("critical");
        
        private final String displayName;
        
        ErrorSeverity(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}