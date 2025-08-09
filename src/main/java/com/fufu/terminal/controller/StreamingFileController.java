package com.fufu.terminal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.StompSessionManager;
import com.fufu.terminal.service.StreamingFileService;
import com.fufu.terminal.security.TokenVault;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * HTTP流式文件传输控制器
 * 提供高性能的文件上传和下载功能，替代基于WebSocket的base64传输
 *
 * @author lizelin
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StreamingFileController {

    private final StreamingFileService streamingFileService;
    private final StompSessionManager sessionManager;
    private final TokenVault tokenVault;
    private final ObjectMapper objectMapper;

    /**
     * 流式下载SFTP文件或目录
     * 支持单文件、目录压缩、多文件打包下载
     *
     * @param token 认证令牌
     * @param paths 文件/目录路径列表，JSON数组格式
     * @param request HTTP请求对象
     * @return 流式响应
     */
    @GetMapping("/download")
    public ResponseEntity<Flux<byte[]>> downloadFiles(
            @RequestParam String token,
            @RequestParam String paths,
            HttpServletRequest request) {

        try {
            // 验证令牌
            if (!tokenVault.isTokenValid(token)) {
                log.warn("下载请求使用无效令牌: {}...", token.substring(0, 8));
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // 解析路径列表
            List<String> pathList = objectMapper.readValue(paths,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

            if (pathList == null || pathList.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // 获取SSH连接
            SshConnection connection = getConnectionFromToken(token);
            if (connection == null) {
                log.warn("下载请求未找到SSH连接，token: {}...", token.substring(0, 8));
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // 创建流式下载
            StreamingFileService.DownloadResult downloadResult =
                streamingFileService.createDownloadStream(connection, pathList, request.getRemoteAddr());

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + downloadResult.getFilename() + "\"");

            if (downloadResult.getContentLength() > 0) {
                headers.setContentLength(downloadResult.getContentLength());
            }

            log.info("开始流式下载，文件: {}, 大小: {} bytes, 客户端: {}",
                downloadResult.getFilename(), downloadResult.getContentLength(), request.getRemoteAddr());

            return ResponseEntity.ok()
                .headers(headers)
                .body(downloadResult.getDataStream());

        } catch (Exception e) {
            log.error("流式下载失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 分块上传文件到SFTP服务器
     * 支持多文件并发上传，自动进度报告
     *
     * @param token 认证令牌
     * @param remotePath 远程目录路径
     * @param files 上传的文件列表
     * @param request HTTP请求对象
     * @return 上传结果
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<String>> uploadFiles(
            @RequestParam String token,
            @RequestParam String remotePath,
            @RequestParam("files") List<MultipartFile> files,
            HttpServletRequest request) {

        return Mono.fromCallable(() -> {
            try {
                // 验证令牌
                if (!tokenVault.isTokenValid(token)) {
                    log.warn("上传请求使用无效令牌: {}...", token.substring(0, 8));
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
                }

                if (files == null || files.isEmpty()) {
                    return ResponseEntity.badRequest().body("No files provided");
                }

                // 获取SSH连接和会话ID
                SshConnection connection = getConnectionFromToken(token);
                String sessionId = extractSessionIdFromToken(token);
                if (connection == null) {
                    log.warn("上传请求未找到SSH连接，token: {}...", token.substring(0, 8));
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("SSH connection not found");
                }

                // 启动流式上传
                String uploadId = streamingFileService.startUpload(
                    connection, sessionId, remotePath, files, request.getRemoteAddr());

                log.info("启动流式上传，ID: {}, 文件数: {}, 目标: {}, 客户端: {}",
                    uploadId, files.size(), remotePath, request.getRemoteAddr());

                return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"uploadId\":\"" + uploadId + "\",\"status\":\"started\"}");

            } catch (Exception e) {
                log.error("启动流式上传失败: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Upload failed: " + e.getMessage());
            }
        });
    }

    /**
     * 取消正在进行的上传
     *
     * @param token 认证令牌
     * @param uploadId 上传ID
     * @return 取消结果
     */
    @PostMapping("/upload/{uploadId}/cancel")
    public ResponseEntity<String> cancelUpload(
            @RequestParam String token,
            @PathVariable String uploadId) {

        try {
            // 验证令牌
            if (!tokenVault.isTokenValid(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
            }

            boolean cancelled = streamingFileService.cancelUpload(uploadId);

            if (cancelled) {
                log.info("上传已取消，ID: {}", uploadId);
                return ResponseEntity.ok("{\"status\":\"cancelled\"}");
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("取消上传失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Cancel failed: " + e.getMessage());
        }
    }

    /**
     * 获取上传进度
     *
     * @param token 认证令牌
     * @param uploadId 上传ID
     * @return 进度信息
     */
    @GetMapping("/upload/{uploadId}/progress")
    public ResponseEntity<String> getUploadProgress(
            @RequestParam String token,
            @PathVariable String uploadId) {

        try {
            // 验证令牌
            if (!tokenVault.isTokenValid(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
            }

            String progressJson = streamingFileService.getUploadProgress(uploadId);

            if (progressJson != null) {
                return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(progressJson);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("获取上传进度失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Progress query failed: " + e.getMessage());
        }
    }

    /**
     * 从令牌获取对应的SSH连接
     * 由于TokenVault是一次性消费的，我们需要通过其他方式关联token和session
     *
     * @param token 认证令牌
     * @return SSH连接，如果未找到则返回null
     */
    private SshConnection getConnectionFromToken(String token) {
        // 目前的架构中，token在STOMP连接时被消费，无法直接映射到session
        // 作为临时解决方案，我们遍历所有活动连接寻找匹配的连接
        // 更好的解决方案是修改TokenVault支持多次验证而非一次性消费

        // 这里需要一个更好的设计，暂时返回第一个可用连接进行测试
        Map<String, SshConnection> allConnections = sessionManager.getAllConnections();
        if (!allConnections.isEmpty()) {
            // 返回第一个活动连接作为临时解决方案
            return allConnections.values().iterator().next();
        }

        return null;
    }

    /**
     * 从令牌提取会话ID
     * 临时实现：由于当前架构限制，使用活动连接的第一个sessionId
     *
     * @param token 认证令牌
     * @return 会话ID
     */
    private String extractSessionIdFromToken(String token) {
        Map<String, SshConnection> allConnections = sessionManager.getAllConnections();
        if (!allConnections.isEmpty()) {
            // 返回第一个活动会话ID作为临时解决方案
            return allConnections.keySet().iterator().next();
        }

        // 如果没有活动连接，生成一个临时ID
        return "temp_session_" + System.currentTimeMillis();
    }

    /**
     * 获取所有活动连接信息（用于调试）
     *
     * @return 活动连接映射
     */
    private Map<String, SshConnection> getAllConnections() {
        return sessionManager.getAllConnections();
    }
}
