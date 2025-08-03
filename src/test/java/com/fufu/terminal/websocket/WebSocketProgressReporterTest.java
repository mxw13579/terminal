package com.fufu.terminal.websocket;

import com.fufu.terminal.script.model.ScriptResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebSocketProgressReporter
 * Tests real-time progress reporting via WebSocket STOMP
 */
@ExtendWith(MockitoExtension.class)
class WebSocketProgressReporterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private WebSocketSessionManager sessionManager;

    private WebSocketProgressReporter progressReporter;

    @BeforeEach
    void setUp() {
        progressReporter = new WebSocketProgressReporter();
        
        // Use reflection to set the mocked dependencies
        try {
            var messagingField = WebSocketProgressReporter.class.getDeclaredField("messagingTemplate");
            messagingField.setAccessible(true);
            messagingField.set(progressReporter, messagingTemplate);
            
            var sessionField = WebSocketProgressReporter.class.getDeclaredField("sessionManager");
            sessionField.setAccessible(true);
            sessionField.set(progressReporter, sessionManager);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set mocked dependencies", e);
        }
    }

    @Test
    @DisplayName("Should report script execution start")
    void shouldReportScriptExecutionStart() {
        // Arrange
        String sessionId = "test-session-123";
        String scriptName = "Test Script";
        String userId = "test-user";
        Optional<Integer> estimatedTime = Optional.of(60);
        
        when(sessionManager.getUserIdBySessionId(sessionId)).thenReturn(userId);
        
        // Act
        progressReporter.reportStart(sessionId, scriptName, estimatedTime);
        
        // Assert
        verify(messagingTemplate).convertAndSendToUser(
            eq(userId), 
            eq("/topic/progress"), 
            argThat(message -> {
                if (message instanceof ProgressMessage pm) {
                    return pm.getType() == ProgressType.STARTED &&
                           pm.getSessionId().equals(sessionId) &&
                           pm.getScriptName().equals(scriptName) &&
                           pm.getPercentage() == 0 &&
                           pm.getEstimatedRemainingSeconds().equals(60);
                }
                return false;
            })
        );
        
        // Verify session is tracked
        Optional<ProgressSession> session = progressReporter.getActiveSession(sessionId);
        assertThat(session).isPresent();
        assertThat(session.get().getScriptName()).isEqualTo(scriptName);
    }

    @Test
    @DisplayName("Should report progress updates")
    void shouldReportProgressUpdates() {
        // Arrange
        String sessionId = "test-session-123";
        String scriptName = "Test Script";
        String userId = "test-user";
        
        when(sessionManager.getUserIdBySessionId(sessionId)).thenReturn(userId);
        
        // Start progress reporting first
        progressReporter.reportStart(sessionId, scriptName, Optional.empty());
        
        // Act
        progressReporter.reportProgress(sessionId, "Installing packages", 50, "Installing Docker...", 30);
        
        // Assert
        verify(messagingTemplate, times(2)).convertAndSendToUser(
            eq(userId), 
            eq("/topic/progress"), 
            any(ProgressMessage.class)
        );
        
        // Verify the progress message
        verify(messagingTemplate).convertAndSendToUser(
            eq(userId), 
            eq("/topic/progress"), 
            argThat(message -> {
                if (message instanceof ProgressMessage pm) {
                    return pm.getType() == ProgressType.PROGRESS &&
                           pm.getPercentage() == 50 &&
                           pm.getStage().equals("Installing packages") &&
                           pm.getDetails().equals("Installing Docker...");
                }
                return false;
            })
        );
    }

    @Test
    @DisplayName("Should report script completion")
    void shouldReportScriptCompletion() {
        // Arrange
        String sessionId = "test-session-123";
        String scriptName = "Test Script";
        String userId = "test-user";
        ScriptResult result = ScriptResult.success("Script executed successfully");
        
        when(sessionManager.getUserIdBySessionId(sessionId)).thenReturn(userId);
        
        // Start progress reporting first
        progressReporter.reportStart(sessionId, scriptName, Optional.empty());
        
        // Act
        progressReporter.reportCompletion(sessionId, result);
        
        // Assert
        verify(messagingTemplate).convertAndSendToUser(
            eq(userId), 
            eq("/topic/progress"), 
            argThat(message -> {
                if (message instanceof ProgressMessage pm) {
                    return pm.getType() == ProgressType.COMPLETED &&
                           pm.getPercentage() == 100 &&
                           pm.getStage().equals("Completed") &&
                           pm.getResult() != null &&
                           pm.getResult().isSuccess();
                }
                return false;
            })
        );
        
        // Verify session is cleaned up
        assertThat(progressReporter.getActiveSession(sessionId)).isEmpty();
    }

    @Test
    @DisplayName("Should report script failure")
    void shouldReportScriptFailure() {
        // Arrange
        String sessionId = "test-session-123";
        String scriptName = "Test Script";
        String userId = "test-user";
        ScriptResult result = ScriptResult.failure("Script execution failed");
        
        when(sessionManager.getUserIdBySessionId(sessionId)).thenReturn(userId);
        
        // Start progress reporting first
        progressReporter.reportStart(sessionId, scriptName, Optional.empty());
        
        // Act
        progressReporter.reportCompletion(sessionId, result);
        
        // Assert
        verify(messagingTemplate).convertAndSendToUser(
            eq(userId), 
            eq("/topic/progress"), 
            argThat(message -> {
                if (message instanceof ProgressMessage pm) {
                    return pm.getType() == ProgressType.FAILED &&
                           pm.getPercentage() == 100 &&
                           pm.getStage().equals("Completed") &&
                           pm.getResult() != null &&
                           !pm.getResult().isSuccess();
                }
                return false;
            })
        );
    }

    @Test
    @DisplayName("Should report execution errors with user-friendly messages")
    void shouldReportExecutionErrorsWithUserFriendlyMessages() {
        // Arrange
        String sessionId = "test-session-123";
        String scriptName = "Test Script";
        String userId = "test-user";
        Exception error = new RuntimeException("Connection failed");
        
        when(sessionManager.getUserIdBySessionId(sessionId)).thenReturn(userId);
        
        // Start progress reporting first
        progressReporter.reportStart(sessionId, scriptName, Optional.empty());
        
        // Act
        progressReporter.reportError(sessionId, error);
        
        // Assert
        verify(messagingTemplate).convertAndSendToUser(
            eq(userId), 
            eq("/topic/progress"), 
            argThat(message -> {
                if (message instanceof ProgressMessage pm) {
                    return pm.getType() == ProgressType.ERROR &&
                           pm.getStage().equals("Error") &&
                           pm.getError() != null &&
                           pm.getError().getMessage().equals("Connection failed");
                }
                return false;
            })
        );
        
        // Verify session is cleaned up
        assertThat(progressReporter.getActiveSession(sessionId)).isEmpty();
    }

    @Test
    @DisplayName("Should report timeout with appropriate message")
    void shouldReportTimeoutWithAppropriateMessage() {
        // Arrange
        String sessionId = "test-session-123";
        String scriptName = "Test Script";
        String userId = "test-user";
        String timeoutMessage = "Script execution timed out after 300 seconds";
        
        when(sessionManager.getUserIdBySessionId(sessionId)).thenReturn(userId);
        
        // Start progress reporting first
        progressReporter.reportStart(sessionId, scriptName, Optional.empty());
        
        // Act
        progressReporter.reportTimeout(sessionId, timeoutMessage);
        
        // Assert
        verify(messagingTemplate).convertAndSendToUser(
            eq(userId), 
            eq("/topic/progress"), 
            argThat(message -> {
                if (message instanceof ProgressMessage pm) {
                    return pm.getType() == ProgressType.TIMEOUT &&
                           pm.getStage().equals("Timeout") &&
                           pm.getDetails().equals(timeoutMessage);
                }
                return false;
            })
        );
    }

    @Test
    @DisplayName("Should handle missing user session gracefully")
    void shouldHandleMissingUserSessionGracefully() {
        // Arrange
        String sessionId = "non-existent-session";
        String scriptName = "Test Script";
        
        when(sessionManager.getUserIdBySessionId(sessionId)).thenReturn(null);
        
        // Act
        progressReporter.reportStart(sessionId, scriptName, Optional.empty());
        
        // Assert
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    @DisplayName("Should handle progress update for non-existent session")
    void shouldHandleProgressUpdateForNonExistentSession() {
        // Arrange
        String sessionId = "non-existent-session";
        
        // Act
        progressReporter.reportProgress(sessionId, "Some stage", 50);
        
        // Assert - Should not throw exception, just log warning
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    @DisplayName("Should generate appropriate error suggestions for SSH connection errors")
    void shouldGenerateAppropriateErrorSuggestionsForSshConnectionErrors() {
        // Arrange
        String sessionId = "test-session-123";
        String scriptName = "Test Script";
        String userId = "test-user";
        Exception sshError = new com.fufu.terminal.ssh.SshConnectionException("Connection refused");
        
        when(sessionManager.getUserIdBySessionId(sessionId)).thenReturn(userId);
        progressReporter.reportStart(sessionId, scriptName, Optional.empty());
        
        // Act
        progressReporter.reportError(sessionId, sshError);
        
        // Assert
        verify(messagingTemplate).convertAndSendToUser(
            eq(userId), 
            eq("/topic/progress"), 
            argThat(message -> {
                if (message instanceof ProgressMessage pm && pm.getError() != null) {
                    ErrorDetails error = pm.getError();
                    return error.getUserMessage().contains("Failed to connect to the server") &&
                           error.getSuggestions().contains("Verify SSH credentials are correct") &&
                           error.isRecoverable();
                }
                return false;
            })
        );
    }

    @Test
    @DisplayName("Should generate appropriate error suggestions for timeout errors")
    void shouldGenerateAppropriateErrorSuggestionsForTimeoutErrors() {
        // Arrange
        String sessionId = "test-session-123";
        String scriptName = "Test Script";
        String userId = "test-user";
        Exception timeoutError = new java.util.concurrent.TimeoutException("Execution timed out");
        
        when(sessionManager.getUserIdBySessionId(sessionId)).thenReturn(userId);
        progressReporter.reportStart(sessionId, scriptName, Optional.empty());
        
        // Act
        progressReporter.reportError(sessionId, timeoutError);
        
        // Assert
        verify(messagingTemplate).convertAndSendToUser(
            eq(userId), 
            eq("/topic/progress"), 
            argThat(message -> {
                if (message instanceof ProgressMessage pm && pm.getError() != null) {
                    ErrorDetails error = pm.getError();
                    return error.getUserMessage().contains("operation timed out") &&
                           error.getSuggestions().contains("Increase the execution timeout value") &&
                           error.isRecoverable();
                }
                return false;
            })
        );
    }

    @Test
    @DisplayName("Should clean up session manually")
    void shouldCleanUpSessionManually() {
        // Arrange
        String sessionId = "test-session-123";
        String scriptName = "Test Script";
        String userId = "test-user";
        
        when(sessionManager.getUserIdBySessionId(sessionId)).thenReturn(userId);
        progressReporter.reportStart(sessionId, scriptName, Optional.empty());
        
        // Verify session exists
        assertThat(progressReporter.getActiveSession(sessionId)).isPresent();
        
        // Act
        progressReporter.cleanupSession(sessionId);
        
        // Assert
        assertThat(progressReporter.getActiveSession(sessionId)).isEmpty();
    }

    @Test
    @DisplayName("Should track all active sessions")
    void shouldTrackAllActiveSessions() {
        // Arrange
        String userId = "test-user";
        when(sessionManager.getUserIdBySessionId(anyString())).thenReturn(userId);
        
        // Act
        progressReporter.reportStart("session1", "Script 1", Optional.empty());
        progressReporter.reportStart("session2", "Script 2", Optional.empty());
        progressReporter.reportStart("session3", "Script 3", Optional.empty());
        
        // Assert
        var activeSessions = progressReporter.getActiveSessions();
        assertThat(activeSessions).hasSize(3);
        assertThat(activeSessions.keySet()).containsExactlyInAnyOrder("session1", "session2", "session3");
    }

    @Test
    @DisplayName("Should handle WebSocket messaging errors gracefully")
    void shouldHandleWebSocketMessagingErrorsGracefully() {
        // Arrange
        String sessionId = "test-session-123";
        String scriptName = "Test Script";
        String userId = "test-user";
        
        when(sessionManager.getUserIdBySessionId(sessionId)).thenReturn(userId);
        doThrow(new RuntimeException("WebSocket error")).when(messagingTemplate)
            .convertAndSendToUser(any(), any(), any());
        
        // Act & Assert - Should not throw exception
        assertThatCode(() -> progressReporter.reportStart(sessionId, scriptName, Optional.empty()))
            .doesNotThrowAnyException();
    }
}