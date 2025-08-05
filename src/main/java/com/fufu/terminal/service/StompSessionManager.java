package com.fufu.terminal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.config.StompAuthenticationInterceptor;
import com.fufu.terminal.model.SshConnection;
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
 * STOMP会话管理器，负责SSH连接生命周期管理和终端输出通过STOMP消息转发。
 * <p>
 * 该类封装了会话的启动、停止、清理、消息发送等操作，确保线程安全和高可用性。
 * </p>
 *
 * @author lizelin
 * @since 1.0
 */
@Slf4j
@Component
public class StompSessionManager {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final StompAuthenticationInterceptor authInterceptor;
    private final ExecutorService executorService;

    // 活动输出转发器映射，key为sessionId
    private final Map<String, Boolean> activeForwarders = new ConcurrentHashMap<>();

    /**
     * 构造函数
     *
     * @param messagingTemplate Spring消息模板
     * @param objectMapper      JSON对象映射器
     * @param authInterceptor   STOMP认证拦截器
     * @param executorService   线程池执行器
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

    /**
     * 启动指定会话的终端输出转发线程。
     * 若连接或通道未建立，将发送错误消息给客户端。
     *
     * @param sessionId 会话ID
     */
    public void startTerminalOutputForwarder(String sessionId) {
        SshConnection connection = authInterceptor.getConnection(sessionId);
        if (connection == null) {
            log.warn("启动失败，未找到SSH连接，session: {}", sessionId);
            sendErrorMessage(sessionId, "SSH连接尚未建立，请检查参数后重试。");
            return;
        }

        if (!connection.getJschSession().isConnected()) {
            log.warn("SSH会话未连接，session: {}", sessionId);
            sendErrorMessage(sessionId, "SSH会话已断开，请重新连接。");
            return;
        }

        if (!connection.getChannelShell().isConnected()) {
            log.warn("SSH通道未连接，session: {}", sessionId);
            sendErrorMessage(sessionId, "SSH通道已断开，请重新连接。");
            return;
        }

        // 防止重复启动
        if (activeForwarders.putIfAbsent(sessionId, true) != null) {
            log.warn("输出转发器已激活，session: {}", sessionId);
            return;
        }

        log.info("启动终端输出转发线程，session: {}", sessionId);

        executorService.submit(() -> {
            try (InputStream inputStream = connection.getInputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;

                log.info("终端输出转发线程已启动，session: {}", sessionId);

                while (activeForwarders.getOrDefault(sessionId, false)
                        && (bytesRead = inputStream.read(buffer)) != -1) {

                    String payload = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);

                    log.debug("收到{}字节终端数据，session {}: {}", bytesRead, sessionId,
                            payload.length() > 50 ? payload.substring(0, 50) + "..." : payload);

                    Map<String, Object> response = Map.of(
                            "type", "terminal_data",
                            "payload", payload
                    );

                    // 推送到用户专属队列
                    messagingTemplate.convertAndSend(
                            "/queue/terminal/output-user" + sessionId,
                            response
                    );

                    log.debug("终端数据已发送，session {}", sessionId);
                }

                log.info("终端输出转发线程结束，session: {} (active: {})", sessionId, activeForwarders.getOrDefault(sessionId, false));
            } catch (IOException e) {
                if (activeForwarders.getOrDefault(sessionId, false)) {
                    log.error("读取终端或发送数据异常，session {}: {}", sessionId, e.getMessage(), e);
                    sendErrorMessage(sessionId, "终端连接异常: " + e.getMessage());
                }
            } finally {
                stopTerminalOutputForwarder(sessionId);
                cleanupSession(sessionId);
            }
        });
    }

    /**
     * 停止指定会话的终端输出转发。
     *
     * @param sessionId 会话ID
     */
    public void stopTerminalOutputForwarder(String sessionId) {
        if (activeForwarders.remove(sessionId) != null) {
            log.info("已停止终端输出转发，session: {}", sessionId);
        }
    }

    /**
     * 清理指定会话的相关资源。
     *
     * @param sessionId 会话ID
     */
    public void cleanupSession(String sessionId) {
        log.info("清理会话资源，session: {}", sessionId);
        stopTerminalOutputForwarder(sessionId);
        // SSH连接的清理由拦截器负责
    }

    /**
     * 发送错误消息到指定用户会话。
     *
     * @param sessionId    会话ID
     * @param errorMessage 错误内容
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
            log.error("发送错误消息失败，session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 发送成功消息到指定用户会话。
     *
     * @param sessionId 会话ID
     * @param type      消息类型
     * @param payload   消息内容
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
            log.error("发送成功消息失败，session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 获取指定会话的SSH连接对象。
     *
     * @param sessionId 会话ID
     * @return SSH连接对象，若不存在返回null
     */
    public SshConnection getConnection(String sessionId) {
        return authInterceptor.getConnection(sessionId);
    }

    /**
     * 检查指定会话的输出转发是否处于活动状态。
     *
     * @param sessionId 会话ID
     * @return 活动状态
     */
    public boolean isForwardingActive(String sessionId) {
        return activeForwarders.getOrDefault(sessionId, false);
    }

    /**
     * 获取当前活动会话数量。
     *
     * @return 活动会话数量
     */
    public int getActiveSessionCount() {
        return authInterceptor.getAllConnections().size();
    }

    /**
     * 获取当前活动输出转发器数量。
     *
     * @return 活动转发器数量
     */
    public int getActiveForwarderCount() {
        return activeForwarders.size();
    }

    /**
     * 发送测试消息以验证STOMP消息通道是否正常。
     *
     * @param sessionId 会话ID
     */
    public void sendDirectTestMessage(String sessionId) {
        try {
            Map<String, String> testMessage = Map.of(
                    "type", "terminal_data",
                    "payload", "\r\n=== STOMP Test Message - Direct Messaging Works! ===\r\n"
            );
            log.info("发送STOMP测试消息，session: {}", sessionId);

            // 方式1：标准用户目的地
            messagingTemplate.convertAndSendToUser(
                    sessionId,
                    "/queue/terminal/output",
                    testMessage
            );

            // 方式2：直接推送到特定队列（调试用）
            messagingTemplate.convertAndSend(
                    "/queue/terminal/output-user" + sessionId,
                    testMessage
            );

            log.info("STOMP测试消息已发送，session: {}", sessionId);
        } catch (Exception e) {
            log.error("发送STOMP测试消息失败，session {}: {}", sessionId, e.getMessage(), e);
        }
    }
}
