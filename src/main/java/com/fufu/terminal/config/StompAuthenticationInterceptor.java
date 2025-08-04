package com.fufu.terminal.config;

import com.fufu.terminal.model.SshConnection;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * STOMP authentication interceptor that handles SSH connection establishment
 * and session management during the STOMP connection process.
 * 
 * @author lizelin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    // Store SSH connections by STOMP session ID
    private final Map<String, SshConnection> connections = new ConcurrentHashMap<>();

    /**
     * Intercept STOMP messages before they are sent to handle authentication
     * and SSH connection establishment.
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null) {
            String sessionId = accessor.getSessionId();
            
            // Handle CONNECT command - establish SSH connection
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                handleConnect(accessor, sessionId);
            }
            // Handle DISCONNECT command - cleanup SSH connection
            else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                handleDisconnect(sessionId);
            }
        }
        
        return message;
    }

    /**
     * Handle STOMP CONNECT command by establishing SSH connection.
     */
    private void handleConnect(StompHeaderAccessor accessor, String sessionId) {
        try {
            // Extract SSH connection parameters from STOMP headers
            String host = accessor.getFirstNativeHeader("host");
            String portStr = accessor.getFirstNativeHeader("port");
            String user = accessor.getFirstNativeHeader("user");
            String password = accessor.getFirstNativeHeader("password");
            
            // Validate required parameters
            if (host == null || user == null || password == null) {
                log.error("Missing required SSH connection parameters for session: {}", sessionId);
                throw new IllegalArgumentException("Missing required SSH connection parameters");
            }
            
            int port = portStr != null ? Integer.parseInt(portStr) : 22;
            
            // Establish SSH connection
            JSch jsch = new JSch();
            Session jschSession = jsch.getSession(user, host, port);
            jschSession.setPassword(password);
            jschSession.setConfig("StrictHostKeyChecking", "no");
            jschSession.setConfig("PreferredAuthentications", "password");
            jschSession.setServerAliveInterval(30000); // Keep connection alive
            jschSession.setServerAliveCountMax(3);
            jschSession.connect(30000);

            // Create shell channel
            ChannelShell channel = (ChannelShell) jschSession.openChannel("shell");
            channel.setPtyType("xterm-256color");
            channel.setPtySize(80, 24, 640, 480); // Set initial size
            InputStream inputStream = channel.getInputStream();
            OutputStream outputStream = channel.getOutputStream();
            channel.connect(10000);

            // Store SSH connection
            SshConnection sshConnection = new SshConnection(jsch, jschSession, channel, inputStream, outputStream);
            connections.put(sessionId, sshConnection);
            
            // Store connection in session attributes for easy access in controllers
            accessor.getSessionAttributes().put("sshConnection", sshConnection);
            
            log.info("SSH connection established for STOMP session: {} ({}@{}:{})", 
                    sessionId, user, host, port);
            
        } catch (Exception e) {
            log.error("Failed to establish SSH connection for session {}: {}", sessionId, e.getMessage(), e);
            // Remove any partially created connection
            handleDisconnect(sessionId);
            // Don't throw exception here as it prevents STOMP connection
            // Instead, we'll send an error message after connection is established
            log.warn("SSH connection failed for session {}, STOMP connection will proceed but SSH functionality will be unavailable", sessionId);
        }
    }

    /**
     * Handle STOMP DISCONNECT command by cleaning up SSH connection.
     */
    private void handleDisconnect(String sessionId) {
        if (sessionId != null) {
            SshConnection connection = connections.remove(sessionId);
            if (connection != null) {
                try {
                    connection.disconnect();
                    log.info("SSH connection cleaned up for session: {}", sessionId);
                } catch (Exception e) {
                    log.error("Error cleaning up SSH connection for session {}: {}", sessionId, e.getMessage());
                }
            }
        }
    }

    /**
     * Get SSH connection by session ID.
     */
    public SshConnection getConnection(String sessionId) {
        return connections.get(sessionId);
    }

    /**
     * Get all active connections (for monitoring/debugging).
     */
    public Map<String, SshConnection> getAllConnections() {
        return Collections.unmodifiableMap(connections);
    }

    /**
     * Parse query string into parameter map.
     * Utility method for extracting connection parameters from various sources.
     */
    private Map<String, String> parseQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        return UriComponentsBuilder.fromUriString("?" + query).build().getQueryParams().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0)));
    }
}