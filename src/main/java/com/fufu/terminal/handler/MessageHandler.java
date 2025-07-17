package com.fufu.terminal.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fufu.terminal.model.SshConnection;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/**
 * 消息处理器接口
 * @author lizelin
 */
@FunctionalInterface
public interface MessageHandler {
    /**
     * 处理消息
     * @param session WebSocket会话
     * @param connection SSH连接对象
     * @param payload 消息的JSON负载
     * @throws IOException IO异常
     */
    void handle(WebSocketSession session, SshConnection connection, JsonNode payload) throws IOException;
}
