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
 * Service for managing temporary file cleanup.
 * Handles automatic cleanup of exported data files and uploaded files.
 */
@Slf4j
@Service
public class FileCleanupService {
    
    @Value("${sillytavern.temp.directory:./temp}")
    private String tempDirectory;
    
    @Value("${sillytavern.cleanup.max-age-hours:1}")
    private int defaultMaxAgeHours;
    
    @Value("${sillytavern.cleanup.max-files:100}")
    private int maxTotalFiles;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ConcurrentHashMap<String, LocalDateTime> scheduledCleanups = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        // Create temp directory if it doesn't exist
        File tempDir = new File(tempDirectory);
        if (!tempDir.exists()) {
            boolean created = tempDir.mkdirs();
            if (created) {
                log.info("Created temporary directory: {}", tempDirectory);
            } else {
                log.warn("Failed to create temporary directory: {}", tempDirectory);
            }
        }
        
        log.info("FileCleanupService initialized with temp directory: {}", tempDirectory);
    }
    
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
     * Schedule cleanup for a specific file
     */
    public void scheduleCleanup(String filePath, int hoursFromNow) {
        if (filePath == null || filePath.trim().isEmpty()) {
            log.warn("Cannot schedule cleanup for null or empty file path");
            return;
        }
        
        LocalDateTime cleanupTime = LocalDateTime.now().plusHours(hoursFromNow);
        scheduledCleanups.put(filePath, cleanupTime);
        
        scheduler.schedule(() -> {
            deleteFile(filePath);
            scheduledCleanups.remove(filePath);
        }, hoursFromNow, TimeUnit.HOURS);
        
        log.debug("Scheduled cleanup for file: {} at {}", filePath, cleanupTime);
    }
    
    /**
     * Schedule immediate cleanup for a file
     */
    public void scheduleImmediateCleanup(String filePath) {
        scheduleCleanup(filePath, 0);
    }
    
    /**
     * Periodic cleanup of old files (runs every 15 minutes)
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    public void periodicCleanup() {
        log.debug("Starting periodic cleanup of temporary files");
        
        File tempDir = new File(tempDirectory);
        if (!tempDir.exists() || !tempDir.isDirectory()) {
            log.warn("Temporary directory does not exist or is not a directory: {}", tempDirectory);
            return;
        }
        
        File[] files = tempDir.listFiles();
        if (files == null) {
            log.debug("No files found in temporary directory");
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
            log.info("Periodic cleanup completed: {} files deleted, {} bytes freed", 
                deletedCount, totalSizeDeleted);
        }
        
        // Check if we have too many files and delete oldest ones
        enforceFileLimit();
    }
    
    /**
     * Enforce maximum file limit by deleting oldest files
     */
    private void enforceFileLimit() {
        File tempDir = new File(tempDirectory);
        File[] files = tempDir.listFiles();
        if (files == null || files.length <= maxTotalFiles) {
            return;
        }
        
        // Sort files by last modified time (oldest first)
        java.util.Arrays.sort(files, (f1, f2) -> 
            Long.compare(f1.lastModified(), f2.lastModified()));
        
        int filesToDelete = files.length - maxTotalFiles;
        int deletedCount = 0;
        
        for (int i = 0; i < filesToDelete && i < files.length; i++) {
            if (files[i].isFile()) {
                if (deleteFile(files[i].getAbsolutePath())) {
                    deletedCount++;
                }
            }
        }
        
        if (deletedCount > 0) {
            log.info("Enforced file limit: deleted {} oldest files (limit: {})", 
                deletedCount, maxTotalFiles);
        }
    }
    
    /**
     * Delete a specific file
     */
    public boolean deleteFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }
        
        File file = new File(filePath);
        if (!file.exists()) {
            log.debug("File does not exist, skipping deletion: {}", filePath);
            return true; // Consider it successful if it doesn't exist
        }
        
        try {
            if (file.delete()) {
                log.debug("Successfully deleted file: {}", filePath);
                return true;
            } else {
                log.warn("Failed to delete file: {}", filePath);
                return false;
            }
        } catch (SecurityException e) {
            log.error("Security exception while deleting file: {}", filePath, e);
            return false;
        }
    }
    
    /**
     * Get current temporary directory usage statistics
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
     * Force cleanup of all files in temp directory (admin function)
     */
    public int forceCleanupAll() {
        log.warn("Force cleanup of all temporary files requested");
        
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
            if (file.isFile()) {
                if (deleteFile(file.getAbsolutePath())) {
                    deletedCount++;
                }
            }
        }
        
        // Clear scheduled cleanups
        scheduledCleanups.clear();
        
        log.info("Force cleanup completed: {} files deleted", deletedCount);
        return deletedCount;
    }
    
    /**
     * Statistics about cleanup service
     */
    public static class FileCleanupStats {
        private final int currentFileCount;
        private final long currentTotalSizeBytes;
        private final int scheduledCleanupCount;
        
        public FileCleanupStats(int currentFileCount, long currentTotalSizeBytes, int scheduledCleanupCount) {
            this.currentFileCount = currentFileCount;
            this.currentTotalSizeBytes = currentTotalSizeBytes;
            this.scheduledCleanupCount = scheduledCleanupCount;
        }
        
        public int getCurrentFileCount() { return currentFileCount; }
        public long getCurrentTotalSizeBytes() { return currentTotalSizeBytes; }
        public int getScheduledCleanupCount() { return scheduledCleanupCount; }
        
        @Override
        public String toString() {
            return String.format("FileCleanupStats{files=%d, size=%d bytes, scheduled=%d}", 
                currentFileCount, currentTotalSizeBytes, scheduledCleanupCount);
        }
    }
}