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
 * STOMP controller for system monitoring operations.
 * Handles start and stop monitoring requests.
 * 
 * @author lizelin
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MonitorStompController {

    private final StompMonitoringService stompMonitoringService;
    private final StompSessionManager sessionManager;

    /**
     * Handle monitoring start requests.
     */
    @MessageMapping("/monitor/start")
    public void handleMonitorStart(
            @Valid MonitorStartDto message,
            SimpMessageHeaderAccessor headerAccessor) {
        
        String sessionId = headerAccessor.getSessionId();
        log.info("Starting monitoring for session: {} with frequency: {}s", 
                sessionId, message.getFrequencySeconds());
        
        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sessionManager.sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            // Use the STOMP monitoring service
            stompMonitoringService.startMonitoring(sessionId, connection);
            
            log.info("Monitoring started successfully for session: {}", sessionId);

        } catch (Exception e) {
            log.error("Error starting monitoring for session {}: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "Failed to start monitoring: " + e.getMessage());
        }
    }

    /**
     * Handle monitoring stop requests.
     */
    @MessageMapping("/monitor/stop")
    public void handleMonitorStop(
            @Valid MonitorStopDto message,
            SimpMessageHeaderAccessor headerAccessor) {
        
        String sessionId = headerAccessor.getSessionId();
        log.info("Stopping monitoring for session: {}", sessionId);
        
        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sessionManager.sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            // Use the STOMP monitoring service
            stompMonitoringService.stopMonitoring(sessionId, connection);
            
            log.info("Monitoring stopped successfully for session: {}", sessionId);

        } catch (Exception e) {
            log.error("Error stopping monitoring for session {}: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "Failed to stop monitoring: " + e.getMessage());
        }
    }
}