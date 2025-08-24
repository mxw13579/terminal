package com.fufu.terminal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 统一的文件传输配置类
 * <p>
 * 该配置类统一管理文件上传下载相关的所有配置参数，避免在多个地方重复定义相同配置。
 * 从application.yml的file.transfer配置节读取参数。
 * </p>
 * 
 * @author lizelin
 */
@Data
@Component
@ConfigurationProperties(prefix = "file.transfer")
public class FileTransferConfig {
    
    /**
     * 单个文件最大大小（字节），默认2GB
     */
    private Long maxFileSize = 2147483648L;
    
    /**
     * 总文件大小限制（字节），默认2GB  
     */
    private Long maxTotalSize = 2147483648L;
    
    /**
     * 文件传输块大小（字节），默认256KB
     */
    private Integer chunkSize = 262144;
    
    /**
     * 最大并发传输数量，默认3个
     */
    private Integer maxConcurrent = 3;
    
    /**
     * 临时文件目录，默认使用系统临时目录
     */
    private String tempDir = System.getProperty("java.io.tmpdir");
    
    /**
     * 传输速率限制（字节/秒），默认10MB/s
     * 0表示无限制
     */
    private Long throttleBytesPerSecond = 10485760L;
    
    /**
     * 获取人类可读的最大文件大小
     */
    public String getMaxFileSizeFormatted() {
        return formatBytes(maxFileSize);
    }
    
    /**
     * 获取人类可读的最大总大小
     */
    public String getMaxTotalSizeFormatted() {
        return formatBytes(maxTotalSize);
    }
    
    /**
     * 获取人类可读的传输速率限制
     */
    public String getThrottleBytesPerSecondFormatted() {
        if (throttleBytesPerSecond == 0) {
            return "无限制";
        }
        return formatBytes(throttleBytesPerSecond) + "/s";
    }
    
    /**
     * 格式化字节数为人类可读格式
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * 验证配置参数的有效性
     */
    public boolean isConfigValid() {
        return maxFileSize > 0 
            && maxTotalSize >= maxFileSize
            && chunkSize > 0 
            && chunkSize <= maxFileSize
            && maxConcurrent > 0
            && throttleBytesPerSecond >= 0;
    }
}