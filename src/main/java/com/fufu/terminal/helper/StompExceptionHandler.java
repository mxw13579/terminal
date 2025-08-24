package com.fufu.terminal.helper;

import com.fufu.terminal.service.StompSessionManager;
import lombok.extern.slf4j.Slf4j;

/**
 * STOMP控制器异常处理工具类
 * 提供统一的异常处理和错误消息发送功能，确保一致的错误处理模式
 * 
 * @author lizelin
 * @since 1.0
 */
@Slf4j
public final class StompExceptionHandler {
    
    // 防止实例化
    private StompExceptionHandler() {
        throw new AssertionError("工具类不允许实例化");
    }
    
    /**
     * 处理带sessionId的异常，记录日志并发送错误消息给客户端
     * <p>
     * 统一的异常处理模式，包含：
     * 1. 记录错误日志（包含sessionId、操作描述、异常信息）
     * 2. 向客户端发送格式化的错误消息
     * </p>
     * 
     * @param sessionId WebSocket会话ID
     * @param exception 发生的异常
     * @param sessionManager STOMP会话管理器
     * @param operation 操作描述（如："启动监控"、"数据导出"）
     */
    public static void handleException(String sessionId, Exception exception, 
                                     StompSessionManager sessionManager, String operation) {
        log.error("{}失败，sessionId: {}，原因: {}", operation, sessionId, exception.getMessage(), exception);
        sessionManager.sendErrorMessage(sessionId, operation + "失败: " + exception.getMessage());
    }
    
    /**
     * 处理带sessionId的异常，使用自定义错误消息
     * <p>
     * 适用于需要特定错误消息格式的场景
     * </p>
     * 
     * @param sessionId WebSocket会话ID
     * @param exception 发生的异常
     * @param sessionManager STOMP会话管理器
     * @param operation 操作描述（如："启动监控"、"数据导出"）
     * @param customErrorMessage 自定义的错误消息
     */
    public static void handleException(String sessionId, Exception exception, 
                                     StompSessionManager sessionManager, String operation, String customErrorMessage) {
        log.error("{}失败，sessionId: {}，原因: {}", operation, sessionId, exception.getMessage(), exception);
        sessionManager.sendErrorMessage(sessionId, customErrorMessage);
    }
    
    /**
     * 处理SillyTavern相关异常，使用统一的日志格式
     * <p>
     * SillyTavern控制器使用的特定日志格式（会话 {} 而不是 sessionId: {}）
     * </p>
     * 
     * @param sessionId WebSocket会话ID
     * @param exception 发生的异常
     * @param operation 操作描述
     * @param sendErrorMessage 错误消息发送函数
     */
    public static void handleSillyTavernException(String sessionId, Exception exception, 
                                                 String operation, java.util.function.BiConsumer<String, String> sendErrorMessage) {
        log.error("{}失败，会话 {}: {}", operation, sessionId, exception.getMessage(), exception);
        sendErrorMessage.accept(sessionId, operation + "失败: " + exception.getMessage());
    }
}