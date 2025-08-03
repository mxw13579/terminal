//package com.fufu.terminal.controller;
//
//import com.fufu.terminal.adapter.StompSessionAdapter;
//import com.fufu.terminal.dto.SftpMessages.*;
//import com.fufu.terminal.model.SshConnection;
//import com.fufu.terminal.service.SftpService;
//import com.fufu.terminal.service.SshConnectionManager;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.messaging.handler.annotation.MessageMapping;
//import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
//import org.springframework.messaging.simp.SimpMessagingTemplate;
//import org.springframework.stereotype.Controller;
//
//import java.security.Principal;
//
///**
// * SFTP文件传输控制器
// * 处理文件列表、上传、下载操作
// *
// * @author lizelin
// */
//@Slf4j
//@Controller
//@RequiredArgsConstructor
//public class SftpController {
//
//    private final SftpService sftpService;
//    private final SshConnectionManager connectionManager;
//    private final SimpMessagingTemplate messagingTemplate;
//
//    /**
//     * 列出SFTP目录文件
//     */
//    @MessageMapping("/sftp/list")
//    public void listFiles(ListRequest request, SimpMessageHeaderAccessor headerAccessor, Principal principal) {
//        String sessionId = headerAccessor.getSessionId();
//        SshConnection connection = connectionManager.getConnection(sessionId);
//
//        if (connection == null) {
//            log.warn("Received SFTP list request for non-existent session: {}", sessionId);
//            messagingTemplate.convertAndSendToUser(
//                sessionId,
//                "/queue/sftp/error",
//                new ErrorResponse("sftp_error", "No SSH connection found")
//            );
//            return;
//        }
//
//        try {
//            // 使用适配器包装STOMP会话
//            StompSessionAdapter sessionAdapter = new StompSessionAdapter(sessionId, messagingTemplate);
//            sftpService.handleSftpList(sessionAdapter, connection, request.getPath());
//        } catch (Exception e) {
//            log.error("Error handling SFTP list for session {}: ", sessionId, e);
//            messagingTemplate.convertAndSendToUser(
//                sessionId,
//                "/queue/sftp/error",
//                new ErrorResponse("sftp_error", "Error listing files: " + e.getMessage())
//            );
//        }
//    }
//
//    /**
//     * 下载SFTP文件
//     */
//    @MessageMapping("/sftp/download")
//    public void downloadFiles(DownloadRequest request, SimpMessageHeaderAccessor headerAccessor, Principal principal) {
//        String sessionId = headerAccessor.getSessionId();
//        SshConnection connection = connectionManager.getConnection(sessionId);
//
//        if (connection == null) {
//            log.warn("Received SFTP download request for non-existent session: {}", sessionId);
//            messagingTemplate.convertAndSendToUser(
//                sessionId,
//                "/queue/sftp/error",
//                new ErrorResponse("sftp_error", "No SSH connection found")
//            );
//            return;
//        }
//
//        try {
//            StompSessionAdapter sessionAdapter = new StompSessionAdapter(sessionId, messagingTemplate);
//            sftpService.handleSftpDownload(sessionAdapter, connection, request.getPaths());
//        } catch (Exception e) {
//            log.error("Error handling SFTP download for session {}: ", sessionId, e);
//            messagingTemplate.convertAndSendToUser(
//                sessionId,
//                "/queue/sftp/error",
//                new ErrorResponse("sftp_error", "Error downloading files: " + e.getMessage())
//            );
//        }
//    }
//
//    /**
//     * 上传文件分片
//     */
//    @MessageMapping("/sftp/upload")
//    public void uploadChunk(UploadChunkRequest request, SimpMessageHeaderAccessor headerAccessor, Principal principal) {
//        String sessionId = headerAccessor.getSessionId();
//        SshConnection connection = connectionManager.getConnection(sessionId);
//
//        if (connection == null) {
//            log.warn("Received SFTP upload request for non-existent session: {}", sessionId);
//            messagingTemplate.convertAndSendToUser(
//                sessionId,
//                "/queue/sftp/error",
//                new ErrorResponse("sftp_error", "No SSH connection found")
//            );
//            return;
//        }
//
//        try {
//            StompSessionAdapter sessionAdapter = new StompSessionAdapter(sessionId, messagingTemplate);
//            sftpService.handleSftpUploadChunk(
//                sessionAdapter,
//                connection,
//                request.getPath(),
//                request.getFilename(),
//                request.getChunkIndex(),
//                request.getTotalChunks(),
//                request.getContent()
//            );
//        } catch (Exception e) {
//            log.error("Error handling SFTP upload chunk for session {}: ", sessionId, e);
//            messagingTemplate.convertAndSendToUser(
//                sessionId,
//                "/queue/sftp/error",
//                new ErrorResponse("sftp_error", "Error uploading file chunk: " + e.getMessage())
//            );
//        }
//    }
//}
