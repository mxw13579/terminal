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
 * WebSocket 会话适配器，专用于监控操作。
 * 该适配器捕获监控更新，并通过 STOMP 协议发送。
 *
 * <p>适用于将 WebSocket 监控消息转发至 STOMP 订阅端。</p>
 *
 * @author lizelin
 */
@Slf4j
@RequiredArgsConstructor
public class StompMonitoringSessionAdapter implements WebSocketSession {

    /** 会话唯一标识 */
    private final String sessionId;
    /** STOMP 消息模板 */
    private final SimpMessagingTemplate messagingTemplate;
    /** 监控服务 */
    private final StompMonitoringService monitoringService;
    /** Jackson 对象映射器（全局共享） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /** 会话属性 */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    /** 会话是否开启 */
    private volatile boolean open = true;

    /**
     * 获取会话 ID。
     *
     * @return 会话唯一标识
     */
    @Override
    public String getId() {
        return sessionId;
    }

    /**
     * 判断会话是否处于开启状态。
     *
     * @return 如果会话开启且正在监控，则返回 true
     */
    @Override
    public boolean isOpen() {
        return open && monitoringService.isActivelyMonitoring(sessionId);
    }

    /**
     * 发送监控更新消息（通过 STOMP）。
     * 拦截原始监控消息，仅处理类型为 monitor_update 的消息。
     *
     * @param message WebSocket 消息
     * @throws IOException 处理消息异常时抛出
     */
    @Override
    public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message) throws IOException {
        if (message instanceof TextMessage textMessage) {
            try {
                // 解析 JSON 消息
                String payload = textMessage.getPayload();
                @SuppressWarnings("unchecked")
                Map<String, Object> messageMap = OBJECT_MAPPER.readValue(payload, Map.class);
                String messageType = (String) messageMap.get("type");

                // 只处理监控更新类型的消息
                if ("monitor_update".equals(messageType)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> monitoringData = (Map<String, Object>) messageMap.get("payload");
                    // 通过监控服务发送 STOMP 消息
                    monitoringService.sendMonitoringUpdate(sessionId, monitoringData);
                } else {
                    log.debug("忽略非监控类型消息: {}", messageType);
                }
            } catch (Exception e) {
                log.error("处理会话 {} 的监控消息异常: {}", sessionId, e.getMessage(), e);
                throw new IOException("处理监控消息失败", e);
            }
        }
    }

    /**
     * 获取会话属性。
     *
     * @return 属性映射
     */
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * 关闭会话（默认正常关闭）。
     *
     * @throws IOException 关闭异常时抛出
     */
    @Override
    public void close() throws IOException {
        close(CloseStatus.NORMAL);
    }

    /**
     * 关闭会话，并清理监控资源。
     *
     * @param status 关闭状态
     * @throws IOException 关闭异常时抛出
     */
    @Override
    public void close(CloseStatus status) throws IOException {
        open = false;
        monitoringService.cleanupMonitoring(sessionId);
        log.debug("监控会话适配器已关闭，session: {}", sessionId);
    }

    /**
     * 获取会话 URI。
     *
     * @return 始终返回 null
     */
    @Override
    public URI getUri() {
        return null;
    }

    /**
     * 获取握手请求头。
     *
     * @return 空的 HttpHeaders
     */
    @Override
    public HttpHeaders getHandshakeHeaders() {
        return new HttpHeaders();
    }

    /**
     * 获取已接受的协议。
     *
     * @return "stomp"
     */
    @Override
    public String getAcceptedProtocol() {
        return "stomp";
    }

    /**
     * 设置文本消息大小限制（无实际作用）。
     *
     * @param messageSizeLimit 消息大小限制
     */
    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {
        // 不适用
    }

    /**
     * 获取文本消息大小限制。
     *
     * @return Integer.MAX_VALUE
     */
    @Override
    public int getTextMessageSizeLimit() {
        return Integer.MAX_VALUE;
    }

    /**
     * 设置二进制消息大小限制（无实际作用）。
     *
     * @param messageSizeLimit 消息大小限制
     */
    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        // 不适用
    }

    /**
     * 获取二进制消息大小限制。
     *
     * @return Integer.MAX_VALUE
     */
    @Override
    public int getBinaryMessageSizeLimit() {
        return Integer.MAX_VALUE;
    }

    /**
     * 获取 WebSocket 扩展列表。
     *
     * @return 空列表
     */
    @Override
    public List<org.springframework.web.socket.WebSocketExtension> getExtensions() {
        return List.of();
    }

    /**
     * 获取会话关联的用户主体。
     *
     * @return 始终返回 null
     */
    @Override
    public Principal getPrincipal() {
        return null;
    }

    /**
     * 获取本地地址。
     *
     * @return 始终返回 null
     */
    @Override
    public InetSocketAddress getLocalAddress() {
        return null;
    }

    /**
     * 获取远程地址。
     *
     * @return 始终返回 null
     */
    @Override
    public InetSocketAddress getRemoteAddress() {
        return null;
    }
}
