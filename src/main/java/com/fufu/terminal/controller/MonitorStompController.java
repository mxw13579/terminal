package com.fufu.terminal.controller;

import com.fufu.terminal.helper.StompControllerHelper;
import com.fufu.terminal.helper.StompExceptionHandler;
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

        String sessionId = headerAccessor.getSessionId();
        log.info("收到启动监控请求，sessionId: {}，频率: {}s", sessionId, message.getFrequencySeconds());

        SshConnection connection = StompControllerHelper.getValidatedConnection(sessionId, sessionManager);
        if (connection == null) {
            return;
        }

        try {
            // 调用监控服务启动监控
            stompMonitoringService.startMonitoring(sessionId, connection);
            log.info("监控启动成功，sessionId: {}", sessionId);
        } catch (Exception e) {
            StompExceptionHandler.handleException(sessionId, e, sessionManager, "启动监控");
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

        String sessionId = headerAccessor.getSessionId();
        log.info("收到停止监控请求，sessionId: {}", sessionId);

        SshConnection connection = StompControllerHelper.getValidatedConnection(sessionId, sessionManager);
        if (connection == null) {
            return;
        }

        try {
            // 调用监控服务停止监控
            stompMonitoringService.stopMonitoring(sessionId, connection);
            log.info("监控已停止，sessionId: {}", sessionId);
        } catch (Exception e) {
            StompExceptionHandler.handleException(sessionId, e, sessionManager, "停止监控");
        }
    }


}
