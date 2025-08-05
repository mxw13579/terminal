package com.fufu.terminal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.handler.MessageHandler;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSH远程主机监控服务，支持高频/低频切换、缓存、降级处理等功能。
 * <p>
 * 该服务负责通过SSH连接定期采集主机与Docker容器的资源状态，并通过WebSocket推送给前端。
 * 支持高频（面板打开时）与低频（面板关闭时）两种监控模式。
 * </p>
 *
 * @author lizelin
 */
@Slf4j
@Service
public class SshMonitorService {

    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService monitorScheduler;
    private final SshCommandService sshCommandService;

    /**
     * 高频监控间隔（秒）
     */
    private static final long HIGH_FREQUENCY_SECONDS = 3;
    /**
     * 低频监控间隔（秒）
     */
    private static final long LOW_FREQUENCY_SECONDS = 30;

    /**
     * 构造SSH监控服务
     *
     * @param objectMapper      JSON对象映射器
     * @param monitorScheduler  调度执行器
     * @param sshCommandService SSH命令服务
     */
    public SshMonitorService(ObjectMapper objectMapper,
                             @Qualifier("monitorScheduler") ScheduledExecutorService monitorScheduler,
                             SshCommandService sshCommandService) {
        this.objectMapper = objectMapper;
        this.monitorScheduler = monitorScheduler;
        this.sshCommandService = sshCommandService;
    }

    /**
     * 处理前端打开监控面板的请求，切换到高频监控模式并立即推送缓存数据。
     *
     * @param session        WebSocket会话
     * @param sshConnection  SSH连接对象
     */
    public void handleMonitorStart(WebSocketSession session, SshConnection sshConnection) {
        log.info("会话 {} 请求启动高频监控。", session.getId());

        // 立即推送缓存数据，提升UI体验
        final Map<String, Object> cachedStats = sshConnection.getLastMonitorStats();
        if (cachedStats != null && !cachedStats.isEmpty()) {
            try {
                Map<String, Object> response = Map.of(
                        "type", "monitor_update",
                        "payload", cachedStats
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            } catch (IOException e) {
                log.error("发送缓存的监控数据失败。会话ID: {}", session.getId(), e);
            }
        }

        // 切换为高频监控模式
        rescheduleMonitoring(sshConnection, session, HIGH_FREQUENCY_SECONDS, true);
    }

    /**
     * 处理前端关闭监控面板的请求，切换到低频监控模式，仅更新缓存。
     *
     * @param sshConnection SSH连接对象
     */
    public void handleMonitorStop(SshConnection sshConnection) {
        if (sshConnection != null) {
            log.info("请求切换到低频监控模式。");
            // 切换为低频模式，不推送数据
            rescheduleMonitoring(sshConnection, null, LOW_FREQUENCY_SECONDS, false);
        }
    }

    /**
     * 为监控功能创建独立的SSH连接，避免与终端shell通道冲突。
     * <p>
     * 实际上由于无法获取原始密码，仍复用原始Session，仅在独立线程执行监控命令。
     * </p>
     *
     * @param session            WebSocket会话
     * @param originalConnection 原始SSH连接
     */
    public void handleMonitorStartWithSeparateConnection(WebSocketSession session, SshConnection originalConnection) {
        log.info("会话 {} 请求启动监控（使用独立SSH连接）。", session.getId());

        try {
            // 立即推送缓存数据
            final Map<String, Object> cachedStats = originalConnection.getLastMonitorStats();
            if (cachedStats != null && !cachedStats.isEmpty()) {
                try {
                    Map<String, Object> response = Map.of(
                            "type", "monitor_update",
                            "payload", cachedStats
                    );
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                } catch (IOException e) {
                    log.error("发送缓存的监控数据失败。会话ID: {}", session.getId(), e);
                }
            }

            // 复用原始Session，独立线程调度
            rescheduleMonitoringWithRetry(originalConnection, session, HIGH_FREQUENCY_SECONDS, true);

        } catch (Exception e) {
            log.error("创建监控SSH连接失败，会话ID: {}", session.getId(), e);

            // 推送错误消息
            try {
                Map<String, Object> errorResponse = Map.of(
                        "type", "monitor_error",
                        "payload", "监控功能暂时不可用：" + e.getMessage()
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
            } catch (IOException ioException) {
                log.error("发送监控错误消息失败", ioException);
            }
        }
    }

    /**
     * 重新调度监控任务：取消现有任务并根据参数安排新任务。
     *
     * @param sshConnection SSH连接对象
     * @param session       WebSocket会话（如需推送则必传）
     * @param periodSeconds 调度周期（秒）
     * @param sendToClient  是否推送数据到前端
     */
    private void rescheduleMonitoring(SshConnection sshConnection, WebSocketSession session, long periodSeconds, boolean sendToClient) {
        // 取消已存在的监控任务
        sshConnection.cancelMonitoringTask();

        Runnable monitoringRunnable = () -> {
            Session jschSession = sshConnection.getJschSession();
            // 检查SSH连接
            if (jschSession == null || !jschSession.isConnected()) {
                log.warn("SSH连接已断开，自动停止监控任务。");
                sshConnection.cancelMonitoringTask();
                return;
            }

            try {
                Map<String, Object> statsPayload = getSystemAndDockerStats(jschSession);

                if (Thread.currentThread().isInterrupted() || statsPayload.isEmpty()) {
                    return;
                }

                // 更新缓存
                sshConnection.setLastMonitorStats(statsPayload);

                // 推送数据
                if (sendToClient) {
                    if (session != null && session.isOpen()) {
                        Map<String, Object> response = Map.of(
                                "type", "monitor_update",
                                "payload", statsPayload
                        );
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                    } else {
                        // session失效自动降级为低频
                        log.warn("需要发送监控数据但WebSocket会话无效，自动切换到低频模式。");
                        rescheduleMonitoring(sshConnection, null, LOW_FREQUENCY_SECONDS, false);
                    }
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    log.info("监控任务被中断，将停止执行。");
                    Thread.currentThread().interrupt();
                } else {
                    log.error("监控任务执行时发生错误，将停止该任务。", e);
                    sshConnection.cancelMonitoringTask();
                }
            }
        };

        // 安排新定时任务
        Future<?> task = monitorScheduler.scheduleAtFixedRate(monitoringRunnable, 0, periodSeconds, TimeUnit.SECONDS);
        sshConnection.setMonitoringTask(task);
        log.info("监控任务已重新调度，周期: {}秒，是否推送: {}", periodSeconds, sendToClient);
    }

    /**
     * 带重试机制的监控调度，如果exec通道失败则降级处理。
     *
     * @param sshConnection  SSH连接对象
     * @param session        WebSocket会话
     * @param intervalSeconds 调度周期（秒）
     * @param sendToClient   是否推送数据到前端
     */
    private void rescheduleMonitoringWithRetry(SshConnection sshConnection, WebSocketSession session, long intervalSeconds, boolean sendToClient) {
        sshConnection.cancelMonitoringTask();

        Future<?> task = monitorScheduler.scheduleAtFixedRate(() -> {
            try {
                Map<String, Object> monitoringData = collectSystemStatsWithFallback(sshConnection);

                // 更新缓存
                sshConnection.setLastMonitorStats(monitoringData);

                // 推送数据
                if (sendToClient && session.isOpen()) {
                    Map<String, Object> response = Map.of(
                            "type", "monitor_update",
                            "payload", monitoringData
                    );
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                }
            } catch (Exception e) {
                log.warn("监控数据收集失败，会话ID: {}，错误: {}", session.getId(), e.getMessage());

                // 推送降级数据
                try {
                    if (session.isOpen()) {
                        Map<String, Object> errorData = Map.of(
                                "cpuModel", "监控暂时不可用",
                                "uptime", "SSH通道冲突",
                                "cpuUsage", 0.0,
                                "memoryUsage", Map.of("used", 0, "total", 0, "percentage", 0.0),
                                "diskUsage", Map.of("used", 0, "total", 0, "percentage", 0.0),
                                "networkStats", Map.of("rx", "N/A", "tx", "N/A"),
                                "dockerContainers", Collections.emptyList()
                        );
                        Map<String, Object> response = Map.of(
                                "type", "monitor_update",
                                "payload", errorData
                        );
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                    }
                } catch (IOException ioException) {
                    log.error("发送监控错误状态失败", ioException);
                }
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);

        sshConnection.setMonitoringTask(task);
        log.info("监控任务已重新调度，间隔: {} 秒，发送数据给客户端: {}", intervalSeconds, sendToClient);
    }

    /**
     * 带降级处理的系统状态收集。
     *
     * @param sshConnection SSH连接对象
     * @return 监控数据Map
     */
    private Map<String, Object> collectSystemStatsWithFallback(SshConnection sshConnection) {
        try {
            return getSystemAndDockerStats(sshConnection.getJschSession());
        } catch (Exception e) {
            log.warn("正常监控失败，返回降级数据: {}", e.getMessage());
            return Map.of(
                    "cpuModel", "监控数据收集失败",
                    "uptime", "请检查SSH连接",
                    "cpuUsage", 0.0,
                    "memoryUsage", Map.of("used", 0, "total", 0, "percentage", 0.0),
                    "diskUsage", Map.of("used", 0, "total", 0, "percentage", 0.0),
                    "networkStats", Map.of("rx", "N/A", "tx", "N/A"),
                    "dockerContainers", Collections.emptyList()
            );
        }
    }

    /**
     * 获取系统及Docker统计信息。
     *
     * @param jschSession SSH会话
     * @return 监控数据Map
     * @throws InterruptedException 线程中断时抛出
     */
    private Map<String, Object> getSystemAndDockerStats(Session jschSession) throws InterruptedException {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        if (jschSession == null || !jschSession.isConnected()) {
            log.warn("获取监控数据时SSH会话已关闭，跳过命令执行。");
            return Collections.emptyMap();
        }
        final String delimiter = "---CMD_DELIMITER---";
        final String dockerPsCmd = "docker ps --format '{{.ID}}\\t{{.Names}}\\t{{.Status}}'";
        final String dockerStatsCmd = "docker stats --no-stream --format '{{.ID}}\\t{{.CPUPerc}}\\t{{.MemUsage}}'";

        // 使用 df -P / 提高兼容性
        String initialCommands = String.join(" ; echo '" + delimiter + "'; ",
                "cat /proc/cpuinfo | grep 'model name' | uniq | sed 's/model name\\s*:\\s*//'",
                "uptime -p", "grep 'cpu ' /proc/stat", "free -m", "df -P /", "cat /proc/net/dev",
                "command -v docker >/dev/null && " + dockerPsCmd + " || echo 'no_docker'",
                "command -v docker >/dev/null && " + dockerStatsCmd + " || echo 'no_docker'"
        );

        CommandResult initialCmdResult = sshCommandService.executeCommand(jschSession, initialCommands);
        if (!initialCmdResult.isSuccess()) {
            log.warn("初始监控命令执行失败, exit={}, cmd={}, stderr={}", initialCmdResult.exitStatus(), initialCommands, initialCmdResult.stderr());
        }

        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("在初始命令执行期间被中断。");
        }
        String initialResult = initialCmdResult.stdout();
        String[] initialParts = initialResult.split(delimiter);

        Thread.sleep(1000);

        String finalCommands = String.join(" ; echo '" + delimiter + "'; ",
                "grep 'cpu ' /proc/stat", "cat /proc/net/dev");
        CommandResult finalCmdResult = sshCommandService.executeCommand(jschSession, finalCommands);
        if (!finalCmdResult.isSuccess()) {
            log.warn("最终监控命令执行失败, exit={}, cmd={}, stderr={}", finalCmdResult.exitStatus(), finalCommands, finalCmdResult.stderr());
        }
        String finalResult = finalCmdResult.stdout();
        String[] finalParts = finalResult.split(delimiter);

        // 解析各项指标
        stats.put("cpuModel", getPart(initialParts, 0, "N/A"));
        stats.put("uptime", getPart(initialParts, 1, "N/A").replace("up ", "").trim());
        stats.put("cpuUsage", parseCpuUsage(getPart(initialParts, 2, ""), getPart(finalParts, 0, "")));
        stats.put("memUsage", parseMemUsage(getPart(initialParts, 3, "")));
        stats.put("diskUsage", parseDiskUsage(getPart(initialParts, 4, "")));
        Map<String, String> net = parseNetUsage(getPart(initialParts, 5, ""), getPart(finalParts, 1, ""));
        stats.put("netRx", net.get("rx"));
        stats.put("netTx", net.get("tx"));
        stats.put("dockerContainers", parseDockerContainers(getPart(initialParts, 6, ""), getPart(initialParts, 7, "")));

        return stats;
    }

    /**
     * 执行远程命令，支持中断。
     *
     * @param session SSH会话
     * @param command 命令字符串
     * @return 命令输出
     * @throws InterruptedException 线程中断时抛出
     */
    private String executeRemoteCommand(Session session, String command) throws InterruptedException {
        if (session == null || !session.isConnected()) {
            log.warn("SSH session 未连接，无法执行命令：{}", command);
            return "";
        }

        ChannelExec channel = null;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try {
            channel = (ChannelExec) session.openChannel("exec");
            String wrappedCmd = String.format("sh -c \"%s\"", command.replace("\"", "\\\""));
            channel.setCommand(wrappedCmd);
            channel.setInputStream(null);
            channel.setOutputStream(stdout);
            channel.setErrStream(stderr);

            channel.connect(5000);
            while (!channel.isClosed()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("命令执行在等待时被中断: " + command);
                }
                Thread.sleep(50);
            }

            int exitStatus = channel.getExitStatus();
            String outStr = stdout.toString(StandardCharsets.UTF_8).trim();
            String errStr = stderr.toString(StandardCharsets.UTF_8).trim();
            if (exitStatus != 0) {
                log.warn("远程命令返回非零状态 {}，cmd={}，stderr={}", exitStatus, command, errStr);
            }
            return outStr;
        } catch (JSchException e) {
            if (e.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new InterruptedException("JSch 操作被中断。");
            }
            log.warn("打开或连接 exec 通道失败 cmd={}：{}", command, e.getMessage());
            return "";
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            try {
                stdout.close();
                stderr.close();
            } catch (IOException ignored) { }
        }
    }

    /**
     * 解析Docker容器信息。
     *
     * @param psOutput    docker ps 输出
     * @param statsOutput docker stats 输出
     * @return 容器信息列表
     */
    private List<Map<String, String>> parseDockerContainers(String psOutput, String statsOutput) {
        if ("no_docker".equals(psOutput) || psOutput.trim().isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Map<String, String>> containers = new HashMap<>();

        for (String line : psOutput.split("\\r?\\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] psParts = line.split("\t");
            if (psParts.length >= 3) {
                Map<String, String> containerInfo = new HashMap<>();
                containerInfo.put("id", psParts[0]);
                containerInfo.put("name", psParts[1]);
                containerInfo.put("status", psParts[2]);
                containers.put(psParts[0], containerInfo);
            }
        }

        for (String line : statsOutput.split("\\r?\\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] statsParts = line.split("\t");
            if (statsParts.length >= 3 && containers.containsKey(statsParts[0])) {
                Map<String, String> containerInfo = containers.get(statsParts[0]);
                containerInfo.put("cpuPerc", statsParts[1]);
                containerInfo.put("memPerc", statsParts[2].split(" / ")[0]);
            }
        }

        return new ArrayList<>(containers.values());
    }

    // --- 解析工具方法 ---

    /**
     * 获取数组指定下标元素，越界返回默认值。
     *
     * @param parts        字符串数组
     * @param index        下标
     * @param defaultValue 默认值
     * @return 元素或默认值
     */
    private String getPart(String[] parts, int index, String defaultValue) {
        return (parts != null && parts.length > index) ? parts[index].trim() : defaultValue;
    }

    /**
     * 解析CPU使用率。
     *
     * @param start 起始/proc/stat行
     * @param end   结束/proc/stat行
     * @return CPU使用率百分比
     */
    private double parseCpuUsage(String start, String end) {
        if (start.isEmpty() || end.isEmpty()) {
            return 0.0;
        }
        try {
            long[] startMetrics = Arrays.stream(start.split("\\s+")).skip(1).mapToLong(Long::parseLong).toArray();
            long[] endMetrics = Arrays.stream(end.split("\\s+")).skip(1).mapToLong(Long::parseLong).toArray();
            long startTotal = Arrays.stream(startMetrics).sum();
            long endTotal = Arrays.stream(endMetrics).sum();
            long startIdle = startMetrics[3];
            long endIdle = endMetrics[3];
            double totalDiff = endTotal - startTotal;
            double idleDiff = endIdle - startIdle;
            return totalDiff > 0 ? 100.0 * (totalDiff - idleDiff) / totalDiff : 0.0;
        } catch (Exception e) {
            log.warn("解析CPU使用率失败", e);
            return 0.0;
        }
    }

    /**
     * 解析内存使用率。
     *
     * @param freeOutput free -m 输出
     * @return 内存使用率百分比
     */
    private double parseMemUsage(String freeOutput) {
        if (freeOutput.isEmpty()) {
            return 0.0;
        }
        try {
            return Arrays.stream(freeOutput.split("\\n"))
                    .filter(line -> line.startsWith("Mem:"))
                    .map(line -> line.trim().split("\\s+"))
                    .filter(parts -> parts.length > 2)
                    .mapToDouble(parts -> {
                        double total = Double.parseDouble(parts[1]);
                        double used = Double.parseDouble(parts[2]);
                        return total > 0 ? (used / total) * 100.0 : 0.0;
                    })
                    .findFirst()
                    .orElse(0.0);
        } catch (Exception e) {
            log.warn("解析内存使用率失败", e);
            return 0.0;
        }
    }

    /**
     * 解析磁盘使用率。
     *
     * @param dfOutput df -P / 输出
     * @return 磁盘使用率百分比
     */
    private double parseDiskUsage(String dfOutput) {
        if (dfOutput == null || dfOutput.isBlank()) {
            return 0.0;
        }
        try {
            String[] lines = dfOutput.trim().split("\\n");
            if (lines.length < 2) {
                log.warn("df输出无效，期望至少2行，实际: {}", lines.length);
                return 0.0;
            }
            String dataLine = lines[1];
            String[] parts = dataLine.trim().split("\\s+");
            if (parts.length >= 5) {
                return Double.parseDouble(parts[4].replace("%", ""));
            }
            log.warn("无法解析磁盘使用率: '{}'", dataLine);
            return 0.0;
        } catch (Exception e) {
            log.warn("解析磁盘使用率失败: '{}'", dfOutput, e);
            return 0.0;
        }
    }

    /**
     * 解析网络流量速率。
     *
     * @param start /proc/net/dev 起始快照
     * @param end   /proc/net/dev 结束快照
     * @return Map(rx, tx)
     */
    private Map<String, String> parseNetUsage(String start, String end) {
        if (start.isEmpty() || end.isEmpty()) {
            return Map.of("rx", "N/A", "tx", "N/A");
        }
        try {
            long startRx = 0, startTx = 0, endRx = 0, endTx = 0;
            for (String line : start.split("\\n")) {
                if (line.contains(":") && !line.contains("lo:")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length > 9) {
                        startRx += Long.parseLong(parts[1]);
                        startTx += Long.parseLong(parts[9]);
                    }
                }
            }
            for (String line : end.split("\\n")) {
                if (line.contains(":") && !line.contains("lo:")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length > 9) {
                        endRx += Long.parseLong(parts[1]);
                        endTx += Long.parseLong(parts[9]);
                    }
                }
            }
            long rxRate = endRx - startRx;
            long txRate = endTx - startTx;
            return Map.of("rx", formatBytes(rxRate) + "/s", "tx", formatBytes(txRate) + "/s");
        } catch (Exception e) {
            log.warn("解析网络流量失败", e);
            return Map.of("rx", "N/A", "tx", "N/A");
        }
    }

    /**
     * 格式化字节数为可读字符串。
     *
     * @param bytes 字节数
     * @return 格式化字符串
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "i";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
