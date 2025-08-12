package com.fufu.terminal.controller;

import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.StompSessionManager;
import com.fufu.terminal.service.TrueStreamingFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

/**
 * 真正的流式文件传输控制器
 * 直接从HTTP请求流传输到SFTP，无临时文件，无内存缓存整个文件
 */
@Slf4j
@RestController
@RequestMapping("/api/streaming")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TrueStreamingController {

    private final TrueStreamingFileService streamingService;
    private final StompSessionManager sessionManager;

    /**
     * 真正的流式上传 - 直接从HTTP流传输到SFTP流
     */
    @PostMapping(value = "/upload", 
                consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> streamUpload(
            @RequestParam String sessionId,
            @RequestParam String remotePath,
            @RequestParam String filename,
            @RequestHeader(value = "Content-Length", required = false) Long contentLength,
            HttpServletRequest request,
            HttpServletResponse response) {

        try {
            // 验证会话和连接
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("流式上传未找到SSH连接，sessionId: {}", sessionId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\":\"SSH connection not found\"}");
            }

            // 直接在控制器线程中处理流式传输（避免async InputBuffer问题）
            String uploadId = streamingService.directStreamUpload(
                connection, 
                sessionId,
                remotePath, 
                filename,
                request.getInputStream(),
                contentLength != null ? contentLength : -1,
                request.getRemoteAddr()
            );

            log.info("启动真正流式上传，ID: {}, 文件: {}, 大小: {} bytes, 客户端: {}",
                uploadId, filename, contentLength, request.getRemoteAddr());

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format("{\"uploadId\":\"%s\",\"status\":\"streaming\"}", uploadId));

        } catch (Exception e) {
            log.error("流式上传失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format("{\"error\":\"Stream upload failed: %s\"}", e.getMessage().replace("\"", "\\\"")));
        }
    }

    /**
     * 检查上传进度
     */
    @GetMapping("/upload/{uploadId}/progress")
    public ResponseEntity<String> getUploadProgress(
            @PathVariable String uploadId,
            @RequestParam String sessionId) {
        try {
            String progressJson = streamingService.getUploadProgress(uploadId);
            if (progressJson != null) {
                return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(progressJson);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取上传进度失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\":\"Progress query failed\"}");
        }
    }

    /**
     * 取消上传
     */
    @PostMapping("/upload/{uploadId}/cancel")
    public ResponseEntity<String> cancelUpload(
            @PathVariable String uploadId,
            @RequestParam String sessionId) {
        try {
            boolean cancelled = streamingService.cancelUpload(uploadId);
            if (cancelled) {
                return ResponseEntity.ok("{\"status\":\"cancelled\"}");
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("取消上传失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\":\"Cancel failed\"}");
        }
    }
}