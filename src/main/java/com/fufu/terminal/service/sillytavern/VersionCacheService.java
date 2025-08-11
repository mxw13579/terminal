package com.fufu.terminal.service.sillytavern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 版本信息缓存服务
 * 提供带TTL的版本信息缓存功能
 * 
 * @author Claude
 */
@Slf4j
@Service
public class VersionCacheService {

    private static class CacheEntry {
        private final List<String> versions;
        private final long timestamp;
        
        public CacheEntry(List<String> versions) {
            this.versions = versions;
            this.timestamp = System.currentTimeMillis();
        }
        
        public List<String> getVersions() {
            return versions;
        }
        
        public boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - timestamp > ttlMillis;
        }
    }
    
    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long TTL_MILLIS = 30 * 60 * 1000; // 30分钟
    private static final String VERSION_CACHE_KEY = "sillytavern-versions";
    
    /**
     * 获取缓存的版本信息
     * 
     * @return 缓存的版本列表，如果缓存过期或不存在则返回null
     */
    public List<String> getCachedVersions() {
        CacheEntry entry = cache.get(VERSION_CACHE_KEY);
        if (entry == null || entry.isExpired(TTL_MILLIS)) {
            if (entry != null) {
                cache.remove(VERSION_CACHE_KEY);
                log.debug("版本缓存已过期，已清除");
            }
            return null;
        }
        log.debug("返回缓存的版本信息，共 {} 个版本", entry.getVersions().size());
        return entry.getVersions();
    }
    
    /**
     * 缓存版本信息
     * 
     * @param versions 要缓存的版本列表
     */
    public void cacheVersions(List<String> versions) {
        if (versions != null && !versions.isEmpty()) {
            cache.put(VERSION_CACHE_KEY, new CacheEntry(versions));
            log.debug("已缓存版本信息，共 {} 个版本", versions.size());
        }
    }
    
    /**
     * 清除所有缓存
     */
    public void clearCache() {
        cache.clear();
        log.debug("已清除所有版本缓存");
    }
    
    /**
     * 定时清理过期缓存项（每10分钟执行一次）
     */
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void cleanupExpiredEntries() {
        int removedCount = 0;
        for (var iterator = cache.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (entry.getValue().isExpired(TTL_MILLIS)) {
                iterator.remove();
                removedCount++;
            }
        }
        if (removedCount > 0) {
            log.debug("清理了 {} 个过期的缓存项", removedCount);
        }
    }
}