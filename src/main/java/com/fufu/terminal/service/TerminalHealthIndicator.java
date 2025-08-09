package com.fufu.terminal.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * SSH 终端应用程序健康检查指示器。
 * <p>
 * 提供应用程序关键组件的健康状态检查，包括线程池状态、
 * 活跃会话数量和系统资源使用情况。
 * </p>
 * 
 * <p><strong>检查项目：</strong></p>
 * <ul>
 *     <li>任务执行器线程池状态</li>
 *     <li>定时任务调度器状态</li>
 *     <li>活跃 SSH 会话数量</li>
 *     <li>系统内存和CPU使用情况</li>
 * </ul>
 *
 * @author lizelin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalHealthIndicator implements HealthIndicator {

    private final ExecutorService taskExecutor;
    private final ScheduledExecutorService monitorScheduler;
    private final TerminalMetrics terminalMetrics;

    /**
     * 执行健康检查。
     *
     * @return 健康状态信息
     */
    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        
        try {
            // 检查任务执行器状态
            checkTaskExecutor(builder);
            
            // 检查调度器状态
            checkScheduler(builder);
            
            // 检查活跃会话
            checkActiveSessions(builder);
            
            // 检查系统资源
            checkSystemResources(builder);
            
        } catch (Exception e) {
            log.error("健康检查过程中发生错误", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("timestamp", System.currentTimeMillis())
                    .build();
        }
        
        return builder.build();
    }

    /**
     * 检查任务执行器状态。
     *
     * @param builder 健康状态构建器
     */
    private void checkTaskExecutor(Health.Builder builder) {
        if (taskExecutor instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor executor = (ThreadPoolExecutor) taskExecutor;
            
            int activeCount = executor.getActiveCount();
            int corePoolSize = executor.getCorePoolSize();
            int maximumPoolSize = executor.getMaximumPoolSize();
            long completedTaskCount = executor.getCompletedTaskCount();
            int queueSize = executor.getQueue().size();
            
            // 计算线程池利用率
            double utilization = (double) activeCount / maximumPoolSize * 100;
            
            builder.withDetail("taskExecutor", Health.up()
                    .withDetail("status", executor.isShutdown() ? "DOWN" : "UP")
                    .withDetail("activeThreads", activeCount)
                    .withDetail("corePoolSize", corePoolSize)
                    .withDetail("maximumPoolSize", maximumPoolSize)
                    .withDetail("completedTasks", completedTaskCount)
                    .withDetail("queueSize", queueSize)
                    .withDetail("utilization", String.format("%.2f%%", utilization))
                    .build());
            
            // 如果线程池利用率过高，标记为警告
            if (utilization > 90) {
                builder.status("WARN")
                       .withDetail("warning", "线程池利用率过高: " + String.format("%.2f%%", utilization));
            }
        }
    }

    /**
     * 检查调度器状态。
     *
     * @param builder 健康状态构建器
     */
    private void checkScheduler(Health.Builder builder) {
        boolean isShutdown = monitorScheduler.isShutdown();
        boolean isTerminated = monitorScheduler.isTerminated();
        
        builder.withDetail("scheduler", Health.up()
                .status(isShutdown ? "DOWN" : "UP")
                .withDetail("isShutdown", isShutdown)
                .withDetail("isTerminated", isTerminated)
                .build());
                
        if (isShutdown) {
            builder.status("DOWN")
                   .withDetail("error", "调度器已关闭");
        }
    }

    /**
     * 检查活跃会话状态。
     *
     * @param builder 健康状态构建器
     */
    private void checkActiveSessions(Health.Builder builder) {
        int activeSessionCount = terminalMetrics.getActiveSessionCount();
        
        builder.withDetail("sessions", Health.up()
                .status("UP")
                .withDetail("activeSessions", activeSessionCount)
                .withDetail("maxRecommendedSessions", 100) // 推荐最大会话数
                .build());
        
        // 如果活跃会话数过多，发出警告
        if (activeSessionCount > 100) {
            builder.status("WARN")
                   .withDetail("warning", "活跃会话数量过多: " + activeSessionCount);
        }
    }

    /**
     * 检查系统资源状态。
     *
     * @param builder 健康状态构建器
     */
    private void checkSystemResources(Health.Builder builder) {
        Runtime runtime = Runtime.getRuntime();
        
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        double memoryUsage = (double) usedMemory / maxMemory * 100;
        int availableProcessors = runtime.availableProcessors();
        
        builder.withDetail("system", Health.up()
                .status("UP")
                .withDetail("totalMemoryMB", totalMemory / 1024 / 1024)
                .withDetail("usedMemoryMB", usedMemory / 1024 / 1024)
                .withDetail("freeMemoryMB", freeMemory / 1024 / 1024)
                .withDetail("maxMemoryMB", maxMemory / 1024 / 1024)
                .withDetail("memoryUsage", String.format("%.2f%%", memoryUsage))
                .withDetail("availableProcessors", availableProcessors)
                .build());
        
        // 如果内存使用率过高，发出警告
        if (memoryUsage > 85) {
            builder.status("WARN")
                   .withDetail("warning", "内存使用率过高: " + String.format("%.2f%%", memoryUsage));
        }
        
        // 如果内存使用率极高，标记为不健康
        if (memoryUsage > 95) {
            builder.status("DOWN")
                   .withDetail("error", "内存使用率极高，可能导致系统不稳定: " + String.format("%.2f%%", memoryUsage));
        }
    }
}