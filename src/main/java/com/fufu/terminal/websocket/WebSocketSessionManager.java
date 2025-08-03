package com.fufu.terminal.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Session Manager
 * 
 * Manages WebSocket session tracking and user isolation for
 * real-time communication and session recovery.
 */
@Component
@Slf4j
public class WebSocketSessionManager {
    
    // Session ID to User ID mapping
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();
    
    // User ID to Session IDs mapping (one user can have multiple sessions)
    private final Map<String, Set<String>> userToSessions = new ConcurrentHashMap<>();
    
    // Session metadata
    private final Map<String, SessionMetadata> sessionMetadata = new ConcurrentHashMap<>();
    
    /**
     * Handle WebSocket session connected event
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        Principal user = accessor.getUser();
        
        if (user != null) {
            String userId = user.getName();
            registerSession(sessionId, userId, accessor);
            log.info("WebSocket session connected: {} for user: {}", sessionId, userId);
        } else {
            // Try to get user from session attributes (for anonymous users)
            Object userIdAttr = accessor.getSessionAttributes().get("userId");
            if (userIdAttr != null) {
                String userId = userIdAttr.toString();
                registerSession(sessionId, userId, accessor);
                log.info("WebSocket session connected: {} for user: {} (from attributes)", sessionId, userId);
            } else {
                log.warn("WebSocket session connected without user information: {}", sessionId);
            }
        }
    }
    
    /**
     * Handle WebSocket session disconnected event
     */
    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        
        String userId = unregisterSession(sessionId);
        
        if (userId != null) {
            log.info("WebSocket session disconnected: {} for user: {}", sessionId, userId);
        } else {
            log.warn("WebSocket session disconnected without known user: {}", sessionId);
        }
    }
    
    /**
     * Register a new session
     */
    private void registerSession(String sessionId, String userId, StompHeaderAccessor accessor) {
        sessionToUser.put(sessionId, userId);
        userToSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        
        // Store session metadata
        SessionMetadata metadata = SessionMetadata.builder()
            .sessionId(sessionId)
            .userId(userId)
            .connectedAt(Instant.now())
            .userType(getUserType(accessor))
            .userAgent(getUserAgent(accessor))
            .remoteAddress(getRemoteAddress(accessor))
            .build();
        
        sessionMetadata.put(sessionId, metadata);
    }
    
    /**
     * Unregister a session
     */
    private String unregisterSession(String sessionId) {
        String userId = sessionToUser.remove(sessionId);
        sessionMetadata.remove(sessionId);
        
        if (userId != null) {
            Set<String> sessions = userToSessions.get(userId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    userToSessions.remove(userId);
                }
            }
        }
        
        return userId;
    }
    
    /**
     * Get user ID by session ID
     */
    public String getUserIdBySessionId(String sessionId) {
        return sessionToUser.get(sessionId);
    }
    
    /**
     * Get all session IDs for a user
     */
    public Set<String> getSessionsByUserId(String userId) {
        return userToSessions.getOrDefault(userId, Collections.emptySet());
    }
    
    /**
     * Check if user is connected (has active sessions)
     */
    public boolean isUserConnected(String userId) {
        Set<String> sessions = userToSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }
    
    /**
     * Get session metadata
     */
    public SessionMetadata getSessionMetadata(String sessionId) {
        return sessionMetadata.get(sessionId);
    }
    
    /**
     * Get all active sessions for a user
     */
    public Map<String, SessionMetadata> getActiveSessionsForUser(String userId) {
        Set<String> sessionIds = getSessionsByUserId(userId);
        Map<String, SessionMetadata> result = new ConcurrentHashMap<>();
        
        for (String sessionId : sessionIds) {
            SessionMetadata metadata = sessionMetadata.get(sessionId);
            if (metadata != null) {
                result.put(sessionId, metadata);
            }
        }
        
        return result;
    }
    
    /**
     * Get total number of active sessions
     */
    public int getActiveSessionCount() {
        return sessionToUser.size();
    }
    
    /**
     * Get total number of active users
     */
    public int getActiveUserCount() {
        return userToSessions.size();
    }
    
    /**
     * Get all active users
     */
    public Set<String> getActiveUsers() {
        return userToSessions.keySet();
    }
    
    /**
     * Disconnect all sessions for a user
     */
    public void disconnectUser(String userId) {
        Set<String> sessions = getSessionsByUserId(userId);
        for (String sessionId : sessions) {
            unregisterSession(sessionId);
            log.info("Forcibly disconnected session {} for user {}", sessionId, userId);
        }
    }
    
    /**
     * Clean up stale sessions (for manual cleanup if needed)
     */
    public void cleanupStaleSessions(long maxAgeMs) {
        Instant cutoff = Instant.now().minusMillis(maxAgeMs);
        
        sessionMetadata.entrySet().removeIf(entry -> {
            SessionMetadata metadata = entry.getValue();
            if (metadata.getConnectedAt().isBefore(cutoff)) {
                String sessionId = entry.getKey();
                String userId = sessionToUser.remove(sessionId);
                
                if (userId != null) {
                    Set<String> sessions = userToSessions.get(userId);
                    if (sessions != null) {
                        sessions.remove(sessionId);
                        if (sessions.isEmpty()) {
                            userToSessions.remove(userId);
                        }
                    }
                }
                
                log.info("Cleaned up stale session: {} (age: {}ms)", sessionId, 
                    System.currentTimeMillis() - metadata.getConnectedAt().toEpochMilli());
                return true;
            }
            return false;
        });
    }
    
    /**
     * Get session statistics
     */
    public SessionStatistics getSessionStatistics() {
        return SessionStatistics.builder()
            .totalSessions(getActiveSessionCount())
            .totalUsers(getActiveUserCount())
            .averageSessionsPerUser(getActiveUserCount() > 0 ? 
                (double) getActiveSessionCount() / getActiveUserCount() : 0.0)
            .build();
    }
    
    // Helper methods for extracting session information
    
    private String getUserType(StompHeaderAccessor accessor) {
        Object userType = accessor.getSessionAttributes().get("userType");
        return userType != null ? userType.toString() : "unknown";
    }
    
    private String getUserAgent(StompHeaderAccessor accessor) {
        return accessor.getFirstNativeHeader("User-Agent");
    }
    
    private String getRemoteAddress(StompHeaderAccessor accessor) {
        return accessor.getFirstNativeHeader("X-Forwarded-For");
    }
    
    /**
     * Session Metadata
     */
    @lombok.Data
    @lombok.Builder
    public static class SessionMetadata {
        private String sessionId;
        private String userId;
        private Instant connectedAt;
        private String userType;
        private String userAgent;
        private String remoteAddress;
        
        public long getConnectionDurationMs() {
            return System.currentTimeMillis() - connectedAt.toEpochMilli();
        }
        
        public long getConnectionDurationSeconds() {
            return getConnectionDurationMs() / 1000;
        }
    }
    
    /**
     * Session Statistics
     */
    @lombok.Data
    @lombok.Builder
    public static class SessionStatistics {
        private int totalSessions;
        private int totalUsers;
        private double averageSessionsPerUser;
    }
}