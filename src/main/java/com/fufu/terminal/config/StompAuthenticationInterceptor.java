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
 * STOMP认证拦截器
 * 在STOMP连接过程中处理SSH连接建立和会话管理
 * 
 * @author lizelin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    /**
     * SSH连接映射表
     * 根据STOMP会话ID存储对应的SSH连接
     */
    private final Map<String, SshConnection> connections = new ConcurrentHashMap<>();

    /**
     * 拦截STOMP消息发送前的处理
     * 处理认证和SSH连接建立
     * 
     * @param message STOMP消息
     * @param channel 消息通道
     * @return 处理后的消息
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
     * 处理STOMP CONNECT命令
     * 通过建立SSH连接来处理STOMP连接请求
     * 
     * @param accessor STOMP头访问器
     * @param sessionId 会话ID
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
     * 处理STOMP DISCONNECT命令
     * 通过清理SSH连接来处理STOMP断开请求
     * 
     * @param sessionId 会话ID
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
     * 根据会话ID获取SSH连接
     * 
     * @param sessionId 会话ID
     * @return 对应的SSH连接，如果不存在则返回null
     */
    public SshConnection getConnection(String sessionId) {
        return connections.get(sessionId);
    }

    /**
     * 获取所有活动的SSH连接
     * 用于监控和调试目的
     * 
     * @return 所有活动连接的不可修改映射
     */
    public Map<String, SshConnection> getAllConnections() {
        return Collections.unmodifiableMap(connections);
    }

    /**
     * 解析查询字符串为参数映射
     * 用于从各种来源提取连接参数的工具方法
     * 
     * @param query 查询字符串
     * @return 解析后的参数映射
     */
    private Map<String, String> parseQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        return UriComponentsBuilder.fromUriString("?" + query).build().getQueryParams().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0)));
    }
}