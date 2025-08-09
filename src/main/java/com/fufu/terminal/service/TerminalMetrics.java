package com.fufu.terminal.service;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Terminal 应用程序自定义指标组件。
 * <p>
 * 提供 SSH 终端应用程序特定的指标收集和报告功能，
 * 包括会话管理、命令执行、文件传输和 WebSocket 稳定性等指标。
 * </p>
 * 
 * <p><strong>指标类别：</strong></p>
 * <ul>
 *     <li>活跃会话计数</li>
 *     <li>命令执行延迟（P50/P95/P99）</li>
 *     <li>文件传输吞吐量</li>
 *     <li>WebSocket 连接稳定性</li>
 *     <li>错误率统计</li>
 * </ul>
 *
 * @author lizelin
 */
@Slf4j
@Component
public class TerminalMetrics implements MeterBinder {

    private final AtomicInteger activeSessionsGauge = new AtomicInteger(0);
    private final AtomicLong totalFileTransferBytes = new AtomicLong(0);
    private final AtomicInteger websocketReconnects = new AtomicInteger(0);
    
    // 用于跟踪活跃会话的映射
    private final ConcurrentHashMap<String, Long> activeSessions = new ConcurrentHashMap<>();
    
    // Micrometer 指标
    private Counter sessionCreatedCounter;
    private Counter sessionDestroyedCounter;
    private Counter commandExecutedCounter;
    private Counter commandFailedCounter;
    private Timer commandExecutionTimer;
    private Counter fileTransferCounter;
    private Timer fileTransferTimer;
    private DistributionSummary fileTransferSize;
    private Counter websocketConnectCounter;
    private Counter websocketDisconnectCounter;
    private Counter stompErrorCounter;
    private Gauge totalTransferBytesGauge;

    private MeterRegistry meterRegistry;
    
    @Override
    public void bindTo(MeterRegistry registry) {
        this.meterRegistry = registry;
        // 会话相关指标
        sessionCreatedCounter = Counter.builder("terminal.sessions.created")
                .description("Total number of SSH sessions created")
                .register(registry);
                
        sessionDestroyedCounter = Counter.builder("terminal.sessions.destroyed")
                .description("Total number of SSH sessions destroyed")
                .register(registry);
                
        // 活跃会话数量仪表
        Gauge.builder("terminal.sessions.active", activeSessionsGauge, AtomicInteger::get)
                .description("Number of currently active SSH sessions")
                .register(registry);

        // 命令执行指标
        commandExecutedCounter = Counter.builder("terminal.commands.executed")
                .description("Total number of SSH commands executed")
                .tag("status", "success")
                .register(registry);
                
        commandFailedCounter = Counter.builder("terminal.commands.executed")
                .description("Total number of failed SSH commands")
                .tag("status", "failed")
                .register(registry);
                
        commandExecutionTimer = Timer.builder("terminal.commands.duration")
                .description("SSH command execution time")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .register(registry);

        // 文件传输指标
        fileTransferCounter = Counter.builder("terminal.sftp.transfers")
                .description("Total number of SFTP file transfers")
                .register(registry);
                
        fileTransferTimer = Timer.builder("terminal.sftp.transfer.duration")
                .description("SFTP file transfer duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
                
        fileTransferSize = DistributionSummary.builder("terminal.sftp.transfer.bytes")
                .description("SFTP file transfer size in bytes")
                .baseUnit("bytes")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
                
        totalTransferBytesGauge = Gauge.builder("terminal.sftp.transfer.bytes.total", totalFileTransferBytes, AtomicLong::get)
                .description("Total bytes transferred via SFTP")
                .baseUnit("bytes")
                .register(registry);

        // WebSocket 连接指标
        websocketConnectCounter = Counter.builder("terminal.websocket.connections")
                .description("Total WebSocket connections")
                .tag("status", "connected")
                .register(registry);
                
        websocketDisconnectCounter = Counter.builder("terminal.websocket.connections")
                .description("Total WebSocket disconnections")
                .tag("status", "disconnected")
                .register(registry);
                
        Gauge.builder("terminal.websocket.reconnects", websocketReconnects, AtomicInteger::get)
                .description("Number of WebSocket reconnection attempts")
                .register(registry);

        // STOMP 错误指标
        stompErrorCounter = Counter.builder("terminal.stomp.errors")
                .description("Total STOMP protocol errors")
                .register(registry);

        log.info("Terminal 自定义指标已注册到 MeterRegistry");
    }

    // === 会话管理指标方法 ===
    
    /**
     * 记录会话创建事件。
     */
    public void recordSessionCreated(String sessionId) {
        activeSessions.put(sessionId, System.currentTimeMillis());
        activeSessionsGauge.incrementAndGet();
        sessionCreatedCounter.increment();
        log.debug("记录会话创建: {}", sessionId);
    }

    /**
     * 记录会话销毁事件。
     */
    public void recordSessionDestroyed(String sessionId) {
        Long startTime = activeSessions.remove(sessionId);
        if (startTime != null) {
            activeSessionsGauge.decrementAndGet();
            sessionDestroyedCounter.increment();
            
            // 记录会话持续时间
            long sessionDuration = System.currentTimeMillis() - startTime;
            Timer.builder("terminal.sessions.duration")
                    .description("SSH session duration")
                    .register(getMeterRegistry())
                    .record(Duration.ofMillis(sessionDuration));
                    
            log.debug("记录会话销毁: {}, 持续时间: {}ms", sessionId, sessionDuration);
        }
    }

    /**
     * 获取当前活跃会话数量。
     */
    public int getActiveSessionCount() {
        return activeSessionsGauge.get();
    }

    // === 命令执行指标方法 ===
    
    /**
     * 记录命令执行成功（包括计时）。
     */
    public void recordCommandExecuted(long startTimeNanos) {
        commandExecutedCounter.increment();
        Duration duration = Duration.ofNanos(System.nanoTime() - startTimeNanos);
        commandExecutionTimer.record(duration);
        log.debug("记录命令执行成功，耗时: {}ms", duration.toMillis());
    }

    /**
     * 记录命令执行失败（包括计时）。
     */
    public void recordCommandFailed(long startTimeNanos, String errorType) {
        Counter.builder("terminal.commands.executed")
                .description("Total number of failed SSH commands")
                .tag("status", "failed")
                .tag("error.type", errorType)
                .register(getMeterRegistry())
                .increment();
        Duration duration = Duration.ofNanos(System.nanoTime() - startTimeNanos);
        commandExecutionTimer.record(duration);
        log.debug("记录命令执行失败，错误类型: {}, 耗时: {}ms", errorType, duration.toMillis());
    }

    /**
     * 开始命令执行计时（返回开始时间）。
     */
    public long startCommandTimer() {
        return System.nanoTime();
    }

    // === 文件传输指标方法 ===
    
    /**
     * 记录文件传输完成（包括计时）。
     */
    public void recordFileTransfer(long bytes, long startTimeNanos, String operation) {
        Counter.builder("terminal.sftp.transfers")
                .description("Total number of SFTP file transfers")
                .tag("operation", operation)
                .register(getMeterRegistry())
                .increment();
        Duration duration = Duration.ofNanos(System.nanoTime() - startTimeNanos);
        fileTransferTimer.record(duration);
        fileTransferSize.record(bytes);
        totalFileTransferBytes.addAndGet(bytes);
        
        // 计算传输速率 (bytes/second)
        double throughputBps = bytes / Math.max(duration.toMillis() / 1000.0, 0.001);
        
        log.debug("记录文件传输: {} bytes, 耗时: {}ms, 操作: {}, 传输速率: {} bytes/s", 
                 bytes, duration.toMillis(), operation, throughputBps);
    }

    /**
     * 开始文件传输计时（返回开始时间）。
     */
    public long startFileTransferTimer() {
        return System.nanoTime();
    }

    // === WebSocket 指标方法 ===
    
    /**
     * 记录 WebSocket 连接建立。
     */
    public void recordWebSocketConnect() {
        websocketConnectCounter.increment();
        log.debug("记录 WebSocket 连接建立");
    }

    /**
     * 记录 WebSocket 连接断开。
     */
    public void recordWebSocketDisconnect(String reason) {
        Counter.builder("terminal.websocket.connections")
                .description("Total WebSocket disconnections")
                .tag("status", "disconnected")
                .tag("reason", reason)
                .register(getMeterRegistry())
                .increment();
        log.debug("记录 WebSocket 连接断开，原因: {}", reason);
    }

    /**
     * 记录 WebSocket 重连尝试。
     */
    public void recordWebSocketReconnect() {
        websocketReconnects.incrementAndGet();
        log.debug("记录 WebSocket 重连尝试");
    }

    // === STOMP 错误指标方法 ===
    
    /**
     * 记录 STOMP 错误。
     */
    public void recordStompError(String destination, String errorType) {
        Counter.builder("terminal.stomp.errors")
                .description("Total STOMP protocol errors")
                .tag("destination", destination)
                .tag("error.type", errorType)
                .register(getMeterRegistry())
                .increment();
        log.debug("记录 STOMP 错误: destination={}, errorType={}", destination, errorType);
    }

    // === 辅助方法 ===
    
    /**
     * 获取 MeterRegistry 实例 (供内部使用)。
     */
    private MeterRegistry getMeterRegistry() {
        return this.meterRegistry != null ? this.meterRegistry : Metrics.globalRegistry;
    }
}