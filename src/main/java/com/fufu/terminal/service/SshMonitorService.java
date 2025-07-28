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
 * SSH远程主机监控的服务 (Refactored)
 * @author lizelin
 */
@Slf4j
@Service
public class SshMonitorService {

    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService monitorScheduler;

    private static final long HIGH_FREQUENCY_SECONDS = 3;
    private static final long LOW_FREQUENCY_SECONDS = 30;

    public SshMonitorService(ObjectMapper objectMapper, @Qualifier("monitorScheduler") ScheduledExecutorService monitorScheduler) {
        this.objectMapper = objectMapper;
        this.monitorScheduler = monitorScheduler;
    }

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

    public void handleMonitorStop(SshConnection sshConnection) {
        if (sshConnection != null) {
            log.info("Request to switch to low-frequency monitoring.");
            rescheduleMonitoring(sshConnection, null, LOW_FREQUENCY_SECONDS, false);
        }
    }

    private void rescheduleMonitoring(SshConnection sshConnection, WebSocketSession session, long periodSeconds, boolean sendToClient) {
        sshConnection.cancelMonitoringTask();

        Runnable monitoringRunnable = () -> {
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

    private Map<String, Object> fetchAndParseStats(SshConnection sshConnection) throws Exception {
        OsInfo osInfo = sshConnection.getOsInfo();
        if (osInfo == null || osInfo.getSystemType() == SystemType.UNKNOWN) {
            log.warn("Cannot fetch stats: OS info is unknown for host {}", sshConnection.getJschSession().getHost());
            return Collections.emptyMap();
        }

        return switch (osInfo.getSystemType()) {
            case DEBIAN, REDHAT, SUSE, ARCH, LINUX -> fetchAndParseLinuxStats(sshConnection);
            // Add cases for other OS types like MACOS in the future
            default -> {
                log.warn("Monitoring is not supported for OS type: {}", osInfo.getSystemType());
                yield Collections.emptyMap();
            }
        };
    }

    private Map<String, Object> fetchAndParseLinuxStats(SshConnection sshConnection) throws Exception {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        final String delimiter = "---CMD_DELIMITER---";
        final String dockerPsCmd = "docker ps --format '{{.ID}}\\t{{.Names}}\\t{{.Status}}'";
        final String dockerStatsCmd = "docker stats --no-stream --format '{{.ID}}\\t{{.CPUPerc}}\\t{{.MemUsage}}'";

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

        Thread.sleep(1000);

        String finalCommands = String.join(" ; echo '" + delimiter + "'; ", "grep 'cpu ' /proc/stat", "cat /proc/net/dev");
        CommandResult finalCmdResult = SshCommandUtil.executeCommand(sshConnection, finalCommands);
        if (!finalCmdResult.isSuccess()) {
            log.warn("Final monitoring command failed, exit={}, stderr={}", finalCmdResult.getExitStatus(), finalCmdResult.getStderr());
        }

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

    private void sendStats(WebSocketSession session, Map<String, Object> stats) throws IOException {
        Map<String, Object> response = Map.of("type", "monitor_update", "payload", stats);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    // --- All parsing helper methods below this line are kept as they were ---

    private String getPart(String[] parts, int index, String defaultValue) {
        return (parts != null && parts.length > index) ? parts[index].trim() : defaultValue;
    }

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

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "i";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
