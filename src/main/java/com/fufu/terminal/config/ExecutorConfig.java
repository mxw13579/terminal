package com.fufu.terminal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 线程池配置类
 * 将ExecutorService和ScheduledExecutorService定义为Spring管理的Bean
 */
@Configuration
public class ExecutorConfig {

    /**
     * 定义一个任务执行器线程池。
     * 使用Spring的ThreadPoolTaskExecutor可以提供更详细的配置，例如核心/最大线程数和队列容量。
     * 这比无界的newCachedThreadPool更安全。
     * @return ExecutorService Bean
     */
    @Bean(name = "taskExecutor")
    public ExecutorService taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10); // 核心线程数
        executor.setMaxPoolSize(100); // 最大线程数
        executor.setQueueCapacity(50); // 任务队列容量
        executor.setThreadNamePrefix("ssh-task-"); // 线程名前缀，便于调试
        executor.setWaitForTasksToCompleteOnShutdown(true); // 优雅关闭
        executor.setAwaitTerminationSeconds(60); // 等待60秒
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    /**
     * 定义一个用于监控任务的调度线程池。
     * @return ScheduledExecutorService Bean
     */
    @Bean(name = "monitorScheduler")
    public ScheduledExecutorService monitorScheduler() {
        // 对于定时任务，一个大小为2的池通常足够，并提供一些冗余
        return Executors.newScheduledThreadPool(2);
    }
}
