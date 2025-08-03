package com.fufu.terminal.controller;

import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.model.stomp.*;
import com.fufu.terminal.service.SftpService;
import com.fufu.terminal.service.StompSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;

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
     * This replaces the "sftp_list" message handler from the original WebSocket implementation.
     */
    @MessageMapping("/sftp/list")
    public void handleSftpList(
            @Valid SftpListRequest request,
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

            // Use the existing SFTP service but adapt for STOMP messaging
            // This will be handled asynchronously to avoid blocking the STOMP thread
            CompletableFuture.runAsync(() -> {
                try {
                    // Create a mock WebSocket session wrapper for backward compatibility
                    StompWebSocketSessionAdapter sessionAdapter = new StompWebSocketSessionAdapter(
                        sessionId, messagingTemplate);
                    
                    sftpService.handleSftpList(sessionAdapter, connection, request.getPath());
                    
                } catch (Exception e) {
                    log.error("Error handling SFTP list for session {}: {}", sessionId, e.getMessage(), e);
                    sessionManager.sendErrorMessage(sessionId, "SFTP list failed: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("Error processing SFTP list request for session {}: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "SFTP list request failed: " + e.getMessage());
        }
    }

    /**
     * Handle SFTP file upload chunk requests.
     * This replaces the "sftp_upload_chunk" message handler from the original WebSocket implementation.
     */
    @MessageMapping("/sftp/upload/chunk")
    public void handleSftpUploadChunk(
            @Valid SftpUploadChunkMessage message,
            SimpMessageHeaderAccessor headerAccessor) {
        
        String sessionId = headerAccessor.getSessionId();
        log.debug("Handling SFTP upload chunk for session: {} file: {} chunk: {}/{}", 
                 sessionId, message.getFilename(), message.getChunkIndex() + 1, message.getTotalChunks());
        
        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sessionManager.sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            // Use the existing SFTP service but adapt for STOMP messaging
            CompletableFuture.runAsync(() -> {
                try {
                    // Create a mock WebSocket session wrapper for backward compatibility
                    StompWebSocketSessionAdapter sessionAdapter = new StompWebSocketSessionAdapter(
                        sessionId, messagingTemplate);
                    
                    sftpService.handleSftpUploadChunk(
                        sessionAdapter, 
                        connection, 
                        message.getPath(),
                        message.getFilename(),
                        message.getChunkIndex(),
                        message.getTotalChunks(),
                        message.getContent()
                    );
                    
                } catch (Exception e) {
                    log.error("Error handling SFTP upload chunk for session {}: {}", sessionId, e.getMessage(), e);
                    sessionManager.sendErrorMessage(sessionId, "SFTP upload chunk failed: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("Error processing SFTP upload chunk for session {}: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "SFTP upload chunk request failed: " + e.getMessage());
        }
    }

    /**
     * Handle SFTP file download requests.
     * This replaces the "sftp_download" message handler from the original WebSocket implementation.
     */
    @MessageMapping("/sftp/download")
    public void handleSftpDownload(
            @Valid SftpDownloadRequest request,
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

            // Use the existing SFTP service but adapt for STOMP messaging
            CompletableFuture.runAsync(() -> {
                try {
                    // Create a mock WebSocket session wrapper for backward compatibility
                    StompWebSocketSessionAdapter sessionAdapter = new StompWebSocketSessionAdapter(
                        sessionId, messagingTemplate);
                    
                    sftpService.handleSftpDownload(sessionAdapter, connection, request.getPaths());
                    
                } catch (Exception e) {
                    log.error("Error handling SFTP download for session {}: {}", sessionId, e.getMessage(), e);
                    sessionManager.sendErrorMessage(sessionId, "SFTP download failed: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("Error processing SFTP download request for session {}: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "SFTP download request failed: " + e.getMessage());
        }
    }
}