package com.fufu.terminal.controller;

import com.fufu.terminal.dto.SftpListDto;
import com.fufu.terminal.dto.SftpDownloadDto;
import com.fufu.terminal.dto.SftpUploadDto;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SftpService;
import com.fufu.terminal.service.StompSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import jakarta.validation.Valid;

/**
 * STOMP 控制器，处理 SFTP 相关操作，包括目录列表、文件上传和下载。
 * <p>
 * 通过 WebSocket STOMP 协议与前端进行交互，调用 SftpService 完成具体业务逻辑。
 * </p>
 * <ul>
 *     <li>/sftp/list - 目录列表</li>
 *     <li>/sftp/download - 文件下载</li>
 *     <li>/sftp/upload - 文件分片上传</li>
 * </ul>
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
     * 处理 SFTP 目录列表请求。
     *
     * @param request        目录列表请求参数
     * @param headerAccessor STOMP 消息头访问器
     */
    @MessageMapping("/sftp/list")
    public void handleSftpList(
            @Valid SftpListDto request,
            SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("处理 SFTP 目录列表请求，session: {}，path: {}", sessionId, request.getPath());

        SshConnection connection = getConnectionOrNotify(sessionId);
        if (connection == null) return;

        try {
            sftpService.handleSftpList(
                    createSessionAdapter(sessionId),
                    connection,
                    request.getPath()
            );
        } catch (Exception e) {
            log.error("处理 SFTP 目录列表异常，session {}: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "SFTP 列表错误: " + e.getMessage());
        }
    }

    /**
     * 处理 SFTP 文件下载请求。
     *
     * @param request        文件下载请求参数
     * @param headerAccessor STOMP 消息头访问器
     */
    @MessageMapping("/sftp/download")
    public void handleSftpDownload(
            @Valid SftpDownloadDto request,
            SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("处理 SFTP 下载请求，session: {}，paths: {}", sessionId, request.getPaths());

        SshConnection connection = getConnectionOrNotify(sessionId);
        if (connection == null) return;

        try {
            sftpService.handleSftpDownload(
                    createSessionAdapter(sessionId),
                    connection,
                    request.getPaths()
            );
        } catch (Exception e) {
            log.error("处理 SFTP 下载异常，session {}: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "SFTP 下载错误: " + e.getMessage());
        }
    }

    /**
     * 处理 SFTP 文件分片上传请求。
     *
     * @param request        文件上传请求参数
     * @param headerAccessor STOMP 消息头访问器
     */
    @MessageMapping("/sftp/upload")
    public void handleSftpUpload(
            @Valid SftpUploadDto request,
            SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("处理 SFTP 上传分片，session: {}，file: {}，chunk: {}/{}",
                sessionId, request.getFilename(), request.getChunkIndex() + 1, request.getTotalChunks());

        SshConnection connection = getConnectionOrNotify(sessionId);
        if (connection == null) return;

        try {
            sftpService.handleSftpUploadChunk(
                    createSessionAdapter(sessionId),
                    connection,
                    request.getPath(),
                    request.getFilename(),
                    request.getChunkIndex(),
                    request.getTotalChunks(),
                    request.getContent()
            );
        } catch (Exception e) {
            log.error("处理 SFTP 上传分片异常，session {}: {}", sessionId, e.getMessage(), e);
            sessionManager.sendErrorMessage(sessionId, "SFTP 上传错误: " + e.getMessage());
        }
    }

    /**
     * 获取 SSH 连接，如果不存在则通知前端错误信息。
     *
     * @param sessionId 会话 ID
     * @return SshConnection 实例或 null
     */
    private SshConnection getConnectionOrNotify(String sessionId) {
        SshConnection connection = sessionManager.getConnection(sessionId);
        if (connection == null) {
            log.warn("未找到 SSH 连接，session: {}", sessionId);
            sessionManager.sendErrorMessage(sessionId, "SSH 连接尚未建立");
        }
        return connection;
    }

    /**
     * 创建 WebSocket 会话适配器。
     *
     * @param sessionId 会话 ID
     * @return StompWebSocketSessionAdapter 实例
     */
    private StompWebSocketSessionAdapter createSessionAdapter(String sessionId) {
        return new StompWebSocketSessionAdapter(sessionId, messagingTemplate);
    }
}
