package com.fufu.terminal.config;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 线程池配置类。
 * <p>
 * 本类负责将 {@link ExecutorService} 和 {@link ScheduledExecutorService} 注册为 Spring 管理的 Bean，
 * 并支持参数化配置，提升灵活性和可维护性。
 * </p>
 *
 * @author your_name
 */
@Configuration
public class ExecutorConfig {

    @Value("${threadpool.corePoolSize:10}")
    private int corePoolSize;

    @Value("${threadpool.maxPoolSize:100}")
    private int maxPoolSize;

    @Value("${threadpool.queueCapacity:50}")
    private int queueCapacity;

    @Value("${threadpool.awaitTerminationSeconds:60}")
    private int awaitTerminationSeconds;

    @Value("${threadpool.scheduledPoolSize:2}")
    private int scheduledPoolSize;

    private ScheduledExecutorService scheduledExecutorService;

    /**
     * 创建任务执行器线程池。
     * <p>
     * 使用 Spring 的 {@link ThreadPoolTaskExecutor}，可灵活配置核心/最大线程数及队列容量，
     * 并支持优雅关闭。
     * </p>
     *
     * @return ExecutorService Bean，供业务异步任务使用
     */
    @Bean(name = "taskExecutor")
    public ExecutorService taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize); // 核心线程数
        executor.setMaxPoolSize(maxPoolSize);   // 最大线程数
        executor.setQueueCapacity(queueCapacity); // 队列容量
        executor.setThreadNamePrefix("ssh-task-"); // 线程名前缀
        executor.setWaitForTasksToCompleteOnShutdown(true); // 优雅关闭
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds); // 关闭等待时长
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    /**
     * 创建调度任务线程池。
     * <p>
     * 用于定时任务调度，线程池大小可配置。
     * </p>
     *
     * @return ScheduledExecutorService Bean，供定时/周期任务使用
     */
    @Bean(name = "monitorScheduler")
    public ScheduledExecutorService monitorScheduler() {
        this.scheduledExecutorService = Executors.newScheduledThreadPool(scheduledPoolSize);
        return this.scheduledExecutorService;
    }

    /**
     * 优雅关闭定时任务线程池，防止资源泄漏。
     */
    @PreDestroy
    public void shutdownScheduler() {
        if (scheduledExecutorService != null && !scheduledExecutorService.isShutdown()) {
            scheduledExecutorService.shutdown();
        }
    }
}
