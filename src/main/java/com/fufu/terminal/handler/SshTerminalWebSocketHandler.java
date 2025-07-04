package com.fufu.terminal.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.model.SshConnection;
import com.jcraft.jsch.*;
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
public class SshTerminalWebSocketHandler extends TextWebSocketHandler {
    private final Map<String, SshConnection> connections = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ObjectMapper objectMapper = new ObjectMapper();

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

            System.out.println("建立链接");

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

            System.out.println("Received WebSocket message: " + message.getPayload());

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

                // --- 新增SFTP处理逻辑 ---
                case "sftp_list":
                    String path = jsonNode.has("path") ? jsonNode.get("path").asText() : ".";
                    handleSftpList(session, sshConnection, path);
                    break;

                // --- 新增：处理下载请求 ---
                case "sftp_download":
                    String downloadPath = jsonNode.get("path").asText();
                    handleSftpDownload(session, sshConnection, downloadPath);
                    break;
                // --- 新增：处理上传请求 ---
                case "sftp_upload":
                    String uploadPath = jsonNode.get("path").asText();
                    String filename = jsonNode.get("filename").asText();
                    String content = jsonNode.get("content").asText();
                    handleSftpUpload(session, sshConnection, uploadPath, filename, content);
                    break;
                default:
                    // 未知类型
                    System.err.println("Unknown message type: " + type);
                    break;
            }
        } catch (IOException e) {
            System.err.println("Error handling message: " + e.getMessage());
            closeConnection(session);
        }
    }

    /**
     * 处理列出SFTP目录的请求
     */
    private void handleSftpList(WebSocketSession session, SshConnection sshConnection, String path) throws IOException {
        try {
            ChannelSftp channelSftp = sshConnection.getOrCreateSftpChannel();
            // 如果路径为空，获取用户主目录
            if(path == null || path.isEmpty() || path.equals(".")) {
                path = channelSftp.getHome();
            }

            // 获取绝对路径，以便前端显示
            String absolutePath = channelSftp.realpath(path);

            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = channelSftp.ls(absolutePath);

            List<Map<String, Object>> fileList = new ArrayList<>();

            // 添加 ".." 返回上一级目录（除非是根目录）
            if (!absolutePath.equals("/")) {
                fileList.add(Map.of(
                        "name", "..",
                        "longname", "d---------   - owner group         0 Jan 01 00:00 ..", // 模拟一个 longname
                        "isDirectory", true,
                        "path", absolutePath + "/.."
                ));
            }

            for (ChannelSftp.LsEntry entry : entries) {
                if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) {
                    continue; // 忽略 . 和 ..
                }
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("name", entry.getFilename());
                fileInfo.put("longname", entry.getLongname());
                fileInfo.put("isDirectory", entry.getAttrs().isDir());
                // 为前端提供完整的路径，方便导航
                fileInfo.put("path", absolutePath.endsWith("/") ? absolutePath + entry.getFilename() : absolutePath + "/" + entry.getFilename());
                fileList.add(fileInfo);
            }

            // 按目录优先，然后按名称排序
            fileList.sort((a,b) -> {
                boolean isDirA = (boolean) a.get("isDirectory");
                boolean isDirB = (boolean) b.get("isDirectory");
                if (isDirA && !isDirB) return -1;
                if (!isDirA && isDirB) return 1;
                return ((String)a.get("name")).compareToIgnoreCase((String)b.get("name"));
            });

            // 构建响应消息
            Map<String, Object> response = Map.of(
                    "type", "sftp_list_response",
                    "path", absolutePath,
                    "files", fileList
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));

        } catch (JSchException | SftpException e) {
            System.err.println("SFTP Error: " + e.getMessage());
            // 发送错误信息给前端
            Map<String, Object> errorResponse = Map.of(
                    "type", "sftp_error",
                    "message", "SFTP operation failed: " + e.getMessage()
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
        }
    }

    private void handleSftpDownload(WebSocketSession session, SshConnection sshConnection, String filePath) throws IOException {
        try {
            ChannelSftp channelSftp = sshConnection.getOrCreateSftpChannel();
            String filename = Paths.get(filePath).getFileName().toString();
            InputStream inputStream = channelSftp.get(filePath);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            String base64Content = Base64.getEncoder().encodeToString(baos.toByteArray());
            Map<String, Object> response = Map.of(
                    "type", "sftp_download_response",
                    "filename", filename,
                    "content", base64Content
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (JSchException | SftpException e) {
            System.err.println("SFTP Download Error: " + e.getMessage());
            sendSftpError(session, "SFTP download failed: " + e.getMessage());
        }
    }
    /**
     * 新增：处理SFTP文件上传
     */
    private void handleSftpUpload(WebSocketSession session, SshConnection sshConnection, String remotePath, String filename, String contentBase64) throws IOException {
        try {
            ChannelSftp channelSftp = sshConnection.getOrCreateSftpChannel();
            byte[] contentBytes = Base64.getDecoder().decode(contentBase64);
            InputStream inputStream = new ByteArrayInputStream(contentBytes);
            String fullRemotePath = remotePath.endsWith("/") ? remotePath + filename : remotePath + "/" + filename;
            channelSftp.put(inputStream, fullRemotePath);
            inputStream.close();
            // 发送成功消息，并附上当前路径以便前端刷新
            Map<String, Object> response = Map.of(
                    "type", "sftp_upload_success",
                    "message", "File '" + filename + "' uploaded successfully.",
                    "path", remotePath
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (JSchException | SftpException e) {
            System.err.println("SFTP Upload Error: " + e.getMessage());
            sendSftpError(session, "SFTP upload failed: " + e.getMessage());
        }
    }
    /**
     * 辅助方法：发送SFTP错误信息给前端
     */
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
        closeConnection(session);
    }

    private void closeConnection(WebSocketSession session) {
        SshConnection sshConnection = connections.remove(session.getId());
        if (sshConnection != null) {
            sshConnection.disconnect();
            try {
                if (session.isOpen()) {
                    session.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private Map<String, String> parseQuery(String query) {
        if (query == null) return Map.of();
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
