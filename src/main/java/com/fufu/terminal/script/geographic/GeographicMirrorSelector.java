package com.fufu.terminal.script.geographic;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced Geographic Mirror Selector
 * Provides intelligent mirror selection based on server location and network conditions
 * Supports Chinese and international environments with automatic failover
 */
@Component
@Slf4j
public class GeographicMirrorSelector {
    
    @Value("${app.mirrors.china.enabled:true}")
    private boolean chinaMirrorsEnabled;
    
    @Value("${app.mirrors.speed-test.enabled:false}")
    private boolean speedTestEnabled;
    
    @Value("${app.mirrors.cache.ttl:3600}")
    private int cacheTtlSeconds;
    
    private final Map<String, CachedMirrorConfig> mirrorCache = new ConcurrentHashMap<>();
    
    /**
     * Select optimal mirror configuration based on detected location
     */
    public MirrorConfiguration selectOptimalMirror(String detectedLocation) {
        return selectOptimalMirror(detectedLocation, null);
    }
    
    /**
     * Select optimal mirror configuration for specific services
     */
    public MirrorConfiguration selectOptimalMirror(String detectedLocation, Set<String> requiredServices) {
        String cacheKey = detectedLocation + "_" + (requiredServices != null ? requiredServices.hashCode() : "all");
        
        // Check cache first
        CachedMirrorConfig cached = mirrorCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Using cached mirror configuration for location: {}", detectedLocation);
            return cached.getConfiguration();
        }
        
        // Generate new configuration
        MirrorConfiguration config = generateMirrorConfiguration(detectedLocation, requiredServices);
        
        // Cache the result
        mirrorCache.put(cacheKey, new CachedMirrorConfig(config, System.currentTimeMillis() + (cacheTtlSeconds * 1000L)));
        
        log.info("Selected mirror configuration for location: {} with services: {}, region: {}", 
            detectedLocation, requiredServices, config.getRegion());
        
        return config;
    }
    
    /**
     * Generate mirror configuration based on location and services
     */
    private MirrorConfiguration generateMirrorConfiguration(String detectedLocation, Set<String> requiredServices) {
        if (isChineseLocation(detectedLocation)) {
            return selectChineseMirror(requiredServices);
        } else {
            return selectInternationalMirror(requiredServices);
        }
    }
    
    /**
     * Check if location is in China based on various indicators
     */
    private boolean isChineseLocation(String location) {
        if (location == null) return false;
        
        String locationLower = location.toLowerCase();
        return locationLower.contains("china") || 
               locationLower.contains("中国") ||
               locationLower.contains("cn") ||
               locationLower.contains("beijing") ||
               locationLower.contains("shanghai") ||
               locationLower.contains("guangzhou") ||
               locationLower.contains("shenzhen") ||
               locationLower.contains("hangzhou") ||
               locationLower.contains("nanjing") ||
               locationLower.contains("chengdu") ||
               locationLower.contains("wuhan") ||
               locationLower.contains("xi'an") ||
               locationLower.contains("tianjin");
    }
    
    /**
     * Select Chinese mirror configuration
     */
    private MirrorConfiguration selectChineseMirror(Set<String> requiredServices) {
        log.info("Selecting Chinese mirrors for services: {}", requiredServices);
        
        MirrorConfiguration.Builder builder = MirrorConfiguration.builder()
            .region("China")
            .priority(1)
            .description("Optimized for Chinese servers with fast local mirrors");
        
        // APT mirrors (Ubuntu/Debian)
        if (requiredServices == null || requiredServices.contains("apt")) {
            builder.aptMirror("https://mirrors.aliyun.com/ubuntu/")
                   .aptBackupMirrors(Arrays.asList(
                       "https://mirrors.tuna.tsinghua.edu.cn/ubuntu/",
                       "https://mirrors.ustc.edu.cn/ubuntu/",
                       "https://mirrors.163.com/ubuntu/",
                       "https://mirrors.huaweicloud.com/ubuntu/"
                   ));
        }
        
        // Docker mirrors
        if (requiredServices == null || requiredServices.contains("docker")) {
            builder.dockerMirror("https://mirror.baidubce.com")
                   .dockerBackupMirrors(Arrays.asList(
                       "https://docker.mirrors.ustc.edu.cn",
                       "https://hub-mirror.c.163.com",
                       "https://mirror.aliyuncs.com",
                       "https://dockerhub.azk8s.cn"
                   ));
        }
        
        // NPM mirrors
        if (requiredServices == null || requiredServices.contains("npm")) {
            builder.npmMirror("https://registry.npmmirror.com")
                   .npmBackupMirrors(Arrays.asList(
                       "https://registry.npm.taobao.org",
                       "https://mirrors.huaweicloud.com/repository/npm/",
                       "https://mirrors.ustc.edu.cn/npm/",
                       "https://npmreg.proxy.ustclug.org/"
                   ));
        }
        
        // Maven mirrors
        if (requiredServices == null || requiredServices.contains("maven")) {
            builder.mavenMirror("https://maven.aliyun.com/repository/public")
                   .mavenBackupMirrors(Arrays.asList(
                       "https://repo.huaweicloud.com/repository/maven/",
                       "https://mirrors.tuna.tsinghua.edu.cn/maven/",
                       "https://mirrors.ustc.edu.cn/maven/",
                       "https://maven.aliyun.com/repository/central"
                   ));
        }
        
        // PyPI mirrors
        if (requiredServices == null || requiredServices.contains("pypi")) {
            builder.pypiMirror("https://pypi.tuna.tsinghua.edu.cn/simple")
                   .pypiBackupMirrors(Arrays.asList(
                       "https://mirrors.aliyun.com/pypi/simple/",
                       "https://pypi.mirrors.ustc.edu.cn/simple/",
                       "https://mirrors.163.com/pypi/simple/",
                       "https://pypi.douban.com/simple/"
                   ));
        }
        
        // Composer mirrors (PHP)
        if (requiredServices == null || requiredServices.contains("composer")) {
            builder.composerMirror("https://mirrors.aliyun.com/composer/")
                   .composerBackupMirrors(Arrays.asList(
                       "https://mirrors.tuna.tsinghua.edu.cn/composer/",
                       "https://packagist.phpcomposer.com"
                   ));
        }
        
        return builder.build();
    }
    
    /**
     * Select international mirror configuration
     */
    private MirrorConfiguration selectInternationalMirror(Set<String> requiredServices) {
        log.info("Selecting international mirrors for services: {}", requiredServices);
        
        MirrorConfiguration.Builder builder = MirrorConfiguration.builder()
            .region("International")
            .priority(1)
            .description("Official international mirrors with global CDN support");
        
        // APT mirrors (Ubuntu/Debian)
        if (requiredServices == null || requiredServices.contains("apt")) {
            builder.aptMirror("http://archive.ubuntu.com/ubuntu/")
                   .aptBackupMirrors(Arrays.asList(
                       "http://security.ubuntu.com/ubuntu/",
                       "http://us.archive.ubuntu.com/ubuntu/",
                       "http://gb.archive.ubuntu.com/ubuntu/",
                       "http://de.archive.ubuntu.com/ubuntu/"
                   ));
        }
        
        // Docker mirrors
        if (requiredServices == null || requiredServices.contains("docker")) {
            builder.dockerMirror("https://registry-1.docker.io")
                   .dockerBackupMirrors(Arrays.asList(
                       "https://index.docker.io/v1/",
                       "https://registry.hub.docker.com"
                   ));
        }
        
        // NPM mirrors
        if (requiredServices == null || requiredServices.contains("npm")) {
            builder.npmMirror("https://registry.npmjs.org")
                   .npmBackupMirrors(Arrays.asList(
                       "https://skimdb.npmjs.com/registry"
                   ));
        }
        
        // Maven mirrors
        if (requiredServices == null || requiredServices.contains("maven")) {
            builder.mavenMirror("https://repo1.maven.org/maven2")
                   .mavenBackupMirrors(Arrays.asList(
                       "https://repo.maven.apache.org/maven2",
                       "https://uk.maven.org/maven2"
                   ));
        }
        
        // PyPI mirrors
        if (requiredServices == null || requiredServices.contains("pypi")) {
            builder.pypiMirror("https://pypi.org/simple")
                   .pypiBackupMirrors(Collections.emptyList());
        }
        
        // Composer mirrors (PHP)
        if (requiredServices == null || requiredServices.contains("composer")) {
            builder.composerMirror("https://packagist.org")
                   .composerBackupMirrors(Collections.emptyList());
        }
        
        return builder.build();
    }
    
    /**
     * Clear mirror cache
     */
    public void clearCache() {
        mirrorCache.clear();
        log.info("Mirror cache cleared");
    }
    
    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", mirrorCache.size());
        stats.put("cacheTtlSeconds", cacheTtlSeconds);
        stats.put("speedTestEnabled", speedTestEnabled);
        stats.put("chinaMirrorsEnabled", chinaMirrorsEnabled);
        
        long validEntries = mirrorCache.values().stream()
            .mapToLong(entry -> entry.isExpired() ? 0 : 1)
            .sum();
        stats.put("validCacheEntries", validEntries);
        
        return stats;
    }
    
    /**
     * Cached mirror configuration with expiration
     */
    private static class CachedMirrorConfig {
        private final MirrorConfiguration configuration;
        private final long expirationTime;
        
        public CachedMirrorConfig(MirrorConfiguration configuration, long expirationTime) {
            this.configuration = configuration;
            this.expirationTime = expirationTime;
        }
        
        public MirrorConfiguration getConfiguration() {
            return configuration;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
}

/**
 * Mirror configuration data class
 */
@Data
@Builder
class MirrorConfiguration {
    private String region;
    private int priority;
    private String description;
    
    // APT (Ubuntu/Debian package manager)
    private String aptMirror;
    private List<String> aptBackupMirrors;
    
    // Docker registry
    private String dockerMirror;
    private List<String> dockerBackupMirrors;
    
    // NPM registry
    private String npmMirror;
    private List<String> npmBackupMirrors;
    
    // Maven repository
    private String mavenMirror;
    private List<String> mavenBackupMirrors;
    
    // PyPI (Python package index)
    private String pypiMirror;
    private List<String> pypiBackupMirrors;
    
    // Composer (PHP package manager)
    private String composerMirror;
    private List<String> composerBackupMirrors;
    
    /**
     * Convert to parameter map for script injection
     */
    public Map<String, Object> toParameterMap() {
        Map<String, Object> params = new HashMap<>();
        
        if (aptMirror != null) params.put("apt_mirror", aptMirror);
        if (dockerMirror != null) params.put("docker_mirror", dockerMirror);
        if (npmMirror != null) params.put("npm_mirror", npmMirror);
        if (mavenMirror != null) params.put("maven_mirror", mavenMirror);
        if (pypiMirror != null) params.put("pypi_mirror", pypiMirror);
        if (composerMirror != null) params.put("composer_mirror", composerMirror);
        
        // Add backup mirrors as comma-separated strings
        if (aptBackupMirrors != null && !aptBackupMirrors.isEmpty()) {
            params.put("apt_backup_mirrors", String.join(",", aptBackupMirrors));
        }
        if (dockerBackupMirrors != null && !dockerBackupMirrors.isEmpty()) {
            params.put("docker_backup_mirrors", String.join(",", dockerBackupMirrors));
        }
        if (npmBackupMirrors != null && !npmBackupMirrors.isEmpty()) {
            params.put("npm_backup_mirrors", String.join(",", npmBackupMirrors));
        }
        if (mavenBackupMirrors != null && !mavenBackupMirrors.isEmpty()) {
            params.put("maven_backup_mirrors", String.join(",", mavenBackupMirrors));
        }
        if (pypiBackupMirrors != null && !pypiBackupMirrors.isEmpty()) {
            params.put("pypi_backup_mirrors", String.join(",", pypiBackupMirrors));
        }
        if (composerBackupMirrors != null && !composerBackupMirrors.isEmpty()) {
            params.put("composer_backup_mirrors", String.join(",", composerBackupMirrors));
        }
        
        params.put("mirror_region", region);
        params.put("mirror_priority", priority);
        params.put("mirror_description", description);
        
        return params;
    }
    
    /**
     * Get mirror for specific service
     */
    public String getMirrorForService(String service) {
        switch (service.toLowerCase()) {
            case "apt": return aptMirror;
            case "docker": return dockerMirror;
            case "npm": return npmMirror;
            case "maven": return mavenMirror;
            case "pypi": return pypiMirror;
            case "composer": return composerMirror;
            default: return null;
        }
    }
    
    /**
     * Get backup mirrors for specific service
     */
    public List<String> getBackupMirrorsForService(String service) {
        switch (service.toLowerCase()) {
            case "apt": return aptBackupMirrors != null ? aptBackupMirrors : Collections.emptyList();
            case "docker": return dockerBackupMirrors != null ? dockerBackupMirrors : Collections.emptyList();
            case "npm": return npmBackupMirrors != null ? npmBackupMirrors : Collections.emptyList();
            case "maven": return mavenBackupMirrors != null ? mavenBackupMirrors : Collections.emptyList();
            case "pypi": return pypiBackupMirrors != null ? pypiBackupMirrors : Collections.emptyList();
            case "composer": return composerBackupMirrors != null ? composerBackupMirrors : Collections.emptyList();
            default: return Collections.emptyList();
        }
    }
}