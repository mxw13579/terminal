package com.fufu.terminal.controller;

import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.StompSessionManager;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * SillyTavern数据管理专用控制器
 * 提供数据导出的简单下载功能
 */
@Slf4j
@RestController
@RequestMapping("/api/sillytavern")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SillyTavernDataController {
    
    private final StompSessionManager sessionManager;
    
    /**
     * 简单直接的文件下载
     * 直接将远程文件内容返回给浏览器
     */
    @GetMapping("/download-stream/{timestamp}")
    public void downloadExportFile(
            @PathVariable String timestamp,
            @RequestParam String sessionId,
            HttpServletResponse response) {
        
        log.info("收到下载请求: timestamp={}, sessionId={}", timestamp, sessionId);
        
        SshConnection connection = sessionManager.getConnection(sessionId);
        if (connection == null) {
            log.warn("未找到SSH连接，sessionId: {}，尝试获取所有连接", sessionId);
            
            // 如果找不到指定的连接，尝试使用任何可用的连接（临时解决方案）
            var allConnections = sessionManager.getAllConnections();
            if (!allConnections.isEmpty()) {
                connection = allConnections.values().iterator().next();
                log.info("使用可用连接，实际sessionId: {}", allConnections.keySet().iterator().next());
            } else {
                log.error("没有任何可用的SSH连接");
                response.setStatus(HttpStatus.NOT_FOUND.value());
                try {
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"没有可用的SSH连接\"}");
                } catch (Exception e) {
                    log.error("写入错误响应失败", e);
                }
                return;
            }
        }
        
        String remoteFilePath = String.format("/tmp/sillytavern_export_%s.tar.gz", timestamp);
        String filename = String.format("sillytavern_data_sillytavern_%s.tar.gz", timestamp);
        
        try {
            log.info("开始下载文件: {} 使用连接: {}", remoteFilePath, sessionId);
            
            // 设置响应头
            response.setContentType("application/gzip");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"" + filename + "\"");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            
            // 直接通过SSH连接读取远程文件并写入响应
            downloadFileViaSSH(connection, remoteFilePath, response);
            
            log.info("文件下载完成: {}", filename);
            
        } catch (Exception e) {
            log.error("下载文件失败: {}", remoteFilePath, e);
            
            // 只有在响应还没有开始写入的情况下才设置错误状态
            if (!response.isCommitted()) {
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.setContentType("application/json");
                try {
                    response.getWriter().write("{\"error\":\"下载失败: " + e.getMessage() + "\"}");
                } catch (IOException ioException) {
                    log.error("写入错误响应失败", ioException);
                }
            }
        }
    }
    
    /**
     * 通过SSH连接下载远程文件
     */
    private void downloadFileViaSSH(SshConnection connection, String remoteFilePath, 
                                   HttpServletResponse response) throws Exception {
        
        // 首先检查远程文件是否存在
        try {
            var sftpChannel = connection.getOrCreateSftpChannel();
            var attrs = sftpChannel.stat(remoteFilePath);
            if (attrs == null) {
                throw new Exception("远程文件不存在: " + remoteFilePath);
            }
            
            long fileSize = attrs.getSize();
            log.info("远程文件大小: {} bytes", fileSize);
            
            // 设置Content-Length头
            response.setContentLengthLong(fileSize);
            
            // 直接从SFTP下载文件到响应流
            try (var outputStream = response.getOutputStream();
                 var inputStream = sftpChannel.get(remoteFilePath)) {
                
                // 使用缓冲区进行流式传输
                byte[] buffer = new byte[8192];
                long totalBytes = 0;
                int bytesRead;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                    
                    // 每传输1MB输出一次进度日志
                    if (totalBytes % (1024 * 1024) == 0) {
                        log.debug("已传输: {} MB / {} MB", 
                                totalBytes / (1024 * 1024), 
                                fileSize / (1024 * 1024));
                    }
                }
                
                outputStream.flush();
                log.info("通过SFTP成功下载文件: {}, 总大小: {} bytes", remoteFilePath, totalBytes);
            }
        } catch (Exception e) {
            log.error("SFTP下载失败: {}", remoteFilePath, e);
            throw e;
        }
        // 注意：不要关闭sftpChannel，因为它是连接池管理的
    }
}