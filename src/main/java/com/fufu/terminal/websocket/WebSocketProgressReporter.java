package com.fufu.terminal.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Progress Reporter
 * 
 * Provides real-time progress reporting for script execution via WebSocket
 * with session-based message routing and comprehensive progress tracking.
 */
@Component
@Slf4j
public class WebSocketProgressReporter {
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private WebSocketSessionManager sessionManager;
    
    private final Map<String, ProgressSession> activeSessions = new ConcurrentHashMap<>();
    
    /**
     * Report script execution start
     */
    public void reportStart(String sessionId, String scriptName, Optional<Integer> estimatedTimeSeconds) {
        ProgressSession progressSession = new ProgressSession(sessionId, scriptName);
        activeSessions.put(sessionId, progressSession);
        
        ProgressMessage message = ProgressMessage.builder()
            .type(ProgressType.STARTED)
            .sessionId(sessionId)
            .scriptName(scriptName)
            .stage("Initialization")
            .percentage(0)
            .estimatedRemainingSeconds(estimatedTimeSeconds.orElse(null))
            .timestamp(Instant.now())
            .build();
            
        sendToSession(sessionId, "/topic/progress", message);
        log.info("Started progress reporting for session: {} - {}", sessionId, scriptName);
    }
    
    /**
     * Report progress update
     */
    public void reportProgress(String sessionId, String stage, Integer percentage) {
        reportProgress(sessionId, stage, percentage, null, null);
    }
    
    /**
     * Report progress update with details
     */
    public void reportProgress(String sessionId, String stage, Integer percentage, 
                             String details, Integer estimatedRemainingSeconds) {
        ProgressSession session = activeSessions.get(sessionId);
        if (session == null) {
            log.warn("No active progress session found for sessionId: {}", sessionId);
            return;
        }
        
        session.updateProgress(stage, percentage, details);
        
        ProgressMessage message = ProgressMessage.builder()
            .type(ProgressType.PROGRESS)
            .sessionId(sessionId)
            .scriptName(session.getScriptName())
            .stage(stage)
            .percentage(percentage)
            .details(details)
            .estimatedRemainingSeconds(estimatedRemainingSeconds)
            .timestamp(Instant.now())
            .elapsedSeconds(session.getElapsedSeconds())
            .build();
            
        sendToSession(sessionId, "/topic/progress", message);
        log.debug("Progress update for session {}: {}% - {}", sessionId, percentage, stage);
    }
    
    /**
     * Report script execution completion
     */
    public void reportCompletion(String sessionId, ScriptExecutionResult result) {
        ProgressSession session = activeSessions.remove(sessionId);
        if (session == null) {
            log.warn("No active progress session found for completion: {}", sessionId);
            return;
        }
        
        ProgressMessage message = ProgressMessage.builder()
            .type(result.isSuccess() ? ProgressType.COMPLETED : ProgressType.FAILED)
            .sessionId(sessionId)
            .scriptName(session.getScriptName())
            .stage("Completed")
            .percentage(100)
            .details(result.getMessage())
            .timestamp(Instant.now())
            .elapsedSeconds(session.getElapsedSeconds())
            .result(result)
            .build();
            
        sendToSession(sessionId, "/topic/progress", message);
        log.info("Completed progress reporting for session: {} - Success: {}", 
                sessionId, result.isSuccess());
    }
    
    /**
     * Report error during execution
     */
    public void reportError(String sessionId, Throwable error) {
        ProgressSession session = activeSessions.remove(sessionId);
        String scriptName = session != null ? session.getScriptName() : "Unknown";
        
        ErrorDetails errorDetails = ErrorDetails.builder()
            .type(error.getClass().getSimpleName())
            .message(error.getMessage())
            .userMessage(generateUserFriendlyErrorMessage(error))
            .suggestions(generateErrorSuggestions(error))
            .recoverable(isRecoverableError(error))
            .build();
        
        ProgressMessage message = ProgressMessage.builder()
            .type(ProgressType.ERROR)
            .sessionId(sessionId)
            .scriptName(scriptName)
            .stage("Error")
            .percentage(null)
            .details(error.getMessage())
            .timestamp(Instant.now())
            .elapsedSeconds(session != null ? session.getElapsedSeconds() : null)
            .error(errorDetails)
            .build();
            
        sendToSession(sessionId, "/topic/progress", message);
        log.error("Error reported for session: {} - {}", sessionId, error.getMessage(), error);
    }
    
    /**
     * Report timeout during execution
     */
    public void reportTimeout(String sessionId, String timeoutMessage) {
        ProgressSession session = activeSessions.remove(sessionId);
        String scriptName = session != null ? session.getScriptName() : "Unknown";
        
        ProgressMessage message = ProgressMessage.builder()
            .type(ProgressType.TIMEOUT)
            .sessionId(sessionId)
            .scriptName(scriptName)
            .stage("Timeout")
            .percentage(null)
            .details(timeoutMessage)
            .timestamp(Instant.now())
            .elapsedSeconds(session != null ? session.getElapsedSeconds() : null)
            .build();
            
        sendToSession(sessionId, "/topic/progress", message);
        log.warn("Timeout reported for session: {} - {}", sessionId, timeoutMessage);
    }
    
    /**
     * Send message to specific session
     */
    private void sendToSession(String sessionId, String destination, Object message) {
        try {
            String userId = sessionManager.getUserIdBySessionId(sessionId);
            if (userId != null) {
                messagingTemplate.convertAndSendToUser(userId, destination, message);
                log.trace("Sent WebSocket message to user {} for session {}: {}", userId, sessionId, destination);
            } else {
                log.warn("No user found for sessionId: {}", sessionId);
            }
        } catch (Exception e) {
            log.error("Failed to send WebSocket message for session: {}", sessionId, e);
        }
    }
    
    /**
     * Generate user-friendly error messages
     */
    private String generateUserFriendlyErrorMessage(Throwable error) {
        if (error instanceof com.fufu.terminal.ssh.SshConnectionException) {
            return "Failed to connect to the server. Please check your SSH configuration and network connectivity.";
        } else if (error instanceof java.util.concurrent.TimeoutException) {
            return "The operation timed out. Please try again or increase the timeout value.";
        } else if (error instanceof IllegalArgumentException) {
            return "Invalid parameters provided. Please check your input and try again.";
        } else if (error instanceof SecurityException) {
            return "Access denied. Please check your permissions and authentication.";
        }
        return "An unexpected error occurred. Please contact support if the problem persists.";
    }
    
    /**
     * Generate error recovery suggestions
     */
    private List<String> generateErrorSuggestions(Throwable error) {
        List<String> suggestions = new ArrayList<>();
        
        if (error instanceof com.fufu.terminal.ssh.SshConnectionException) {
            suggestions.add("Verify SSH credentials are correct");
            suggestions.add("Check network connectivity to the target server");
            suggestions.add("Ensure SSH service is running on the target server");
            suggestions.add("Check firewall settings on both client and server");
        } else if (error instanceof java.util.concurrent.TimeoutException) {
            suggestions.add("Increase the execution timeout value");
            suggestions.add("Check server performance and load");
            suggestions.add("Try executing the script during off-peak hours");
        } else if (error instanceof IllegalArgumentException) {
            suggestions.add("Review and correct the script parameters");
            suggestions.add("Check parameter types and formats");
            suggestions.add("Refer to script documentation for valid parameters");
        }
        
        return suggestions;
    }
    
    /**
     * Check if error is recoverable
     */
    private boolean isRecoverableError(Throwable error) {
        return error instanceof java.util.concurrent.TimeoutException ||
               error instanceof IllegalArgumentException ||
               error instanceof com.fufu.terminal.ssh.SshConnectionException;
    }
    
    /**
     * Get active session information
     */
    public Optional<ProgressSession> getActiveSession(String sessionId) {
        return Optional.ofNullable(activeSessions.get(sessionId));
    }
    
    /**
     * Clean up session
     */
    public void cleanupSession(String sessionId) {
        ProgressSession session = activeSessions.remove(sessionId);
        if (session != null) {
            log.debug("Cleaned up progress session: {}", sessionId);
        }
    }
    
    /**
     * Get all active sessions
     */
    public Map<String, ProgressSession> getActiveSessions() {
        return Map.copyOf(activeSessions);
    }
}