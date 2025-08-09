package com.fufu.terminal.config;

import com.fufu.terminal.service.CorrelationIdService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP 消息追踪拦截器。
 * <p>
 * 为 STOMP 消息添加分布式追踪支持，自动生成和传播关联 ID，
 * 确保跨 WebSocket 消息的请求追踪能力。
 * </p>
 * 
 * <p><strong>功能特性：</strong></p>
 * <ul>
 *     <li>自动生成关联 ID</li>
 *     <li>从消息头中提取现有的关联 ID</li>
 *     <li>设置 MDC 上下文信息</li>
 *     <li>支持会话和用户信息追踪</li>
 * </ul>
 *
 * @author lizelin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompTracingInterceptor implements ChannelInterceptor {

    private final CorrelationIdService correlationIdService;

    /**
     * 处理发送前的消息，设置追踪上下文。
     *
     * @param message 要发送的消息
     * @param channel 消息通道
     * @return 处理后的消息
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null) {
            String correlationId = extractOrGenerateCorrelationId(accessor);
            String sessionId = accessor.getSessionId();
            String destination = accessor.getDestination();
            String command = accessor.getCommand() != null ? accessor.getCommand().name() : "UNKNOWN";
            
            // 设置追踪上下文
            correlationIdService.setFullContext(
                correlationId,
                sessionId,
                null, // 用户 ID 可以从认证信息中获取
                "stomp_" + command.toLowerCase()
            );
            
            // 将关联 ID 添加到消息头中
            accessor.addNativeHeader("X-Correlation-ID", correlationId);
            
            log.debug("STOMP 消息追踪设置: correlationId={}, sessionId={}, destination={}, command={}", 
                     correlationId, correlationIdService.maskSensitiveData(sessionId), destination, command);
        }
        
        return message;
    }

    /**
     * 处理发送后的消息，清理追踪上下文。
     *
     * @param message 发送的消息
     * @param channel 消息通道
     * @param sent 是否发送成功
     */
    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
        // 发送完成后清理上下文（如果需要的话）
        // correlationIdService.clearContext();
    }

    /**
     * 处理发送异常。
     *
     * @param message 发送的消息
     * @param channel 消息通道
     * @param ex 异常信息
     * @return 是否处理了异常
     */
    @Override
    public boolean preReceive(MessageChannel channel) {
        return true;
    }

    /**
     * 处理接收后的消息。
     *
     * @param message 接收的消息
     * @param channel 消息通道
     */
    @Override
    public Message<?> postReceive(Message<?> message, MessageChannel channel) {
        if (message != null) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (accessor != null) {
                String correlationId = extractCorrelationIdFromHeaders(accessor);
                if (correlationId != null) {
                    correlationIdService.setCorrelationId(correlationId);
                    log.debug("STOMP 消息接收追踪: correlationId={}", correlationId);
                }
            }
        }
        return message;
    }

    /**
     * 处理接收完成后的清理。
     *
     * @param message 接收的消息
     * @param channel 消息通道
     * @param ex 异常信息（如果有）
     */
    @Override
    public void afterReceiveCompletion(Message<?> message, MessageChannel channel, Exception ex) {
        if (ex != null) {
            String correlationId = correlationIdService.getCorrelationId();
            log.warn("STOMP 消息接收异常: correlationId={}, error={}", correlationId, ex.getMessage());
        }
    }

    /**
     * 从消息头中提取或生成关联 ID。
     *
     * @param accessor STOMP 头访问器
     * @return 关联 ID
     */
    private String extractOrGenerateCorrelationId(StompHeaderAccessor accessor) {
        // 首先尝试从原生头中获取
        String correlationId = extractCorrelationIdFromHeaders(accessor);
        
        if (correlationId == null || correlationId.trim().isEmpty()) {
            // 如果没有现有的关联 ID，则生成新的
            correlationId = correlationIdService.generateCorrelationId();
        }
        
        return correlationId;
    }

    /**
     * 从消息头中提取关联 ID。
     *
     * @param accessor STOMP 头访问器
     * @return 关联 ID，如果不存在则返回 null
     */
    private String extractCorrelationIdFromHeaders(StompHeaderAccessor accessor) {
        // 尝试从多个可能的头字段中获取关联 ID
        String correlationId = accessor.getFirstNativeHeader("X-Correlation-ID");
        
        if (correlationId == null) {
            correlationId = accessor.getFirstNativeHeader("correlationId");
        }
        
        if (correlationId == null) {
            correlationId = accessor.getFirstNativeHeader("traceId");
        }
        
        return correlationId;
    }
}