package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.dto.sillytavern.RealTimeLogDto;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import com.fufu.terminal.service.StompSessionManager;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * 实时日志查看服务。
 * <p>
 * 该服务通过SSH连接远程服务器，使用docker logs -f命令实现实时日志推送，避免轮询带来的性能问题。
 * 支持日志级别过滤、历史日志获取、内存缓存和多会话并发管理。
 * </p>
 *
 * @author fufu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeLogService {

    /** SSH命令服务 */
    private final SshCommandService sshCommandService;
    /** WebSocket消息推送模板 */
    private final SimpMessagingTemplate messagingTemplate;
    /** 会话管理器 */
    private final StompSessionManager sessionManager;

    /** 活跃会话日志流管理器映射 */
    private final Map<String, LogStreamManager> activeStreams = new ConcurrentHashMap<>();

    /** 日志流线程池，线程命名便于排查 */
    private final ExecutorService executorService = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("RealTimeLogService-LogStream-" + t.getId());
        t.setDaemon(true);
        return t;
    });

    /** 默认最大日志行数 */
    private static final int DEFAULT_MAX_LINES = 1000;
    /** 内存最大缓存行数 */
    private static final int MAX_MEMORY_LINES = 5000;
    /** 日志时间戳正则 */
    private static final Pattern LOG_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");

    /**
     * 启动指定会话的实时日志流。
     *
     * @param sessionId     会话ID
     * @param containerName 容器名
     * @param maxLines      最大缓存行数
     */
    public void startLogStream(String sessionId, String containerName, int maxLines) {
        log.info("开始实时日志流，会话: {} 容器: {} 最大行数: {}", sessionId, containerName, maxLines);

        // 停止现有日志流
        stopLogStream(sessionId);

        int actualMaxLines = Math.min(maxLines, MAX_MEMORY_LINES);

        LogStreamManager streamManager = new LogStreamManager(sessionId, containerName, actualMaxLines);
        activeStreams.put(sessionId, streamManager);

        streamManager.start();
    }

    /**
     * 停止指定会话的实时日志流。
     *
     * @param sessionId 会话ID
     */
    public void stopLogStream(String sessionId) {
        LogStreamManager streamManager = activeStreams.remove(sessionId);
        if (streamManager != null) {
            streamManager.stop();
            log.info("已停止实时日志流，会话: {}", sessionId);
        }
    }

    /**
     * 获取历史日志。
     *
     * @param connection    SSH连接
     * @param containerName 容器名
     * @param lines         获取行数
     * @param level         日志级别
     * @return 日志DTO
     * @throws Exception SSH或命令执行异常
     */
    public RealTimeLogDto getHistoryLogs(SshConnection connection, String containerName,
                                         int lines, String level) throws Exception {
        log.debug("获取历史日志，容器: {} 行数: {} 级别: {}", containerName, lines, level);

        StringBuilder command = new StringBuilder("sudo docker logs");
        if (lines > 0) {
            command.append(" --tail ").append(Math.min(lines, MAX_MEMORY_LINES));
        }
        command.append(" --timestamps ").append(containerName);

        String result = executeCommand(connection, command.toString());
        List<String> logLines = Arrays.asList(result.split("\n"));

        // 按级别过滤
        if (level != null && !"all".equalsIgnoreCase(level)) {
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
     * 日志流管理器。
     * <p>
     * 负责通过SSH实时拉取docker日志并推送到前端，支持批量发送和内存循环缓存。
     * </p>
     */
    private class LogStreamManager {
        private final String sessionId;
        private final String containerName;
        private final int maxLines;
        private final CircularBuffer logBuffer;
        private volatile boolean running = false;
        private Future<?> logStreamTask;
        private ChannelExec dockerLogsChannel;

        /**
         * 构造方法。
         *
         * @param sessionId     会话ID
         * @param containerName 容器名
         * @param maxLines      最大缓存行数
         */
        public LogStreamManager(String sessionId, String containerName, int maxLines) {
            this.sessionId = sessionId;
            this.containerName = containerName;
            this.maxLines = maxLines;
            this.logBuffer = new CircularBuffer(maxLines);
        }

        /**
         * 启动日志流线程。
         */
        public void start() {
            if (running) return;
            running = true;
            logStreamTask = executorService.submit(this::startDockerLogsStream);
        }

        /**
         * 停止日志流线程并释放资源。
         */
        public void stop() {
            running = false;

            if (dockerLogsChannel != null && dockerLogsChannel.isConnected()) {
                try {
                    dockerLogsChannel.disconnect();
                } catch (Exception e) {
                    log.warn("关闭docker logs通道时出错: {}", e.getMessage());
                }
            }

            if (logStreamTask != null) {
                logStreamTask.cancel(true);
            }

            log.debug("LogStreamManager已停止，会话: {}", sessionId);
        }

        /**
         * 启动docker logs -f流，实时推送日志。
         */
        private void startDockerLogsStream() {
            try {
                SshConnection connection = getConnectionForSession(sessionId);
                if (connection == null) {
                    log.warn("会话 {} 的SSH连接不可用，无法启动日志流", sessionId);
                    return;
                }

                dockerLogsChannel = (ChannelExec) connection.getJschSession().openChannel("exec");
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
                    List<String> batchLines = new ArrayList<>();
                    long lastSendTime = System.currentTimeMillis();

                    while (running && (line = reader.readLine()) != null) {
                        if (line.trim().isEmpty()) continue;

                        logBuffer.add(line);
                        batchLines.add(line);

                        long currentTime = System.currentTimeMillis();
                        if (batchLines.size() >= 10 || (currentTime - lastSendTime) > 500) {
                            pushLogsToClient(new ArrayList<>(batchLines), false);
                            batchLines.clear();
                            lastSendTime = currentTime;
                        }
                    }

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

        /**
         * 推送日志到前端客户端。
         *
         * @param newLines   新日志行
         * @param isComplete 是否为最后一批
         */
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

        /**
         * 推送错误信息到前端客户端。
         *
         * @param errorMessage 错误消息
         */
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
         * 获取所有缓存日志。
         *
         * @return 日志列表
         */
        public List<String> getAllCachedLogs() {
            return logBuffer.getAll();
        }
    }

    /**
     * 内存循环缓冲区。
     * <p>
     * 用于日志缓存，保证内存占用可控。
     * </p>
     */
    private static class CircularBuffer {
        private final String[] buffer;
        private final int capacity;
        private int head = 0;
        private int tail = 0;
        private int size = 0;

        /**
         * 构造方法。
         *
         * @param capacity 缓冲区容量
         */
        public CircularBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer = new String[capacity];
        }

        /**
         * 添加日志行。
         *
         * @param item 日志内容
         */
        public synchronized void add(String item) {
            buffer[tail] = item;
            tail = (tail + 1) % capacity;

            if (size < capacity) {
                size++;
            } else {
                head = (head + 1) % capacity;
            }
        }

        /**
         * 获取所有缓存内容。
         *
         * @return 日志列表
         */
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

        /**
         * 当前缓存行数。
         *
         * @return 行数
         */
        public synchronized int size() {
            return size;
        }
    }

    /**
     * 按日志级别过滤日志行。
     *
     * @param logLines 日志行
     * @param level    日志级别
     * @return 过滤后的日志行
     */
    private List<String> filterLogsByLevel(List<String> logLines, String level) {
        if ("all".equalsIgnoreCase(level)) {
            return logLines;
        }
        List<String> filtered = new ArrayList<>();
        String levelPattern = level.toUpperCase();
        Pattern pattern = Pattern.compile("\\b" + levelPattern + "\\b|\\[" + levelPattern + "\\]");
        for (String line : logLines) {
            if (pattern.matcher(line.toUpperCase()).find()) {
                filtered.add(line);
            }
        }
        return filtered;
    }

    /**
     * 获取会话对应的SSH连接。
     *
     * @param sessionId 会话ID
     * @return SSH连接
     */
    private SshConnection getConnectionForSession(String sessionId) {
        return sessionManager.getConnection(sessionId);
    }

    /**
     * 执行SSH命令并返回结果。
     *
     * @param connection SSH连接
     * @param command    命令
     * @return 命令输出
     * @throws Exception 命令执行异常
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
     * 服务销毁时清理资源。
     * <p>
     * 停止所有活跃日志流并优雅关闭线程池。
     * </p>
     */
    @PreDestroy
    public void cleanup() {
        log.info("清理实时日志服务资源...");

        activeStreams.values().forEach(LogStreamManager::stop);
        activeStreams.clear();

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("部分任务未在5秒内完成，强制关闭");
                List<Runnable> pendingTasks = executorService.shutdownNow();
                log.info("强制取消了 {} 个待执行任务", pendingTasks.size());

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
