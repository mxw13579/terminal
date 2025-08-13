package com.fufu.terminal.controller;

import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.StompSessionManager;
import com.fufu.terminal.service.TrueStreamingFileService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 真正的流式文件传输控制器 - 最终稳定版
 * 结合了所有调试步骤的最终成果，解决了所有已知问题。
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
     * 流式上传最终解决方案：使用StreamingResponseBody。
     * 此方法会立即返回HTTP 200 OK和响应头，与浏览器建立一个持久的响应流，
     * 防止浏览器因长时间等待而主动断开连接 (ECONNRESET)。
     */
    @PostMapping(value = "/upload",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> streamUpload(
            @RequestParam String sessionId,
            @RequestParam String remotePath,
            @RequestParam String filename,
            @RequestHeader(value = "Content-Length", required = false) Long contentLength,
            HttpServletRequest request) {

        SshConnection connection = sessionManager.getConnection(sessionId);
        if (connection == null) {
            log.warn("流式上传未找到SSH连接，sessionId: {}", sessionId);
            return createErrorResponseEntity(HttpStatus.NOT_FOUND, "SSH connection not found");
        }

        StreamingResponseBody responseBody = outputStream -> {
            String uploadId = null;
            try {
                CompletableFuture<String> uploadFuture = streamingService.directStreamUpload(
                        connection, sessionId, remotePath, filename,
                        request.getInputStream(),
                        contentLength != null ? contentLength : -1,
                        request.getRemoteAddr()
                );

                // 在Spring管理的后台线程中安全地等待结果
                uploadId = uploadFuture.get();

                log.info("StreamingResponseBody: 真正流式上传完成，ID: {}, 文件: {}", uploadId, filename);
                String successResponse = String.format("{\"uploadId\":\"%s\",\"status\":\"completed\"}", uploadId);
                outputStream.write(successResponse.getBytes(StandardCharsets.UTF_8));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 保持中断状态
                log.error("StreamingResponseBody: 上传任务被中断, uploadId: {}", uploadId, e);
                writeErrorToStream(outputStream, "Upload task was interrupted.");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.error("StreamingResponseBody: 上传任务执行失败, uploadId: {}", uploadId, cause);
                writeErrorToStream(outputStream, "Stream upload failed: " + cause.getMessage());
            } catch (Exception e) {
                log.error("StreamingResponseBody: 启动上传时发生意外错误", e);
                writeErrorToStream(outputStream, "An unexpected error occurred: " + e.getMessage());
            } finally {
                // 确保响应流被关闭
                outputStream.close();
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }

    /**
     * 将错误信息写入已经建立的响应流
     */
    private void writeErrorToStream(OutputStream os, String message) throws IOException {
        String errorResponse = String.format("{\"error\":\"%s\"}", message.replace("\"", "\\\""));
        os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 为方法创建流式错误响应
     */
    private ResponseEntity<StreamingResponseBody> createErrorResponseEntity(HttpStatus status, String message) {
        String escapedMessage = message.replace("\"", "\\\"");
        String errorJson = String.format("{\"error\":\"%s\"}", escapedMessage);

        StreamingResponseBody body = outputStream -> {
            outputStream.write(errorJson.getBytes(StandardCharsets.UTF_8));
            outputStream.close();
        };

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
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
                return ResponseEntity.status(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(String.format("{\"uploadId\":\"%s\",\"status\":\"completed_or_not_found\"}", uploadId));
            }
        } catch (Exception e) {
            log.error("获取上传进度失败: {}", e.getMessage());
            return createStringErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Progress query failed");
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
                return ResponseEntity.ok("{\"status\":\"cancellation_requested\"}");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"error\":\"Upload not found or already completed.\"}");
            }
        } catch (Exception e) {
            log.error("取消上传失败: {}", e.getMessage());
            return createStringErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Cancel failed");
        }
    }

    /**
     * 新增的辅助方法：为返回String的端点创建错误响应，以解决编译问题
     */
    private ResponseEntity<String> createStringErrorResponse(HttpStatus status, String message) {
        String escapedMessage = message.replace("\"", "\\\"");
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(String.format("{\"error\":\"%s\"}", escapedMessage));
    }
}
