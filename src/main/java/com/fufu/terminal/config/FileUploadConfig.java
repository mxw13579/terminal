package com.fufu.terminal.config;

import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import jakarta.servlet.MultipartConfigElement;

/**
 * 文件上传配置
 * 解决大文件上传限制问题
 */
@Configuration
public class FileUploadConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        
        // 设置单个文件最大大小为2GB
        factory.setMaxFileSize(DataSize.ofBytes(2147483648L));
        
        // 设置总请求最大大小为2GB
        factory.setMaxRequestSize(DataSize.ofBytes(2147483648L));
        
        // 设置文件写入磁盘的阈值为10KB
        factory.setFileSizeThreshold(DataSize.ofKilobytes(10));
        
        return factory.createMultipartConfig();
    }
}