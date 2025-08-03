package com.fufu.terminal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket session adapter specifically for monitoring operations.
 * This adapter captures monitoring updates and sends them via STOMP.
 *
 * @author lizelin
 */
@Slf4j
@RequiredArgsConstructor
public class StompMonitoringSessionAdapter implements WebSocketSession {

    private final String sessionId;
    private final SimpMessagingTemplate messagingTemplate;
    private final StompMonitoringService monitoringService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private volatile boolean open = true;

    @Override
    public String getId() {
        return sessionId;
    }

    @Override
    public boolean isOpen() {
        return open && monitoringService.isActivelyMonitoring(sessionId);
    }

    /**
     * Send monitoring updates via STOMP.
     * This method intercepts monitoring messages from the original service.
     */
    @Override
    public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message) throws IOException {
        if (message instanceof TextMessage textMessage) {
            try {
                // Parse the monitoring message
                String payload = textMessage.getPayload();
                @SuppressWarnings("unchecked")
                Map<String, Object> messageMap = objectMapper.readValue(payload, Map.class);
                String messageType = (String) messageMap.get("type");

                // Handle monitoring updates
                if ("monitor_update".equals(messageType)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> monitoringData = (Map<String, Object>) messageMap.get("payload");

                    // Send the monitoring update via STOMP
                    monitoringService.sendMonitoringUpdate(sessionId, monitoringData);

                } else {
                    log.debug("Ignoring non-monitoring message type: {}", messageType);
                }

            } catch (Exception e) {
                log.error("Error processing monitoring message for session {}: {}", sessionId, e.getMessage(), e);
                throw new IOException("Failed to process monitoring message", e);
            }
        }
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public void close() throws IOException {
        close(CloseStatus.NORMAL);
    }

    @Override
    public void close(CloseStatus status) throws IOException {
        open = false;
        monitoringService.cleanupMonitoring(sessionId);
        log.debug("Monitoring session adapter closed for session: {}", sessionId);
    }

    // Minimal implementations for remaining WebSocketSession methods

    @Override
    public URI getUri() {
        return null;
    }

    @Override
    public HttpHeaders getHandshakeHeaders() {
        return new HttpHeaders();
    }

    @Override
    public String getAcceptedProtocol() {
        return "stomp";
    }

    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {
        // Not applicable
    }

    @Override
    public int getTextMessageSizeLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        // Not applicable
    }

    @Override
    public int getBinaryMessageSizeLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public List<org.springframework.web.socket.WebSocketExtension> getExtensions() {
        return List.of();
    }

    @Override
    public Principal getPrincipal() {
        return null;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return null;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return null;
    }
}
