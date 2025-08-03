//package com.fufu.terminal.controller;
//
//import com.fufu.terminal.dto.TerminalMessages.*;
//import com.fufu.terminal.model.SshConnection;
//import com.fufu.terminal.service.SshConnectionManager;
//import com.jcraft.jsch.ChannelShell;
//import com.jcraft.jsch.JSch;
//import com.jcraft.jsch.Session;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.messaging.handler.annotation.MessageMapping;
//import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
//import org.springframework.messaging.simp.SimpMessagingTemplate;
//import org.springframework.stereotype.Controller;
//
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.security.Principal;
//import java.util.concurrent.ExecutorService;
//
///**
// * SSH终端控制器
// * 处理终端连接、输入输出和尺寸调整
// *
// * @author lizelin
// */
//@Slf4j
//@Controller
//public class TerminalController {
//
//    private final SshConnectionManager connectionManager;
//    private final SimpMessagingTemplate messagingTemplate;
//    private final ExecutorService executorService;
//
//    public TerminalController(SshConnectionManager connectionManager, SimpMessagingTemplate messagingTemplate,@Qualifier("taskExecutor") ExecutorService executorService) {
//        this.connectionManager = connectionManager;
//        this.messagingTemplate = messagingTemplate;
//        this.executorService = executorService;
//    }
//
//    /**
//     * 建立SSH连接
//     */
//    @MessageMapping("/terminal/connect")
//    public void connect(ConnectionRequest request, SimpMessageHeaderAccessor headerAccessor, Principal principal) {
//        String sessionId = headerAccessor.getSessionId();
//
//        try {
//            log.info("Establishing SSH connection for session: {} to {}@{}:{}",
//                    sessionId, request.getUser(), request.getHost(), request.getPort());
//
//            JSch jsch = new JSch();
//            Session jschSession = jsch.getSession(request.getUser(), request.getHost(), request.getPort());
//            jschSession.setPassword(request.getPassword());
//            jschSession.setConfig("StrictHostKeyChecking", "no");
//            jschSession.connect(30000);
//
//            ChannelShell channel = (ChannelShell) jschSession.openChannel("shell");
//            channel.setPtyType("xterm");
//            InputStream inputStream = channel.getInputStream();
//            OutputStream outputStream = channel.getOutputStream();
//            channel.connect(3000);
//
//            SshConnection sshConnection = new SshConnection(jsch, jschSession, channel, inputStream, outputStream);
//            connectionManager.addConnection(sessionId, sshConnection);
//
//            // 启动SSH输出转发线程
//            startShellOutputForwarder(sessionId, sshConnection);
//
//            log.info("SSH connection established successfully for session: {}", sessionId);
//
//        } catch (Exception e) {
//            log.error("Error establishing SSH connection for session {}: ", sessionId, e);
//            messagingTemplate.convertAndSendToUser(
//                sessionId,
//                "/queue/terminal/error",
//                new ErrorMessage("error", "Connection failed: " + e.getMessage())
//            );
//        }
//    }
//
//    /**
//     * 处理终端输入
//     */
//    @MessageMapping("/terminal/input")
//    public void handleInput(TerminalInput input, SimpMessageHeaderAccessor headerAccessor) {
//        String sessionId = headerAccessor.getSessionId();
//        SshConnection connection = connectionManager.getConnection(sessionId);
//
//        if (connection == null) {
//            log.warn("Received input for non-existent session: {}", sessionId);
//            return;
//        }
//
//        try {
//            OutputStream outputStream = connection.getOutputStream();
//            outputStream.write(input.getData().getBytes());
//            outputStream.flush();
//        } catch (IOException e) {
//            log.error("Error writing to SSH connection for session {}: ", sessionId, e);
//            messagingTemplate.convertAndSendToUser(
//                sessionId,
//                "/queue/terminal/error",
//                new ErrorMessage("error", "Error writing to terminal: " + e.getMessage())
//            );
//        }
//    }
//
//    /**
//     * 处理终端尺寸调整
//     */
//    @MessageMapping("/terminal/resize")
//    public void handleResize(TerminalResize resize, SimpMessageHeaderAccessor headerAccessor) {
//        String sessionId = headerAccessor.getSessionId();
//        SshConnection connection = connectionManager.getConnection(sessionId);
//
//        if (connection == null) {
//            log.warn("Received resize for non-existent session: {}", sessionId);
//            return;
//        }
//
//        try {
//            connection.getChannelShell().setPtySize(
//                resize.getCols(),
//                resize.getRows(),
//                resize.getCols() * 8,
//                resize.getRows() * 8
//            );
//            log.debug("Terminal resized for session {}: {}x{}", sessionId, resize.getCols(), resize.getRows());
//        } catch (Exception e) {
//            log.error("Error resizing terminal for session {}: ", sessionId, e);
//        }
//    }
//
//    /**
//     * 启动SSH Shell输出转发器
//     */
//    private void startShellOutputForwarder(String sessionId, SshConnection connection) {
//        executorService.submit(() -> {
//            try (InputStream inputStream = connection.getInputStream()) {
//                byte[] buffer = new byte[1024];
//                int i;
//                while ((i = inputStream.read(buffer)) != -1) {
//                    String payload = new String(buffer, 0, i, java.nio.charset.StandardCharsets.UTF_8);
//
//                    // 发送终端输出到用户的队列
//                    messagingTemplate.convertAndSendToUser(
//                        sessionId,
//                        "/queue/terminal/output",
//                        new TerminalOutput("terminal_data", payload)
//                    );
//                }
//            } catch (IOException e) {
//                log.error("Error reading from shell for session {}: ", sessionId, e);
//            } finally {
//                // 清理连接
//                connectionManager.removeConnection(sessionId);
//                log.info("SSH output forwarder stopped for session: {}", sessionId);
//            }
//        });
//    }
//}
