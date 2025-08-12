package com.fufu.terminal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.StompSessionManager;
import com.fufu.terminal.service.StreamingFileService;
import com.fufu.terminal.security.TokenVault;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.fufu.terminal.utils.FluxInputStream;

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
     * @param sessionId STOMP会话ID
     * @param paths 文件/目录路径列表，JSON数组格式
     * @param request HTTP请求对象
     * @return 流式响应
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFiles(
            @RequestParam String sessionId,
            @RequestParam String paths,
            HttpServletRequest request) {

        try {
            // 验证会话ID和SSH连接
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("下载请求未找到SSH连接，sessionId: {}", sessionId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // 解析路径列表
            List<String> pathList = objectMapper.readValue(paths,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

            if (pathList == null || pathList.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // 创建流式下载
            StreamingFileService.DownloadResult downloadResult =
                streamingFileService.createDownloadStream(connection, pathList, request.getRemoteAddr());

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            
            // 正确处理包含Unicode字符的文件名
            String filename = downloadResult.getFilename();
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
            
            // 使用RFC 5987标准的filename*参数支持Unicode文件名
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename);

            if (downloadResult.getContentLength() > 0) {
                headers.setContentLength(downloadResult.getContentLength());
            }

            log.info("开始流式下载，文件: {}, 大小: {} bytes, 客户端: {}, Content-Type: {}",
                downloadResult.getFilename(), downloadResult.getContentLength(), request.getRemoteAddr(),
                MediaType.APPLICATION_OCTET_STREAM);
                
            // 添加一些调试信息
            log.debug("响应头 Content-Disposition: {}", headers.get(HttpHeaders.CONTENT_DISPOSITION));
            log.debug("响应头 Content-Type: {}", headers.get(HttpHeaders.CONTENT_TYPE));
            log.debug("响应头 Content-Length: {}", headers.get(HttpHeaders.CONTENT_LENGTH));

            // 将Flux转换为Resource
            InputStreamResource resource = new InputStreamResource(
                new FluxInputStream(downloadResult.getDataStream())
            );

            return ResponseEntity.ok()
                .headers(headers)
                .body(resource);

        } catch (Exception e) {
            log.error("流式下载失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 分块上传文件到SFTP服务器
     * 支持多文件并发上传，自动进度报告
     *
     * @param sessionId STOMP会话ID
     * @param remotePath 远程目录路径
     * @param files 上传的文件列表
     * @param request HTTP请求对象
     * @return 上传结果
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<String>> uploadFiles(
            @RequestParam String sessionId,
            @RequestParam String remotePath,
            @RequestParam("files") List<MultipartFile> files,
            HttpServletRequest request) {

        return Mono.fromCallable(() -> {
            try {
                // 验证会话ID和SSH连接
                SshConnection connection = sessionManager.getConnection(sessionId);
                if (connection == null) {
                    log.warn("上传请求未找到SSH连接，sessionId: {}", sessionId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("SSH connection not found");
                }

                if (files == null || files.isEmpty()) {
                    return ResponseEntity.badRequest().body("No files provided");
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
     * @param sessionId STOMP会话ID
     * @param uploadId 上传ID
     * @return 取消结果
     */
    @PostMapping("/upload/{uploadId}/cancel")
    public ResponseEntity<String> cancelUpload(
            @RequestParam String sessionId,
            @PathVariable String uploadId) {

        try {
            // 验证会话ID和SSH连接
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("取消上传请求未找到SSH连接，sessionId: {}", sessionId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("SSH connection not found");
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
     * @param sessionId STOMP会话ID
     * @param uploadId 上传ID
     * @return 进度信息
     */
    @GetMapping("/upload/{uploadId}/progress")
    public ResponseEntity<String> getUploadProgress(
            @RequestParam String sessionId,
            @PathVariable String uploadId) {

        try {
            // 验证会话ID和SSH连接
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("获取上传进度请求未找到SSH连接，sessionId: {}", sessionId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("SSH connection not found");
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

}
