//package com.fufu.terminal.controller;
//
//import com.fufu.terminal.adapter.StompSessionAdapter;
//import com.fufu.terminal.dto.MonitorMessages.*;
//import com.fufu.terminal.model.SshConnection;
//import com.fufu.terminal.service.SshConnectionManager;
//import com.fufu.terminal.service.SshMonitorService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.messaging.handler.annotation.MessageMapping;
//import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
//import org.springframework.messaging.simp.SimpMessagingTemplate;
//import org.springframework.stereotype.Controller;
//
//import java.security.Principal;
//
///**
// * 系统监控控制器
// * 处理监控启动、停止和数据推送
// *
// * @author lizelin
// */
//@Slf4j
//@Controller
//@RequiredArgsConstructor
//public class MonitorController {
//
//    private final SshMonitorService sshMonitorService;
//    private final SshConnectionManager connectionManager;
//    private final SimpMessagingTemplate messagingTemplate;
//
//    /**
//     * 启动系统监控
//     */
//    @MessageMapping("/monitor/start")
//    public void startMonitoring(StartRequest request, SimpMessageHeaderAccessor headerAccessor, Principal principal) {
//        String sessionId = headerAccessor.getSessionId();
//        SshConnection connection = connectionManager.getConnection(sessionId);
//
//        if (connection == null) {
//            log.warn("Received monitor start request for non-existent session: {}", sessionId);
//            messagingTemplate.convertAndSendToUser(
//                sessionId,
//                "/queue/monitor/error",
//                new MonitorUpdate("error", "No SSH connection found")
//            );
//            return;
//        }
//
//        try {
//            log.info("Starting monitoring for session: {}", sessionId);
//            StompSessionAdapter sessionAdapter = new StompSessionAdapter(sessionId, messagingTemplate);
//            sshMonitorService.handleMonitorStart(sessionAdapter, connection);
//        } catch (Exception e) {
//            log.error("Error starting monitor for session {}: ", sessionId, e);
//            messagingTemplate.convertAndSendToUser(
//                sessionId,
//                "/queue/monitor/error",
//                new MonitorUpdate("error", "Error starting monitor: " + e.getMessage())
//            );
//        }
//    }
//
//    /**
//     * 停止系统监控
//     */
//    @MessageMapping("/monitor/stop")
//    public void stopMonitoring(StopRequest request, SimpMessageHeaderAccessor headerAccessor, Principal principal) {
//        String sessionId = headerAccessor.getSessionId();
//        SshConnection connection = connectionManager.getConnection(sessionId);
//
//        if (connection == null) {
//            log.warn("Received monitor stop request for non-existent session: {}", sessionId);
//            return;
//        }
//
//        try {
//            log.info("Stopping monitoring for session: {}", sessionId);
//            sshMonitorService.handleMonitorStop(connection);
//        } catch (Exception e) {
//            log.error("Error stopping monitor for session {}: ", sessionId, e);
//        }
//    }
//}
