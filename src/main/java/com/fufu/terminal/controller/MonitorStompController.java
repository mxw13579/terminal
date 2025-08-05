package com.fufu.terminal.controller;

import com.fufu.terminal.dto.MonitorStartDto;
import com.fufu.terminal.dto.MonitorStopDto;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.StompMonitoringService;
import com.fufu.terminal.service.StompSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import jakarta.validation.Valid;

/**
 * <p>STOMP 控制器：处理系统监控相关的 WebSocket 消息。</p>
 * <p>包括启动和停止监控的请求处理。</p>
 *
 * <p>该控制器通过 WebSocket STOMP 协议与前端进行交互，管理 SSH 监控任务的启动与停止。</p>
 *
 * @author lizelin
 * @since 1.0
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MonitorStompController {

    private final StompMonitoringService stompMonitoringService;
    private final StompSessionManager sessionManager;

    /**
     * 处理监控启动请求。
     *
     * @param message        启动监控的请求参数，包含监控频率等信息
     * @param headerAccessor WebSocket 消息头访问器，用于获取 sessionId
     */
    @MessageMapping("/monitor/start")
    public void handleMonitorStart(
            @Valid MonitorStartDto message,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = getSessionId(headerAccessor);
        log.info("收到启动监控请求，sessionId: {}，频率: {}s", sessionId, message.getFrequencySeconds());

        SshConnection connection = getConnectionOrNotifyError(sessionId);
        if (connection == null) {
            return;
        }

        try {
            // 调用监控服务启动监控
            stompMonitoringService.startMonitoring(sessionId, connection);
            log.info("监控启动成功，sessionId: {}", sessionId);
        } catch (Exception e) {
            log.error("启动监控失败，sessionId: {}，原因: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "启动监控失败: " + e.getMessage());
        }
    }

    /**
     * 处理监控停止请求。
     *
     * @param message        停止监控的请求参数
     * @param headerAccessor WebSocket 消息头访问器，用于获取 sessionId
     */
    @MessageMapping("/monitor/stop")
    public void handleMonitorStop(
            @Valid MonitorStopDto message,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = getSessionId(headerAccessor);
        log.info("收到停止监控请求，sessionId: {}", sessionId);

        SshConnection connection = getConnectionOrNotifyError(sessionId);
        if (connection == null) {
            return;
        }

        try {
            // 调用监控服务停止监控
            stompMonitoringService.stopMonitoring(sessionId, connection);
            log.info("监控已停止，sessionId: {}", sessionId);
        } catch (Exception e) {
            log.error("停止监控失败，sessionId: {}，原因: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "停止监控失败: " + e.getMessage());
        }
    }

    /**
     * 根据 sessionId 获取 SSH 连接，若不存在则发送错误消息给客户端。
     *
     * @param sessionId WebSocket 会话 ID
     * @return SshConnection 实例，若不存在则返回 null
     */
    private SshConnection getConnectionOrNotifyError(String sessionId) {
        SshConnection connection = sessionManager.getConnection(sessionId);
        if (connection == null) {
            log.warn("未找到 SSH 连接，sessionId: {}", sessionId);
            sessionManager.sendErrorMessage(sessionId, "SSH 连接尚未建立");
            return null;
        }
        return connection;
    }

    /**
     * 从消息头访问器中获取 sessionId。
     *
     * @param headerAccessor WebSocket 消息头访问器
     * @return sessionId 字符串
     */
    private String getSessionId(SimpMessageHeaderAccessor headerAccessor) {
        return headerAccessor.getSessionId();
    }
}
