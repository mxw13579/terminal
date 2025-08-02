package com.fufu.terminal.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for script execution system
 * Provides configurable settings for SSH connections, timeouts, and monitoring
 */
@Data
@Component
@ConfigurationProperties(prefix = "script-execution")
public class ScriptExecutionProperties {
    
    private Ssh ssh = new Ssh();
    private Execution execution = new Execution();
    private Validation validation = new Validation();
    private Monitoring monitoring = new Monitoring();
    
    @Data
    public static class Ssh {
        private ConnectionPool connectionPool = new ConnectionPool();
        private Retry retry = new Retry();
        private Timeouts timeouts = new Timeouts();
        
        @Data
        public static class ConnectionPool {
            private int maxSize = 20;
            private int minIdle = 5;
            private int maxIdle = 10;
            private Duration maxWaitTime = Duration.ofSeconds(30);
            private boolean testOnBorrow = true;
            private boolean testOnReturn = false;
            private boolean testWhileIdle = true;
            private Duration timeBetweenEvictionRuns = Duration.ofMinutes(1);
            private Duration minEvictableIdleTime = Duration.ofMinutes(5);
            private String validationQuery = "echo 'test'";
        }
        
        @Data
        public static class Retry {
            private int maxAttempts = 3;
            private Duration backoffDelay = Duration.ofSeconds(1);
            private Duration maxBackoffDelay = Duration.ofSeconds(10);
            private double multiplier = 2.0;
            private boolean enableCircuitBreaker = true;
            private int circuitBreakerFailureThreshold = 5;
            private Duration circuitBreakerRecoveryTime = Duration.ofMinutes(1);
        }
        
        @Data
        public static class Timeouts {
            private Duration connectionTimeout = Duration.ofSeconds(30);
            private Duration commandTimeout = Duration.ofMinutes(5);
            private Duration keepAliveInterval = Duration.ofMinutes(1);
            private Duration sessionTimeout = Duration.ofMinutes(30);
        }
    }
    
    @Data
    public static class Execution {
        private Duration staticScriptTimeout = Duration.ofSeconds(30);
        private Duration dynamicScriptTimeout = Duration.ofMinutes(30);
        private int maxConcurrentExecutions = 10;
        private int maxQueuedExecutions = 50;
        private Duration executionCleanupInterval = Duration.ofMinutes(10);
        private Duration executionHistoryRetention = Duration.ofDays(30);
    }
    
    @Data
    public static class Validation {
        private boolean enableCrossFieldValidation = true;
        private boolean enableSecurityChecks = true;
        private int maxParameterLength = 1000;
        private int maxParameterCount = 100;
        private boolean enableParameterSanitization = true;
    }
    
    @Data
    public static class Monitoring {
        private boolean enableMetrics = true;
        private Duration metricsRetention = Duration.ofDays(30);
        private boolean enableHealthChecks = true;
        private AlertThresholds alertThresholds = new AlertThresholds();
        
        @Data
        public static class AlertThresholds {
            private double errorRate = 0.1; // 10% error rate threshold
            private Duration slowExecutionThreshold = Duration.ofMinutes(5);
            private int maxConnectionPoolWaitTime = 30; // seconds
            private double connectionPoolUtilizationThreshold = 0.8; // 80%
        }
    }
}