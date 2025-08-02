package com.fufu.terminal.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 脚本执行相关指标收集器
 * 收集脚本执行的性能指标
 * @author lizelin
 */
@Component
public class ScriptMetrics {
    
    private final MeterRegistry meterRegistry;
    
    private final AtomicInteger activeExecutions = new AtomicInteger(0);
    private final AtomicLong totalExecutions = new AtomicLong(0);
    private final AtomicLong successfulExecutions = new AtomicLong(0);
    private final AtomicLong failedExecutions = new AtomicLong(0);
    
    private final Counter executionAttempts;
    private final Counter executionSuccesses;
    private final Counter executionFailures;
    private final Counter interactionRequests;
    private final Timer executionTime;
    
    public ScriptMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // 初始化计数器
        this.executionAttempts = Counter.builder("script.executions.attempts")
            .description("Total number of script execution attempts")
            .register(meterRegistry);
            
        this.executionSuccesses = Counter.builder("script.executions.successes")
            .description("Total number of successful script executions")
            .register(meterRegistry);
            
        this.executionFailures = Counter.builder("script.executions.failures")
            .description("Total number of failed script executions")
            .register(meterRegistry);
            
        this.interactionRequests = Counter.builder("script.interactions.requests")
            .description("Total number of script interaction requests")
            .register(meterRegistry);
        
        // 初始化计时器
        this.executionTime = Timer.builder("script.execution.duration")
            .description("Script execution time")
            .register(meterRegistry);
        
        // 初始化仪表
        Gauge.builder("script.executions.active", this, ScriptMetrics::getActiveExecutions)
            .description("Number of active script executions")
            .register(meterRegistry);
            
        Gauge.builder("script.executions.total", this, ScriptMetrics::getTotalExecutions)
            .description("Total number of script executions")
            .register(meterRegistry);
            
        Gauge.builder("script.executions.success.rate", this, ScriptMetrics::getSuccessRate)
            .description("Script execution success rate")
            .register(meterRegistry);
    }
    
    public void recordExecutionAttempt() {
        executionAttempts.increment();
        totalExecutions.incrementAndGet();
        activeExecutions.incrementAndGet();
    }
    
    public void recordExecutionSuccess(String scriptType) {
        Counter.builder("script.executions.successes")
            .tag("type", scriptType)
            .register(meterRegistry)
            .increment();
        executionSuccesses.increment();
        successfulExecutions.incrementAndGet();
        activeExecutions.decrementAndGet();
    }
    
    public void recordExecutionFailure(String scriptType, String errorType) {
        Counter.builder("script.executions.failures")
            .tag("type", scriptType)
            .tag("error", errorType)
            .register(meterRegistry)
            .increment();
        executionFailures.increment();
        failedExecutions.incrementAndGet();
        activeExecutions.decrementAndGet();
    }
    
    public void recordInteractionRequest(String interactionType) {
        Counter.builder("script.interaction.requests")
            .tag("type", interactionType)
            .register(meterRegistry)
            .increment();
        interactionRequests.increment();
    }
    
    public Timer.Sample startExecutionTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void stopExecutionTimer(Timer.Sample sample, String scriptType) {
        sample.stop(Timer.builder("script.execution.duration")
            .tag("type", scriptType)
            .register(meterRegistry));
    }
    
    public int getActiveExecutions() {
        return activeExecutions.get();
    }
    
    public long getTotalExecutions() {
        return totalExecutions.get();
    }
    
    public long getSuccessfulExecutions() {
        return successfulExecutions.get();
    }
    
    public long getFailedExecutions() {
        return failedExecutions.get();
    }
    
    public double getSuccessRate() {
        long total = totalExecutions.get();
        if (total == 0) {
            return 1.0;
        }
        return (double) successfulExecutions.get() / total;
    }
}