package com.fufu.terminal.service.ssh;

import com.fufu.terminal.command.model.SshConnectionConfig;
import com.fufu.terminal.config.properties.ScriptExecutionProperties;
import com.fufu.terminal.exception.ConnectionException;
import com.fufu.terminal.model.SshConnection;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production-ready SSH connection service with connection pooling, validation, and retry mechanisms
 * Replaces placeholder implementations with real SSH connection management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionSshConnectionService {
    
    private final ScriptExecutionProperties properties;
    private final ConcurrentHashMap<String, ObjectPool<SshConnection>> connectionPools = new ConcurrentHashMap<>();
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger totalConnectionsCreated = new AtomicInteger(0);
    private final AtomicInteger connectionFailures = new AtomicInteger(0);
    
    private RetryTemplate retryTemplate;
    
    @PostConstruct
    public void initialize() {
        setupRetryTemplate();
        log.info("ProductionSshConnectionService initialized with max pool size: {}", 
                properties.getSsh().getConnectionPool().getMaxSize());
    }
    
    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up SSH connection pools...");
        connectionPools.values().forEach(pool -> {
            try {
                pool.close();
            } catch (Exception e) {
                log.warn("Error closing connection pool", e);
            }
        });
        connectionPools.clear();
        log.info("SSH connection service cleanup completed");
    }
    
    /**
     * Create SSH connection with validation and retry mechanism
     */
    public SshConnection createConnection(SshConnectionConfig config) throws ConnectionException {
        validateConfiguration(config);
        
        String poolKey = generatePoolKey(config);
        ObjectPool<SshConnection> pool = getOrCreateConnectionPool(poolKey, config);
        
        try {
            return retryTemplate.execute(new RetryCallback<SshConnection, ConnectionException>() {
                @Override
                public SshConnection doWithRetry(RetryContext context) throws ConnectionException {
                    try {
                        log.debug("Attempting to get connection from pool (attempt: {})", context.getRetryCount() + 1);
                        SshConnection connection = pool.borrowObject();
                        
                        // Test connection validity
                        if (!testConnection(connection)) {
                            // Return invalid connection to pool for cleanup
                            try {
                                pool.invalidateObject(connection);
                            } catch (Exception e) {
                                log.warn("Error invalidating connection", e);
                            }
                            throw new ConnectionException(
                                "Connection validation failed", 
                                config.getHost(), 
                                config.getPort(), 
                                config.getUsername(),
                                context.getRetryCount()
                            );
                        }
                        
                        activeConnections.incrementAndGet();
                        log.debug("Successfully obtained SSH connection to {}@{}:{}", 
                                config.getUsername(), config.getHost(), config.getPort());
                        return connection;
                        
                    } catch (Exception e) {
                        connectionFailures.incrementAndGet();
                        throw new ConnectionException(
                            "Failed to create SSH connection: " + e.getMessage(),
                            e,
                            config.getHost(),
                            config.getPort(), 
                            config.getUsername(),
                            context.getRetryCount()
                        );
                    }
                }
            });
            
        } catch (Exception e) {
            if (e instanceof ConnectionException) {
                throw (ConnectionException) e;
            }
            throw new ConnectionException(
                "Unexpected error creating SSH connection: " + e.getMessage(),
                e,
                config.getHost(),
                config.getPort(),
                config.getUsername(),
                0
            );
        }
    }
    
    /**
     * Return connection to the pool
     */
    public void returnConnection(SshConnection connection, SshConnectionConfig config) {
        if (connection == null) {
            return;
        }
        
        String poolKey = generatePoolKey(config);
        ObjectPool<SshConnection> pool = connectionPools.get(poolKey);
        
        if (pool != null) {
            try {
                activeConnections.decrementAndGet();
                pool.returnObject(connection);
                log.debug("Connection returned to pool for {}@{}:{}", 
                        config.getUsername(), config.getHost(), config.getPort());
            } catch (Exception e) {
                log.warn("Error returning connection to pool", e);
                try {
                    pool.invalidateObject(connection);
                } catch (Exception invalidateError) {
                    log.warn("Error invalidating connection", invalidateError);
                }
            }
        } else {
            // Pool not found, close connection directly
            try {
                connection.close();
                log.debug("Connection closed directly (pool not found)");
            } catch (Exception e) {
                log.warn("Error closing connection", e);
            }
        }
    }
    
    /**
     * Test connection validity by executing a simple command
     */
    public boolean testConnection(SshConnection connection) {
        try {
            if (connection == null || !connection.isConnected()) {
                return false;
            }
            
            Session session = connection.getJschSession();
            if (session == null || !session.isConnected()) {
                return false;
            }
            
            // Execute validation command
            String validationQuery = properties.getSsh().getConnectionPool().getValidationQuery();
            com.fufu.terminal.command.CommandResult result = 
                com.fufu.terminal.command.SshCommandUtil.executeCommand(connection, validationQuery);
            
            return result != null && result.getExitStatus() == 0;
            
        } catch (Exception e) {
            log.debug("Connection validation failed", e);
            return false;
        }
    }
    
    /**
     * Get connection pool health status
     */
    public ConnectionPoolHealth getPoolHealth() {
        int totalActive = 0;
        int totalIdle = 0;
        int totalPools = connectionPools.size();
        
        for (ObjectPool<SshConnection> pool : connectionPools.values()) {
            if (pool instanceof GenericObjectPool) {
                GenericObjectPool<SshConnection> genericPool = (GenericObjectPool<SshConnection>) pool;
                totalActive += genericPool.getNumActive();
                totalIdle += genericPool.getNumIdle();
            }
        }
        
        return ConnectionPoolHealth.builder()
            .totalPools(totalPools)
            .totalActiveConnections(totalActive)
            .totalIdleConnections(totalIdle)
            .totalConnectionsCreated(totalConnectionsCreated.get())
            .connectionFailures(connectionFailures.get())
            .maxPoolSize(properties.getSsh().getConnectionPool().getMaxSize())
            .healthy(connectionFailures.get() < totalConnectionsCreated.get() * 0.1) // Less than 10% failure rate
            .build();
    }
    
    /**
     * Validate SSH connection configuration
     */
    private void validateConfiguration(SshConnectionConfig config) throws ConnectionException {
        if (config == null) {
            throw new ConnectionException("SSH configuration is null", "", 0, "");
        }
        
        if (config.getHost() == null || config.getHost().trim().isEmpty()) {
            throw new ConnectionException("SSH host is required", 
                config.getHost(), config.getPort(), config.getUsername());
        }
        
        if (config.getPort() <= 0 || config.getPort() > 65535) {
            throw new ConnectionException("SSH port must be between 1 and 65535", 
                config.getHost(), config.getPort(), config.getUsername());
        }
        
        if (config.getUsername() == null || config.getUsername().trim().isEmpty()) {
            throw new ConnectionException("SSH username is required", 
                config.getHost(), config.getPort(), config.getUsername());
        }
        
        if (config.getPassword() == null || config.getPassword().trim().isEmpty()) {
            throw new ConnectionException("SSH password is required", 
                config.getHost(), config.getPort(), config.getUsername());
        }
    }
    
    /**
     * Generate unique pool key for connection configuration
     */
    private String generatePoolKey(SshConnectionConfig config) {
        return String.format("%s@%s:%d", config.getUsername(), config.getHost(), config.getPort());
    }
    
    /**
     * Get or create connection pool for the given configuration
     */
    private ObjectPool<SshConnection> getOrCreateConnectionPool(String poolKey, SshConnectionConfig config) {
        return connectionPools.computeIfAbsent(poolKey, key -> {
            log.info("Creating new SSH connection pool for: {}", key);
            
            GenericObjectPoolConfig<SshConnection> poolConfig = new GenericObjectPoolConfig<>();
            poolConfig.setMaxTotal(properties.getSsh().getConnectionPool().getMaxSize());
            poolConfig.setMaxIdle(properties.getSsh().getConnectionPool().getMaxIdle());
            poolConfig.setMinIdle(properties.getSsh().getConnectionPool().getMinIdle());
            poolConfig.setTestOnBorrow(properties.getSsh().getConnectionPool().isTestOnBorrow());
            poolConfig.setTestOnReturn(properties.getSsh().getConnectionPool().isTestOnReturn());
            poolConfig.setTestWhileIdle(properties.getSsh().getConnectionPool().isTestWhileIdle());
            poolConfig.setTimeBetweenEvictionRunsMillis(
                properties.getSsh().getConnectionPool().getTimeBetweenEvictionRuns().toMillis());
            poolConfig.setMinEvictableIdleTimeMillis(
                properties.getSsh().getConnectionPool().getMinEvictableIdleTime().toMillis());
            poolConfig.setMaxWaitMillis(properties.getSsh().getConnectionPool().getMaxWaitTime().toMillis());
            
            SshConnectionFactory factory = new SshConnectionFactory(config, properties);
            return new GenericObjectPool<>(factory, poolConfig);
        });
    }
    
    /**
     * Setup retry template with exponential backoff
     */
    private void setupRetryTemplate() {
        retryTemplate = new RetryTemplate();
        
        // Configure retry policy
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(properties.getSsh().getRetry().getMaxAttempts());
        retryTemplate.setRetryPolicy(retryPolicy);
        
        // Configure backoff policy
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(properties.getSsh().getRetry().getBackoffDelay().toMillis());
        backOffPolicy.setMaxInterval(properties.getSsh().getRetry().getMaxBackoffDelay().toMillis());
        backOffPolicy.setMultiplier(properties.getSsh().getRetry().getMultiplier());
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        log.info("Retry template configured with {} max attempts and {}ms initial backoff", 
                properties.getSsh().getRetry().getMaxAttempts(),
                properties.getSsh().getRetry().getBackoffDelay().toMillis());
    }
    
    /**
     * Connection pool health status
     */
    @lombok.Data
    @lombok.Builder
    public static class ConnectionPoolHealth {
        private int totalPools;
        private int totalActiveConnections;
        private int totalIdleConnections;
        private int totalConnectionsCreated;
        private int connectionFailures;
        private int maxPoolSize;
        private boolean healthy;
        
        public double getUtilizationPercentage() {
            if (maxPoolSize == 0) return 0.0;
            return (double) totalActiveConnections / maxPoolSize * 100.0;
        }
        
        public double getFailureRate() {
            if (totalConnectionsCreated == 0) return 0.0;
            return (double) connectionFailures / totalConnectionsCreated * 100.0;
        }
    }
}