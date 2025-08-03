package com.fufu.terminal.service;

import com.fufu.terminal.model.SshConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * SSH连接管理服务
 * 管理STOMP会话与SSH连接的映射关系
 * 
 * @author lizelin
 */
@Slf4j
@Service
public class SshConnectionManager {

    private final Map<String, SshConnection> connections = new ConcurrentHashMap<>();

    /**
     * 添加SSH连接
     */
    public void addConnection(String sessionId, SshConnection connection) {
        connections.put(sessionId, connection);
        log.info("SSH connection added for session: {}", sessionId);
    }

    /**
     * 获取SSH连接
     */
    public SshConnection getConnection(String sessionId) {
        return connections.get(sessionId);
    }

    /**
     * 获取SSH连接（通过STOMP消息头）
     */
    public SshConnection getConnection(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        return getConnection(sessionId);
    }

    /**
     * 移除SSH连接并清理资源
     */
    public void removeConnection(String sessionId) {
        SshConnection connection = connections.remove(sessionId);
        if (connection != null) {
            connection.disconnect();
            log.info("SSH connection removed and cleaned up for session: {}", sessionId);
        }
    }

    /**
     * 检查会话是否存在SSH连接
     */
    public boolean hasConnection(String sessionId) {
        return connections.containsKey(sessionId);
    }

    /**
     * 获取当前连接数
     */
    public int getConnectionCount() {
        return connections.size();
    }

    /**
     * 清理所有连接（通常在应用关闭时调用）
     */
    public void cleanup() {
        log.info("Cleaning up {} SSH connections", connections.size());
        for (String sessionId : connections.keySet()) {
            removeConnection(sessionId);
        }
    }
}