package com.fufu.terminal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.config.StompAuthenticationInterceptor;
import com.fufu.terminal.model.SshConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * STOMP session manager that handles SSH connection lifecycle management
 * and coordinates terminal output forwarding through STOMP messaging.
 *
 * @author lizelin
 */
@Slf4j
@Component
public class StompSessionManager {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final StompAuthenticationInterceptor authInterceptor;
    private final ExecutorService executorService;

    public StompSessionManager(SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper,
            StompAuthenticationInterceptor authInterceptor,
            @Qualifier("taskExecutor") ExecutorService executorService) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.authInterceptor = authInterceptor;
        this.executorService = executorService;
    }

    // Track active output forwarding tasks by session ID
    private final Map<String, Boolean> activeForwarders = new ConcurrentHashMap<>();

    /**
     * Start terminal output forwarding for a given session.
     * This replaces the output forwarding logic from the original WebSocket handler.
     */
    public void startTerminalOutputForwarder(String sessionId) {
        SshConnection connection = authInterceptor.getConnection(sessionId);
        if (connection == null) {
            log.warn("Cannot start output forwarder - no SSH connection found for session: {}", sessionId);
            return;
        }

        // Prevent multiple forwarders for the same session
        if (activeForwarders.putIfAbsent(sessionId, true) != null) {
            log.warn("Output forwarder already active for session: {}", sessionId);
            return;
        }

        log.info("Starting terminal output forwarder for session: {}", sessionId);

        executorService.submit(() -> {
            try (InputStream inputStream = connection.getInputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;

                while (activeForwarders.getOrDefault(sessionId, false) &&
                        (bytesRead = inputStream.read(buffer)) != -1) {

                    String payload = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);

                    // Create terminal data message in the same format as original
                    Map<String, String> response = Map.of(
                            "type", "terminal_data",
                            "payload", payload
                    );

                    // Send to user-specific queue
                    messagingTemplate.convertAndSendToUser(
                            sessionId,
                            "/queue/terminal/data",
                            response
                    );
                }
            } catch (IOException e) {
                if (activeForwarders.getOrDefault(sessionId, false)) {
                    log.error("Error reading from terminal or sending to session {}: ", sessionId, e);
                    sendErrorMessage(sessionId, "Terminal connection error: " + e.getMessage());
                }
            } finally {
                stopTerminalOutputForwarder(sessionId);
                cleanupSession(sessionId);
            }
        });
    }

    /**
     * Stop terminal output forwarding for a session.
     */
    public void stopTerminalOutputForwarder(String sessionId) {
        if (activeForwarders.remove(sessionId) != null) {
            log.info("Stopped terminal output forwarder for session: {}", sessionId);
        }
    }

    /**
     * Clean up session resources.
     */
    public void cleanupSession(String sessionId) {
        log.info("Cleaning up session: {}", sessionId);

        // Stop output forwarding
        stopTerminalOutputForwarder(sessionId);

        // The SSH connection cleanup is handled by the interceptor
        // Additional cleanup can be added here if needed
    }

    /**
     * Send error message to a specific user session.
     */
    public void sendErrorMessage(String sessionId, String errorMessage) {
        try {
            Map<String, String> errorResponse = Map.of(
                    "type", "error",
                    "payload", errorMessage
            );

            messagingTemplate.convertAndSendToUser(
                    sessionId,
                    "/queue/error",
                    errorResponse
            );
        } catch (Exception e) {
            log.error("Failed to send error message to session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Send success message to a specific user session.
     */
    public void sendSuccessMessage(String sessionId, String type, Object payload) {
        try {
            Map<String, Object> response = Map.of(
                    "type", type,
                    "payload", payload
            );

            messagingTemplate.convertAndSendToUser(
                    sessionId,
                    "/queue/response",
                    response
            );
        } catch (Exception e) {
            log.error("Failed to send success message to session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Get SSH connection for a session.
     */
    public SshConnection getConnection(String sessionId) {
        return connectionManager.getConnection(sessionId);
    }

    /**
     * Check if output forwarding is active for a session.
     */
    public boolean isForwardingActive(String sessionId) {
        return activeForwarders.getOrDefault(sessionId, false);
    }

    /**
     * Get count of active sessions.
     */
    public int getActiveSessionCount() {
        return authInterceptor.getAllConnections().size();
    }

    /**
     * Get count of active forwarders.
     */
    public int getActiveForwarderCount() {
        return activeForwarders.size();
    }
}
