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

    /**
     * 构造STOMP会话管理器
     * @param messagingTemplate Spring消息模板，用于发送STOMP消息
     * @param objectMapper JSON对象映射器
     * @param authInterceptor STOMP认证拦截器
     * @param executorService 线程池执行器
     */
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
     * 启动指定会话的终端输出转发
     * 替换原始WebSocket处理器中的输出转发逻辑
     * @param sessionId 会话ID
     */
    public void startTerminalOutputForwarder(String sessionId) {
        SshConnection connection = authInterceptor.getConnection(sessionId);
        if (connection == null) {
            log.warn("Cannot start output forwarder - no SSH connection found for session: {}", sessionId);
            sendErrorMessage(sessionId, "SSH connection was not established. Please check your connection parameters and try again.");
            return;
        }

        // Check if SSH session is still connected
        if (!connection.getJschSession().isConnected()) {
            log.warn("SSH session is not connected for session: {}", sessionId);
            sendErrorMessage(sessionId, "SSH session disconnected. Please reconnect.");
            return;
        }

        // Check if channel is still open
        if (!connection.getChannelShell().isConnected()) {
            log.warn("SSH channel is not connected for session: {}", sessionId);
            sendErrorMessage(sessionId, "SSH channel disconnected. Please reconnect.");
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

                log.info("Terminal output forwarder thread started for session: {}", sessionId);

                while (activeForwarders.getOrDefault(sessionId, false) &&
                        (bytesRead = inputStream.read(buffer)) != -1) {

                    String payload = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);

                    log.debug("Received {} bytes from terminal for session {}: {}",
                             bytesRead, sessionId, payload.length() > 50 ? payload.substring(0, 50) + "..." : payload);

                    // Create terminal data message in the same format as original
                    Map<String, Object> response = Map.of(
                            "type", "terminal_data",
                            "payload", payload
                    );

                    // Send to user-specific queue (Spring converts /user/queue/terminal/output to /queue/terminal/output-user{sessionId})
                    messagingTemplate.convertAndSend(
                            "/queue/terminal/output-user" + sessionId,
                            response
                    );


                    log.debug("Sent terminal data to session {}", sessionId);
                }

                log.info("Terminal output forwarder ended for session: {} (active: {})",
                        sessionId, activeForwarders.getOrDefault(sessionId, false));
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
     * 停止指定会话的终端输出转发
     * @param sessionId 会话ID
     */
    public void stopTerminalOutputForwarder(String sessionId) {
        if (activeForwarders.remove(sessionId) != null) {
            log.info("Stopped terminal output forwarder for session: {}", sessionId);
        }
    }

    /**
     * 清理会话资源
     * @param sessionId 会话ID
     */
    public void cleanupSession(String sessionId) {
        log.info("Cleaning up session: {}", sessionId);

        // Stop output forwarding
        stopTerminalOutputForwarder(sessionId);

        // The SSH connection cleanup is handled by the interceptor
        // Additional cleanup can be added here if needed
    }

    /**
     * 发送错误消息到指定用户会话
     * @param sessionId 会话ID
     * @param errorMessage 错误消息内容
     */
    public void sendErrorMessage(String sessionId, String errorMessage) {
        try {
            Map<String, String> errorResponse = Map.of(
                    "type", "error",
                    "payload", errorMessage
            );

            messagingTemplate.convertAndSendToUser(
                    sessionId,
                    "/queue/errors",
                    errorResponse
            );
        } catch (Exception e) {
            log.error("Failed to send error message to session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 发送成功消息到指定用户会话
     * @param sessionId 会话ID
     * @param type 消息类型
     * @param payload 消息内容
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
     * 获取指定会话的SSH连接
     * @param sessionId 会话ID
     * @return SSH连接对象
     */
    public SshConnection getConnection(String sessionId) {
        return authInterceptor.getConnection(sessionId);
    }

    /**
     * 检查指定会话的输出转发是否处于活动状态
     * @param sessionId 会话ID
     * @return 如果输出转发处于活动状态则返回true，否则返回false
     */
    public boolean isForwardingActive(String sessionId) {
        return activeForwarders.getOrDefault(sessionId, false);
    }

    /**
     * 获取活动会话数量
     * @return 活动会话数量
     */
    public int getActiveSessionCount() {
        return authInterceptor.getAllConnections().size();
    }

    /**
     * 获取活动转发器数量
     * @return 活动转发器数量
     */
    public int getActiveForwarderCount() {
        return activeForwarders.size();
    }

    /**
     * 发送直接测试消息以验证STOMP消息传递是否正常工作
     * @param sessionId 会话ID
     */
    public void sendDirectTestMessage(String sessionId) {
        try {
            Map<String, String> testMessage = Map.of(
                    "type", "terminal_data",
                    "payload", "\r\n=== STOMP Test Message - Direct Messaging Works! ===\r\n"
            );

            // Try different destination formats to see which one works
            log.info("Sending direct test message to session: {}", sessionId);

            // Method 1: Standard user destination
            messagingTemplate.convertAndSendToUser(
                    sessionId,
                    "/queue/terminal/output",
                    testMessage
            );

            // Method 2: Direct to specific queue (for debugging)
            messagingTemplate.convertAndSend(
                    "/queue/terminal/output-user" + sessionId,
                    testMessage
            );

            log.info("Direct test message sent to session: {}", sessionId);

        } catch (Exception e) {
            log.error("Failed to send direct test message to session {}: {}", sessionId, e.getMessage(), e);
        }
    }
}
