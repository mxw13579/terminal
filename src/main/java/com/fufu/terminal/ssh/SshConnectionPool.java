package com.fufu.terminal.ssh;

import com.fufu.terminal.model.SshConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Iterator;

/**
 * SSH Connection Pool Implementation
 * 
 * Manages pooled SSH connections with lifecycle management,
 * idle timeout, and connection validation.
 */
@Component
@Slf4j
public class SshConnectionPool {
    
    private final Map<String, Queue<PooledSshConnection>> connectionPools = new ConcurrentHashMap<>();
    private final Map<String, SshConfig> configsByKey = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(2);
    
    @Value("${app.ssh.pool.max-connections-per-pool:10}")
    private int maxConnectionsPerPool;
    
    @Value("${app.ssh.pool.idle-timeout-ms:600000}") // 10 minutes default
    private long idleTimeoutMs;
    
    @Value("${app.ssh.pool.connection-timeout-ms:30000}") // 30 seconds default
    private int connectionTimeoutMs;
    
    @Value("${app.ssh.pool.session-timeout-ms:300000}") // 5 minutes default
    private int sessionTimeoutMs;
    
    @PostConstruct
    public void initialize() {
        // Schedule periodic cleanup of idle connections
        cleanupExecutor.scheduleAtFixedRate(this::cleanupIdleConnections, 5, 5, TimeUnit.MINUTES);
        log.info("SSH Connection Pool initialized with maxConnectionsPerPool={}, idleTimeoutMs={}", 
                maxConnectionsPerPool, idleTimeoutMs);
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down SSH Connection Pool...");
        cleanupExecutor.shutdown();
        
        try {
            if (!cleanupExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Close all pooled connections
        connectionPools.values().forEach(pool -> {
            pool.forEach(conn -> {
                try {
                    conn.forceClose();
                } catch (Exception e) {
                    log.warn("Error closing pooled connection during shutdown", e);
                }
            });
        });
        
        connectionPools.clear();
        configsByKey.clear();
        log.info("SSH Connection Pool shutdown completed");
    }
    
    /**
     * Borrow a connection from the pool
     */
    public SshConnection borrowConnection(SshConfig config, String userId) throws SshConnectionException {
        String poolKey = generatePoolKey(config, userId);
        configsByKey.put(poolKey, config);
        
        Queue<PooledSshConnection> pool = connectionPools.computeIfAbsent(poolKey, 
            k -> new ConcurrentLinkedQueue<>());
        
        // Try to reuse existing connection
        PooledSshConnection connection = pool.poll();
        while (connection != null) {
            if (connection.isValid() && !connection.isIdleTimeout(idleTimeoutMs)) {
                connection.markAsUsed();
                log.debug("Reused existing SSH connection from pool: {}", poolKey);
                return connection;
            } else {
                try {
                    connection.forceClose();
                } catch (Exception e) {
                    log.warn("Error closing invalid pooled connection", e);
                }
                connection = pool.poll();
            }
        }
        
        // Create new connection if pool is empty or all connections invalid
        log.debug("Creating new SSH connection for pool: {}", poolKey);
        return createNewConnection(config, poolKey);
    }
    
    /**
     * Return a connection to the pool
     */
    public void returnConnection(String poolKey, PooledSshConnection connection) {
        if (connection.isValid()) {
            Queue<PooledSshConnection> pool = connectionPools.get(poolKey);
            if (pool != null && pool.size() < maxConnectionsPerPool) {
                connection.markAsReturned();
                pool.offer(connection);
                log.debug("Returned SSH connection to pool: {}", poolKey);
            } else {
                try {
                    connection.forceClose();
                    log.debug("Closed excess SSH connection for pool: {}", poolKey);
                } catch (Exception e) {
                    log.warn("Error closing excess pooled connection", e);
                }
            }
        } else {
            try {
                connection.forceClose();
                log.debug("Closed invalid SSH connection for pool: {}", poolKey);
            } catch (Exception e) {
                log.warn("Error closing invalid pooled connection", e);
            }
        }
    }
    
    /**
     * Create a new SSH connection
     */
    private PooledSshConnection createNewConnection(SshConfig config, String poolKey) throws SshConnectionException {
        try {
            com.jcraft.jsch.JSch jsch = new com.jcraft.jsch.JSch();
            
            // Add private key if provided
            if (config.getPrivateKey() != null && !config.getPrivateKey().isEmpty()) {
                byte[] privateKeyBytes = config.getPrivateKey().getBytes();
                byte[] passphraseBytes = config.getPassphrase() != null ? 
                    config.getPassphrase().getBytes() : null;
                jsch.addIdentity("ssh-key", privateKeyBytes, null, passphraseBytes);
            }
            
            // Create session
            com.jcraft.jsch.Session session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            
            // Set password if provided
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                session.setPassword(config.getPassword());
            }
            
            // Configure session properties
            java.util.Properties props = new java.util.Properties();
            props.put("StrictHostKeyChecking", config.isStrictHostKeyChecking() ? "yes" : "no");
            props.put("PreferredAuthentications", buildAuthMethods(config));
            props.put("ConnectTimeout", String.valueOf(connectionTimeoutMs));
            props.put("ServerAliveInterval", "60"); // Send keep-alive every 60 seconds
            props.put("ServerAliveCountMax", "3"); // Max 3 missed keep-alives before disconnect
            session.setConfig(props);
            
            // Set timeouts
            session.setTimeout(sessionTimeoutMs);
            
            // Connect
            session.connect(connectionTimeoutMs);
            
            log.info("Created new SSH connection: {}@{}:{}", config.getUsername(), config.getHost(), config.getPort());
            
            return new PooledSshConnection(session, poolKey, this);
            
        } catch (Exception e) {
            log.error("Failed to create SSH connection: {}@{}:{}", config.getUsername(), config.getHost(), config.getPort(), e);
            throw new SshConnectionException("Failed to create SSH connection to " + config.getConnectionString(), e);
        }
    }
    
    /**
     * Build authentication methods string based on config
     */
    private String buildAuthMethods(SshConfig config) {
        StringBuilder methods = new StringBuilder();
        
        if (config.isAllowPublicKeyAuth() && config.hasPublicKeyAuth()) {
            methods.append("publickey");
        }
        
        if (config.isAllowPasswordAuth() && config.hasPasswordAuth()) {
            if (methods.length() > 0) methods.append(",");
            methods.append("password");
        }
        
        if (config.isAllowKeyboardInteractiveAuth()) {
            if (methods.length() > 0) methods.append(",");
            methods.append("keyboard-interactive");
        }
        
        return methods.length() > 0 ? methods.toString() : "publickey,password,keyboard-interactive";
    }
    
    /**
     * Cleanup idle connections periodically
     */
    private void cleanupIdleConnections() {
        log.debug("Starting cleanup of idle SSH connections");
        
        connectionPools.forEach((poolKey, pool) -> {
            Iterator<PooledSshConnection> iterator = pool.iterator();
            int cleanedUp = 0;
            
            while (iterator.hasNext()) {
                PooledSshConnection connection = iterator.next();
                if (!connection.isValid() || connection.isIdleTimeout(idleTimeoutMs)) {
                    iterator.remove();
                    try {
                        connection.forceClose();
                        cleanedUp++;
                    } catch (Exception e) {
                        log.warn("Error closing idle connection during cleanup", e);
                    }
                }
            }
            
            if (cleanedUp > 0) {
                log.debug("Cleaned up {} idle connections from pool: {}", cleanedUp, poolKey);
            }
        });
    }
    
    /**
     * Generate pool key for connection sharing
     */
    private String generatePoolKey(SshConfig config, String userId) {
        return String.format("%s@%s:%d-%s", config.getUsername(), config.getHost(), config.getPort(), userId);
    }
    
    /**
     * Get pool statistics
     */
    public PoolStatistics getPoolStatistics() {
        int totalConnections = 0;
        int activeConnections = 0;
        int idleConnections = 0;
        
        for (Queue<PooledSshConnection> pool : connectionPools.values()) {
            for (PooledSshConnection connection : pool) {
                totalConnections++;
                if (connection.isInUse()) {
                    activeConnections++;
                } else {
                    idleConnections++;
                }
            }
        }
        
        return PoolStatistics.builder()
            .totalPools(connectionPools.size())
            .totalConnections(totalConnections)
            .activeConnections(activeConnections)
            .idleConnections(idleConnections)
            .maxConnectionsPerPool(maxConnectionsPerPool)
            .idleTimeoutMs(idleTimeoutMs)
            .build();
    }
    
    /**
     * Pool Statistics Data Class
     */
    @lombok.Data
    @lombok.Builder
    public static class PoolStatistics {
        private int totalPools;
        private int totalConnections;
        private int activeConnections;
        private int idleConnections;
        private int maxConnectionsPerPool;
        private long idleTimeoutMs;
    }
}