package com.fufu.terminal.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.model.SshConnection;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSH终端 与 SFTP WebSocket 处理程序
 * @author lizelin
 */
@Slf4j
public class SshTerminalWebSocketHandler extends TextWebSocketHandler {
    private final Map<String, SshConnection> connections = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    // 新增一个用于定时任务的调度器
    private final ScheduledExecutorService monitorScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * 分片缓存
     */
    private final Map<String, List<byte[]>> uploadChunks = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            String query = session.getUri().getQuery();
            Map<String, String> queryParams = parseQuery(query);
            String host = queryParams.get("host");
            int port = Integer.parseInt(queryParams.getOrDefault("port", "22"));
            String user = queryParams.get("user");
            String password = queryParams.get("password");

            JSch jsch = new JSch();
            Session jschSession = jsch.getSession(user, host, port);
            jschSession.setPassword(password);
            jschSession.setConfig("StrictHostKeyChecking", "no");
            jschSession.connect(30000);

            // 建立 Shell 通道
            ChannelShell channel = (ChannelShell) jschSession.openChannel("shell");
            channel.setPtyType("xterm");
            InputStream inputStream = channel.getInputStream();
            OutputStream outputStream = channel.getOutputStream();
            channel.connect(3000);

            log.info("建立连接");

            // 注意 SshConnection 的构造函数现在只接收 shell 相关部分
            SshConnection sshConnection = new SshConnection(jsch, jschSession, channel, inputStream, outputStream);
            connections.put(session.getId(), sshConnection);

            executorService.submit(() -> {
                try {
                    byte[] buffer = new byte[1024];
                    int i;
                    // 循环读取 Shell 的输出
                    while ((i = inputStream.read(buffer)) != -1) {
                        // 1. 将原始字节数据转换为字符串
                        String payload = new String(buffer, 0, i, java.nio.charset.StandardCharsets.UTF_8);
                        // 2. 构建一个符合前端约定的 Map/JSON 对象
                        Map<String, String> response = Map.of(
                                "type", "terminal_data",
                                "payload", payload
                        );

                        // 3. 将 Map 序列化为 JSON 字符串
                        String jsonResponse = objectMapper.writeValueAsString(response);
                        // 4. 发送封装好的 JSON 字符串，而不是原始数据
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(jsonResponse));
                        }
                    }
                } catch (IOException e) {
                    log.error("Error reading from shell or writing to session: " ,e);
                } finally {
                    closeConnection(session);
                }
            });
        } catch (Exception e) {
            log.error("Error establishing SSH connection: " , e);
            // 发送错误信息给前端
            Map<String, String> errorResponse = Map.of(
                    "type", "error",
                    "payload", "Connection failed: " + e.getMessage()
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
            session.close();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SshConnection sshConnection = connections.get(session.getId());
        if (sshConnection == null) {
            return;
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(message.getPayload());
            String type = jsonNode.get("type").asText();

            log.info("Received WebSocket message type: {}", type);

            // 根据消息类型分发处理
            switch (type) {
                case "data": // 终端输入
                    String data = jsonNode.get("payload").asText();
                    sshConnection.getOutputStream().write(data.getBytes());
                    sshConnection.getOutputStream().flush();
                    break;

                case "resize": // 终端尺寸调整
                    int cols = jsonNode.get("cols").asInt();
                    int rows = jsonNode.get("rows").asInt();
                    sshConnection.getChannelShell().setPtySize(cols, rows, cols * 8, rows * 8);
                    break;

                // --- SFTP处理逻辑 ---
                case "sftp_list":
                    String path = jsonNode.has("path") ? jsonNode.get("path").asText() : ".";
                    handleSftpList(session, sshConnection, path);
                    break;

                // --- 处理下载请求 ---
                case "sftp_download":
                    if (jsonNode.has("paths")) {
                        List<String> paths = new ArrayList<>();
                        for (JsonNode n : jsonNode.get("paths")) {
                            paths.add(n.asText());
                        }
                        handleSftpDownload(session, sshConnection, paths);
                    }
                    break;
                case "sftp_upload_chunk":
                    String uploadPathChunk = jsonNode.get("path").asText();
                    String filenameChunk = jsonNode.get("filename").asText();
                    int chunkIndex = jsonNode.get("chunkIndex").asInt();
                    int totalChunks = jsonNode.get("totalChunks").asInt();
                    String contentChunk = jsonNode.get("content").asText();
                    handleSftpUploadChunk(session, sshConnection, uploadPathChunk, filenameChunk, chunkIndex, totalChunks, contentChunk);
                    break;
                case "monitor_start":
                    handleMonitorStart(session, sshConnection);
                    break;
                case "monitor_stop":
                    handleMonitorStop(session, sshConnection);
                    break;
                default:
                    // 未知类型
                    log.warn("Unknown message type: " + type);
                    break;
            }
        } catch (IOException e) {
            log.error("Error handling message: " + e.getMessage(), e);
            closeConnection(session);
        }
    }

    /**
     * 处理列出SFTP目录的请求
     */
    private void handleSftpList(WebSocketSession session, SshConnection sshConnection, String path) throws IOException {
        try {
            ChannelSftp channelSftp = sshConnection.getOrCreateSftpChannel();
            if (path == null || path.isEmpty() || path.equals(".")) {
                path = channelSftp.getHome();
            }
            String absolutePath = channelSftp.realpath(path);
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = channelSftp.ls(absolutePath);
            List<Map<String, Object>> fileList = new ArrayList<>();
            if (!absolutePath.equals("/")) {
                fileList.add(Map.of(
                        "name", "..",
                        "longname", "d---------   - owner group         0 Jan 01 00:00 ..",
                        "isDirectory", true,
                        "path", Paths.get(absolutePath, "..").normalize().toString().replace("\\", "/")
                ));
            }
            for (ChannelSftp.LsEntry entry : entries) {
                if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) {
                    continue;
                }
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("name", entry.getFilename());
                fileInfo.put("longname", entry.getLongname());
                fileInfo.put("isDirectory", entry.getAttrs().isDir());
                fileInfo.put("size", entry.getAttrs().getSize());
                fileInfo.put("mtime", entry.getAttrs().getMTime());
                fileInfo.put("path", Paths.get(absolutePath, entry.getFilename()).normalize().toString().replace("\\", "/"));
                fileList.add(fileInfo);
            }

            //使用 Comparator 链式调用进行排序，目录优先，然后按名称不区分大小写排序
            fileList.sort(Comparator.comparing((Map<String, Object> m) -> (Boolean) m.get("isDirectory")).reversed()
                    .thenComparing(m -> (String) m.get("name"), String.CASE_INSENSITIVE_ORDER));

            Map<String, Object> response = Map.of(
                    "type", "sftp_list_response",
                    "path", absolutePath,
                    "files", fileList
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (JSchException | SftpException e) {
            log.error("SFTP List Error: " + e.getMessage(), e);
            sendSftpError(session, "SFTP operation failed: " + e.getMessage());
        }
    }


    private void handleSftpDownload(WebSocketSession session, SshConnection sshConnection, List<String> paths) throws IOException {
        try {
            ChannelSftp channelSftp = sshConnection.getOrCreateSftpChannel();
            if (paths.size() == 1) {
                String filePath = paths.get(0);
                SftpATTRS attrs = channelSftp.lstat(filePath);
                if (attrs.isDir()) {
                    ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
                    try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(zipOut)) {
                        zipDirectory(channelSftp, filePath, "", zos);
                    }
                    sendDownloadResponse(session, Paths.get(filePath).getFileName().toString() + ".zip", zipOut.toByteArray());
                } else {
                    try (InputStream inputStream = channelSftp.get(filePath); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        inputStream.transferTo(baos);
                        sendDownloadResponse(session, Paths.get(filePath).getFileName().toString(), baos.toByteArray());
                    }
                }
            } else {
                ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
                try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(zipOut)) {
                    for (String path : paths) {
                        SftpATTRS attrs = channelSftp.lstat(path);
                        String entryName = Paths.get(path).getFileName().toString();
                        if (attrs.isDir()) {
                            zipDirectory(channelSftp, path, entryName + "/", zos);
                        } else {
                            zipFile(channelSftp, path, entryName, zos);
                        }
                    }
                }
                sendDownloadResponse(session, "download.zip", zipOut.toByteArray());
            }
        } catch (JSchException | SftpException e) {
            log.error("SFTP Download Error: " + e.getMessage(), e);
            sendSftpError(session, "SFTP download failed: " + e.getMessage());
        }
    }

    private void sendDownloadResponse(WebSocketSession session, String filename, byte[] data) throws IOException {
        Map<String, Object> response = Map.of(
                "type", "sftp_download_response",
                "filename", filename,
                "content", Base64.getEncoder().encodeToString(data)
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void zipDirectory(ChannelSftp sftp, String dirPath, String base, java.util.zip.ZipOutputStream zos) throws SftpException, IOException {
        @SuppressWarnings("unchecked")
        Vector<ChannelSftp.LsEntry> entries = sftp.ls(dirPath);
        for (ChannelSftp.LsEntry entry : entries) {
            if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) {
                continue;
            }
            String fullPath = Paths.get(dirPath, entry.getFilename()).toString().replace("\\", "/");
            String zipEntryName = base + entry.getFilename();
            if (entry.getAttrs().isDir()) {
                zipDirectory(sftp, fullPath, zipEntryName + "/", zos);
            } else {
                zipFile(sftp, fullPath, zipEntryName, zos);
            }
        }
    }

    private void zipFile(ChannelSftp sftp, String filePath, String zipEntryName, java.util.zip.ZipOutputStream zos) throws SftpException, IOException {
        zos.putNextEntry(new java.util.zip.ZipEntry(zipEntryName));
        try (InputStream is = sftp.get(filePath)) {
            is.transferTo(zos);
        }
        zos.closeEntry();
    }

    private void sendSftpError(WebSocketSession session, String message) throws IOException {
        Map<String, Object> errorResponse = Map.of(
                "type", "sftp_error",
                "message", message
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
    }


    private void handleMonitorStart(WebSocketSession session, SshConnection sshConnection) {
        log.info("Starting monitoring for session {}", session.getId());
        Runnable monitoringRunnable = () -> {
            try {
                if (!session.isOpen()) {
                    handleMonitorStop(session, sshConnection);
                    return;
                }

                Map<String, Object> statsPayload = new HashMap<>();
                statsPayload.put("systemStats", getSystemStats(sshConnection.getJschSession()));
                statsPayload.put("dockerContainers", getDockerContainers(sshConnection.getJschSession()));

                Map<String, Object> response = Map.of(
                        "type", "monitor_update",
                        "payload", statsPayload
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            } catch (Exception e) {
                log.error("Monitoring task failed for session {}: {}", session.getId(), e.getMessage());
                // (可选) 向前端发送一个错误
                handleMonitorStop(session, sshConnection); // 出现错误时停止监控
            }
        };

        // 每3秒执行一次
        Future<?> task = monitorScheduler.scheduleAtFixedRate(monitoringRunnable, 0, 3, TimeUnit.SECONDS);
        sshConnection.setMonitoringTask(task);
    }

    private void handleMonitorStop(WebSocketSession session, SshConnection sshConnection) {
        log.info("Stopping monitoring for session {}", session.getId());
        if (sshConnection != null) {
            sshConnection.cancelMonitoringTask();
        }
    }

    /**
     * 获取系统统计信息。
     * 优化点：将多个命令合并为一次SSH执行，大幅减少开销。
     */
    private Map<String, Object> getSystemStats(Session jschSession) {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        final String delimiter = "---CMD_DELIMITER---";
        final String initialCommands = String.join(" ; echo '" + delimiter + "'; ",
                "cat /proc/cpuinfo | grep 'model name' | uniq | sed 's/model name\\s*:\\s*//'",
                "uptime -p",
                "grep 'cpu ' /proc/stat",
                "free -m",
                "df -h /",
                "cat /proc/net/dev"
        );

        String initialResult = executeRemoteCommand(jschSession, initialCommands);
        String[] initialParts = initialResult.split(delimiter);

        // 为了计算CPU和网络速率，需要间隔一段时间再获取一次
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        final String finalCommands = String.join(" ; echo '" + delimiter + "'; ",
                "grep 'cpu ' /proc/stat",
                "cat /proc/net/dev"
        );
        String finalResult = executeRemoteCommand(jschSession, finalCommands);
        String[] finalParts = finalResult.split(delimiter);

        // 安全地获取命令输出
        String cpuModel = initialParts.length > 0 ? initialParts[0].trim() : "N/A";
        String uptime = initialParts.length > 1 ? initialParts[1].trim() : "N/A";
        String initialCpu = initialParts.length > 2 ? initialParts[2] : "";
        String memUsage = initialParts.length > 3 ? initialParts[3] : "";
        String diskUsage = initialParts.length > 4 ? initialParts[4] : "";
        String initialNet = initialParts.length > 5 ? initialParts[5] : "";

        String finalCpu = finalParts.length > 0 ? finalParts[0] : "";
        String finalNet = finalParts.length > 1 ? finalParts[1] : "";

        stats.put("cpuModel", cpuModel);
        stats.put("uptime", uptime.replace("up ", "").trim());
        stats.put("cpuUsage", parseCpuUsage(initialCpu, finalCpu));
        stats.put("memUsage", parseMemUsage(memUsage));
        stats.put("diskUsage", parseDiskUsage(diskUsage));

        Map<String, String> net = parseNetUsage(initialNet, finalNet);
        stats.put("netRx", net.get("rx"));
        stats.put("netTx", net.get("tx"));

        return stats;
    }

    /**
     * 获取Docker容器列表。
     * 优化点：将两个docker命令合并为一次SSH执行。
     */
    private List<Map<String, String>> getDockerContainers(Session jschSession) {
        String dockerCheck = executeRemoteCommand(jschSession, "command -v docker");
        if (dockerCheck == null || dockerCheck.trim().isEmpty()) {
            return Collections.emptyList();
        }

        final String delimiter = "---CMD_DELIMITER---";
        String command = "docker ps --format '{{.ID}}\\t{{.Names}}\\t{{.Status}}';" +
                "echo '" + delimiter + "';" +
                "docker stats --no-stream --format '{{.ID}}\\t{{.CPUPerc}}\\t{{.MemUsage}}'";

        String result = executeRemoteCommand(jschSession, command);
        String[] parts = result.split(delimiter, 2);
        String psOutput = parts.length > 0 ? parts[0] : "";
        String statsOutput = parts.length > 1 ? parts[1] : "";

        Map<String, Map<String, String>> containers = new HashMap<>();

        if (!psOutput.isEmpty()) {
            for (String line : psOutput.split("\\r?\\n")) {
                if(line.trim().isEmpty()) {
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
        }

        if (!statsOutput.isEmpty()) {
            for (String line : statsOutput.split("\\r?\\n")) {
                if(line.trim().isEmpty()) {
                    continue;
                }
                String[] statsParts = line.split("\t");
                if (statsParts.length >= 3 && containers.containsKey(statsParts[0])) {
                    Map<String, String> containerInfo = containers.get(statsParts[0]);
                    containerInfo.put("cpuPerc", statsParts[1]);
                    // 只取使用的内存部分, e.g., "1.2MiB / 1.9GiB" -> "1.2MiB"
                    containerInfo.put("memPerc", statsParts[2].split(" / ")[0]);
                }
            }
        }

        return new ArrayList<>(containers.values());
    }



    /**
     * 执行远程命令。
     * 优化点：改进了等待命令完成的循环，并增加了退出状态码的日志记录。
     */
    private String executeRemoteCommand(Session session, String command) {
        ChannelExec channel = null;
        try (ByteArrayOutputStream responseStream = new ByteArrayOutputStream()) {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setOutputStream(responseStream);
            channel.connect(5000); // 增加连接超时

            while (channel.isConnected()) {
                try {
                    Thread.sleep(20); // 短暂等待
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Command execution interrupted: {}", command);
                    return "";
                }
            }

            int exitStatus = channel.getExitStatus();
            if (exitStatus != 0) {
                log.warn("Remote command '{}' exited with status {}", command, exitStatus);
            }

            return responseStream.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (JSchException | IOException e) {
            log.warn("Failed to execute remote command '{}': {}", command, e.getMessage());
            return "";
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }


    // 解析工具方法
    /**
     * CPU 解析
     * @param start 开始
     * @param end 结束
     * @return CPU使用率
     */
    private double parseCpuUsage(String start, String end) {
        if (start == null || end == null || start.isEmpty() || end.isEmpty()) {
            return 0.0;
        }
        try {
            long[] startMetrics = Arrays.stream(start.trim().split("\\s+")).skip(1).mapToLong(Long::parseLong).toArray();
            long[] endMetrics = Arrays.stream(end.trim().split("\\s+")).skip(1).mapToLong(Long::parseLong).toArray();
            long startTotal = Arrays.stream(startMetrics).sum();
            long endTotal = Arrays.stream(endMetrics).sum();
            long startIdle = startMetrics[3]; // idle time is the 4th value
            long endIdle = endMetrics[3];
            double totalDiff = endTotal - startTotal;
            double idleDiff = endIdle - startIdle;
            return totalDiff > 0 ? 100.0 * (totalDiff - idleDiff) / totalDiff : 0.0;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            log.warn("Failed to parse CPU usage", e);
            return 0.0;
        }
    }

    /**
     * 内存解析
     * @param freeOutput free输出
     * @return 内存使用率
     */
    private double parseMemUsage(String freeOutput) {
        if (freeOutput == null || freeOutput.isEmpty()) {
            return 0.0;
        }
        try {
            for (String line : freeOutput.split("\\n")) {
                if (line.startsWith("Mem:")) {
                    String[] parts = line.trim().split("\\s+");
                    double total = Double.parseDouble(parts[1]);
                    double used = Double.parseDouble(parts[2]);
                    return total > 0 ? (used / total) * 100.0 : 0.0;
                }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            log.warn("Failed to parse memory usage", e);
            return 0.0;
        }
        return 0.0;
    }

    /**
     * 解析df命令的输出以获取磁盘使用率。
     *
     * @param dfOutput df -h / 命令的原始输出字符串
     * @return 磁盘使用率百分比，如果解析失败则返回 0.0
     */
    private double parseDiskUsage(String dfOutput) {
        if (dfOutput == null || dfOutput.trim().isEmpty()) {
            return 0.0;
        }
        try {
            String[] lines = dfOutput.trim().split("\\n");
            // 遍历所有行，跳过表头
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                // 寻找以根挂载点 " /" 结尾的行
                if (line.endsWith(" /")) {
                    String[] parts = line.split("\\s+");
                    // 在该行的所有部分中寻找包含 '%' 的字段
                    for (String part : parts) {
                        if (part.endsWith("%")) {
                            return Double.parseDouble(part.replace("%", ""));
                        }
                    }
                }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            log.warn("Failed to parse disk usage from output: '{}'", dfOutput, e);
            return 0.0;
        }
        // 如果循环结束仍未找到，记录警告信息
        log.warn("Could not find root filesystem '/' or usage percentage in df output: '{}'", dfOutput);
        return 0.0;
    }

    /**
     * 网络解析
     * @param start 开始
     * @param end 结束
     * @return 网络使用率
     */
    private Map<String, String> parseNetUsage(String start, String end) {
        if (start == null || end == null || start.isEmpty() || end.isEmpty()) {
            return Map.of("rx", "N/A", "tx", "N/A");
        }
        try {
            long startRx = 0, startTx = 0, endRx = 0, endTx = 0;
            for (String line : start.split("\\n")) {
                if (line.contains(":")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length > 9) {
                        startRx += Long.parseLong(parts[1]);
                        startTx += Long.parseLong(parts[9]);
                    }
                }
            }
            for (String line : end.split("\\n")) {
                if (line.contains(":")) {
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
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            log.warn("Failed to parse network usage", e);
            return Map.of("rx", "N/A", "tx", "N/A");
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "i";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeConnection(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Transport error for session {}", session.getId(), exception);
        closeConnection(session);
    }

    private void closeConnection(WebSocketSession session) {
        SshConnection sshConnection = connections.remove(session.getId());
        if (sshConnection != null) {
            sshConnection.disconnect();
            log.info("Session {} closed and connection resources released.", session.getId());
        }
        uploadChunks.keySet().removeIf(key -> key.startsWith(session.getId() + ":"));
    }

    private void handleSftpUploadChunk(WebSocketSession session, SshConnection sshConnection, String remotePath, String filename, int chunkIndex, int totalChunks, String contentBase64) throws IOException {
        String uploadKey = session.getId() + ":" + remotePath + "/" + filename;
        List<byte[]> chunks = uploadChunks.computeIfAbsent(uploadKey, k -> Collections.synchronizedList(new ArrayList<>(Collections.nCopies(totalChunks, null))));
        byte[] decodedChunk = Base64.getDecoder().decode(contentBase64);
        chunks.set(chunkIndex, decodedChunk);
        if (chunks.stream().allMatch(Objects::nonNull)) {
            List<byte[]> finalChunks = uploadChunks.remove(uploadKey);
            if (finalChunks == null) {
                log.warn("Upload task for {} already processed or removed.", uploadKey);
                return;
            }
            executorService.submit(() -> {
                ChannelSftp sftpChannel = null;
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    for (byte[] chunk : finalChunks) {
                        baos.write(chunk);
                    }
                    byte[] fileBytes = baos.toByteArray();
                    sftpChannel = (ChannelSftp) sshConnection.getJschSession().openChannel("sftp");
                    sftpChannel.connect(5000);
                    String fullRemotePath = toUnixPath(Paths.get(remotePath, filename));
                    WebSocketSftpProgressMonitor monitor = new WebSocketSftpProgressMonitor(session, objectMapper);
                    monitor.init(SftpProgressMonitor.PUT, "local-stream", fullRemotePath, fileBytes.length);
                    try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
                        sftpChannel.put(inputStream, fullRemotePath, monitor);
                    }
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                            "type", "sftp_upload_final_success",
                            "message", String.format("文件 '%s' 已成功上传到服务器。", filename),
                            "path", remotePath
                    ))));
                } catch (Exception e) {
                    log.error("Upload file {} failed", filename, e);
                    try { sendSftpError(session, "后台上传文件失败: " + e.getMessage()); }
                    catch (IOException ioException) {
                        log.error("发送SFTP上传错误消息失败", ioException);
                    }
                } finally {
                    if (sftpChannel != null && sftpChannel.isConnected()) {
                        sftpChannel.disconnect();
                        log.info("临时SFTP上传通道已关闭。");
                    }
                }
            });
        } else {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "sftp_upload_chunk_success",
                    "chunkIndex", chunkIndex,
                    "totalChunks", totalChunks
            ))));
        }
    }


    private static String toUnixPath(java.nio.file.Path path) {
        return path.normalize().toString().replace("\\", "/");
    }


    private Map<String, String> parseQuery(String query) {
        if (query == null) {
            return Collections.emptyMap();
        }
        Map<String, String> params = new HashMap<>();
        UriComponentsBuilder.fromUriString("?" + query).build().getQueryParams()
                .forEach((k, v) -> params.put(k, v.get(0)));
        return params;
    }
}
