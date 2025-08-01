package com.fufu.terminal.health;

import com.fufu.terminal.service.SshMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 临时注释掉 Actuator 依赖，直到依赖问题解决
// import org.springframework.boot.actuator.health.Health;
// import org.springframework.boot.actuator.health.HealthIndicator;

/**
 * SSH连接健康指示器
 * 监控SSH连接池的健康状态
 * 
 * 注意：当前被注释掉，因为 Actuator 依赖问题
 * 请运行 fix-actuator.bat 脚本来修复依赖问题
 * 
 * @author lizelin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SshConnectionHealthIndicator { // implements HealthIndicator {
    
    private final SshMonitorService sshMonitorService;

    // 临时注释掉，直到 Actuator 依赖修复
    /*
    @Override
    public Health health() {
        try {
            // 获取SSH连接池状态
            int activeConnections = sshMonitorService.getActiveConnectionCount();
            int maxConnections = sshMonitorService.getMaxConnectionCount();
            double connectionUsage = (double) activeConnections / maxConnections;
            
            Health.Builder builder = Health.up()
                .withDetail("activeConnections", activeConnections)
                .withDetail("maxConnections", maxConnections)
                .withDetail("connectionUsage", String.format("%.2f%%", connectionUsage * 100));
            
            // 如果连接使用率超过90%，标记为警告
            if (connectionUsage > 0.9) {
                builder = Health.up()
                    .withDetail("warning", "High connection usage detected")
                    .withDetail("activeConnections", activeConnections)
                    .withDetail("maxConnections", maxConnections)
                    .withDetail("connectionUsage", String.format("%.2f%%", connectionUsage * 100));
            }
            
            // 检查连接池是否可用
            if (!sshMonitorService.isConnectionPoolHealthy()) {
                return Health.down()
                    .withDetail("error", "SSH connection pool is unhealthy")
                    .withDetail("activeConnections", activeConnections)
                    .withDetail("maxConnections", maxConnections)
                    .build();
            }
            
            return builder.build();
            
        } catch (Exception e) {
            log.error("Error checking SSH connection health", e);
            return Health.down()
                .withDetail("error", "Failed to check SSH connection health")
                .withDetail("exception", e.getMessage())
                .build();
        }
    }
    */
    
    /**
     * 临时的健康检查方法
     * @return 连接状态信息
     */
    public String getConnectionStatus() {
        try {
            int activeConnections = sshMonitorService.getActiveConnectionCount();
            int maxConnections = sshMonitorService.getMaxConnectionCount();
            double connectionUsage = (double) activeConnections / maxConnections;
            
            return String.format("SSH Connections: %d/%d (%.2f%%)", 
                activeConnections, maxConnections, connectionUsage * 100);
                
        } catch (Exception e) {
            log.error("Error checking SSH connection status", e);
            return "SSH Connection status unavailable: " + e.getMessage();
        }
    }
}