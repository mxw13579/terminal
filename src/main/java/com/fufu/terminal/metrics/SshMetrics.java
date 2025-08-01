package com.fufu.terminal.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSH相关指标收集器
 * 收集SSH连接和操作的性能指标
 * @author lizelin
 */
@Component
@RequiredArgsConstructor
public class SshMetrics {
    
    private final MeterRegistry meterRegistry;
    
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong failedConnections = new AtomicLong(0);
    
    private final Counter connectionAttempts;
    private final Counter connectionFailures;
    private final Counter commandExecutions;
    private final Counter commandFailures;
    private final Timer connectionTime;
    private final Timer commandExecutionTime;
    
    public SshMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // 初始化计数器
        this.connectionAttempts = Counter.builder("ssh.connections.attempts")
            .description("Total number of SSH connection attempts")
            .register(meterRegistry);
            
        this.connectionFailures = Counter.builder("ssh.connections.failures")
            .description("Total number of failed SSH connections")
            .register(meterRegistry);
            
        this.commandExecutions = Counter.builder("ssh.commands.executions")
            .description("Total number of SSH command executions")
            .register(meterRegistry);
            
        this.commandFailures = Counter.builder("ssh.commands.failures")
            .description("Total number of failed SSH command executions")
            .register(meterRegistry);
        
        // 初始化计时器
        this.connectionTime = Timer.builder("ssh.connection.duration")
            .description("SSH connection establishment time")
            .register(meterRegistry);
            
        this.commandExecutionTime = Timer.builder("ssh.command.duration")
            .description("SSH command execution time")
            .register(meterRegistry);
        
        // 初始化仪表
        Gauge.builder("ssh.connections.active")
            .description("Number of active SSH connections")
            .register(meterRegistry, this, SshMetrics::getActiveConnections);
            
        Gauge.builder("ssh.connections.total")
            .description("Total number of SSH connections created")
            .register(meterRegistry, this, SshMetrics::getTotalConnections);
    }
    
    public void recordConnectionAttempt() {
        connectionAttempts.increment();
        totalConnections.incrementAndGet();
    }
    
    public void recordConnectionSuccess() {
        activeConnections.incrementAndGet();
    }
    
    public void recordConnectionFailure() {
        connectionFailures.increment();
        failedConnections.incrementAndGet();
    }
    
    public void recordConnectionClose() {
        activeConnections.decrementAndGet();
    }
    
    public void recordCommandExecution(String commandType) {
        commandExecutions.increment("type", commandType);
    }
    
    public void recordCommandFailure(String commandType, String errorType) {
        commandFailures.increment("type", commandType, "error", errorType);
    }
    
    public Timer.Sample startConnectionTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void stopConnectionTimer(Timer.Sample sample) {
        sample.stop(connectionTime);
    }
    
    public Timer.Sample startCommandTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void stopCommandTimer(Timer.Sample sample, String commandType) {
        sample.stop(Timer.builder("ssh.command.duration")
            .tag("type", commandType)
            .register(meterRegistry));
    }
    
    public int getActiveConnections() {
        return activeConnections.get();
    }
    
    public long getTotalConnections() {
        return totalConnections.get();
    }
    
    public long getFailedConnections() {
        return failedConnections.get();
    }
}