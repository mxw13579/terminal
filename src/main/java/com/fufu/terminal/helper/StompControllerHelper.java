package com.fufu.terminal.helper;

import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.StompSessionManager;
import lombok.extern.slf4j.Slf4j;

/**
 * STOMP控制器通用工具类
 * 提供STOMP控制器间的公共功能，避免重复代码
 * 
 * @author lizelin
 * @since 1.0
 */
@Slf4j
public final class StompControllerHelper {
    
    // 防止实例化
    private StompControllerHelper() {
        throw new AssertionError("工具类不允许实例化");
    }
    
    /**
     * 获取SSH连接并验证有效性
     * <p>
     * 如果连接不存在，会自动记录警告日志并向客户端发送错误消息
     * </p>
     * 
     * @param sessionId WebSocket会话ID
     * @param sessionManager STOMP会话管理器
     * @return SSH连接实例，如果不存在则返回null
     */
    public static SshConnection getValidatedConnection(String sessionId, StompSessionManager sessionManager) {
        SshConnection connection = sessionManager.getConnection(sessionId);
        if (connection == null) {
            log.warn("未找到 SSH 连接，sessionId: {}", sessionId);
            sessionManager.sendErrorMessage(sessionId, "SSH 连接尚未建立");
            return null;
        }
        return connection;
    }
}