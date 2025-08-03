package com.fufu.terminal.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.Map;

/**
 * STOMP全局异常处理器
 * 统一处理STOMP消息处理过程中的异常
 * 
 * @author lizelin
 */
@Slf4j
@ControllerAdvice
public class StompExceptionHandler {

    private final SimpMessagingTemplate messagingTemplate;

    public StompExceptionHandler(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 处理消息传递异常
     */
    @MessageExceptionHandler(MessageDeliveryException.class)
    public void handleMessageDeliveryException(MessageDeliveryException ex, Message<?> message) {
        log.error("Message delivery exception: ", ex);
        
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(message);
        String sessionId = headerAccessor.getSessionId();
        
        if (sessionId != null) {
            sendErrorToUser(sessionId, "Message delivery failed: " + ex.getMessage());
        }
    }

    /**
     * 处理一般异常
     */
    @MessageExceptionHandler(Exception.class)
    public void handleGenericException(Exception ex, Message<?> message) {
        log.error("Unexpected exception in STOMP message handler: ", ex);
        
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(message);
        String sessionId = headerAccessor.getSessionId();
        
        if (sessionId != null) {
            sendErrorToUser(sessionId, "Internal server error: " + ex.getMessage());
        }
    }

    /**
     * 处理非法参数异常
     */
    @MessageExceptionHandler(IllegalArgumentException.class)
    public void handleIllegalArgumentException(IllegalArgumentException ex, Message<?> message) {
        log.warn("Invalid argument in STOMP message: ", ex);
        
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(message);
        String sessionId = headerAccessor.getSessionId();
        
        if (sessionId != null) {
            sendErrorToUser(sessionId, "Invalid request: " + ex.getMessage());
        }
    }

    /**
     * 发送错误消息给用户
     */
    private void sendErrorToUser(String sessionId, String errorMessage) {
        try {
            Map<String, String> errorResponse = Map.of(
                "type", "error",
                "payload", errorMessage,
                "timestamp", String.valueOf(System.currentTimeMillis())
            );
            
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors", errorResponse);
        } catch (Exception e) {
            log.error("Failed to send error message to user {}: ", sessionId, e);
        }
    }
}