package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.dto.sillytavern.RealTimeLogDto;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import com.fufu.terminal.service.StompSessionManager;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * 实时日志查看服务
 * 使用docker logs -f实现真正的实时推送，避免轮询带来的性能问题
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeLogService {
    
    private final SshCommandService sshCommandService;
    private final SimpMessagingTemplate messagingTemplate;
    private final StompSessionManager sessionManager;
    
    // 每个会话的日志流管理
    private final Map<String, LogStreamManager> activeStreams = new ConcurrentHashMap<>(); 
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    // 日志缓存配置
    private static final int DEFAULT_MAX_LINES = 1000;
    private static final int MAX_MEMORY_LINES = 5000; // 最大内存缓存行数
    private static final Pattern LOG_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
    
    /**
     * 开始实时日志流
     */
    public void startLogStream(String sessionId, String containerName, int maxLines) {
        log.info("开始实时日志流，会话: {} 容器: {} 最大行数: {}", sessionId, containerName, maxLines);
        
        // 停止现有的日志流
        stopLogStream(sessionId);
        
        // 限制最大行数以保护内存
        int actualMaxLines = Math.min(maxLines, MAX_MEMORY_LINES);
        
        // 创建新的日志流管理器
        LogStreamManager streamManager = new LogStreamManager(sessionId, containerName, actualMaxLines);
        activeStreams.put(sessionId, streamManager);
        
        // 开始异步日志推送
        streamManager.start();
    }
    
    /**
     * 停止实时日志流
     */
    public void stopLogStream(String sessionId) {
        LogStreamManager streamManager = activeStreams.remove(sessionId);
        if (streamManager != null) {
            streamManager.stop();
            log.info("已停止实时日志流，会话: {}", sessionId);
        }
    }
    
    /**
     * 获取指定数量的历史日志
     */
    public RealTimeLogDto getHistoryLogs(SshConnection connection, String containerName, 
                                        int lines, String level) throws Exception {
        log.debug("获取历史日志，容器: {} 行数: {} 级别: {}", containerName, lines, level);
        
        // 构建docker logs命令
        StringBuilder command = new StringBuilder("sudo docker logs");
        
        if (lines > 0) {
            command.append(" --tail ").append(Math.min(lines, MAX_MEMORY_LINES));
        }
        
        command.append(" --timestamps ").append(containerName);
        
        String result = executeCommand(connection, command.toString());
        List<String> logLines = Arrays.asList(result.split("\n"));
        
        // 过滤日志级别
        if (level != null && !level.equalsIgnoreCase("all")) {
            logLines = filterLogsByLevel(logLines, level);
        }
        
        return RealTimeLogDto.builder()
            .sessionId("")
            .containerName(containerName)
            .lines(logLines)
            .totalLines(logLines.size())
            .timestamp(LocalDateTime.now())
            .isRealTime(false)
            .build();
    }
    
    /**
     * 日志流管理器内部类 - 使用docker logs -f实现真正的实时推送
     */
    private class LogStreamManager {
        private final String sessionId;
        private final String containerName;
        private final int maxLines;
        private final CircularBuffer logBuffer;
        private volatile boolean running = false;
        private Future<?> logStreamTask;
        private ChannelExec dockerLogsChannel;
        
        public LogStreamManager(String sessionId, String containerName, int maxLines) {
            this.sessionId = sessionId;
            this.containerName = containerName;
            this.maxLines = maxLines;
            this.logBuffer = new CircularBuffer(maxLines);
        }
        
        public void start() {
            if (running) return;
            
            running = true;
            
            // 异步启动docker logs -f命令流
            logStreamTask = executorService.submit(this::startDockerLogsStream);
        }
        
        public void stop() {
            running = false;
            
            // 停止docker logs进程
            if (dockerLogsChannel != null && dockerLogsChannel.isConnected()) {
                try {
                    dockerLogsChannel.disconnect();
                } catch (Exception e) {
                    log.warn("关闭docker logs通道时出错: {}", e.getMessage());
                }
            }
            
            // 取消任务
            if (logStreamTask != null) {
                logStreamTask.cancel(true);
            }
            
            log.debug("LogStreamManager已停止，会话: {}", sessionId);
        }
        
        /**
         * 启动docker logs -f流，真正的实时推送
         */
        private void startDockerLogsStream() {
            try {
                SshConnection connection = getConnectionForSession(sessionId);
                if (connection == null) {
                    log.warn("会话 {} 的SSH连接不可用，无法启动日志流", sessionId);
                    return;
                }
                
                // 创建新的SSH通道用于docker logs -f
                dockerLogsChannel = (ChannelExec) connection.getJschSession().openChannel("exec");
                
                // 构建docker logs -f命令，包含初始历史日志
                String command = String.format("sudo docker logs -f --tail %d --timestamps %s", 
                    Math.min(100, maxLines), containerName);
                
                dockerLogsChannel.setCommand(command);
                dockerLogsChannel.setInputStream(null);
                dockerLogsChannel.setErrStream(System.err);
                
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(dockerLogsChannel.getInputStream()))) {
                    
                    dockerLogsChannel.connect();
                    log.debug("Docker logs流已连接，会话: {} 容器: {}", sessionId, containerName);
                    
                    String line;
                    List<String> batchLines = new ArrayList<>(); // 批量发送提高效率
                    long lastSendTime = System.currentTimeMillis();
                    
                    while (running && (line = reader.readLine()) != null) {
                        if (line.trim().isEmpty()) continue;
                        
                        // 添加到缓冲区
                        logBuffer.add(line);
                        batchLines.add(line);
                        
                        // 批量发送或超时发送（减少网络开销）
                        long currentTime = System.currentTimeMillis();
                        if (batchLines.size() >= 10 || (currentTime - lastSendTime) > 500) {
                            pushLogsToClient(new ArrayList<>(batchLines), false);
                            batchLines.clear();
                            lastSendTime = currentTime;
                        }
                    }
                    
                    // 发送剩余的日志
                    if (!batchLines.isEmpty()) {
                        pushLogsToClient(batchLines, true);
                    }
                    
                } catch (IOException e) {
                    if (running) {
                        log.error("读取docker logs流时出错，会话: {} 容器: {}", sessionId, containerName, e);
                        pushErrorToClient("日志流读取错误: " + e.getMessage());
                    }
                }
                
            } catch (JSchException e) {
                if (running) {
                    log.error("创建SSH通道失败，会话: {} 容器: {}", sessionId, containerName, e);
                    pushErrorToClient("SSH连接错误: " + e.getMessage());
                }
            } catch (Exception e) {
                if (running) {
                    log.error("Docker logs流异常，会话: {} 容器: {}", sessionId, containerName, e);
                    pushErrorToClient("日志流异常: " + e.getMessage());
                }
            } finally {
                if (dockerLogsChannel != null && dockerLogsChannel.isConnected()) {
                    dockerLogsChannel.disconnect();
                }
                log.debug("Docker logs流已断开，会话: {} 容器: {}", sessionId, containerName);
            }
        }
        
        private void pushLogsToClient(List<String> newLines, boolean isComplete) {
            RealTimeLogDto logDto = RealTimeLogDto.builder()
                .sessionId(sessionId)
                .containerName(containerName)
                .lines(newLines)
                .totalLines(logBuffer.size())
                .timestamp(LocalDateTime.now())
                .isRealTime(true)
                .isComplete(isComplete)
                .build();
                
            messagingTemplate.convertAndSend(
                "/queue/sillytavern/realtime-logs-user" + sessionId, 
                Map.of("type", "realtime-logs", "payload", logDto)
            );
        }
        
        private void pushErrorToClient(String errorMessage) {
            Map<String, Object> errorMsg = Map.of(
                "type", "realtime-logs-error",
                "message", errorMessage,
                "sessionId", sessionId
            );
            
            messagingTemplate.convertAndSend(
                "/queue/sillytavern/realtime-logs-user" + sessionId, 
                errorMsg
            );
        }
        
        /**
         * 获取完整的缓存日志
         */
        public List<String> getAllCachedLogs() {
            return logBuffer.getAll();
        }
    }
    
    /**
     * 循环缓冲区实现，用于内存管理
     */
    private static class CircularBuffer {
        private final String[] buffer;
        private final int capacity;
        private int head = 0;
        private int tail = 0;
        private int size = 0;
        
        public CircularBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer = new String[capacity];
        }
        
        public synchronized void add(String item) {
            buffer[tail] = item;
            tail = (tail + 1) % capacity;
            
            if (size < capacity) {
                size++;
            } else {
                head = (head + 1) % capacity;
            }
        }
        
        public synchronized List<String> getAll() {
            List<String> result = new ArrayList<>();
            if (size == 0) return result;
            
            int current = head;
            for (int i = 0; i < size; i++) {
                result.add(buffer[current]);
                current = (current + 1) % capacity;
            }
            
            return result;
        }
        
        public synchronized int size() {
            return size;
        }
    }
    
    /**
     * 根据日志级别过滤日志行
     */
    private List<String> filterLogsByLevel(List<String> logLines, String level) {
        if ("all".equalsIgnoreCase(level)) {
            return logLines;
        }
        
        List<String> filtered = new ArrayList<>();
        String levelPattern = level.toUpperCase();
        
        for (String line : logLines) {
            if (line.toUpperCase().contains(levelPattern) || 
                line.toUpperCase().contains("[" + levelPattern + "]")) {
                filtered.add(line);
            }
        }
        
        return filtered;
    }
    
    /**
     * 获取会话对应的SSH连接
     */
    private SshConnection getConnectionForSession(String sessionId) {
        return sessionManager.getConnection(sessionId);
    }
    
    /**
     * 执行SSH命令
     */
    private String executeCommand(SshConnection connection, String command) throws Exception {
        try {
            CommandResult result = sshCommandService.executeCommand(connection.getJschSession(), command);
            
            if (result.exitStatus() != 0) {
                String errorMsg = "命令执行失败，退出码 " + result.exitStatus() + 
                               ": " + result.stderr();
                log.debug("命令执行失败: {} - {}", command, errorMsg);
                throw new RuntimeException(errorMsg);
            }
            
            return result.stdout();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("命令执行被中断: " + command, e);
        }
    }
    
    /**
     * 清理资源 - 改进的资源清理机制
     */
    @PreDestroy
    public void cleanup() {
        log.info("清理实时日志服务资源...");
        
        // 停止所有活跃的日志流
        activeStreams.values().forEach(LogStreamManager::stop);
        activeStreams.clear();
        
        // 关闭执行器服务
        executorService.shutdown();
        try {
            // 等待正在执行的任务完成
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("部分任务未在5秒内完成，强制关闭");
                List<Runnable> pendingTasks = executorService.shutdownNow();
                log.info("强制取消了 {} 个待执行任务", pendingTasks.size());
                
                // 再次等待
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.error("执行器服务无法正常关闭");
                }
            }
        } catch (InterruptedException e) {
            log.warn("等待执行器关闭时被中断");
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("实时日志服务资源清理完成");
    }
}