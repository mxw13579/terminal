package com.fufu.terminal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter class that wraps STOMP messaging functionality to look like a WebSocketSession.
 * This allows reuse of existing SFTP service methods without modification.
 *
 * @author lizelin
 */
@Slf4j
@RequiredArgsConstructor
public class StompWebSocketSessionAdapter implements WebSocketSession {

    private final String sessionId;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private volatile boolean open = true;

    @Override
    public String getId() {
        return sessionId;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    /**
     * Send a text message via STOMP to the user's queue.
     * This method adapts WebSocket sendMessage calls to STOMP messaging.
     */
    @Override
    public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message) throws IOException {
        if (message instanceof TextMessage textMessage) {
            try {
                // Parse the message to determine the appropriate STOMP destination
                String payload = textMessage.getPayload();
                @SuppressWarnings("unchecked")
                Map<String, Object> messageMap = objectMapper.readValue(payload, Map.class);
                String messageType = (String) messageMap.get("type");

                // Route to appropriate STOMP destination based on message type
                String destination = determineDestination(messageType);

                // 直接发送到具体队列，与终端输出使用相同的模式
                messagingTemplate.convertAndSend(destination + "-user" + sessionId, messageMap);

                log.debug("Sent STOMP message to session {} destination {}: {}",
                         sessionId, destination + "-user" + sessionId, messageType);

            } catch (Exception e) {
                log.error("Error sending STOMP message for session {}: {}", sessionId, e.getMessage(), e);
                throw new IOException("Failed to send STOMP message", e);
            }
        } else {
            log.warn("Unsupported message type for STOMP adapter: {}", message.getClass());
            throw new IOException("Unsupported message type for STOMP adapter");
        }
    }

    /**
     * Determine the appropriate STOMP destination based on message type.
     */
    private String determineDestination(String messageType) {
        if (messageType == null) {
            return "/queue/response";
        }

        return switch (messageType) {
            case "sftp_list_response" -> "/queue/sftp/list";
            case "sftp_download_response" -> "/queue/sftp/download";
            case "sftp_upload_chunk_success" -> "/queue/sftp/upload";
            case "sftp_remote_progress" -> "/queue/sftp/upload";
            case "sftp_upload_final_success" -> "/queue/sftp/upload";
            case "sftp_error" -> "/queue/sftp/error";
            case "error" -> "/queue/errors";
            default -> "/queue/response";
        };
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
        log.debug("STOMP WebSocket adapter closed for session: {} with status: {}", sessionId, status);
    }

    // Remaining WebSocketSession methods with minimal implementations

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
        // Not applicable for STOMP
    }

    @Override
    public int getTextMessageSizeLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        // Not applicable for STOMP
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
