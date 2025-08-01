package com.fufu.terminal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;

/**
 * 性能优化配置
 * 内存管理和资源清理
 * @author lizelin
 */
@Configuration
@EnableScheduling
public class PerformanceConfig {
    
    /**
     * 创建用于定期清理的线程池
     */
    @Bean("cleanupScheduler")
    public ScheduledExecutorService cleanupScheduler() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "cleanup-thread");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 创建任务调度器
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("task-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}