package com.fufu.terminal.controller;

import com.fufu.terminal.dto.SftpListDto;
import com.fufu.terminal.dto.SftpDownloadDto;
import com.fufu.terminal.dto.SftpUploadDto;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SftpService;
import com.fufu.terminal.service.StompSessionManager;
import com.fufu.terminal.controller.StompWebSocketSessionAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import jakarta.validation.Valid;

/**
 * STOMP controller for SFTP operations.
 * Handles file listing, upload, and download operations.
 *
 * @author lizelin
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SftpStompController {

    private final SftpService sftpService;
    private final StompSessionManager sessionManager;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle SFTP directory listing requests.
     */
    @MessageMapping("/sftp/list")
    public void handleSftpList(
            @Valid SftpListDto request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("Handling SFTP list request for session: {} path: {}", sessionId, request.getPath());

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sessionManager.sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            // Use adapter to call existing SFTP service
            StompWebSocketSessionAdapter sessionAdapter = new StompWebSocketSessionAdapter(
                sessionId, messagingTemplate
            );

            sftpService.handleSftpList(sessionAdapter, connection, request.getPath());

        } catch (Exception e) {
            log.error("Error handling SFTP list for session {}: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "SFTP list error: " + e.getMessage());
        }
    }

    /**
     * Handle SFTP download requests.
     */
    @MessageMapping("/sftp/download")
    public void handleSftpDownload(
            @Valid SftpDownloadDto request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("Handling SFTP download request for session: {} paths: {}", sessionId, request.getPaths());

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sessionManager.sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            StompWebSocketSessionAdapter sessionAdapter = new StompWebSocketSessionAdapter(
                sessionId, messagingTemplate
            );

            sftpService.handleSftpDownload(sessionAdapter, connection, request.getPaths());

        } catch (Exception e) {
            log.error("Error handling SFTP download for session {}: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "SFTP download error: " + e.getMessage());
        }
    }

    /**
     * Handle SFTP upload chunk requests.
     */
    @MessageMapping("/sftp/upload")
    public void handleSftpUpload(
            @Valid SftpUploadDto request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("Handling SFTP upload chunk for session: {} file: {} chunk: {}/{}",
                 sessionId, request.getFilename(), request.getChunkIndex() + 1, request.getTotalChunks());

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sessionManager.sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            StompWebSocketSessionAdapter sessionAdapter = new StompWebSocketSessionAdapter(
                sessionId, messagingTemplate
            );

            sftpService.handleSftpUploadChunk(
                sessionAdapter,
                connection,
                request.getPath(),
                request.getFilename(),
                request.getChunkIndex(),
                request.getTotalChunks(),
                request.getContent()
            );

        } catch (Exception e) {
            log.error("Error handling SFTP upload chunk for session {}: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "SFTP upload error: " + e.getMessage());
        }
    }
}
