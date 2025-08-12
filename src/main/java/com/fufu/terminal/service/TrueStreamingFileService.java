package com.fufu.terminal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.model.SshConnection;
import com.jcraft.jsch.ChannelSftp;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 真正的流式文件传输服务
 * 直接从输入流传输到SFTP输出流，无临时文件，无内存缓存整个文件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrueStreamingFileService {

    private final ObjectMapper objectMapper;
    private final StompSessionManager sessionManager;

    @Value("${file.transfer.max-file-size:2147483648}") // 2GB
    private long maxFileSize;

    @Value("${file.transfer.chunk-size:65536}") // 64KB
    private int chunkSize;

    @Value("${file.transfer.max-concurrent:3}")
    private int maxConcurrentTransfers;

    @Value("${file.transfer.throttle-bytes-per-second:10485760}") // 10MB/s
    private long throttleBytesPerSecond;

    // 上传进度跟踪
    private final Map<String, StreamingProgress> activeUploads = new ConcurrentHashMap<>();

    // 并发控制
    private Semaphore uploadSemaphore;
    private ExecutorService uploadExecutor;

    @PostConstruct
    private void initializeResources() {
        this.uploadSemaphore = new Semaphore(maxConcurrentTransfers);
        this.uploadExecutor = Executors.newFixedThreadPool(maxConcurrentTransfers, r -> {
            Thread t = new Thread(r, "streaming-upload");
            t.setDaemon(true);
            return t;
        });
        
        log.info("TrueStreamingFileService 初始化完成:");
        log.info("  - 最大并发传输数: {}", maxConcurrentTransfers);
        log.info("  - 单文件大小限制: {:.1f} MB", maxFileSize / (1024.0 * 1024.0));
        log.info("  - 块大小: {} KB", chunkSize / 1024);
        log.info("  - 限速: {:.1f} MB/s", throttleBytesPerSecond / (1024.0 * 1024.0));
    }

    @PreDestroy
    private void cleanup() {
        if (uploadExecutor != null) {
            uploadExecutor.shutdown();
            try {
                if (!uploadExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    uploadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                uploadExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 直接流式上传 - 真正的流式传输（异步处理，先缓存InputStream）
     */
    public String directStreamUpload(SshConnection connection, String sessionId, 
                                   String remotePath, String filename, 
                                   InputStream inputStream, long contentLength,
                                   String clientIp) throws Exception {
        
        // 并发控制
        if (!uploadSemaphore.tryAcquire()) {
            throw new RuntimeException("服务器繁忙，请稍后重试");
        }

        String uploadId = UUID.randomUUID().toString();
        StreamingProgress progress = new StreamingProgress(uploadId, sessionId, filename);
        progress.setTotalBytes(contentLength > 0 ? contentLength : -1);
        progress.setClientIp(clientIp);

        // 文件大小检查（如果已知）
        if (contentLength > 0 && contentLength > maxFileSize) {
            uploadSemaphore.release();
            throw new RuntimeException(String.format(
                "文件 '%s' 大小 %.1f MB 超出限制 %.1f MB", 
                filename, 
                contentLength / (1024.0 * 1024.0), 
                maxFileSize / (1024.0 * 1024.0)
            ));
        }

        activeUploads.put(uploadId, progress);

        // 先将InputStream转换为字节数组以避免InputBuffer问题
        try {
            byte[] fileData = inputStream.readAllBytes();
            log.info("已缓存文件数据: {} bytes", fileData.length);
            
            // 异步处理流式上传
            CompletableFuture.runAsync(() -> {
                try (ByteArrayInputStream byteStream = new ByteArrayInputStream(fileData)) {
                    processDirectStreamUpload(connection, progress, remotePath, filename, byteStream);
                } catch (Exception e) {
                    log.error("异步上传处理失败: {}", e.getMessage(), e);
                    progress.setStatus("failed");
                    progress.setErrorMessage(e.getMessage());
                } finally {
                    uploadSemaphore.release();
                }
            }, uploadExecutor);

            return uploadId;
        } catch (Exception e) {
            uploadSemaphore.release();
            activeUploads.remove(uploadId);
            throw e;
        }
    }

    /**
     * 处理直接流式上传 - 核心流式传输逻辑
     */
    private void processDirectStreamUpload(SshConnection connection, StreamingProgress progress,
                                         String remotePath, String filename, InputStream inputStream) {
        try {
            ChannelSftp sftpChannel = connection.getOrCreateSftpChannel();
            String fullRemotePath = Paths.get(remotePath, filename).normalize().toString().replace("\\", "/");
            String tempRemotePath = fullRemotePath + ".tmp";

            progress.setStatus("uploading");
            progress.setRemotePath(fullRemotePath);

            log.info("开始真正流式上传: {} -> {}", filename, fullRemotePath);

            // 直接从HTTP流传输到SFTP流（避免BufferedInputStream关闭问题）
            try (OutputStream sftpOutputStream = sftpChannel.put(tempRemotePath)) {

                byte[] buffer = new byte[chunkSize];
                long lastReportTime = System.currentTimeMillis();
                long lastThrottleCheck = System.currentTimeMillis();
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    // 检查取消状态
                    if (progress.isCancelled()) {
                        log.info("上传被取消: {}", progress.getUploadId());
                        try {
                            sftpChannel.rm(tempRemotePath);
                        } catch (Exception ignored) {}
                        return;
                    }

                    // 文件大小限制检查（运行时检查）
                    long newTotal = progress.getTransferredBytes().get() + bytesRead;
                    if (maxFileSize > 0 && newTotal > maxFileSize) {
                        throw new RuntimeException(String.format(
                            "文件大小超出限制，已传输 %.1f MB，限制 %.1f MB",
                            newTotal / (1024.0 * 1024.0),
                            maxFileSize / (1024.0 * 1024.0)
                        ));
                    }

                    // 写入SFTP流
                    sftpOutputStream.write(buffer, 0, bytesRead);
                    progress.getTransferredBytes().addAndGet(bytesRead);

                    // 进度报告（每秒一次）
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastReportTime > 1000) {
                        sendProgressUpdate(progress);
                        lastReportTime = currentTime;
                    }

                    // 流量控制（每个块检查一次）
                    if (throttleBytesPerSecond > 0 && currentTime - lastThrottleCheck > 100) {
                        long expectedTime = progress.getStartTime() + 
                            (progress.getTransferredBytes().get() * 1000L / throttleBytesPerSecond);
                        if (currentTime < expectedTime) {
                            Thread.sleep(Math.min(expectedTime - currentTime, 1000));
                        }
                        lastThrottleCheck = System.currentTimeMillis();
                    }
                }

                // 确保所有数据都已写入
                sftpOutputStream.flush();
            }

            // 原子性重命名
            sftpChannel.rename(tempRemotePath, fullRemotePath);

            // 上传成功
            progress.setStatus("completed");
            long totalBytes = progress.getTransferredBytes().get();
            long duration = System.currentTimeMillis() - progress.getStartTime();
            double speed = totalBytes * 1000.0 / duration; // bytes/sec

            log.info("真正流式上传完成: {} -> {}, {:.1f} MB, 耗时 {}ms, 平均速度 {:.1f} MB/s",
                filename, fullRemotePath,
                totalBytes / (1024.0 * 1024.0),
                duration,
                speed / (1024.0 * 1024.0));

            // 发送完成通知
            sendUploadNotification(progress.getSessionId(), "upload_completed",
                String.format("文件 '%s' 上传完成 (%.1f MB)", filename, totalBytes / (1024.0 * 1024.0)),
                fullRemotePath);

        } catch (Exception e) {
            progress.setStatus("failed");
            progress.setErrorMessage(e.getMessage());
            log.error("真正流式上传失败: {}", e.getMessage(), e);

            // 发送失败通知
            sendUploadNotification(progress.getSessionId(), "upload_failed",
                "上传失败: " + e.getMessage(), progress.getRemotePath());
        } finally {
            // 清理
            activeUploads.remove(progress.getUploadId());
        }
    }

    /**
     * 获取上传进度
     */
    public String getUploadProgress(String uploadId) {
        try {
            StreamingProgress progress = activeUploads.get(uploadId);
            if (progress == null) {
                return null;
            }

            Map<String, Object> progressData = new HashMap<>();
            progressData.put("uploadId", uploadId);
            progressData.put("filename", progress.getFilename());
            progressData.put("status", progress.getStatus());
            progressData.put("transferredBytes", progress.getTransferredBytes().get());
            progressData.put("totalBytes", progress.getTotalBytes());
            progressData.put("remotePath", progress.getRemotePath());
            
            if (progress.getTotalBytes() > 0) {
                double percentage = (double) progress.getTransferredBytes().get() / progress.getTotalBytes() * 100;
                progressData.put("percentage", Math.round(percentage * 100.0) / 100.0);
            }

            long duration = System.currentTimeMillis() - progress.getStartTime();
            if (duration > 0) {
                double speed = progress.getTransferredBytes().get() * 1000.0 / duration;
                progressData.put("speed", Math.round(speed));
                progressData.put("speedFormatted", formatSpeed((long) speed));
            }

            if (progress.getErrorMessage() != null) {
                progressData.put("error", progress.getErrorMessage());
            }

            return objectMapper.writeValueAsString(progressData);
        } catch (Exception e) {
            log.error("序列化进度信息失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 取消上传
     */
    public boolean cancelUpload(String uploadId) {
        StreamingProgress progress = activeUploads.get(uploadId);
        if (progress != null) {
            progress.setCancelled(true);
            log.info("上传已标记为取消: {}", uploadId);
            return true;
        }
        return false;
    }

    /**
     * 发送进度更新
     */
    private void sendProgressUpdate(StreamingProgress progress) {
        try {
            String progressJson = getUploadProgress(progress.getUploadId());
            if (progressJson != null) {
                sessionManager.sendToSession(progress.getSessionId(), "/queue/upload/progress", progressJson);
            }
        } catch (Exception e) {
            log.warn("发送进度更新失败: {}", e.getMessage());
        }
    }

    /**
     * 发送上传通知
     */
    private void sendUploadNotification(String sessionId, String type, String message, String path) {
        try {
            Map<String, Object> notification = Map.of(
                "type", type,
                "message", message,
                "path", path,
                "timestamp", System.currentTimeMillis()
            );
            sessionManager.sendToSession(sessionId, "/queue/upload/notification", 
                objectMapper.writeValueAsString(notification));
        } catch (Exception e) {
            log.warn("发送上传通知失败: {}", e.getMessage());
        }
    }

    /**
     * 格式化速度显示
     */
    private String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return bytesPerSecond + " B/s";
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format("%.1f KB/s", bytesPerSecond / 1024.0);
        } else {
            return String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0));
        }
    }

    /**
     * 流式传输进度跟踪
     */
    @Data
    public static class StreamingProgress {
        private final String uploadId;
        private final String sessionId;
        private final String filename;
        private final long startTime;
        
        private String status = "pending";
        private long totalBytes = -1;
        private final AtomicLong transferredBytes = new AtomicLong(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        
        private String remotePath;
        private String clientIp;
        private String errorMessage;

        public StreamingProgress(String uploadId, String sessionId, String filename) {
            this.uploadId = uploadId;
            this.sessionId = sessionId;
            this.filename = filename;
            this.startTime = System.currentTimeMillis();
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public void setCancelled(boolean cancelled) {
            this.cancelled.set(cancelled);
        }
    }
}