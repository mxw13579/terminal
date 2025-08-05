package com.fufu.terminal.service.sillytavern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 文件清理服务，负责临时文件的自动与手动清理。
 * <p>
 * 支持定时任务、手动触发、最大文件数与最大存活时间的自动维护。
 * </p>
 *
 * @author lizelin
 */
@Slf4j
@Service
public class FileCleanupService {

    /**
     * 临时目录路径
     */
    @Value("${sillytavern.temp.directory:./temp}")
    private String tempDirectory;

    /**
     * 文件最大存活时间（小时）
     */
    @Value("${sillytavern.cleanup.max-age-hours:1}")
    private int defaultMaxAgeHours;

    /**
     * 临时目录最大文件数
     */
    @Value("${sillytavern.cleanup.max-files:100}")
    private int maxTotalFiles;

    /**
     * 定时任务线程池（单线程）
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * 已调度的文件清理任务映射（文件路径 -> 清理时间）
     */
    private final ConcurrentHashMap<String, LocalDateTime> scheduledCleanups = new ConcurrentHashMap<>();

    /**
     * 初始化方法，创建临时目录（如不存在）。
     */
    @PostConstruct
    public void initialize() {
        File tempDir = new File(tempDirectory);
        if (!tempDir.exists() && tempDir.mkdirs()) {
            log.info("已创建临时目录: {}", tempDirectory);
        } else if (!tempDir.exists()) {
            log.warn("无法创建临时目录: {}", tempDirectory);
        }
        log.info("FileCleanupService 初始化完成，临时目录: {}", tempDirectory);
    }

    /**
     * 销毁前关闭线程池，确保资源释放。
     */
    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 调度指定文件在若干小时后清理。
     *
     * @param filePath     文件绝对路径
     * @param hoursFromNow 多少小时后清理
     */
    public void scheduleCleanup(String filePath, int hoursFromNow) {
        if (filePath == null || filePath.trim().isEmpty()) {
            log.warn("无法调度清理，文件路径为空");
            return;
        }
        LocalDateTime cleanupTime = LocalDateTime.now().plusHours(hoursFromNow);
        scheduledCleanups.put(filePath, cleanupTime);
        scheduler.schedule(() -> {
            deleteFile(filePath);
            scheduledCleanups.remove(filePath);
        }, hoursFromNow, TimeUnit.HOURS);
        log.debug("已调度文件清理: {}，清理时间: {}", filePath, cleanupTime);
    }

    /**
     * 立即调度文件清理。
     *
     * @param filePath 文件绝对路径
     */
    public void scheduleImmediateCleanup(String filePath) {
        scheduleCleanup(filePath, 0);
    }

    /**
     * 定时任务：每 15 分钟清理超时文件，并维护最大文件数。
     * <p>由Spring定时任务自动触发。</p>
     */
    @Scheduled(fixedRate = 900_000)
    public void periodicCleanup() {
        log.debug("开始定时清理临时文件");
        File tempDir = new File(tempDirectory);
        if (!tempDir.exists() || !tempDir.isDirectory()) {
            log.warn("临时目录不存在或不是目录: {}", tempDirectory);
            return;
        }
        File[] files = tempDir.listFiles();
        if (files == null) {
            log.debug("临时目录下无文件");
            return;
        }
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(defaultMaxAgeHours);
        int deletedCount = 0;
        long totalSizeDeleted = 0;
        for (File file : files) {
            if (file.isFile()) {
                LocalDateTime fileTime = LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(file.lastModified()),
                        java.time.ZoneId.systemDefault()
                );
                if (fileTime.isBefore(cutoffTime)) {
                    long fileSize = file.length();
                    if (deleteFile(file.getAbsolutePath())) {
                        deletedCount++;
                        totalSizeDeleted += fileSize;
                    }
                }
            }
        }
        if (deletedCount > 0) {
            log.info("定时清理完成: 删除 {} 个文件，释放 {} 字节", deletedCount, totalSizeDeleted);
        }
        enforceFileLimit();
    }

    /**
     * 强制限制临时目录文件数，超出则删除最旧文件。
     */
    private void enforceFileLimit() {
        File tempDir = new File(tempDirectory);
        File[] files = tempDir.listFiles();
        if (files == null || files.length <= maxTotalFiles) {
            return;
        }
        // 按最后修改时间升序排序
        java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
        int filesToDelete = files.length - maxTotalFiles;
        int deletedCount = 0;
        for (int i = 0; i < filesToDelete && i < files.length; i++) {
            if (files[i].isFile() && deleteFile(files[i].getAbsolutePath())) {
                deletedCount++;
            }
        }
        if (deletedCount > 0) {
            log.info("已强制删除 {} 个最旧文件（文件数上限: {}）", deletedCount, maxTotalFiles);
        }
    }

    /**
     * 删除指定文件。
     *
     * @param filePath 文件绝对路径
     * @return 删除成功返回 true，不存在视为成功
     */
    public boolean deleteFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }
        File file = new File(filePath);
        if (!file.exists()) {
            log.debug("文件不存在，跳过删除: {}", filePath);
            return true;
        }
        try {
            if (file.delete()) {
                log.debug("成功删除文件: {}", filePath);
                return true;
            } else {
                log.warn("删除文件失败: {}", filePath);
                return false;
            }
        } catch (SecurityException e) {
            log.error("删除文件时发生安全异常: {}", filePath, e);
            return false;
        }
    }

    /**
     * 获取当前临时目录的文件统计信息。
     *
     * @return 文件统计信息对象
     */
    public FileCleanupStats getCleanupStats() {
        File tempDir = new File(tempDirectory);
        if (!tempDir.exists() || !tempDir.isDirectory()) {
            return new FileCleanupStats(0, 0, 0);
        }
        File[] files = tempDir.listFiles();
        if (files == null) {
            return new FileCleanupStats(0, 0, 0);
        }
        long totalSize = 0;
        int fileCount = 0;
        for (File file : files) {
            if (file.isFile()) {
                totalSize += file.length();
                fileCount++;
            }
        }
        return new FileCleanupStats(fileCount, totalSize, scheduledCleanups.size());
    }

    /**
     * 强制清理临时目录下所有文件（管理员功能）。
     *
     * @return 实际删除的文件数
     */
    public int forceCleanupAll() {
        log.warn("收到强制清理所有临时文件请求");
        File tempDir = new File(tempDirectory);
        if (!tempDir.exists() || !tempDir.isDirectory()) {
            return 0;
        }
        File[] files = tempDir.listFiles();
        if (files == null) {
            return 0;
        }
        int deletedCount = 0;
        for (File file : files) {
            if (file.isFile() && deleteFile(file.getAbsolutePath())) {
                deletedCount++;
            }
        }
        scheduledCleanups.clear();
        log.info("强制清理完成: 删除 {} 个文件", deletedCount);
        return deletedCount;
    }

    /**
     * 文件清理服务的统计信息。
     */
    public static class FileCleanupStats {
        /**
         * 当前文件数
         */
        private final int currentFileCount;
        /**
         * 当前总文件大小（字节）
         */
        private final long currentTotalSizeBytes;
        /**
         * 已调度清理的文件数
         */
        private final int scheduledCleanupCount;

        /**
         * 构造方法
         *
         * @param currentFileCount       当前文件数
         * @param currentTotalSizeBytes  当前总大小
         * @param scheduledCleanupCount  已调度清理数
         */
        public FileCleanupStats(int currentFileCount, long currentTotalSizeBytes, int scheduledCleanupCount) {
            this.currentFileCount = currentFileCount;
            this.currentTotalSizeBytes = currentTotalSizeBytes;
            this.scheduledCleanupCount = scheduledCleanupCount;
        }

        /**
         * 获取当前文件数
         * @return 当前文件数
         */
        public int getCurrentFileCount() {
            return currentFileCount;
        }

        /**
         * 获取当前总文件大小（字节）
         * @return 当前总大小
         */
        public long getCurrentTotalSizeBytes() {
            return currentTotalSizeBytes;
        }

        /**
         * 获取已调度清理的文件数
         * @return 已调度清理数
         */
        public int getScheduledCleanupCount() {
            return scheduledCleanupCount;
        }

        @Override
        public String toString() {
            return String.format("FileCleanupStats{files=%d, size=%d bytes, scheduled=%d}",
                    currentFileCount, currentTotalSizeBytes, scheduledCleanupCount);
        }
    }
}
