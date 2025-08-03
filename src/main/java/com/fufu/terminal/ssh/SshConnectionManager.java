package com.fufu.terminal.ssh;

import com.fufu.terminal.model.SshConfig;
import com.fufu.terminal.resilience.CircuitBreakerManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * SSH Connection Manager with Circuit Breaker and Retry Logic
 * 
 * Manages SSH connections with resilience patterns including
 * circuit breaker, retry logic with exponential backoff,
 * and connection validation.
 */
@Component
@Slf4j
public class SshConnectionManager {
    
    @Autowired
    private SshConnectionPool connectionPool;
    
    @Autowired
    private CircuitBreakerManager circuitBreakerManager;
    
    @Value("${app.ssh.connection.timeout:30000}")
    private int connectionTimeout;
    
    @Value("${app.ssh.session.timeout:300000}")
    private int sessionTimeout;
    
    @Value("${app.ssh.retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    /**
     * Get an SSH connection with circuit breaker protection
     */
    public SshConnection getConnection(SshConfig config, String userId) {
        String connectionKey = generateConnectionKey(config, userId);
        
        // Validate configuration first
        var validationResult = config.validate();
        if (!validationResult.isValid()) {
            throw new SshConnectionException("Invalid SSH configuration: " + validationResult.getErrorMessage());
        }
        
        return circuitBreakerManager.getCircuitBreaker(connectionKey)
            .executeSupplier(() -> {
                try {
                    SshConnection connection = connectionPool.borrowConnection(config, userId);
                    log.debug("Successfully obtained SSH connection for {}", connectionKey);
                    return connection;
                } catch (Exception e) {
                    log.error("Failed to get SSH connection for {}", connectionKey, e);
                    throw new SshConnectionException("Failed to establish SSH connection to " + config.getConnectionString(), e);
                }
            });
    }
    
    /**
     * Get connection with retry logic and exponential backoff
     */
    @Retryable(
        value = {SshConnectionException.class}, 
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 16000)
    )
    public SshConnection getConnectionWithRetry(SshConfig config, String userId) {
        log.info("Attempting SSH connection with retry for user: {}", userId);
        return getConnection(config, userId);
    }
    
    /**
     * Test SSH connection without borrowing from pool
     */
    public ConnectionTestResult testConnection(SshConfig config) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate configuration
            var validationResult = config.validate();
            if (!validationResult.isValid()) {
                return ConnectionTestResult.failure("Configuration validation failed: " + validationResult.getErrorMessage(), 
                    System.currentTimeMillis() - startTime);
            }
            
            // Create temporary connection for testing
            com.jcraft.jsch.JSch jsch = new com.jcraft.jsch.JSch();
            
            // Add private key if provided
            if (config.getPrivateKey() != null && !config.getPrivateKey().isEmpty()) {
                byte[] privateKeyBytes = config.getPrivateKey().getBytes();
                byte[] passphraseBytes = config.getPassphrase() != null ? 
                    config.getPassphrase().getBytes() : null;
                jsch.addIdentity("test-key", privateKeyBytes, null, passphraseBytes);
            }
            
            // Create and configure session
            com.jcraft.jsch.Session session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                session.setPassword(config.getPassword());
            }
            
            java.util.Properties props = new java.util.Properties();
            props.put("StrictHostKeyChecking", config.isStrictHostKeyChecking() ? "yes" : "no");
            session.setConfig(props);
            session.setTimeout(connectionTimeout);
            
            // Attempt connection
            session.connect(connectionTimeout);
            
            // Test command execution
            com.jcraft.jsch.ChannelExec channel = (com.jcraft.jsch.ChannelExec) session.openChannel("exec");
            channel.setCommand("echo 'test'");
            channel.connect(5000); // 5 second timeout for test command
            
            // Wait for completion
            int timeout = 5000;
            while (!channel.isClosed() && timeout > 0) {
                try {
                    Thread.sleep(100);
                    timeout -= 100;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            boolean success = channel.isClosed() && channel.getExitStatus() == 0;
            
            // Clean up
            channel.disconnect();
            session.disconnect();
            
            long duration = System.currentTimeMillis() - startTime;
            
            if (success) {
                log.info("SSH connection test successful for {}@{}:{} ({}ms)", 
                    config.getUsername(), config.getHost(), config.getPort(), duration);
                return ConnectionTestResult.success("Connection test successful", duration);
            } else {
                return ConnectionTestResult.failure("Test command execution failed", duration);
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("SSH connection test failed for {}@{}:{}", 
                config.getUsername(), config.getHost(), config.getPort(), e);
            return ConnectionTestResult.failure("Connection test failed: " + e.getMessage(), duration);
        }
    }
    
    /**
     * Get connection status information
     */
    public ConnectionStatus getConnectionStatus(SshConfig config, String userId) {
        String connectionKey = generateConnectionKey(config, userId);
        var circuitBreakerState = circuitBreakerManager.getState(connectionKey);
        var circuitBreakerMetrics = circuitBreakerManager.getMetrics(connectionKey);
        
        return ConnectionStatus.builder()
            .connectionKey(connectionKey)
            .circuitBreakerState(circuitBreakerState)
            .circuitBreakerMetrics(circuitBreakerMetrics)
            .poolStatistics(connectionPool.getPoolStatistics())
            .build();
    }
    
    /**
     * Force reset circuit breaker for a connection
     */
    public void resetCircuitBreaker(SshConfig config, String userId) {
        String connectionKey = generateConnectionKey(config, userId);
        circuitBreakerManager.reset(connectionKey);
        log.info("Reset circuit breaker for connection: {}", connectionKey);
    }
    
    /**
     * Generate unique connection key for circuit breaker and pooling
     */
    private String generateConnectionKey(SshConfig config, String userId) {
        return String.format("%s@%s:%d-%s", config.getUsername(), config.getHost(), config.getPort(), userId);
    }
    
    /**
     * Connection Test Result
     */
    @lombok.Data
    @lombok.Builder
    public static class ConnectionTestResult {
        private boolean success;
        private String message;
        private long durationMs;
        private String errorDetails;
        
        public static ConnectionTestResult success(String message, long durationMs) {
            return ConnectionTestResult.builder()
                .success(true)
                .message(message)
                .durationMs(durationMs)
                .build();
        }
        
        public static ConnectionTestResult failure(String message, long durationMs) {
            return ConnectionTestResult.builder()
                .success(false)
                .message(message)
                .durationMs(durationMs)
                .build();
        }
        
        public ConnectionTestResult withErrorDetails(String errorDetails) {
            this.errorDetails = errorDetails;
            return this;
        }
    }
    
    /**
     * Connection Status Information
     */
    @lombok.Data
    @lombok.Builder
    public static class ConnectionStatus {
        private String connectionKey;
        private CircuitBreakerManager.CircuitBreakerState circuitBreakerState;
        private CircuitBreakerManager.CircuitBreakerMetrics circuitBreakerMetrics;
        private SshConnectionPool.PoolStatistics poolStatistics;
    }
}