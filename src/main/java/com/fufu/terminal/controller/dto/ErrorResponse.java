package com.fufu.terminal.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Enhanced error response with hierarchical error handling
 * Provides user-friendly error messages and actionable resolution steps
 */
@Data
@Builder
public class ErrorResponse {
    private String errorCode;
    private String message;
    private String userMessage;
    private List<String> suggestions;
    private Map<String, Object> context;
    private long timestamp;
    private String correlationId;
    private ErrorType errorType;
    private boolean recoverable;
    
    public static ErrorResponse fromException(Exception e, String correlationId) {
        ErrorType errorType = determineErrorType(e);
        
        return ErrorResponse.builder()
            .errorCode(errorType.name())
            .message(e.getMessage())
            .userMessage(generateUserFriendlyMessage(e, errorType))
            .suggestions(generateSuggestions(e, errorType))
            .context(extractContext(e))
            .timestamp(System.currentTimeMillis())
            .correlationId(correlationId)
            .errorType(errorType)
            .recoverable(errorType.isUserRecoverable())
            .build();
    }
    
    public static ErrorResponse validationError(String message, List<String> suggestions) {
        return ErrorResponse.builder()
            .errorCode(ErrorType.VALIDATION_ERROR.name())
            .message(message)
            .userMessage("Please check your input and try again")
            .suggestions(suggestions)
            .timestamp(System.currentTimeMillis())
            .errorType(ErrorType.VALIDATION_ERROR)
            .recoverable(true)
            .build();
    }
    
    public static ErrorResponse systemError(String message, String correlationId) {
        return ErrorResponse.builder()
            .errorCode(ErrorType.INTERNAL_ERROR.name())
            .message(message)
            .userMessage("An internal error occurred. Please try again later.")
            .suggestions(List.of("Try again in a few minutes", "Contact support if the problem persists"))
            .timestamp(System.currentTimeMillis())
            .correlationId(correlationId)
            .errorType(ErrorType.INTERNAL_ERROR)
            .recoverable(false)
            .build();
    }
    
    private static ErrorType determineErrorType(Exception e) {
        String className = e.getClass().getSimpleName();
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        
        if (className.contains("Validation") || message.contains("invalid") || message.contains("validation")) {
            return ErrorType.VALIDATION_ERROR;
        } else if (className.contains("Ssh") || message.contains("ssh") || message.contains("connection")) {
            return ErrorType.SSH_CONNECTION_ERROR;
        } else if (className.contains("Timeout") || message.contains("timeout")) {
            return ErrorType.USER_INTERACTION_TIMEOUT;
        } else if (className.contains("Permission") || message.contains("permission") || message.contains("access denied")) {
            return ErrorType.PERMISSION_DENIED;
        } else if (className.contains("Configuration") || message.contains("configuration")) {
            return ErrorType.CONFIGURATION_ERROR;
        } else {
            return ErrorType.INTERNAL_ERROR;
        }
    }
    
    private static String generateUserFriendlyMessage(Exception e, ErrorType errorType) {
        switch (errorType) {
            case VALIDATION_ERROR:
                return "Please check your input parameters and ensure all required fields are filled correctly.";
            case SSH_CONNECTION_ERROR:
                return "Unable to connect to the server. Please verify your SSH connection settings.";
            case USER_INTERACTION_TIMEOUT:
                return "No response received within the expected time. The operation will use default values or be cancelled.";
            case PERMISSION_DENIED:
                return "You don't have permission to perform this action. Please contact your administrator.";
            case CONFIGURATION_ERROR:
                return "There's an issue with the system configuration. Please contact support.";
            default:
                return "An unexpected error occurred. Please try again or contact support if the problem persists.";
        }
    }
    
    private static List<String> generateSuggestions(Exception e, ErrorType errorType) {
        switch (errorType) {
            case VALIDATION_ERROR:
                return List.of(
                    "Check that all required fields are filled",
                    "Verify parameter formats match the expected types",
                    "Review validation error messages for specific issues"
                );
            case SSH_CONNECTION_ERROR:
                return List.of(
                    "Verify the SSH host and port are correct",
                    "Check that your username and password are valid",
                    "Ensure the server is running and accessible",
                    "Test the connection separately if possible"
                );
            case USER_INTERACTION_TIMEOUT:
                return List.of(
                    "Respond more quickly to interaction prompts",
                    "Check if default values are acceptable",
                    "Consider pre-configuring parameters to avoid interactions"
                );
            case PERMISSION_DENIED:
                return List.of(
                    "Contact your administrator to request access",
                    "Verify you're logged in with the correct account",
                    "Check if your account has the necessary permissions"
                );
            default:
                return List.of(
                    "Try the operation again in a few minutes",
                    "Check system status and connectivity",
                    "Contact support with the correlation ID if the problem persists"
                );
        }
    }
    
    private static Map<String, Object> extractContext(Exception e) {
        Map<String, Object> context = new java.util.HashMap<>();
        context.put("exceptionType", e.getClass().getSimpleName());
        if (e.getCause() != null) {
            context.put("causeType", e.getCause().getClass().getSimpleName());
            context.put("causeMessage", e.getCause().getMessage());
        }
        return context;
    }
    
    /**
     * Error type enumeration with user recoverability information
     */
    public enum ErrorType {
        // User-related errors
        VALIDATION_ERROR("Invalid input parameters", true),
        USER_INTERACTION_TIMEOUT("User interaction timeout", true),
        PERMISSION_DENIED("Insufficient permissions", true),
        
        // System-related errors
        SSH_CONNECTION_ERROR("SSH connectivity issues", false),
        SCRIPT_EXECUTION_ERROR("Script runtime errors", false),
        SYSTEM_RESOURCE_ERROR("System resource limitations", false),
        
        // Configuration-related errors
        CONFIGURATION_ERROR("Configuration or setup errors", false),
        MIRROR_SELECTION_ERROR("Mirror selection failed", false),
        
        // Internal errors
        INTERNAL_ERROR("Internal system error", false);
        
        private final String description;
        private final boolean userRecoverable;
        
        ErrorType(String description, boolean userRecoverable) {
            this.description = description;
            this.userRecoverable = userRecoverable;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isUserRecoverable() {
            return userRecoverable;
        }
    }
}