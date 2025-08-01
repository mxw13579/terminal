package com.fufu.terminal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.SshCommandUtil;
import com.fufu.terminal.command.model.OsInfo;
import com.fufu.terminal.command.model.enums.SystemType;
import com.fufu.terminal.model.SshConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSH远程主机监控服务。
 * <p>
 * 负责通过SSH定时采集主机运行状态（如CPU、内存、磁盘、网络、Docker等），
 * 并支持高频/低频推送到WebSocket客户端。
 * </p>
 * <p>
 * 注意：本服务依赖于SshConnection的正确生命周期管理。
 * </p>
 *
 * @author lizelin
 */
@Slf4j
@Service
public class SshMonitorService {

    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService monitorScheduler;

    /** 高频监控周期（秒） */
    private static final long HIGH_FREQUENCY_SECONDS = 3;
    /** 低频监控周期（秒） */
    private static final long LOW_FREQUENCY_SECONDS = 30;

    /**
     * 构造方法。
     *
     * @param objectMapper      Jackson对象映射器
     * @param monitorScheduler  Spring注入的定时任务线程池
     */
    public SshMonitorService(ObjectMapper objectMapper, @Qualifier("monitorScheduler") ScheduledExecutorService monitorScheduler) {
        this.objectMapper = objectMapper;
        this.monitorScheduler = monitorScheduler;
    }

    /**
     * 启动高频监控，并将最新监控数据推送到WebSocket客户端。
     *
     * @param session        WebSocket会话
     * @param sshConnection  SSH连接对象
     */
    public void handleMonitorStart(WebSocketSession session, SshConnection sshConnection) {
        log.info("Session {} requested to start high-frequency monitoring.", session.getId());

        if (sshConnection.getOsInfo() == null) {
            log.warn("Monitoring started before OS detection was complete for session {}. Monitoring may not be accurate.", session.getId());
        }

        final Map<String, Object> cachedStats = sshConnection.getLastMonitorStats();
        if (cachedStats != null && !cachedStats.isEmpty()) {
            try {
                sendStats(session, cachedStats);
            } catch (IOException e) {
                log.error("Failed to send cached monitoring data. Session ID: {}", session.getId(), e);
            }
        }
        rescheduleMonitoring(sshConnection, session, HIGH_FREQUENCY_SECONDS, true);
    }

    /**
     * 切换为低频监控（不主动推送到WebSocket客户端）。
     *
     * @param sshConnection SSH连接对象
     */
    public void handleMonitorStop(SshConnection sshConnection) {
        if (sshConnection != null) {
            log.info("Request to switch to low-frequency monitoring.");
            rescheduleMonitoring(sshConnection, null, LOW_FREQUENCY_SECONDS, false);
        }
    }

    /**
     * 重新调度监控任务（高频/低频），并根据需要推送到客户端。
     *
     * @param sshConnection SSH连接对象
     * @param session       WebSocket会话（可为null）
     * @param periodSeconds 执行周期（秒）
     * @param sendToClient  是否推送到客户端
     */
    private void rescheduleMonitoring(SshConnection sshConnection, WebSocketSession session, long periodSeconds, boolean sendToClient) {
        sshConnection.cancelMonitoringTask();

        Runnable monitoringRunnable = () -> {
            // 检查SSH连接状态
            if (sshConnection.getJschSession() == null || !sshConnection.getJschSession().isConnected()) {
                log.warn("SSH connection is disconnected, stopping monitoring task.");
                sshConnection.cancelMonitoringTask();
                return;
            }

            try {
                Map<String, Object> statsPayload = fetchAndParseStats(sshConnection);
                if (Thread.currentThread().isInterrupted() || statsPayload.isEmpty()) {
                    return;
                }

                sshConnection.setLastMonitorStats(statsPayload);

                if (sendToClient) {
                    if (session != null && session.isOpen()) {
                        sendStats(session, statsPayload);
                    } else {
                        log.warn("WebSocket session is invalid while trying to send stats, switching to low-frequency mode.");
                        // WebSocket会话失效时，自动切换为低频监控，避免线程空转
                        rescheduleMonitoring(sshConnection, null, LOW_FREQUENCY_SECONDS, false);
                    }
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    log.info("Monitoring task was interrupted and will stop.");
                    Thread.currentThread().interrupt();
                } else {
                    log.error("An error occurred during monitoring task execution, stopping task.", e);
                    sshConnection.cancelMonitoringTask();
                }
            }
        };

        Future<?> task = monitorScheduler.scheduleAtFixedRate(monitoringRunnable, 0, periodSeconds, TimeUnit.SECONDS);
        sshConnection.setMonitoringTask(task);
        log.info("Monitoring task rescheduled. Period: {}s, Push to client: {}", periodSeconds, sendToClient);
    }

    /**
     * 拉取并解析主机实时监控数据。
     *
     * @param sshConnection SSH连接对象
     * @return 监控数据Map
     * @throws Exception SSH命令执行异常、线程中断等
     */
    private Map<String, Object> fetchAndParseStats(SshConnection sshConnection) throws Exception {
        OsInfo osInfo = sshConnection.getOsInfo();
        if (osInfo == null || osInfo.getSystemType() == SystemType.UNKNOWN) {
            log.warn("Cannot fetch stats: OS info is unknown for host {}", sshConnection.getJschSession().getHost());
            return Collections.emptyMap();
        }

        // 目前仅支持常见Linux发行版
        return switch (osInfo.getSystemType()) {
            case DEBIAN, REDHAT, SUSE, ARCH -> fetchAndParseLinuxStats(sshConnection);
            // 未来可扩展MACOS等系统
            default -> {
                log.warn("Monitoring is not supported for OS type: {}", osInfo.getSystemType());
                yield Collections.emptyMap();
            }
        };
    }

    /**
     * 拉取并解析Linux主机的监控数据。
     *
     * @param sshConnection SSH连接对象
     * @return 监控数据Map
     * @throws Exception SSH命令执行异常、线程中断等
     */
    private Map<String, Object> fetchAndParseLinuxStats(SshConnection sshConnection) throws Exception {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        final String delimiter = "---CMD_DELIMITER---";
        final String dockerPsCmd = "docker ps --format '{{.ID}}\\t{{.Names}}\\t{{.Status}}'";
        final String dockerStatsCmd = "docker stats --no-stream --format '{{.ID}}\\t{{.CPUPerc}}\\t{{.MemUsage}}'";

        // 拼接批量命令，减少SSH往返
        String initialCommands = String.join(" ; echo '" + delimiter + "'; ",
                "cat /proc/cpuinfo | grep 'model name' | uniq | sed 's/model name\\s*:\\s*//'",
                "uptime -p", "grep 'cpu ' /proc/stat", "free -m", "df -P /", "cat /proc/net/dev",
                "command -v docker >/dev/null && " + dockerPsCmd + " || echo 'no_docker'",
                "command -v docker >/dev/null && " + dockerStatsCmd + " || echo 'no_docker'"
        );

        CommandResult initialCmdResult = SshCommandUtil.executeCommand(sshConnection, initialCommands);
        if (!initialCmdResult.isSuccess()) {
            log.warn("Initial monitoring command failed, exit={}, stderr={}", initialCmdResult.getExitStatus(), initialCmdResult.getStderr());
        }

        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Interrupted after initial command.");

        String[] initialParts = initialCmdResult.getStdout().split(delimiter);

        // 采集网络、CPU二次快照，用于速率计算
        Thread.sleep(1000);

        String finalCommands = String.join(" ; echo '" + delimiter + "'; ", "grep 'cpu ' /proc/stat", "cat /proc/net/dev");
        CommandResult finalCmdResult = SshCommandUtil.executeCommand(sshConnection, finalCommands);
        if (!finalCmdResult.isSuccess()) {
            log.warn("Final monitoring command failed, exit={}, stderr={}", finalCmdResult.getExitStatus(), finalCmdResult.getStderr());
        }

        String[] finalParts = finalCmdResult.getStdout().split(delimiter);

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
     * 向WebSocket客户端推送监控数据。
     *
     * @param session WebSocket会话
     * @param stats   监控数据
     * @throws IOException 序列化或发送异常
     */
    private void sendStats(WebSocketSession session, Map<String, Object> stats) throws IOException {
        Map<String, Object> response = Map.of("type", "monitor_update", "payload", stats);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    // -------------------- 解析辅助方法 --------------------

    /**
     * 获取命令分割后的指定部分
     *
     * @param parts        分割数组
     * @param index        下标
     * @param defaultValue 默认值
     * @return 指定部分或默认值
     */
    private String getPart(String[] parts, int index, String defaultValue) {
        return (parts != null && parts.length > index) ? parts[index].trim() : defaultValue;
    }

    /**
     * 解析Docker容器列表及资源占用信息。
     *
     * @param psOutput    docker ps 命令输出
     * @param statsOutput docker stats 命令输出
     * @return 容器信息列表
     */
    private List<Map<String, String>> parseDockerContainers(String psOutput, String statsOutput) {
        if ("no_docker".equals(psOutput) || psOutput.trim().isEmpty()) return Collections.emptyList();
        Map<String, Map<String, String>> containers = new HashMap<>();
        for (String line : psOutput.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            String[] psParts = line.split("\\t");
            if (psParts.length >= 3) {
                Map<String, String> containerInfo = new HashMap<>();
                containerInfo.put("id", psParts[0]);
                containerInfo.put("name", psParts[1]);
                containerInfo.put("status", psParts[2]);
                containers.put(psParts[0], containerInfo);
            }
        }
        for (String line : statsOutput.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            String[] statsParts = line.split("\\t");
            if (statsParts.length >= 3 && containers.containsKey(statsParts[0])) {
                Map<String, String> containerInfo = containers.get(statsParts[0]);
                containerInfo.put("cpuPerc", statsParts[1]);
                containerInfo.put("memPerc", statsParts[2].split(" / ")[0]);
            }
        }
        return new ArrayList<>(containers.values());
    }

    /**
     * 计算CPU使用率百分比。
     *
     * @param start 起始/proc/stat内容
     * @param end   结束/proc/stat内容
     * @return 使用率百分比
     */
    private double parseCpuUsage(String start, String end) {
        if (start.isEmpty() || end.isEmpty()) return 0.0;
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
            log.warn("Failed to parse CPU usage", e);
            return 0.0;
        }
    }

    /**
     * 解析内存使用率。
     *
     * @param freeOutput free -m命令输出
     * @return 使用率百分比
     */
    private double parseMemUsage(String freeOutput) {
        if (freeOutput.isEmpty()) return 0.0;
        try {
            return Arrays.stream(freeOutput.split("\\n")).filter(line -> line.startsWith("Mem:")).map(line -> line.trim().split("\\s+")).filter(parts -> parts.length > 2).mapToDouble(parts -> {
                double total = Double.parseDouble(parts[1]);
                double used = Double.parseDouble(parts[2]);
                return total > 0 ? (used / total) * 100.0 : 0.0;
            }).findFirst().orElse(0.0);
        } catch (Exception e) {
            log.warn("Failed to parse memory usage", e);
            return 0.0;
        }
    }

    /**
     * 解析根分区磁盘使用率。
     *
     * @param dfOutput df -P / 命令输出
     * @return 使用率百分比
     */
    private double parseDiskUsage(String dfOutput) {
        if (dfOutput == null || dfOutput.isBlank()) return 0.0;
        try {
            String[] lines = dfOutput.trim().split("\\n");
            if (lines.length < 2) return 0.0;
            String[] parts = lines[1].trim().split("\\s+");
            return parts.length >= 5 ? Double.parseDouble(parts[4].replace("%", "")) : 0.0;
        } catch (Exception e) {
            log.warn("Failed to parse disk usage from output: '{}'", dfOutput, e);
            return 0.0;
        }
    }

    /**
     * 解析网络流量速率。
     *
     * @param start /proc/net/dev初始内容
     * @param end   /proc/net/dev结束内容
     * @return rx、tx速率字符串（带单位）
     */
    private Map<String, String> parseNetUsage(String start, String end) {
        if (start.isEmpty() || end.isEmpty()) return Map.of("rx", "N/A", "tx", "N/A");
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
            log.warn("Failed to parse network usage", e);
            return Map.of("rx", "N/A", "tx", "N/A");
        }
    }

    /**
     * 字节数格式化为带单位字符串。
     *
     * @param bytes 字节数
     * @return 格式化字符串
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "i";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    // -------------------- 健康检查相关方法 --------------------
    
    /**
     * 获取当前活跃SSH连接数量
     * @return 活跃连接数
     */
    public int getActiveConnectionCount() {
        // 这里需要从SSH连接管理器中获取实际的连接数
        // 暂时返回一个模拟值，实际应该从连接管理器获取
        return 0; // TODO: 实现实际连接数统计
    }
    
    /**
     * 获取最大SSH连接数量
     * @return 最大连接数
     */
    public int getMaxConnectionCount() {
        // 从配置中获取最大连接数
        return 100; // TODO: 从配置中获取实际值
    }
    
    /**
     * 检查SSH连接池是否健康
     * @return 连接池健康状态
     */
    public boolean isConnectionPoolHealthy() {
        // 检查连接池健康状态
        // 这里可以检查是否有僵死连接、连接超时等问题
        return true; // TODO: 实现实际健康检查逻辑
    }
}
