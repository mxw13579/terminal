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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
    /**
     * 临时测试端点：绕过会话验证，直接测试文件上传
     * 仅用于调试文件损坏问题
     */
    @PostMapping(value = "/test-upload",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> testUpload(
            @RequestParam String filename,
            HttpServletRequest request) {

        try {
            // 直接保存到临时文件，不经过SFTP
            String tempFile = "/tmp/test_" + filename;

            log.info("测试上传: 保存文件到 {}", tempFile);

            // 读取请求体并直接写入文件
            try (InputStream inputStream = request.getInputStream();
                 java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }

                log.info("测试上传完成: {} bytes 写入到 {}", totalBytes, tempFile);

                return ResponseEntity.ok(String.format("{\"message\":\"测试上传成功\",\"file\":\"%s\",\"size\":%d}", tempFile, totalBytes));
            }

        } catch (Exception e) {
            log.error("测试上传失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(String.format("{\"error\":\"%s\"}", e.getMessage()));
        }
    }

    /**
     * 测试端点：直接SFTP上传，绕过所有包装器
     */
    @PostMapping(value = "/test-sftp-upload",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> testSftpUpload(
            @RequestParam String sessionId,
            @RequestParam String filename,
            HttpServletRequest request) {

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("{\"error\":\"Invalid session ID\"}");
            }

            InputStream inputStream = request.getInputStream();

            // 直接使用SFTP上传，不使用任何包装器
            com.jcraft.jsch.ChannelSftp sftpChannel = connection.getOrCreateSftpChannel();
            String remotePath = "/root/test_" + filename;

            log.info("测试直接SFTP上传到: {}", remotePath);

            // 直接上传原始流
            sftpChannel.put(inputStream, remotePath);

            log.info("测试直接SFTP上传完成: {}", remotePath);

            return ResponseEntity.ok(String.format(
                "{\"message\":\"直接SFTP上传成功\",\"remotePath\":\"%s\"}",
                remotePath
            ));

        } catch (Exception e) {
            log.error("测试直接SFTP上传失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(String.format("{\"error\":\"%s\"}", e.getMessage()));
        }
    }
    @PostMapping(value = "/debug-hash",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> debugHash(HttpServletRequest request) {

        try {
            InputStream inputStream = request.getInputStream();
            java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                sha256.update(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            String hash = bytesToHex(sha256.digest());

            return ResponseEntity.ok(String.format(
                "{\"totalBytes\":%d,\"sha256\":\"%s\"}",
                totalBytes, hash
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(String.format("{\"error\":\"%s\"}", e.getMessage()));
        }
    }

    /**
     * 流式上传最终解决方案：使用Spring MVC的CompletableFuture异步支持。
     * 此方法会立即返回，但Servlet容器会保持连接开放，直到后台上传任务完成。
     * 这是处理长时间运行的请求上传的标准、正确方式。
     */
    @PostMapping(value = "/upload",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<Map<String, String>>> streamUpload(
            @RequestParam String sessionId,
            @RequestParam String remotePath,
            @RequestParam String filename,
            @RequestHeader(value = "Content-Length", required = false) Long contentLength,
            HttpServletRequest request) {
        log.info("异步上传请求: sessionId={}, remotePath={}, filename={}, contentLength={}",
                sessionId, remotePath, filename, contentLength);
        SshConnection connection = sessionManager.getConnection(sessionId);
        if (connection == null) {
            log.warn("异步上传未找到SSH连接, sessionId: {}", sessionId);
            // 对于异步方法，需要返回一个已完成的Future来立即响应错误
            return CompletableFuture.completedFuture(
                    createErrorResponseEntity(HttpStatus.UNAUTHORIZED, "Invalid session ID or session expired")
            );
        }
        if (sessionId.startsWith("temp_")) {
            log.error("安全拒绝: 检测到临时session ID, sessionId: {}, 来源IP: {}", sessionId, request.getRemoteAddr());
            return CompletableFuture.completedFuture(
                    createErrorResponseEntity(HttpStatus.FORBIDDEN, "Temporary session IDs are not allowed for security reasons")
            );
        }
        try {
            // 直接调用返回CompletableFuture的服务方法
            // Spring将管理请求的生命周期，确保InputStream在后台任务完成前保持有效
            return streamingService.directStreamUpload(
                    connection, sessionId, remotePath, filename,
                    request.getInputStream(),
                    contentLength != null ? contentLength : -1,
                    request.getRemoteAddr()
            ).thenApply(uploadId -> {
                // 异步任务成功完成时的回调
                log.info("异步上传成功完成, ID: {}, 文件: {}", uploadId, filename);
                Map<String, String> successResponse = Map.of(
                        "uploadId", uploadId,
                        "status", "completed"
                );
                return ResponseEntity.ok(successResponse);
            }).exceptionally(ex -> {
                // 异步任务执行过程中发生异常时的回调
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                log.error("异步上传失败, 文件: {}", filename, cause);
                return createErrorResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR, "Stream upload failed: " + cause.getMessage());
            });
        } catch (Exception e) {
            // 捕获在调用streamingService之前可能发生的同步异常 (例如 getInputStream 失败)
            log.error("启动异步上传时发生意外错误", e);
            return CompletableFuture.completedFuture(
                    createErrorResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred: " + e.getMessage())
            );
        }
    }
    // 辅助方法，用于创建统一的错误响应
    private ResponseEntity<Map<String, String>> createErrorResponseEntity(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    /**
     * 将错误信息写入已经建立的响应流
     */
    private void writeErrorToStream(OutputStream os, String message) throws IOException {
        String errorResponse = String.format("{\"error\":\"%s\"}", message.replace("\"", "\\\""));
        os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
    }
//
//    /**
//     * 为方法创建流式错误响应
//     */
//    private ResponseEntity<StreamingResponseBody> createErrorResponseEntity(HttpStatus status, String message) {
//        String escapedMessage = message.replace("\"", "\\\"");
//        String errorJson = String.format("{\"error\":\"%s\"}", escapedMessage);
//
//        StreamingResponseBody body = outputStream -> {
//            outputStream.write(errorJson.getBytes(StandardCharsets.UTF_8));
//            // 不要强制关闭，让Spring管理
//            // outputStream.close();
//        };
//
//        return ResponseEntity.status(status)
//                .contentType(MediaType.APPLICATION_JSON)
//                .body(body);
//    }

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

    /**
     * 将字节数组转换为十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
