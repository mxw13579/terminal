package com.fufu.terminal.health;

import com.fufu.terminal.service.ScriptExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 临时注释掉 Actuator 依赖，直到依赖问题解决
// import org.springframework.boot.actuator.health.Health;
// import org.springframework.boot.actuator.health.HealthIndicator;

/**
 * 脚本执行健康指示器
 * 监控脚本执行系统的健康状态
 * 
 * 注意：当前被注释掉，因为 Actuator 依赖问题
 * 请运行 fix-actuator.bat 脚本来修复依赖问题
 * 
 * @author lizelin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScriptExecutionHealthIndicator { // implements HealthIndicator {
    
    private final ScriptExecutionService scriptExecutionService;

    // 临时注释掉，直到 Actuator 依赖修复
    /*
    @Override
    public Health health() {
        try {
            // 获取脚本执行状态
            int activeExecutions = scriptExecutionService.getActiveExecutionCount();
            int maxConcurrentExecutions = scriptExecutionService.getMaxConcurrentExecutions();
            double executionUsage = (double) activeExecutions / maxConcurrentExecutions;
            
            Health.Builder builder = Health.up()
                .withDetail("activeExecutions", activeExecutions)
                .withDetail("maxConcurrentExecutions", maxConcurrentExecutions)
                .withDetail("executionUsage", String.format("%.2f%%", executionUsage * 100))
                .withDetail("queuedExecutions", scriptExecutionService.getQueuedExecutionCount());
            
            // 如果执行使用率超过90%，标记为警告
            if (executionUsage > 0.9) {
                builder = Health.up()
                    .withDetail("warning", "High script execution usage detected")
                    .withDetail("activeExecutions", activeExecutions)
                    .withDetail("maxConcurrentExecutions", maxConcurrentExecutions)
                    .withDetail("executionUsage", String.format("%.2f%%", executionUsage * 100))
                    .withDetail("queuedExecutions", scriptExecutionService.getQueuedExecutionCount());
            }
            
            // 检查是否有长时间运行的脚本
            int longRunningCount = scriptExecutionService.getLongRunningExecutionCount();
            if (longRunningCount > 0) {
                builder.withDetail("longRunningExecutions", longRunningCount)
                       .withDetail("warning", "Long running executions detected");
            }
            
            return builder.build();
            
        } catch (Exception e) {
            log.error("Error checking script execution health", e);
            return Health.down()
                .withDetail("error", "Failed to check script execution health")
                .withDetail("exception", e.getMessage())
                .build();
        }
    }
    */
    
    /**
     * 临时的执行状态检查方法
     * @return 执行状态信息
     */
    public String getExecutionStatus() {
        try {
            int activeExecutions = scriptExecutionService.getActiveExecutionCount();
            int maxConcurrentExecutions = scriptExecutionService.getMaxConcurrentExecutions();
            int queuedExecutions = scriptExecutionService.getQueuedExecutionCount();
            double executionUsage = (double) activeExecutions / maxConcurrentExecutions;
            
            return String.format("Script Executions: %d/%d (%.2f%%), Queued: %d", 
                activeExecutions, maxConcurrentExecutions, executionUsage * 100, queuedExecutions);
                
        } catch (Exception e) {
            log.error("Error checking script execution status", e);
            return "Script execution status unavailable: " + e.getMessage();
        }
    }
}