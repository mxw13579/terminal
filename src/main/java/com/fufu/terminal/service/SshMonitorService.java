package com.fufu.terminal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.handler.MessageHandler;
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
 * SSH远程主机监控的服务
 * @author lizelin
 */
@Slf4j
@Service
public class SshMonitorService {

    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService monitorScheduler;


    public SshMonitorService(ObjectMapper objectMapper,
                             @Qualifier("monitorScheduler") ScheduledExecutorService monitorScheduler) {
        this.objectMapper = objectMapper;
        this.monitorScheduler = monitorScheduler;
    }

    public void handleMonitorStart(WebSocketSession session, SshConnection sshConnection) {
        log.info("正在为会话 {} 启动监控", session.getId());
        Runnable monitoringRunnable = () -> {
            try {
                // 检查会话有效性，避免用已经断开的session
                Session jschSession = sshConnection.getJschSession();
                if (!session.isOpen() || jschSession == null || !jschSession.isConnected()) {
                    log.warn("WebSocket或SSH连接已断开，停止监控任务，sessionId={}", session.getId());
                    handleMonitorStop(sshConnection); // 确保在这种情况下停止
                    return;
                }
                // 此方法现在会抛出InterruptedException
                Map<String, Object> statsPayload = getSystemAndDockerStats(sshConnection.getJschSession());

                // 如果线程在获取数据后被中断，或者数据为空，则不发送消息
                if (Thread.currentThread().isInterrupted() || statsPayload.isEmpty()) {
                    log.info("监控任务被中断或未获取到数据，跳过本次发送。");
                    return;
                }

                Map<String, Object> response = Map.of(
                        "type", "monitor_update",
                        "payload", statsPayload
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            } catch (Exception e) {
                if (e instanceof InterruptedException || (e.getCause() != null && e.getCause() instanceof InterruptedException)) {
                    log.info("监控任务被正常中断，将停止执行。会话ID: {}", session.getId());
                    Thread.currentThread().interrupt();
                } else {
                    // 对于所有其他意料之外的异常
                    log.error("监控任务执行时发生意外错误，将停止后续调度。会话ID: {}，错误: {}", session.getId(), e.getMessage(), e);
                    // 停止后续的执行，以避免无限循环的失败。
                    handleMonitorStop(sshConnection);
                }
            }
        };

        // 停止旧的监控任务，防止重复定时
        if (sshConnection.getMonitoringTask() != null) {
            sshConnection.cancelMonitoringTask();
        }
        // 每3秒执行一次
        Future<?> task = monitorScheduler.scheduleAtFixedRate(monitoringRunnable, 0, 3, TimeUnit.SECONDS);
        sshConnection.setMonitoringTask(task);
    }

    public void handleMonitorStop(SshConnection sshConnection) {
        if (sshConnection != null) {
            log.info("正在请求停止监控任务。");
            sshConnection.cancelMonitoringTask();
        }
    }

    /**
     * 获取系统统计信息。
     * 修改了方法签名，使其可以向上抛出 InterruptedException。
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

        // 使用 df -P / 提高跨Linux发行版的兼容性
        String initialCommands = String.join(" ; echo '" + delimiter + "'; ",
                "cat /proc/cpuinfo | grep 'model name' | uniq | sed 's/model name\\s*:\\s*//'",
                "uptime -p", "grep 'cpu ' /proc/stat", "free -m", "df -P /", "cat /proc/net/dev",
                "command -v docker >/dev/null && " + dockerPsCmd + " || echo 'no_docker'",
                "command -v docker >/dev/null && " + dockerStatsCmd + " || echo 'no_docker'"
        );
        String initialResult = executeRemoteCommand(jschSession, initialCommands);
        // 如果第一个命令执行就被中断，则后续无需进行
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("在初始命令执行期间被中断。");
        }
        String[] initialParts = initialResult.split(delimiter);

        // 为了计算CPU和网络速率，需要间隔一段时间再获取一次
        // 此处 sleep 现在会正确地响应中断并抛出异常
        Thread.sleep(1000);

        String finalCommands = String.join(" ; echo '" + delimiter + "'; ",
                "grep 'cpu ' /proc/stat", "cat /proc/net/dev");
        String finalResult = executeRemoteCommand(jschSession, finalCommands);
        String[] finalParts = finalResult.split(delimiter);

        // --- 解析逻辑 ... ---
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

    /**
     * 执行远程命令。
     * 这是最关键的修改，方法签名改为`throws InterruptedException`，并在捕获中断后立即向上抛出，
     * 以便调用栈上层能感知到任务已被取消。
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
            // 如果JSch异常的根本原因是中断，则将其转换为InterruptedException并抛出
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


    // --- 解析工具方法 ---
    private String getPart(String[] parts, int index, String defaultValue) {
        return (parts != null && parts.length > index) ? parts[index].trim() : defaultValue;
    }


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
            log.warn("Failed to parse CPU usage", e);
            return 0.0;
        }
    }

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
            log.warn("Failed to parse memory usage", e);
            return 0.0;
        }
    }

    private double parseDiskUsage(String dfOutput) {
        if (dfOutput == null || dfOutput.isBlank()) {
            return 0.0;
        }
        try {
            // Split into lines and skip the header
            String[] lines = dfOutput.trim().split("\\n");
            if (lines.length < 2) {
                log.warn("Invalid df output, expected at least 2 lines but got: {}", lines.length);
                return 0.0;
            }
            // Data is on the second line
            String dataLine = lines[1];
            String[] parts = dataLine.trim().split("\\s+");
            // The 'Capacity' is the 5th column (index 4)
            if (parts.length >= 5) {
                return Double.parseDouble(parts[4].replace("%", ""));
            }
            log.warn("Could not parse disk usage parts from line: '{}'", dataLine);
            return 0.0;
        } catch (Exception e) {
            log.warn("Failed to parse disk usage from output: '{}'", dfOutput, e);
            return 0.0;
        }
    }

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
            log.warn("Failed to parse network usage", e);
            return Map.of("rx", "N/A", "tx", "N/A");
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "i";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
