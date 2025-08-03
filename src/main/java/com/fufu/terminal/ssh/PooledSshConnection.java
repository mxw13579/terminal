package com.fufu.terminal.ssh;

import com.fufu.terminal.model.CommandResult;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Pooled SSH Connection Implementation using JSch
 * 
 * Wraps a JSch SSH session and provides command execution capabilities
 * with connection pooling support.
 */
@Slf4j
public class PooledSshConnection implements SshConnection {
    
    private final Session session;
    private final String poolKey;
    private final SshConnectionPool pool;
    private final long createdAt;
    private long lastUsedAt;
    private boolean inUse;
    
    public PooledSshConnection(Session session, String poolKey, SshConnectionPool pool) {
        this.session = session;
        this.poolKey = poolKey;
        this.pool = pool;
        this.createdAt = System.currentTimeMillis();
        this.lastUsedAt = createdAt;
        this.inUse = true;
    }
    
    @Override
    public CommandResult executeCommand(String command, Duration timeout) {
        if (!isValid()) {
            throw new SshConnectionException("SSH connection is not valid");
        }
        
        Instant startTime = Instant.now();
        ChannelExec channel = null;
        
        try {
            // Create execution channel
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            
            // Set up streams for capturing output
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
            
            channel.setOutputStream(outputStream);
            channel.setErrStream(errorStream);
            
            // Configure channel properties
            channel.setInputStream(null);
            channel.setPty(false); // Don't allocate pseudo-terminal
            
            // Connect and execute
            int timeoutMs = timeout != null ? (int) timeout.toMillis() : 300000; // 5 minutes default
            channel.connect(Math.min(timeoutMs, 30000)); // Max 30s for connection
            
            // Wait for command completion with timeout
            long elapsed = 0;
            while (!channel.isClosed() && elapsed < timeoutMs) {
                try {
                    Thread.sleep(100);
                    elapsed = Duration.between(startTime, Instant.now()).toMillis();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SshConnectionException("Command execution interrupted", e);
                }
            }
            
            Instant endTime = Instant.now();
            long executionTimeMs = Duration.between(startTime, endTime).toMillis();
            
            // Check for timeout
            if (!channel.isClosed()) {
                log.warn("Command execution timed out after {}ms: {}", elapsed, command);
                return CommandResult.timeout(command, executionTimeMs)
                    .withConnectionInfo(getConnectionInfo())
                    .withTiming(startTime, endTime);
            }
            
            // Get results
            int exitCode = channel.getExitStatus();
            String output = outputStream.toString("UTF-8");
            String errorOutput = errorStream.toString("UTF-8");
            
            boolean success = exitCode == 0;
            
            CommandResult result = CommandResult.builder()
                .command(command)
                .exitCode(exitCode)
                .output(output)
                .errorOutput(errorOutput)
                .executionTimeMs(executionTimeMs)
                .success(success)
                .startTime(startTime)
                .endTime(endTime)
                .connectionInfo(getConnectionInfo())
                .build();
            
            if (success) {
                log.debug("Command executed successfully: {} ({}ms)", command, executionTimeMs);
            } else {
                log.warn("Command failed with exit code {}: {} ({}ms)", exitCode, command, executionTimeMs);
            }
            
            return result;
            
        } catch (Exception e) {
            Instant endTime = Instant.now();
            long executionTimeMs = Duration.between(startTime, endTime).toMillis();
            
            log.error("Failed to execute SSH command: {}", command, e);
            
            return CommandResult.failure(command, -1, e.getMessage(), executionTimeMs)
                .withConnectionInfo(getConnectionInfo())
                .withTiming(startTime, endTime);
            
        } finally {
            // Always close the channel
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            
            // Update last used time
            this.lastUsedAt = System.currentTimeMillis();
        }
    }
    
    @Override
    public boolean isConnected() {
        return session != null && session.isConnected();
    }
    
    @Override
    public boolean isValid() {
        if (session == null) {
            return false;
        }
        
        try {
            // Check if session is connected and not stale
            return session.isConnected() && session.getServerVersion() != null;
        } catch (Exception e) {
            log.debug("SSH session validation failed", e);
            return false;
        }
    }
    
    @Override
    public String getConnectionInfo() {
        if (session == null) {
            return "No session";
        }
        
        try {
            return String.format("%s@%s:%d", session.getUserName(), session.getHost(), session.getPort());
        } catch (Exception e) {
            return "Unknown connection";
        }
    }
    
    @Override
    public void close() {
        if (inUse) {
            // Return to pool instead of closing
            inUse = false;
            pool.returnConnection(poolKey, this);
        } else {
            // Actually close the connection
            forceClose();
        }
    }
    
    /**
     * Force close the connection without returning to pool
     */
    public void forceClose() {
        try {
            if (session != null && session.isConnected()) {
                session.disconnect();
                log.debug("SSH session closed: {}", getConnectionInfo());
            }
        } catch (Exception e) {
            log.warn("Error closing SSH session", e);
        }
    }
    
    /**
     * Mark connection as being used
     */
    public void markAsUsed() {
        this.lastUsedAt = System.currentTimeMillis();
        this.inUse = true;
    }
    
    /**
     * Mark connection as returned to pool
     */
    public void markAsReturned() {
        this.lastUsedAt = System.currentTimeMillis();
        this.inUse = false;
    }
    
    /**
     * Check if connection has been idle for too long
     */
    public boolean isIdleTimeout(long timeoutMs) {
        return !inUse && (System.currentTimeMillis() - lastUsedAt) > timeoutMs;
    }
    
    /**
     * Check if connection is currently in use
     */
    public boolean isInUse() {
        return inUse;
    }
    
    /**
     * Get connection age in milliseconds
     */
    public long getAgeMs() {
        return System.currentTimeMillis() - createdAt;
    }
    
    /**
     * Get idle time in milliseconds
     */
    public long getIdleTimeMs() {
        return System.currentTimeMillis() - lastUsedAt;
    }
    
    /**
     * Get session details for monitoring
     */
    public SessionDetails getSessionDetails() {
        if (session == null) {
            return null;
        }
        
        try {
            return SessionDetails.builder()
                .host(session.getHost())
                .port(session.getPort())
                .username(session.getUserName())
                .connected(session.isConnected())
                .serverVersion(session.getServerVersion())
                .clientVersion(session.getClientVersion())
                .ageMs(getAgeMs())
                .idleTimeMs(getIdleTimeMs())
                .inUse(inUse)
                .build();
        } catch (Exception e) {
            log.debug("Failed to get session details", e);
            return null;
        }
    }
    
    /**
     * Session Details Data Class
     */
    @lombok.Data
    @lombok.Builder
    public static class SessionDetails {
        private String host;
        private int port;
        private String username;
        private boolean connected;
        private String serverVersion;
        private String clientVersion;
        private long ageMs;
        private long idleTimeMs;
        private boolean inUse;
    }
}