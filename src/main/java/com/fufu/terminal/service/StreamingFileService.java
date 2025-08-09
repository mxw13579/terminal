package com.fufu.terminal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.model.SshConnection;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 流式文件传输服务
 * 提供内存友好的大文件上传下载功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingFileService {

    private final ObjectMapper objectMapper;
    private final StompSessionManager sessionManager;

    @Value("${file.transfer.max-file-size:104857600}") // 100MB
    private long maxFileSize;

    @Value("${file.transfer.max-total-size:1073741824}") // 1GB
    private long maxTotalSize;

    @Value("${file.transfer.chunk-size:65536}") // 64KB
    private int chunkSize;

    @Value("${file.transfer.max-concurrent:3}")
    private int maxConcurrentTransfers;

    @Value("${file.transfer.temp-dir:${java.io.tmpdir}}")
    private String tempDir;

    @Value("${file.transfer.throttle-bytes-per-second:10485760}") // 10MB/s
    private long throttleBytesPerSecond;

    // 上传进度跟踪
    private final Map<String, UploadProgress> activeUploads = new ConcurrentHashMap<>();

    // 并发控制 - 延迟初始化
    private Semaphore downloadSemaphore;
    private Semaphore uploadSemaphore;

    // 执行器 - 延迟初始化
    private ExecutorService uploadExecutor;

    @PostConstruct
    private void initializeResources() {
        this.downloadSemaphore = new Semaphore(maxConcurrentTransfers);
        this.uploadSemaphore = new Semaphore(maxConcurrentTransfers);
        this.uploadExecutor = Executors.newFixedThreadPool(maxConcurrentTransfers);
        log.info("StreamingFileService 初始化完成，最大并发传输数: {}", maxConcurrentTransfers);
    }

    /**
     * 下载结果封装
     */
    @Data
    public static class DownloadResult {
        private final String filename;
        private final long contentLength;
        private final Flux<byte[]> dataStream;
    }

    /**
     * 上传进度跟踪
     */
    @Data
    public static class UploadProgress {
        private String uploadId;
        private String sessionId;
        private List<String> filenames;
        private long totalBytes;
        private AtomicLong transferredBytes = new AtomicLong(0);
        private long startTime;
        private String status; // "uploading", "completed", "failed", "cancelled"
        private String errorMessage;
        private boolean cancelled = false;
        private List<Path> tempFiles = new ArrayList<>();

        public UploadProgress(String uploadId, String sessionId) {
            this.uploadId = uploadId;
            this.sessionId = sessionId;
            this.startTime = System.currentTimeMillis();
            this.status = "uploading";
        }
    }

    /**
     * 创建下载流
     */
    public DownloadResult createDownloadStream(SshConnection connection, List<String> paths, String clientIp) throws Exception {

        if (!downloadSemaphore.tryAcquire()) {
            throw new RuntimeException("Too many concurrent downloads");
        }

        try {
            ChannelSftp channelSftp = connection.getOrCreateSftpChannel();

            if (paths.size() == 1) {
                String filePath = paths.get(0);
                SftpATTRS attrs = channelSftp.lstat(filePath);

                if (attrs.isDir()) {
                    // 目录压缩下载
                    return createDirectoryDownloadStream(channelSftp, filePath, clientIp);
                } else {
                    // 单文件下载
                    return createSingleFileDownloadStream(channelSftp, filePath, clientIp);
                }
            } else {
                // 多文件打包下载
                return createMultiFileDownloadStream(channelSftp, paths, clientIp);
            }

        } catch (Exception e) {
            downloadSemaphore.release();
            throw e;
        }
    }

    /**
     * 创建单文件下载流
     */
    private DownloadResult createSingleFileDownloadStream(ChannelSftp sftp, String filePath, String clientIp) throws Exception {
        SftpATTRS attrs = sftp.lstat(filePath);
        String filename = Paths.get(filePath).getFileName().toString();
        long contentLength = attrs.getSize();

        // 检查文件大小限制
        if (contentLength > maxFileSize) {
            throw new RuntimeException("File too large: " + contentLength + " bytes");
        }

        Flux<byte[]> dataStream = Flux.create(sink -> {
            try (InputStream inputStream = sftp.get(filePath)) {
                byte[] buffer = new byte[chunkSize];
                long totalRead = 0;
                long lastThrottleTime = System.currentTimeMillis();

                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    if (sink.isCancelled()) {
                        break;
                    }

                    byte[] chunk = Arrays.copyOf(buffer, bytesRead);
                    sink.next(chunk);

                    totalRead += bytesRead;

                    // 流量限制
                    if (throttleBytesPerSecond > 0) {
                        long currentTime = System.currentTimeMillis();
                        long expectedTime = lastThrottleTime + (bytesRead * 1000L / throttleBytesPerSecond);
                        if (currentTime < expectedTime) {
                            try {
                                Thread.sleep(expectedTime - currentTime);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        lastThrottleTime = System.currentTimeMillis();
                    }
                }

                sink.complete();
                log.info("单文件下载完成: {}, {} bytes, 客户端: {}", filename, totalRead, clientIp);

            } catch (Exception e) {
                log.error("单文件下载失败: {}", e.getMessage(), e);
                sink.error(e);
            } finally {
                downloadSemaphore.release();
            }
        }).subscribeOn(Schedulers.boundedElastic()).cast(byte[].class);

        return new DownloadResult(filename, contentLength, dataStream);
    }

    /**
     * 创建目录下载流（压缩为zip）
     */
    private DownloadResult createDirectoryDownloadStream(ChannelSftp sftp, String dirPath, String clientIp) throws Exception {
        String dirName = Paths.get(dirPath).getFileName().toString();
        String filename = dirName + ".zip";

        Flux<byte[]> dataStream = Flux.create(sink -> {
            try {
                PipedOutputStream pipedOut = new PipedOutputStream();
                PipedInputStream pipedIn = new PipedInputStream(pipedOut, chunkSize * 4);

                // 异步写入ZIP数据
                CompletableFuture.runAsync(() -> {
                    try (ZipOutputStream zos = new ZipOutputStream(pipedOut)) {
                        zipDirectory(sftp, dirPath, "", zos);
                    } catch (Exception e) {
                        log.error("目录压缩失败: {}", e.getMessage(), e);
                        try {
                            pipedOut.close();
                        } catch (IOException ioException) {
                            log.error("关闭输出流失败", ioException);
                        }
                    }
                });

                // 读取并发送数据
                byte[] buffer = new byte[chunkSize];
                long totalRead = 0;
                long lastThrottleTime = System.currentTimeMillis();

                int bytesRead;
                while ((bytesRead = pipedIn.read(buffer)) != -1) {
                    if (sink.isCancelled()) {
                        break;
                    }

                    byte[] chunk = Arrays.copyOf(buffer, bytesRead);
                    sink.next(chunk);

                    totalRead += bytesRead;

                    // 流量限制
                    if (throttleBytesPerSecond > 0 && totalRead > chunkSize) {
                        long currentTime = System.currentTimeMillis();
                        long expectedTime = lastThrottleTime + (bytesRead * 1000L / throttleBytesPerSecond);
                        if (currentTime < expectedTime) {
                            try {
                                Thread.sleep(expectedTime - currentTime);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        lastThrottleTime = System.currentTimeMillis();
                    }
                }

                pipedIn.close();
                sink.complete();
                log.info("目录下载完成: {}, {} bytes, 客户端: {}", filename, totalRead, clientIp);

            } catch (Exception e) {
                log.error("目录下载失败: {}", e.getMessage(), e);
                sink.error(e);
            } finally {
                downloadSemaphore.release();
            }
        }).subscribeOn(Schedulers.boundedElastic()).cast(byte[].class);

        return new DownloadResult(filename, -1, dataStream); // 压缩大小未知
    }

    /**
     * 创建多文件下载流（打包为zip）
     */
    private DownloadResult createMultiFileDownloadStream(ChannelSftp sftp, List<String> paths, String clientIp) throws Exception {
        String filename = "download.zip";

        Flux<byte[]> dataStream = Flux.create(sink -> {
            try {
                PipedOutputStream pipedOut = new PipedOutputStream();
                PipedInputStream pipedIn = new PipedInputStream(pipedOut, chunkSize * 4);

                // 异步写入ZIP数据
                CompletableFuture.runAsync(() -> {
                    try (ZipOutputStream zos = new ZipOutputStream(pipedOut)) {
                        for (String path : paths) {
                            SftpATTRS attrs = sftp.lstat(path);
                            String entryName = Paths.get(path).getFileName().toString();

                            if (attrs.isDir()) {
                                zipDirectory(sftp, path, entryName + "/", zos);
                            } else {
                                zipFile(sftp, path, entryName, zos);
                            }
                        }
                    } catch (Exception e) {
                        log.error("多文件压缩失败: {}", e.getMessage(), e);
                        try {
                            pipedOut.close();
                        } catch (IOException ioException) {
                            log.error("关闭输出流失败", ioException);
                        }
                    }
                });

                // 读取并发送数据
                byte[] buffer = new byte[chunkSize];
                long totalRead = 0;
                long lastThrottleTime = System.currentTimeMillis();

                int bytesRead;
                while ((bytesRead = pipedIn.read(buffer)) != -1) {
                    if (sink.isCancelled()) {
                        break;
                    }

                    byte[] chunk = Arrays.copyOf(buffer, bytesRead);
                    sink.next(chunk);

                    totalRead += bytesRead;

                    // 流量限制
                    if (throttleBytesPerSecond > 0 && totalRead > chunkSize) {
                        long currentTime = System.currentTimeMillis();
                        long expectedTime = lastThrottleTime + (bytesRead * 1000L / throttleBytesPerSecond);
                        if (currentTime < expectedTime) {
                            try {
                                Thread.sleep(expectedTime - currentTime);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        lastThrottleTime = System.currentTimeMillis();
                    }
                }

                pipedIn.close();
                sink.complete();
                log.info("多文件下载完成: {}, {} bytes, 客户端: {}", filename, totalRead, clientIp);

            } catch (Exception e) {
                log.error("多文件下载失败: {}", e.getMessage(), e);
                sink.error(e);
            } finally {
                downloadSemaphore.release();
            }
        }).subscribeOn(Schedulers.boundedElastic()).cast(byte[].class);

        return new DownloadResult(filename, -1, dataStream);
    }

    /**
     * 启动流式上传
     */
    public String startUpload(SshConnection connection, String sessionId, String remotePath,
                             List<MultipartFile> files, String clientIp) throws Exception {

        if (!uploadSemaphore.tryAcquire()) {
            throw new RuntimeException("Too many concurrent uploads");
        }

        String uploadId = UUID.randomUUID().toString();
        UploadProgress progress = new UploadProgress(uploadId, sessionId);

        // 检查文件大小限制
        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > maxTotalSize) {
            uploadSemaphore.release();
            throw new RuntimeException("Total upload size exceeds limit: " + totalSize + " bytes");
        }

        for (MultipartFile file : files) {
            if (file.getSize() > maxFileSize) {
                uploadSemaphore.release();
                throw new RuntimeException("File too large: " + file.getOriginalFilename() + " (" + file.getSize() + " bytes)");
            }
        }

        progress.setTotalBytes(totalSize);
        progress.setFilenames(files.stream().map(MultipartFile::getOriginalFilename).toList());
        activeUploads.put(uploadId, progress);

        // 异步处理上传
        CompletableFuture.runAsync(() -> {
            try {
                processUpload(connection, progress, remotePath, files, clientIp);
            } finally {
                uploadSemaphore.release();
            }
        }, uploadExecutor);

        return uploadId;
    }

    /**
     * 处理上传过程
     */
    private void processUpload(SshConnection connection, UploadProgress progress,
                              String remotePath, List<MultipartFile> files, String clientIp) {

        try {
            ChannelSftp sftpChannel = connection.getOrCreateSftpChannel();

            for (MultipartFile file : files) {
                if (progress.isCancelled()) {
                    break;
                }

                // 创建临时文件
                Path tempFile = createTempFile(file);
                progress.getTempFiles().add(tempFile);

                // 上传文件
                uploadSingleFile(sftpChannel, tempFile, remotePath, file.getOriginalFilename(), progress);
            }

            if (!progress.isCancelled()) {
                progress.setStatus("completed");
                log.info("上传完成: {}, {} bytes, 客户端: {}", progress.getUploadId(),
                    progress.getTransferredBytes().get(), clientIp);

                // 通过STOMP发送完成通知
                sendUploadNotification(progress.getSessionId(), "upload_completed",
                    "所有文件上传完成", remotePath);
            }

        } catch (Exception e) {
            progress.setStatus("failed");
            progress.setErrorMessage(e.getMessage());
            log.error("上传失败: {}", e.getMessage(), e);

            // 通过STOMP发送错误通知
            sendUploadNotification(progress.getSessionId(), "upload_failed",
                "上传失败: " + e.getMessage(), remotePath);
        } finally {
            // 清理临时文件
            cleanupTempFiles(progress);

            // 延迟移除进度记录
            CompletableFuture.delayedExecutor(300, TimeUnit.SECONDS)
                .execute(() -> activeUploads.remove(progress.getUploadId()));
        }
    }

    /**
     * 上传单个文件
     */
    private void uploadSingleFile(ChannelSftp sftpChannel, Path tempFile, String remotePath,
                                 String filename, UploadProgress progress) throws Exception {

        String fullRemotePath = Paths.get(remotePath, filename).normalize().toString().replace("\\", "/");
        long fileSize = Files.size(tempFile);

        try (InputStream inputStream = Files.newInputStream(tempFile)) {
            byte[] buffer = new byte[chunkSize];
            long transferred = 0;
            long lastReportTime = System.currentTimeMillis();

            // 创建临时远程文件
            String tempRemotePath = fullRemotePath + ".tmp";
            try (var outputStream = sftpChannel.put(tempRemotePath)) {

                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    if (progress.isCancelled()) {
                        // 清理临时远程文件
                        try {
                            sftpChannel.rm(tempRemotePath);
                        } catch (Exception ignored) {}
                        throw new RuntimeException("Upload cancelled");
                    }

                    outputStream.write(buffer, 0, bytesRead);
                    transferred += bytesRead;
                    progress.getTransferredBytes().addAndGet(bytesRead);

                    // 定期报告进度
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastReportTime > 1000) { // 每秒报告一次
                        sendProgressUpdate(progress);
                        lastReportTime = currentTime;

                        // 流量限制
                        if (throttleBytesPerSecond > 0) {
                            long expectedTime = progress.getStartTime() +
                                (progress.getTransferredBytes().get() * 1000L / throttleBytesPerSecond);
                            if (currentTime < expectedTime) {
                                Thread.sleep(expectedTime - currentTime);
                            }
                        }
                    }
                }
            }

            // 原子性重命名
            sftpChannel.rename(tempRemotePath, fullRemotePath);

            log.debug("文件上传完成: {} -> {}, {} bytes", filename, fullRemotePath, transferred);
        }
    }

    /**
     * 创建临时文件
     */
    private Path createTempFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String fileName = originalFilename != null ? originalFilename : "upload";
        String prefix = fileName.contains(".") ?
            fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String suffix = fileName.contains(".") ?
            fileName.substring(fileName.lastIndexOf('.')) : ".tmp";

        Path tempFile = Files.createTempFile(Paths.get(tempDir), "sftp_" + prefix + "_", suffix);
        file.transferTo(tempFile.toFile());
        return tempFile;
    }

    /**
     * 发送进度更新
     */
    private void sendProgressUpdate(UploadProgress progress) {
        try {
            int percentage = (int) ((progress.getTransferredBytes().get() * 100) / progress.getTotalBytes());
            double speed = calculateSpeed(progress);

            String message = String.format(
                "{\"type\":\"upload_progress\",\"uploadId\":\"%s\",\"progress\":%d,\"speed\":%.2f}",
                progress.getUploadId(), percentage, speed);

            sessionManager.sendToSession(progress.getSessionId(), "/queue/upload/progress", message);
        } catch (Exception e) {
            log.warn("发送进度更新失败: {}", e.getMessage());
        }
    }

    /**
     * 发送上传通知
     */
    private void sendUploadNotification(String sessionId, String type, String message, String path) {
        try {
            String notification = String.format(
                "{\"type\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                type, message, path);
            sessionManager.sendToSession(sessionId, "/queue/upload/notification", notification);
        } catch (Exception e) {
            log.warn("发送上传通知失败: {}", e.getMessage());
        }
    }

    /**
     * 计算传输速度
     */
    private double calculateSpeed(UploadProgress progress) {
        long elapsed = System.currentTimeMillis() - progress.getStartTime();
        if (elapsed <= 0) return 0.0;
        return (progress.getTransferredBytes().get() * 1000.0) / elapsed; // bytes per second
    }

    /**
     * 取消上传
     */
    public boolean cancelUpload(String uploadId) {
        UploadProgress progress = activeUploads.get(uploadId);
        if (progress != null) {
            progress.setCancelled(true);
            progress.setStatus("cancelled");
            log.info("上传已取消: {}", uploadId);
            return true;
        }
        return false;
    }

    /**
     * 获取上传进度
     */
    public String getUploadProgress(String uploadId) {
        UploadProgress progress = activeUploads.get(uploadId);
        if (progress == null) {
            return null;
        }

        try {
            int percentage = (int) ((progress.getTransferredBytes().get() * 100) / progress.getTotalBytes());
            double speed = calculateSpeed(progress);

            return String.format(
                "{\"uploadId\":\"%s\",\"status\":\"%s\",\"progress\":%d,\"speed\":%.2f," +
                "\"transferred\":%d,\"total\":%d,\"filenames\":%s}",
                progress.getUploadId(), progress.getStatus(), percentage, speed,
                progress.getTransferredBytes().get(), progress.getTotalBytes(),
                objectMapper.writeValueAsString(progress.getFilenames()));
        } catch (Exception e) {
            log.error("生成进度JSON失败: {}", e.getMessage(), e);
            return "{\"error\":\"Failed to get progress\"}";
        }
    }

    /**
     * 清理临时文件
     */
    private void cleanupTempFiles(UploadProgress progress) {
        for (Path tempFile : progress.getTempFiles()) {
            try {
                Files.deleteIfExists(tempFile);
                log.debug("临时文件已清理: {}", tempFile);
            } catch (IOException e) {
                log.warn("清理临时文件失败: {}", tempFile, e);
            }
        }
        progress.getTempFiles().clear();
    }

    // ZIP utility methods (reused from original SftpService)
    private void zipDirectory(ChannelSftp sftp, String dirPath, String base, ZipOutputStream zos)
            throws SftpException, IOException {
        @SuppressWarnings("unchecked")
        Vector<ChannelSftp.LsEntry> entries = sftp.ls(dirPath);
        for (ChannelSftp.LsEntry entry : entries) {
            if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) {
                continue;
            }
            String fullPath = Paths.get(dirPath, entry.getFilename()).toString().replace("\\", "/");
            String zipEntryName = base + entry.getFilename();
            if (entry.getAttrs().isDir()) {
                zipDirectory(sftp, fullPath, zipEntryName + "/", zos);
            } else {
                zipFile(sftp, fullPath, zipEntryName, zos);
            }
        }
    }

    private void zipFile(ChannelSftp sftp, String filePath, String zipEntryName, ZipOutputStream zos)
            throws SftpException, IOException {
        zos.putNextEntry(new ZipEntry(zipEntryName));
        try (InputStream is = sftp.get(filePath)) {
            byte[] buffer = new byte[chunkSize];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                zos.write(buffer, 0, bytesRead);
            }
        }
        zos.closeEntry();
    }

    /**
     * 服务关闭时清理资源。
     */
    @PreDestroy
    private void cleanup() {
        if (uploadExecutor != null && !uploadExecutor.isShutdown()) {
            uploadExecutor.shutdown();
            try {
                if (!uploadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    uploadExecutor.shutdownNow();
                }
                log.info("StreamingFileService 资源清理完成");
            } catch (InterruptedException e) {
                uploadExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
