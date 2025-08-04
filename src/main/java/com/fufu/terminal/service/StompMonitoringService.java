package com.fufu.terminal.service;

import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.dto.MonitorUpdateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * STOMP-compatible monitoring service that integrates with the existing SshMonitorService
 * and sends monitoring updates via STOMP messaging.
 * 
 * @author lizelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StompMonitoringService {

    private final SshMonitorService sshMonitorService;
    private final SimpMessagingTemplate messagingTemplate;
    
    // Track which sessions are receiving monitoring updates
    private final Map<String, Boolean> activeMonitoringSessions = new ConcurrentHashMap<>();

    /**
     * Start monitoring for a STOMP session.
     * This adapts the original monitoring service to work with STOMP messaging.
     */
    public void startMonitoring(String sessionId, SshConnection connection) {
        log.info("Starting STOMP monitoring for session: {}", sessionId);
        
        try {
            // Mark session as actively monitoring
            activeMonitoringSessions.put(sessionId, true);
            
            // Create a custom session adapter that sends monitoring updates via STOMP
            StompMonitoringSessionAdapter sessionAdapter = new StompMonitoringSessionAdapter(
                sessionId, messagingTemplate, this);
            
            // Start monitoring using the existing service
            sshMonitorService.handleMonitorStart(sessionAdapter, connection);
            
        } catch (Exception e) {
            log.error("Error starting STOMP monitoring for session {}: {}", sessionId, e.getMessage(), e);
            activeMonitoringSessions.remove(sessionId);
            throw e;
        }
    }

    /**
     * Stop monitoring for a STOMP session.
     */
    public void stopMonitoring(String sessionId, SshConnection connection) {
        log.info("Stopping STOMP monitoring for session: {}", sessionId);
        
        try {
            // Remove from active sessions
            activeMonitoringSessions.remove(sessionId);
            
            // Stop monitoring using the existing service
            sshMonitorService.handleMonitorStop(connection);
            
        } catch (Exception e) {
            log.error("Error stopping STOMP monitoring for session {}: {}", sessionId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Send monitoring update to a specific session.
     * This method is called by the session adapter to send updates via STOMP.
     */
    public void sendMonitoringUpdate(String sessionId, Map<String, Object> monitoringData) {
        if (!activeMonitoringSessions.getOrDefault(sessionId, false)) {
            log.debug("Session {} is no longer actively monitoring, skipping update", sessionId);
            return;
        }
        
        try {
            // Convert monitoring data to STOMP message
            MonitorUpdateDto updateMessage = new MonitorUpdateDto(monitoringData);
            
            // Send to user-specific monitoring queue
            messagingTemplate.convertAndSendToUser(
                sessionId, 
                "/queue/monitor/update", 
                updateMessage
            );
            
            log.debug("Sent monitoring update to session: {}", sessionId);
            
        } catch (Exception e) {
            log.error("Error sending monitoring update to session {}: {}", sessionId, e.getMessage(), e);
            // Remove session from active monitoring if there's a persistent error
            activeMonitoringSessions.remove(sessionId);
        }
    }

    /**
     * Check if a session is actively monitoring.
     */
    public boolean isActivelyMonitoring(String sessionId) {
        return activeMonitoringSessions.getOrDefault(sessionId, false);
    }

    /**
     * Get count of active monitoring sessions.
     */
    public int getActiveMonitoringSessionCount() {
        return activeMonitoringSessions.size();
    }

    /**
     * Cleanup monitoring for a session (called when session disconnects).
     */
    public void cleanupMonitoring(String sessionId) {
        activeMonitoringSessions.remove(sessionId);
        log.debug("Cleaned up monitoring for session: {}", sessionId);
    }
}