package com.fufu.terminal.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * 会话管理控制器
 * 处理会话相关的STOMP消息
 * 
 * @author lizelin
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SessionController {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 处理会话信息请求
     * 客户端可以通过此接口获取当前的会话ID
     * 
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/session/info")
    public void handleSessionInfoRequest(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("处理会话信息请求，sessionId: {}", sessionId);

        try {
            Map<String, Object> sessionInfo = Map.of(
                "type", "session_established",
                "sessionId", sessionId,
                "message", "会话信息响应"
            );
            
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/session", sessionInfo);
            log.debug("已发送会话信息给前端，sessionId: {}", sessionId);
            
        } catch (Exception e) {
            log.error("发送会话信息失败: {}", e.getMessage(), e);
        }
    }
}