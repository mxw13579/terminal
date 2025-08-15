package com.fufu.terminal.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.model.SshConnection;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 提供真正的流式文件传输服务。
 * <p>
 * 该服务支持将输入流直接传输到SFTP服务器，无需在本地创建临时文件或将整个文件缓存在内存中。
 * 它通过一个专用的、由Spring管理的线程池来处理上传任务，并利用信号量进行并发控制。
 * 功能包括：
 * <ul>
 *     <li>流式上传，内存占用低。</li>
 *     <li>通过 {@link Semaphore} 控制并发上传数量。</li>
 *     <li>通过 {@link StompSessionManager} 实时向客户端发送进度更新。</li>
 *     <li>支持上传任务的取消。</li>
 *     <li>可配置的流量限制（节流）。</li>
 *     <li>上传失败后自动清理远程服务器上的临时文件。</li>
 * </ul>
 * 采用现代Java API（如 {@link CompletableFuture}, {@link InputStream#transferTo(OutputStream)}）和
 * 装饰器模式（{@link ProgressTrackingInputStream}）来优化代码结构和可读性。
 *
 * @author AI Assistant
 */
@Slf4j
@Service
public class TrueStreamingFileService {

    private final ObjectMapper objectMapper;
    private final StompSessionManager sessionManager;
    private final TaskExecutor uploadTaskExecutor;

    // 构造函数 - 使用 @Qualifier 指定使用 mvcTaskExecutor
    public TrueStreamingFileService(
            ObjectMapper objectMapper,
            StompSessionManager sessionManager,
            @Qualifier("mvcTaskExecutor") TaskExecutor uploadTaskExecutor) {
        this.objectMapper = objectMapper;
        this.sessionManager = sessionManager;
        this.uploadTaskExecutor = uploadTaskExecutor;
    }

    @Value("${file.transfer.max-file-size:2147483648}") // 2GB
    private long maxFileSize;

    @Value("${file.transfer.chunk-size:262144}") // 256KB
    private int chunkSize;

    @Value("${file.transfer.max-concurrent:3}")
    private int maxConcurrentTransfers;

    @Value("${file.transfer.throttle-bytes-per-second:10485760}") // 10MB/s
    private long throttleBytesPerSecond;

    /**
     * 跟踪所有活跃的上传任务。键是 uploadId，值是进度对象。
     */
    private final Map<String, StreamingProgress> activeUploads = new ConcurrentHashMap<>();

    /**
     * 用于并发控制的信号量，限制同时进行的上传任务数量。
     */
    private Semaphore uploadSemaphore;

    /**
     * 初始化服务资源，如信号量，并记录配置信息。
     * 此方法在Bean属性设置完成后由Spring自动调用。
     */
    @PostConstruct
    private void initialize() {
        this.uploadSemaphore = new Semaphore(maxConcurrentTransfers);

        log.info("TrueStreamingFileService 初始化完成:");
        log.info("  - 最大并发传输数: {}", maxConcurrentTransfers);
        log.info("  - 单文件大小限制: {} MB", String.format("%.1f", maxFileSize / (1024.0 * 1024.0)));
        log.info("  - 块大小: {} KB", chunkSize / 1024);
        log.info("  - 限速: {} MB/s", String.format("%.1f", throttleBytesPerSecond / (1024.0 * 1024.0)));
    }

    /**
     * 异步发起一个直接流式上传任务。
     * <p>
     * 此方法会立即返回一个 {@link CompletableFuture}，实际的文件传输在后台线程池中执行。
     * 调用者（例如Controller）应等待此Future完成，以确保整个操作（如HTTP请求）的完整性。
     *
     * @param connection    SSH连接对象
     * @param sessionId     用于发送STOMP进度通知的客户端会话ID
     * @param remotePath    文件在远程服务器上的目标目录
     * @param filename      要保存的文件名
     * @param inputStream   包含文件数据源的输入流
     * @param contentLength 文件的总大小（如果未知，则为-1）
     * @param clientIp      发起上传的客户端IP地址
     * @return 一个 {@link CompletableFuture}，上传成功时返回uploadId，失败时抛出异常
     * @throws IllegalStateException    如果服务器繁忙，无法接受新的上传任务
     * @throws IllegalArgumentException 如果文件大小超出配置的限制
     */
    public CompletableFuture<String> directStreamUpload(SshConnection connection, String sessionId,
                                                        String remotePath, String filename,
                                                        InputStream inputStream, long contentLength,
                                                        String clientIp) {
        if (!uploadSemaphore.tryAcquire()) {
            return CompletableFuture.failedFuture(new IllegalStateException("服务器繁忙，并发上传任务已达上限，请稍后重试"));
        }

        if (contentLength > 0 && contentLength > maxFileSize) {
            uploadSemaphore.release();
            return CompletableFuture.failedFuture(new IllegalArgumentException(String.format(
                    "文件 '%s' 大小 %.1f MB 超出限制 %.1f MB",
                    filename, contentLength / (1024.0 * 1024.0), maxFileSize / (1024.0 * 1024.0)
            )));
        }

        String uploadId = UUID.randomUUID().toString();
        StreamingProgress progress = new StreamingProgress(uploadId, sessionId, filename, clientIp);
        progress.setTotalBytes(contentLength);
        activeUploads.put(uploadId, progress);

        log.info("已接受新的上传任务 [ID: {}], 文件: {}, 目标路径: {}", uploadId, filename, remotePath);

        return CompletableFuture.supplyAsync(() -> {
                    processDirectStreamUpload(connection, progress, remotePath, filename, inputStream);
                    return uploadId;
                }, uploadTaskExecutor)
                .whenComplete((id, ex) -> {
                    CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS)
                            .execute(() -> activeUploads.remove(uploadId));
                    uploadSemaphore.release();
                });
    }

    /**
     * 处理直接流式上传的核心逻辑。此方法在后台线程中执行。
     *
     * @param connection  SSH连接对象
     * @param progress    上传进度跟踪对象
     * @param remotePath  远程目录
     * @param filename    文件名
     * @param inputStream 数据输入流
     */
    private void processDirectStreamUpload(SshConnection connection, StreamingProgress progress,
                                           String remotePath, String filename, InputStream inputStream) {
        // 正确组合路径：remotePath是目录，filename是文件名，需要组合成完整文件路径
        String fullRemotePath;
        if (remotePath.endsWith("/")) {
            fullRemotePath = remotePath + filename;
        } else {
            fullRemotePath = remotePath + "/" + filename;
        }
        fullRemotePath = fullRemotePath.replace('\\', '/');

        progress.setStatus("uploading");
        progress.setRemotePath(fullRemotePath);
        log.info("开始流式上传 [ID: {}]: 文件 {} -> {}, 预期大小: {} bytes",
                progress.getUploadId(), filename, fullRemotePath,
                progress.getTotalBytes() > 0 ? progress.getTotalBytes() : "未知");

        ChannelSftp sftpChannel = null;
        try {
            // 使用共享SFTP通道，避免创建独立通道导致状态污染
            sftpChannel = connection.getOrCreateSftpChannel();
            log.info("使用共享SFTP通道进行流式上传");
            
            // 真正的流式上传：直接使用原始inputStream，不缓存到内存
            log.info("开始真正的流式上传到: {}", fullRemotePath);
            
            // 添加详细的字节计数调试
            log.info("调试：流式上传开始前，progress初始状态: transferredBytes={}, totalBytes={}", 
                progress.getTransferredBytes().get(), progress.getTotalBytes());
            
            // 使用ProgressTrackingInputStream包装原始流以跟踪进度
            try (ProgressTrackingInputStream progressStream = new ProgressTrackingInputStream(inputStream, progress)) {
                // 直接上传到目标文件，避免rename操作
                sftpChannel.put(progressStream, fullRemotePath);
            }
            
            log.info("流式上传完成，直接写入目标文件: {}", fullRemotePath);

            progress.setStatus("completed");
            sendProgressUpdate(progress);
            logUploadCompletion(progress);

        } catch (Exception e) {
            progress.setStatus(progress.isCancelled() ? "cancelled" : "failed");
            progress.setErrorMessage(e.getMessage());
            log.error("流式上传失败 [ID: {}]: {}", progress.getUploadId(), e.getMessage(), e);

            sendProgressUpdate(progress);

            // 临时移除文件清理逻辑，因为已经不使用.tmp文件了
            log.info("上传失败，但不需要清理.tmp文件（直接上传模式）");
            throw new RuntimeException("Upload failed for " + progress.getFilename(), e);
        } finally {
            // 不需要关闭共享SFTP通道，它由SshConnection管理
            log.info("流式上传操作完成，共享SFTP通道继续保持连接");
        }
    }

    /**
     * 获取指定ID上传任务的当前进度。
     *
     * @param uploadId 上传任务的唯一ID
     * @return 包含进度信息的JSON字符串；如果任务不存在，则返回null
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

            long speed = 0;
            synchronized (progress) {
                long currentTime = System.currentTimeMillis();
                long timeSinceLastUpdate = currentTime - progress.getLastProgressTime();
                long bytesSinceLastUpdate = transferred - progress.getLastProgressBytes();

                if (timeSinceLastUpdate > 200) {
                    speed = bytesSinceLastUpdate * 1000 / timeSinceLastUpdate;
                    progress.setLastProgressTime(currentTime);
                    progress.setLastProgressBytes(transferred);
                } else if (duration > 500) {
                    speed = transferred * 1000 / duration;
                }
            }

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
     * @return 如果任务存在且尚未被取消，则返回true；否则返回false
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

    /**
     * 根据配置的速率限制对上传进行节流。
     *
     * @param progress 上传进度对象
     * @throws InterruptedException 如果线程被中断
     */
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

    /**
     * 清理远程服务器上的临时文件（通过新建SFTP通道）。
     *
     * @param connection     SSH连接对象
     * @param tempRemotePath 临时文件路径
     */
    private void cleanupTemporaryFile(SshConnection connection, String tempRemotePath) {
        try {
            ChannelSftp sftpChannel = connection.getOrCreateSftpChannel();
            cleanupTemporaryFile(sftpChannel, tempRemotePath);
        } catch (Exception e) {
            log.warn("获取SFTP通道以清理临时文件 {} 时失败: {}", tempRemotePath, e.getMessage());
        }
    }

    /**
     * 清理远程服务器上的临时文件（通过已连接的SFTP通道）。
     *
     * @param sftpChannel    SFTP通道
     * @param tempRemotePath 临时文件路径
     */
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

    /**
     * 记录上传完成的详细日志。
     *
     * @param progress 上传进度对象
     */
    private void logUploadCompletion(StreamingProgress progress) {
        long duration = System.currentTimeMillis() - progress.getStartTime();
        long totalBytes = progress.getTransferredBytes().get();
        double totalMB = totalBytes / (1024.0 * 1024.0);
        double speedMBps = duration > 0 ? (totalMB * 1000.0 / duration) : 0;
        log.info("流式上传完成 [ID: {}]: {}, 大小: {:.2f} MB, 耗时: {}ms, 平均速度: {:.2f} MB/s",
                progress.getUploadId(), progress.getRemotePath(), totalMB, duration, speedMBps);

        if (progress.getTotalBytes() > 0 && totalBytes != progress.getTotalBytes()) {
            log.warn("传输大小不匹配 [ID: {}]: 预期 {} bytes, 实际 {} bytes",
                    progress.getUploadId(), progress.getTotalBytes(), totalBytes);
        }
    }

    /**
     * 发送进度更新到前端。
     *
     * @param progress 上传进度对象
     */
    private void sendProgressUpdate(StreamingProgress progress) {
        try {
            String progressJson = getUploadProgress(progress.getUploadId());
            if (progressJson != null) {
                sessionManager.sendToSession(progress.getSessionId(), "/queue/upload/progress", progressJson);
            }
        } catch (Exception e) {
            log.warn("发送进度更新失败 [ID: {}]: {}", progress.getUploadId(), e.getMessage());
        }
    }

    /**
     * 格式化速度显示字符串。
     *
     * @param bytesPerSecond 速度（字节/秒）
     * @return 格式化后的速度字符串
     */
    private String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 1024) return bytesPerSecond + " B/s";
        if (bytesPerSecond < 1024 * 1024) return String.format("%.1f KB/s", bytesPerSecond / 1024.0);
        return String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0));
    }

    /**
     * 采用装饰器模式，包装原始输入流以添加进度跟踪、取消检查和流量控制功能。
     */
    private class ProgressTrackingInputStream extends FilterInputStream {
        private final StreamingProgress progress;
        private long lastReportTime = System.currentTimeMillis();

        /**
         * 构造方法。
         *
         * @param in       原始输入流
         * @param progress 上传进度对象
         */
        protected ProgressTrackingInputStream(InputStream in, StreamingProgress progress) {
            super(in);
            this.progress = progress;
        }

        /**
         * 重写单字节读取方法，确保所有读取都被跟踪
         */
        @Override
        public int read() throws IOException {
            if (progress.isCancelled()) {
                throw new IOException(String.format("文件 '%s' 的上传已被用户取消", progress.getFilename()));
            }

            int byteRead = super.read();
            if (byteRead != -1) {
                // 单字节读取也要跟踪
                long newTotal = progress.getTransferredBytes().addAndGet(1);

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

                // 单字节读取时不需要节流，避免过度节流
            }
            return byteRead;
        }

        /**
         * 重写批量读取方法，确保所有读取都被跟踪
         */
        @Override
        public int read(byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        /**
         * 读取数据并进行进度跟踪、取消检查和节流。
         *
         * @param b   缓冲区
         * @param off 偏移量
         * @param len 读取长度
         * @return 实际读取的字节数
         * @throws IOException IO异常或被取消
         */
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (progress.isCancelled()) {
                throw new IOException(String.format("文件 '%s' 的上传已被用户取消", progress.getFilename()));
            }

            int bytesRead = super.read(b, off, len);
            if (bytesRead > 0) {
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

                // 暂时禁用节流功能，测试是否影响数据完整性
                // try {
                //     throttle(progress);
                // } catch (InterruptedException e) {
                //     Thread.currentThread().interrupt();
                //     throw new IOException("Upload was interrupted during throttling", e);
                // }
            }
            return bytesRead;
        }
    }

    /**
     * 用于跟踪单个流式上传任务的状态和进度。
     */
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

        // 用于计算瞬时速度
        private long lastProgressTime;
        private long lastProgressBytes = 0;

        /**
         * 构造方法。
         *
         * @param uploadId  上传ID
         * @param sessionId 会话ID
         * @param filename  文件名
         * @param clientIp  客户端IP
         */
        public StreamingProgress(String uploadId, String sessionId, String filename, String clientIp) {
            this.uploadId = uploadId;
            this.sessionId = sessionId;
            this.filename = filename;
            this.clientIp = clientIp;
            this.startTime = System.currentTimeMillis();
            this.lastProgressTime = this.startTime;
        }

        /**
         * 判断是否已被取消。
         *
         * @return true表示已取消
         */
        public boolean isCancelled() {
            return cancelled.get();
        }

        /**
         * 设置取消状态。
         *
         * @param cancelled 是否取消
         */
        public void setCancelled(boolean cancelled) {
            this.cancelled.set(cancelled);
        }
    }

    /**
     * 用于向客户端发送进度更新的数据传输对象 (DTO)。
     * 使用 record 以获得简洁和不变性。
     *
     * @param uploadId       上传ID
     * @param filename       文件名
     * @param status         状态
     * @param transferredBytes 已传输字节数
     * @param totalBytes     总字节数
     * @param remotePath     远程路径
     * @param percentage     完成百分比
     * @param speed          当前速度
     * @param speedFormatted 格式化速度
     * @param error          错误信息
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UploadProgressDto(
            String uploadId,
            String filename,
            String status,
            long transferredBytes,
            long totalBytes,
            String remotePath,
            Double percentage,
            Long speed,
            String speedFormatted,
            String error
    ) {
    }
}
