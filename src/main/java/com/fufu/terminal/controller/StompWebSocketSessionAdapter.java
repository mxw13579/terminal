package com.fufu.terminal.controller;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>
 * STOMP WebSocket 会话适配器，将 STOMP 消息功能包装为 WebSocketSession 接口。
 * 该适配器用于复用现有 SFTP 服务方法，无需修改其对 WebSocketSession 的依赖。
 * 通过将 WebSocket 消息适配为 STOMP 消息，实现终端与后端的透明通信。
 * </p>
 *
 * <p>
 * 本类线程安全，适用于多线程环境。
 * </p>
 *
 * @author lizelin
 * @since 2024-06
 */
@Slf4j
@RequiredArgsConstructor
public class StompWebSocketSessionAdapter implements WebSocketSession {

    /** 会话唯一标识 */
    private final String sessionId;

    /** Spring 的 STOMP 消息模板 */
    private final SimpMessagingTemplate messagingTemplate;

    /** Jackson 对象映射器，用于 JSON 解析 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 会话属性，线程安全 */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    /** 会话是否打开，线程安全 */
    private volatile boolean open = true;

    /**
     * 获取会话唯一标识。
     *
     * @return 会话ID
     */
    @Override
    public String getId() {
        return sessionId;
    }

    /**
     * 判断会话是否处于打开状态。
     *
     * @return true 表示会话打开
     */
    @Override
    public boolean isOpen() {
        return open;
    }

    /**
     * 发送文本消息，将 WebSocket 消息适配为 STOMP 消息并发送到指定队列。
     *
     * @param message WebSocket 消息
     * @throws IOException 发送失败时抛出
     */
    @Override
    public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message) throws IOException {
        if (message instanceof TextMessage textMessage) {
            try {
                // 解析消息内容，获取消息类型
                String payload = textMessage.getPayload();
                Map<String, Object> messageMap = objectMapper.readValue(payload, new TypeReference<>() {});
                String messageType = (String) messageMap.get("type");

                // 根据消息类型确定 STOMP 目标队列
                String destination = determineDestination(messageType);

                // 发送消息到指定队列，队列名带上 sessionId 保证私有性
                messagingTemplate.convertAndSend(destination + "-user" + sessionId, messageMap);

                log.debug("STOMP 消息已发送，sessionId={}, destination={}, type={}", sessionId, destination + "-user" + sessionId, messageType);

            } catch (Exception e) {
                log.error("发送 STOMP 消息失败，sessionId={}, error={}", sessionId, e.getMessage(), e);
                throw new IOException("发送 STOMP 消息失败", e);
            }
        } else {
            log.warn("不支持的消息类型：{}", message.getClass());
            throw new IOException("STOMP 适配器不支持该消息类型");
        }
    }

    /**
     * 根据消息类型确定 STOMP 目标队列。
     *
     * @param messageType 消息类型
     * @return STOMP 目标队列
     */
    private String determineDestination(String messageType) {
        if (messageType == null) {
            return "/queue/response";
        }
        // 根据不同类型路由到不同队列
        return switch (messageType) {
            case "sftp_list_response" -> "/queue/sftp/list";
            case "sftp_download_response" -> "/queue/sftp/download";
            case "sftp_upload_chunk_success", "sftp_remote_progress", "sftp_upload_final_success" -> "/queue/sftp/upload";
            case "sftp_error" -> "/queue/sftp/error";
            case "error" -> "/queue/errors";
            default -> "/queue/response";
        };
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
     * 关闭会话，使用默认关闭状态。
     *
     * @throws IOException 关闭异常
     */
    @Override
    public void close() throws IOException {
        close(CloseStatus.NORMAL);
    }

    /**
     * 关闭会话，并指定关闭状态。
     *
     * @param status 关闭状态
     * @throws IOException 关闭异常
     */
    @Override
    public void close(CloseStatus status) throws IOException {
        open = false;
        log.debug("STOMP WebSocket 适配器关闭，sessionId={}, status={}", sessionId, status);
    }

    /**
     * 获取 URI。
     *
     * @return null，STOMP 不适用
     */
    @Override
    public URI getUri() {
        return null;
    }

    /**
     * 获取握手头信息。
     *
     * @return 空 HttpHeaders
     */
    @Override
    public HttpHeaders getHandshakeHeaders() {
        return new HttpHeaders();
    }

    /**
     * 获取协议名称。
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
     * @param messageSizeLimit 消息大小
     */
    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {
        // STOMP 不适用
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
     * @param messageSizeLimit 消息大小
     */
    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        // STOMP 不适用
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
     * 获取 WebSocket 扩展。
     *
     * @return 空列表
     */
    @Override
    public List<org.springframework.web.socket.WebSocketExtension> getExtensions() {
        return Collections.emptyList();
    }

    /**
     * 获取会话关联的 Principal。
     *
     * @return null
     */
    @Override
    public Principal getPrincipal() {
        return null;
    }

    /**
     * 获取本地地址。
     *
     * @return null
     */
    @Override
    public InetSocketAddress getLocalAddress() {
        return null;
    }

    /**
     * 获取远程地址。
     *
     * @return null
     */
    @Override
    public InetSocketAddress getRemoteAddress() {
        return null;
    }
}
