package com.fufu.terminal.config;

import com.fufu.terminal.service.SftpService;
import com.fufu.terminal.service.SshConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * STOMP会话事件监听器
 * 处理STOMP连接建立和断开事件
 * 
 * @author lizelin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompSessionEventListener {

    private final SshConnectionManager connectionManager;
    private final SftpService sftpService;

    /**
     * 处理STOMP会话连接事件
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        String sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
        log.info("STOMP session connected: {}", sessionId);
    }

    /**
     * 处理STOMP会话断开事件
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
        log.info("STOMP session disconnected: {}", sessionId);
        
        // 清理SSH连接
        if (connectionManager.hasConnection(sessionId)) {
            connectionManager.removeConnection(sessionId);
        }
        
        // 清理SFTP上传缓存
        sftpService.clearUploadCacheForSession(sessionId);
        
        log.info("Cleaned up resources for disconnected session: {}", sessionId);
    }
}