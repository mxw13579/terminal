package com.fufu.terminal.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import jakarta.servlet.MultipartConfigElement;

/**
 * 文件上传配置
 * 解决大文件上传限制问题，使用统一的FileTransferConfig配置
 */
@Configuration
@RequiredArgsConstructor
public class FileUploadConfig {
    
    private final FileTransferConfig fileTransferConfig;

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        
        // 使用统一配置设置单个文件最大大小
        factory.setMaxFileSize(DataSize.ofBytes(fileTransferConfig.getMaxFileSize()));
        
        // 使用统一配置设置总请求最大大小
        factory.setMaxRequestSize(DataSize.ofBytes(fileTransferConfig.getMaxTotalSize()));
        
        // 设置文件写入磁盘的阈值
        factory.setFileSizeThreshold(DataSize.ofKilobytes(10));
        
        return factory.createMultipartConfig();
    }
}