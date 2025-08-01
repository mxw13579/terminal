package com.fufu.terminal.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 内存管理服务
 * 定期清理过期数据和优化内存使用
 * @author lizelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryManagementService {
    
    @Qualifier("cleanupScheduler")
    private final ScheduledExecutorService cleanupScheduler;
    
    // 用于存储需要定期清理的缓存
    private final ConcurrentHashMap<String, CacheEntry> sessionCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> rateLimitCache = new ConcurrentHashMap<>();
    
    /**
     * 定期清理过期的会话缓存
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 5 * 60 * 1000) // 5分钟
    public void cleanExpiredSessions() {
        long currentTime = System.currentTimeMillis();
        int removedCount = 0;
        
        for (var entry : sessionCache.entrySet()) {
            if (currentTime - entry.getValue().getTimestamp() > TimeUnit.HOURS.toMillis(2)) {
                sessionCache.remove(entry.getKey());
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            log.info("Cleaned {} expired session cache entries", removedCount);
        }
    }
    
    /**
     * 定期清理限流缓存
     * 每10分钟执行一次
     */
    @Scheduled(fixedRate = 10 * 60 * 1000) // 10分钟
    public void cleanRateLimitCache() {
        long currentTime = System.currentTimeMillis();
        int removedCount = 0;
        
        for (var entry : rateLimitCache.entrySet()) {
            if (currentTime - entry.getValue() > TimeUnit.HOURS.toMillis(1)) {
                rateLimitCache.remove(entry.getKey());
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            log.info("Cleaned {} expired rate limit cache entries", removedCount);
        }
    }
    
    /**
     * 强制垃圾回收（谨慎使用）
     */
    @Scheduled(fixedRate = 30 * 60 * 1000) // 30分钟
    public void suggestGarbageCollection() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double memoryUsage = (double) usedMemory / totalMemory;
        
        log.debug("Memory usage: {}/{} MB ({}%)", 
                 usedMemory / 1024 / 1024, 
                 totalMemory / 1024 / 1024, 
                 String.format("%.2f", memoryUsage * 100));
        
        // 如果内存使用率超过80%，建议垃圾回收
        if (memoryUsage > 0.8) {
            log.info("High memory usage detected ({}%), suggesting garbage collection", 
                    String.format("%.2f", memoryUsage * 100));
            System.gc();
        }
    }
    
    /**
     * 获取内存使用统计
     */
    public MemoryStats getMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        return new MemoryStats(
            runtime.totalMemory(),
            runtime.freeMemory(),
            runtime.maxMemory(),
            sessionCache.size(),
            rateLimitCache.size()
        );
    }
    
    /**
     * 缓存条目
     */
    private static class CacheEntry {
        private final Object data;
        private final long timestamp;
        
        public CacheEntry(Object data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        public Object getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * 内存统计
     */
    public static class MemoryStats {
        private final long totalMemory;
        private final long freeMemory;
        private final long maxMemory;
        private final int sessionCacheSize;
        private final int rateLimitCacheSize;
        
        public MemoryStats(long totalMemory, long freeMemory, long maxMemory, 
                          int sessionCacheSize, int rateLimitCacheSize) {
            this.totalMemory = totalMemory;
            this.freeMemory = freeMemory;
            this.maxMemory = maxMemory;
            this.sessionCacheSize = sessionCacheSize;
            this.rateLimitCacheSize = rateLimitCacheSize;
        }
        
        public long getTotalMemory() { return totalMemory; }
        public long getFreeMemory() { return freeMemory; }
        public long getUsedMemory() { return totalMemory - freeMemory; }
        public long getMaxMemory() { return maxMemory; }
        public double getMemoryUsage() { return (double) getUsedMemory() / totalMemory; }
        public int getSessionCacheSize() { return sessionCacheSize; }
        public int getRateLimitCacheSize() { return rateLimitCacheSize; }
    }
}