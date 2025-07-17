package com.fufu.terminal.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SftpService;
import com.fufu.terminal.service.SshMonitorService;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * SSH终端 与 SFTP WebSocket 处理程序
 * 1. 管理WebSocket连接生命周期
 * 2. 解析和分发传入的消息到相应的服务
 * 3. 处理核心的SSH Shell数据流
 * @author lizelin
 */
@Slf4j
@Component
public class SshTerminalWebSocketHandler  extends TextWebSocketHandler {

    private final Map<String, SshConnection> connections = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();


    private final ExecutorService executorService;
    private final SftpService sftpService;
    private final SshMonitorService sshMonitorService;


    // 使用Map实现策略模式，用于消息分发
    private final Map<String, MessageHandler> messageHandlers = new HashMap<>();

    public SshTerminalWebSocketHandler(SftpService sftpService,
                                       SshMonitorService sshMonitorService,
                                       @Qualifier("taskExecutor") ExecutorService executorService) {
        this.sftpService = sftpService;
        this.sshMonitorService = sshMonitorService;
        this.executorService = executorService;
    }

    /**
     * 初始化消息处理器映射
     */
    @PostConstruct
    private void initializeMessageHandlers() {
        // 终端数据输入
        messageHandlers.put("data", (session, connection, payload) -> {
            String data = payload.get("payload").asText();
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(data.getBytes());
            outputStream.flush();
        });

        // 终端尺寸调整
        messageHandlers.put("resize", (session, connection, payload) -> {
            int cols = payload.get("cols").asInt();
            int rows = payload.get("rows").asInt();
            connection.getChannelShell().setPtySize(cols, rows, cols * 8, rows * 8);
        });

        // SFTP: 列出文件
        messageHandlers.put("sftp_list", (session, connection, payload) -> {
            String path = payload.has("path") ? payload.get("path").asText() : ".";
            sftpService.handleSftpList(session, connection, path);
        });

        // SFTP: 下载文件/目录
        messageHandlers.put("sftp_download", (session, connection, payload) -> {
            if (payload.has("paths")) {
                List<String> paths = new ArrayList<>();
                payload.get("paths").forEach(node -> paths.add(node.asText()));
                sftpService.handleSftpDownload(session, connection, paths);
            }
        });

        // SFTP: 上传文件分片
        messageHandlers.put("sftp_upload_chunk", (session, connection, payload) -> {
            String uploadPath = payload.get("path").asText();
            String filename = payload.get("filename").asText();
            int chunkIndex = payload.get("chunkIndex").asInt();
            int totalChunks = payload.get("totalChunks").asInt();
            String content = payload.get("content").asText();
            sftpService.handleSftpUploadChunk(session, connection, uploadPath, filename, chunkIndex, totalChunks, content);
        });

        // 监控: 开始
        messageHandlers.put("monitor_start", (session, connection, payload) ->
                sshMonitorService.handleMonitorStart(session, connection)
        );

        // 监控: 停止
        messageHandlers.put("monitor_stop", (session, connection, payload) ->
                sshMonitorService.handleMonitorStop(connection)
        );
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            Map<String, String> queryParams = parseQuery(session.getUri().getQuery());
            String host = queryParams.get("host");
            int port = Integer.parseInt(queryParams.getOrDefault("port", "22"));
            String user = queryParams.get("user");
            String password = queryParams.get("password");

            JSch jsch = new JSch();
            Session jschSession = jsch.getSession(user, host, port);
            jschSession.setPassword(password);
            jschSession.setConfig("StrictHostKeyChecking", "no");
            jschSession.connect(30000);

            ChannelShell channel = (ChannelShell) jschSession.openChannel("shell");
            channel.setPtyType("xterm");
            InputStream inputStream = channel.getInputStream();
            OutputStream outputStream = channel.getOutputStream();
            channel.connect(3000);

            log.info("SSH connection established for session: {}", session.getId());

            SshConnection sshConnection = new SshConnection(jsch, jschSession, channel, inputStream, outputStream);
            connections.put(session.getId(), sshConnection);

            // 启动一个线程来读取SSH Shell的输出并转发到WebSocket客户端
            startShellOutputForwarder(session, sshConnection);

        } catch (Exception e) {
            log.error("Error establishing SSH connection for session {}: ", session.getId(), e);
            sendJsonError(session, "Connection failed: " + e.getMessage());
            session.close();
        }
    }

    private void startShellOutputForwarder(WebSocketSession session, SshConnection connection) {
        executorService.submit(() -> {
            try (InputStream inputStream = connection.getInputStream()) {
                byte[] buffer = new byte[1024];
                int i;
                while (session.isOpen() && (i = inputStream.read(buffer)) != -1) {
                    String payload = new String(buffer, 0, i, java.nio.charset.StandardCharsets.UTF_8);
                    Map<String, String> response = Map.of("type", "terminal_data", "payload", payload);
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                }
            } catch (IOException e) {
                if (session.isOpen()) {
                    log.error("Error reading from shell or writing to session {}: ", session.getId(), e);
                }
            } finally {
                closeConnection(session);
            }
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SshConnection sshConnection = connections.get(session.getId());
        if (sshConnection == null) {
            log.warn("Received message for a non-existent or closed session: {}", session.getId());
            return;
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(message.getPayload());
            String type = jsonNode.get("type").asText();

            MessageHandler handler = messageHandlers.get(type);
            if (handler != null) {
                handler.handle(session, sshConnection, jsonNode);
            } else {
                log.warn("Unknown message type received: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling message for session {}: {}", session.getId(), e.getMessage(), e);
            try {
                sendJsonError(session, "Error processing message: " + e.getMessage());
            } catch (IOException ioException) {
                log.error("Failed to send error message to session {}", session.getId(), ioException);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Session {} closed with status {}", session.getId(), status);
        closeConnection(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Transport error for session {}: {}", session.getId(), exception.getMessage());
        closeConnection(session);
    }

    private void closeConnection(WebSocketSession session) {
        SshConnection sshConnection = connections.remove(session.getId());
        if (sshConnection != null) {
            // 确保停止任何正在运行的监控任务
            sshMonitorService.handleMonitorStop(sshConnection);
            // 断开SSH连接
            sshConnection.disconnect();
            // 清理与此会话相关的SFTP上传缓存
            sftpService.clearUploadCacheForSession(session.getId());
            log.info("Cleaned up resources for session {}.", session.getId());
        }
    }

    private void sendJsonError(WebSocketSession session, String errorMessage) throws IOException {
        if (session.isOpen()) {
            Map<String, String> errorResponse = Map.of("type", "error", "payload", errorMessage);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
        }
    }

    private Map<String, String> parseQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        return UriComponentsBuilder.fromUriString("?" + query).build().getQueryParams().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0)));
    }
}
