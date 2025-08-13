package com.fufu.terminal.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.model.SshConnection;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 提供真正的流式文件传输服务。
 * 该服务支持将输入流直接传输到SFTP服务器，无需在本地创建临时文件或将整个文件缓存在内存中。
 * 它还包括并发控制、上传进度跟踪、流量限制和取消上传等功能。
 *
 * @author AI Assistant
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrueStreamingFileService {

    private final ObjectMapper objectMapper;
    private final StompSessionManager sessionManager;

    @Value("${file.transfer.max-file-size:2147483648}") // 2GB
    private long maxFileSize;

    @Value("${file.transfer.chunk-size:262144}") // 256KB
    private int chunkSize;

    @Value("${file.transfer.max-concurrent:3}")
    private int maxConcurrentTransfers;

    @Value("${file.transfer.throttle-bytes-per-second:10485760}") // 10MB/s
    private long throttleBytesPerSecond;

    // 跟踪活跃的上传任务
    private final Map<String, StreamingProgress> activeUploads = new ConcurrentHashMap<>();

    // 用于并发控制的信号量
    private Semaphore uploadSemaphore;
    // 用于执行上传任务的线程池
    private ExecutorService uploadExecutor;

    @PostConstruct
    private void initializeResources() {
        this.uploadSemaphore = new Semaphore(maxConcurrentTransfers);
        this.uploadExecutor = Executors.newFixedThreadPool(maxConcurrentTransfers, r -> {
            Thread t = new Thread(r, "streaming-upload-worker");
            t.setDaemon(true);
            return t;
        });

        log.info("TrueStreamingFileService 初始化完成:");
        log.info("  - 最大并发传输数: {}", maxConcurrentTransfers);
        log.info("  - 单文件大小限制: {} MB", String.format("%.1f", maxFileSize / (1024.0 * 1024.0)));
        log.info("  - 块大小: {} KB", chunkSize / 1024);
        log.info("  - 限速: {} MB/s", String.format("%.1f", throttleBytesPerSecond / (1024.0 * 1024.0)));
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
     * 发起一个直接流式上传任务，并返回一个在任务完成后解析的Future。
     * <p>
     * 此方法将实际的文件传输任务提交到后台线程池执行，但返回一个CompletableFuture，
     * 调用者（如Controller）必须等待此Future完成，以确保HTTP请求-响应周期的完整性。
     *
     * @param connection    SSH连接对象
     * @param sessionId     与客户端关联的会话ID，用于发送进度通知
     * @param remotePath    文件在远程服务器上的目标目录
     * @param filename      要保存的文件名
     * @param inputStream   包含文件数据源的输入流
     * @param contentLength 文件的总大小（如果未知，则为-1）
     * @param clientIp      发起上传的客户端IP地址
     * @return 一个CompletableFuture，它将在上传成功时完成并返回uploadId，在失败时完成并抛出异常。
     * @throws IllegalStateException 如果服务器繁忙（达到最大并发数）
     * @throws IllegalArgumentException 如果文件大小超出限制
     */
    public CompletableFuture<String> directStreamUpload(SshConnection connection, String sessionId,
                                                        String remotePath, String filename,
                                                        InputStream inputStream, long contentLength,
                                                        String clientIp) {
        if (!uploadSemaphore.tryAcquire()) {
            throw new IllegalStateException("服务器繁忙，并发上传任务已达上限，请稍后重试");
        }

        if (contentLength > 0 && contentLength > maxFileSize) {
            uploadSemaphore.release();
            throw new IllegalArgumentException(String.format(
                    "文件 '%s' 大小 %.1f MB 超出限制 %.1f MB",
                    filename, contentLength / (1024.0 * 1024.0), maxFileSize / (1024.0 * 1024.0)
            ));
        }

        String uploadId = UUID.randomUUID().toString();
        StreamingProgress progress = new StreamingProgress(uploadId, sessionId, filename, clientIp);
        progress.setTotalBytes(contentLength);
        activeUploads.put(uploadId, progress);

        CompletableFuture<String> uploadFuture = new CompletableFuture<>();

        uploadExecutor.submit(() -> {
            try {
                processDirectStreamUpload(connection, progress, remotePath, filename, inputStream);
                // 只有在没有异常的情况下，才算成功
                if ("completed".equals(progress.getStatus())) {
                    uploadFuture.complete(uploadId);
                } else {
                    // 对于已处理的取消或失败情况，也视为异常完成
                    uploadFuture.completeExceptionally(new RuntimeException(
                            progress.getErrorMessage() != null ? progress.getErrorMessage() : "Upload did not complete successfully. Status: " + progress.getStatus()
                    ));
                }
            } catch (Exception e) {
                // 捕获未预料的异常
                uploadFuture.completeExceptionally(e);
            } finally {
                activeUploads.remove(uploadId);
                uploadSemaphore.release();
            }
        });

        log.info("已接受新的上传任务 [ID: {}], 文件: {}, 目标路径: {}", uploadId, filename, remotePath);
        return uploadFuture;
    }

    /**
     * 处理直接流式上传的核心逻辑。
     * 此方法在后台线程中执行。
     */
    private void processDirectStreamUpload(SshConnection connection, StreamingProgress progress,
                                           String remotePath, String filename, InputStream inputStream) {
        String fullRemotePath = Paths.get(remotePath, filename).toString().replace('\\', '/');
        String tempRemotePath = fullRemotePath + ".tmp";

        progress.setStatus("uploading");
        progress.setRemotePath(fullRemotePath);
        log.info("开始流式上传 [ID: {}]: {} -> {}", progress.getUploadId(), filename, fullRemotePath);

        ChannelSftp sftpChannel = null;
        try {
            sftpChannel = connection.getOrCreateSftpChannel();

            try (OutputStream sftpOutputStream = sftpChannel.put(tempRemotePath)) {
                byte[] buffer = new byte[chunkSize];
                long lastReportTime = System.currentTimeMillis();
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    if (progress.isCancelled()) {
                        progress.setStatus("cancelled");
                        progress.setErrorMessage(String.format("文件 '%s' 的上传已被取消", filename));
                        log.info("上传被用户取消 [ID: {}]", progress.getUploadId());
                        cleanupTemporaryFile(sftpChannel, tempRemotePath);
                        // 注释掉STOMP通知，避免与前端新的流式上传机制冲突
                        // sendUploadNotification(progress.getSessionId(), "upload_cancelled", progress.getErrorMessage(), fullRemotePath);
                        return;
                    }

                    sftpOutputStream.write(buffer, 0, bytesRead);
                    long newTotal = progress.getTransferredBytes().addAndGet(bytesRead);

                    if (maxFileSize > 0 && newTotal > maxFileSize) {
                        throw new IOException(String.format(
                                "文件大小超出限制，已传输 %.1f MB，限制 %.1f MB",
                                newTotal / (1024.0 * 1024.0), maxFileSize / (1024.0 * 1024.0)
                        ));
                    }

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastReportTime > 1000) {
                        sendProgressUpdate(progress);
                        lastReportTime = currentTime;
                    }

                    throttle(progress);
                }
                sftpOutputStream.flush();
            }

            sftpChannel.rename(tempRemotePath, fullRemotePath);

            progress.setStatus("completed");
            long duration = System.currentTimeMillis() - progress.getStartTime();
            logUploadCompletion(progress, duration);
            // 注释掉STOMP通知，避免与前端新的流式上传机制冲突
            // sendUploadNotification(progress.getSessionId(), "upload_completed", String.format("文件 '%s' 上传成功", filename), fullRemotePath);

        } catch (Exception e) {
            progress.setStatus("failed");
            progress.setErrorMessage(e.getMessage());
            log.error("流式上传失败 [ID: {}]: {}", progress.getUploadId(), e.getMessage(), e);

            if (sftpChannel != null && sftpChannel.isConnected()) {
                cleanupTemporaryFile(sftpChannel, tempRemotePath);
            } else {
                cleanupTemporaryFile(connection, tempRemotePath);
            }

            // 注释掉STOMP通知，避免与前端新的流式上传机制冲突
            // sendUploadNotification(progress.getSessionId(), "upload_failed", "上传失败: " + e.getMessage(), progress.getRemotePath());
        }
    }

    /**
     * 获取指定ID的上传任务的当前进度。
     *
     * @param uploadId 上传任务的唯一ID
     * @return 包含进度信息的JSON字符串，如果任务不存在则返回null
     */
    public String getUploadProgress(String uploadId) {
        try {
            StreamingProgress progress = activeUploads.get(uploadId);
            if (progress == null) {
                return null;
            }

            long transferred = progress.getTransferredBytes().get();
            long total = progress.getTotalBytes();
            Double percentage = (total > 0) ? (double) transferred / total * 100 : null;

            long duration = System.currentTimeMillis() - progress.getStartTime();
            long speed = (duration > 500) ? (transferred * 1000 / duration) : 0;

            UploadProgressDto progressDto = new UploadProgressDto(
                    uploadId, progress.getFilename(), progress.getStatus(),
                    transferred, total, progress.getRemotePath(),
                    (percentage != null) ? Math.round(percentage * 100.0) / 100.0 : null,
                    speed, formatSpeed(speed), progress.getErrorMessage()
            );

            return objectMapper.writeValueAsString(progressDto);
        } catch (Exception e) {
            log.error("序列化上传进度信息失败 [ID: {}]: {}", uploadId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 请求取消一个正在进行的上传任务。
     *
     * @param uploadId 要取消的上传任务的ID
     * @return 如果任务存在并被成功标记为取消，则返回true；否则返回false。
     */
    public boolean cancelUpload(String uploadId) {
        StreamingProgress progress = activeUploads.get(uploadId);
        if (progress != null && !progress.isCancelled()) {
            progress.setCancelled(true);
            log.info("上传任务已被标记为取消 [ID: {}]", uploadId);
            return true;
        }
        return false;
    }

    private void throttle(StreamingProgress progress) throws InterruptedException {
        if (throttleBytesPerSecond <= 0) return;
        long elapsedTime = System.currentTimeMillis() - progress.getStartTime();
        if (elapsedTime == 0) return;
        long expectedBytes = (elapsedTime * throttleBytesPerSecond) / 1000;
        long actualBytes = progress.getTransferredBytes().get();
        if (actualBytes > expectedBytes) {
            long sleepTime = ((actualBytes - expectedBytes) * 1000) / throttleBytesPerSecond;
            if (sleepTime > 10) {
                Thread.sleep(sleepTime);
            }
        }
    }

    private void cleanupTemporaryFile(SshConnection connection, String tempRemotePath) {
        try {
            ChannelSftp sftpChannel = connection.getOrCreateSftpChannel();
            cleanupTemporaryFile(sftpChannel, tempRemotePath);
        } catch (Exception e) {
            log.warn("获取SFTP通道以清理临时文件 {} 时失败: {}", tempRemotePath, e.getMessage());
        }
    }

    private void cleanupTemporaryFile(ChannelSftp sftpChannel, String tempRemotePath) {
        try {
            sftpChannel.rm(tempRemotePath);
            log.info("已清理临时上传文件: {}", tempRemotePath);
        } catch (SftpException e) {
            if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                log.warn("清理临时文件 {} 失败: {}", tempRemotePath, e.getMessage());
            }
        }
    }

    private void logUploadCompletion(StreamingProgress progress, long duration) {
        long totalBytes = progress.getTransferredBytes().get();
        double speedBytesPerSec = duration > 0 ? (totalBytes * 1000.0 / duration) : 0;
        double totalMB = totalBytes / (1024.0 * 1024.0);
        double speedMBPerSec = speedBytesPerSec / (1024.0 * 1024.0);
        log.info("流式上传完成 [ID: {}]: {}, 大小: {:.2f} MB, 耗时: {}ms, 平均速度: {:.2f} MB/s",
                progress.getUploadId(), progress.getRemotePath(), totalMB, duration, speedMBPerSec);
    }

    private void sendProgressUpdate(StreamingProgress progress) {
        // 注释掉STOMP进度通知，新的流式上传通过HTTP XMLHttpRequest原生事件提供进度
        // 避免与前端新的流式上传机制冲突
        /*
        try {
            String progressJson = getUploadProgress(progress.getUploadId());
            if (progressJson != null) {
                sessionManager.sendToSession(progress.getSessionId(), "/queue/upload/progress", progressJson);
            }
        } catch (Exception e) {
            log.warn("发送进度更新失败 [ID: {}]: {}", progress.getUploadId(), e.getMessage());
        }
        */
    }

    private void sendUploadNotification(String sessionId, String type, String message, String path) {
        try {
            Map<String, Object> notification = Map.of(
                    "type", type, "message", message, "path", path, "timestamp", System.currentTimeMillis()
            );
            sessionManager.sendToSession(sessionId, "/queue/upload/notification", objectMapper.writeValueAsString(notification));
        } catch (Exception e) {
            log.warn("发送上传通知失败 [SessionID: {}]: {}", sessionId, e.getMessage());
        }
    }

    private String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 1024) return bytesPerSecond + " B/s";
        if (bytesPerSecond < 1024 * 1024) return String.format("%.1f KB/s", bytesPerSecond / 1024.0);
        return String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0));
    }

    @Data
    public static class StreamingProgress {
        private final String uploadId;
        private final long startTime;
        private final String sessionId;
        private final String filename;
        private final String clientIp;
        private final AtomicLong transferredBytes = new AtomicLong(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private String status = "pending";
        private long totalBytes = -1;
        private String remotePath;
        private String errorMessage;

        public StreamingProgress(String uploadId, String sessionId, String filename, String clientIp) {
            this.uploadId = uploadId;
            this.sessionId = sessionId;
            this.filename = filename;
            this.clientIp = clientIp;
            this.startTime = System.currentTimeMillis();
        }

        public boolean isCancelled() { return cancelled.get(); }
        public void setCancelled(boolean cancelled) { this.cancelled.set(cancelled); }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UploadProgressDto(
            String uploadId, String filename, String status,
            long transferredBytes, long totalBytes, String remotePath,
            Double percentage, Long speed, String speedFormatted, String error
    ) {}
}
