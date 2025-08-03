package com.fufu.terminal.adapter;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * STOMP会话适配器，将STOMP会话适配为WebSocketSession接口
 * 使现有的服务能够与STOMP协议兼容
 *
 * @author lizelin
 */
public class StompSessionAdapter implements WebSocketSession {

    private final String sessionId;
    private final SimpMessagingTemplate messagingTemplate;

    public StompSessionAdapter(String sessionId, SimpMessagingTemplate messagingTemplate) {
        this.sessionId = sessionId;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public String getId() {
        return sessionId;
    }

    @Override
    public URI getUri() {
        return null; // STOMP适配器不需要URI
    }

    @Override
    public HttpHeaders getHandshakeHeaders() {
        // 空的握手头
        return new HttpHeaders();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of(); // 空属性
    }

    @Override
    public Principal getPrincipal() {
        return null; // 暂不支持认证
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return null; // STOMP适配器不需要地址信息
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return null; // STOMP适配器不需要地址信息
    }

    @Override
    public String getAcceptedProtocol() {
        return "stomp";
    }

    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {
        // STOMP适配器忽略消息大小限制设置
    }

    @Override
    public int getTextMessageSizeLimit() {
        return 0; // 返回默认值
    }

    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        // STOMP适配器忽略二进制消息大小限制设置
    }

    @Override
    public int getBinaryMessageSizeLimit() {
        return 0; // 返回默认值
    }

    @Override
    public List<WebSocketExtension> getExtensions() {
        return List.of(); // 空扩展列表
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) throws IOException {
        if (message instanceof TextMessage) {
            TextMessage textMessage = (TextMessage) message;
            String payload = textMessage.getPayload();

            // 根据消息内容路由到不同的STOMP队列
            routeMessage(payload);
        }
    }

    /**
     * 根据消息内容路由到不同的STOMP队列
     */
    private void routeMessage(String jsonMessage) {
        try {
            if (jsonMessage.contains("\"type\":\"sftp_list_response\"")) {
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/sftp/list", jsonMessage);
            } else if (jsonMessage.contains("\"type\":\"sftp_upload")) {
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/sftp/upload", jsonMessage);
            } else if (jsonMessage.contains("\"type\":\"sftp_download")) {
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/sftp/download", jsonMessage);
            } else if (jsonMessage.contains("\"type\":\"sftp_error")) {
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/sftp/error", jsonMessage);
            } else if (jsonMessage.contains("\"type\":\"monitor_update")) {
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/monitor/data", jsonMessage);
            } else if (jsonMessage.contains("\"type\":\"error")) {
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", jsonMessage);
            } else {
                // 默认发送到通用响应队列
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/response", jsonMessage);
            }
        } catch (Exception e) {
            // 发送失败时记录日志，但不抛出异常
            System.err.println("Failed to route STOMP message: " + e.getMessage());
        }
    }

    @Override
    public boolean isOpen() {
        // STOMP会话假设始终开放，由Spring管理生命周期
        return true;
    }

    @Override
    public void close() throws IOException {
        // STOMP会话关闭由Spring框架管理，这里不需要实现
    }

    @Override
    public void close(CloseStatus status) throws IOException {
        // STOMP会话关闭由Spring框架管理，这里不需要实现
    }
}
