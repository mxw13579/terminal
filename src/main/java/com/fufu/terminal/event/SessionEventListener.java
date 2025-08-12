package com.fufu.terminal.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 会话事件监听器
 * 监听会话建立事件并向前端发送会话信息
 * 
 * @author lizelin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 处理会话建立事件
     * 向前端发送会话ID，供HTTP文件传输使用
     * 
     * @param event 会话建立事件
     */
    @EventListener
    public void handleSessionEstablished(SessionEstablishedEvent event) {
        try {
            String sessionId = event.getSessionId();
            String message = event.getMessage();
            
            Map<String, Object> sessionInfo = Map.of(
                "type", "session_established",
                "sessionId", sessionId,
                "message", message
            );
            
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/session", sessionInfo);
            log.debug("已向前端发送会话ID: {}", sessionId);
            
        } catch (Exception e) {
            log.error("发送会话建立消息失败: {}", e.getMessage(), e);
        }
    }
}