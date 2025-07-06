package com.fufu.terminal.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.model.SshConnection;
import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * SSH终端 与 SFTP WebSocket 处理程序
 * @author lizelin
 */
@Slf4j
public class SshTerminalWebSocketHandler extends TextWebSocketHandler {
    private final Map<String, SshConnection> connections = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
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
                    System.err.println("Error reading from shell or writing to session: " + e.getMessage());
                } finally {
                    closeConnection(session);
                }
            });
        } catch (Exception e) {
            System.err.println("Error establishing SSH connection: " + e.getMessage());
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
            if(path == null || path.isEmpty() || path.equals(".")) {
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
            fileList.sort((a,b) -> {
                boolean isDirA = (boolean) a.get("isDirectory");
                boolean isDirB = (boolean) b.get("isDirectory");
                if (isDirA && !isDirB) return -1;
                if (!isDirA && isDirB) return 1;
                return ((String)a.get("name")).compareToIgnoreCase((String)b.get("name"));
            });
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
            if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) continue;
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
        // 再次确认所有分片都已收到 (使用 stream().allMatch 更可靠)
        boolean allReceived = chunks.stream().allMatch(Objects::nonNull);
        if (allReceived) {
            // 关键：从缓存中移除，防止重复触发上传
            List<byte[]> finalChunks = uploadChunks.remove(uploadKey);

            if (finalChunks == null) {
                log.warn("Upload task for {} already processed or removed.", uploadKey);
                return;
            }
            executorService.submit(() -> {
                ChannelSftp tempSftpChannel = null;
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    for (byte[] chunk : finalChunks) {
                        baos.write(chunk);
                    }
                    byte[] fileBytes = baos.toByteArray();
                    //  获取文件总大小
                    long fileSize = fileBytes.length;
                    Session jschSession = sshConnection.getJschSession();
                    tempSftpChannel = (ChannelSftp) jschSession.openChannel("sftp");
                    tempSftpChannel.connect(5000);
                    String fullRemotePath = Paths.get(remotePath, filename).normalize().toString().replace("\\", "/");
                    WebSocketSftpProgressMonitor monitor = new WebSocketSftpProgressMonitor(session, objectMapper);
                    // 手动初始化监视器，并传入正确的文件总大小 ---
                    // 这将确保 monitor 内部的 totalSize 是正确的，从而使百分比计算正确。
                    monitor.init(SftpProgressMonitor.PUT, "local-stream", fullRemotePath, fileSize);

                    // 执行上传，JSch会继续使用这个已经初始化好的 monitor
                    try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
                        tempSftpChannel.put(inputStream, fullRemotePath, monitor);
                    }

                    Map<String, Object> finalResponse = Map.of(
                            "type", "sftp_upload_final_success",
                            "message", "文件 '" + filename + "' 已成功上传到服务器。",
                            "path", remotePath
                    );
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(finalResponse)));
                } catch (Exception e) {
                    log.error("在独立的SFTP通道中上传文件 {} 时失败", filename, e);
                    try {
                        sendSftpError(session, "后台上传文件失败: " + e.getMessage());
                    } catch (IOException ioException) {
                        log.error("发送SFTP上传错误消息失败", ioException);
                    }
                } finally {
                    if (tempSftpChannel != null && tempSftpChannel.isConnected()) {
                        tempSftpChannel.disconnect();
                        log.info("临时SFTP上传通道已关闭。");
                    }
                }
            });
        } else {
            // 分片未收齐，发送确认消息给前端
            Map<String, Object> ackResponse = Map.of(
                    "type", "sftp_upload_chunk_success",
                    "chunkIndex", chunkIndex,
                    "totalChunks", totalChunks
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ackResponse)));
        }
    }

    private Map<String, String> parseQuery(String query) {
        if (query == null) return Collections.emptyMap();
        Map<String, String> params = new ConcurrentHashMap<>();
        Arrays.stream(query.split("&")).forEach(param -> {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2) {
                params.put(keyValue[0], keyValue[1]);
            }
        });
        return params;
    }
}
