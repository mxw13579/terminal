package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Docker Hub版本信息DTO
 *
 * @author lizelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DockerHubVersionDto {
    
    /**
     * 标签名称
     */
    private String tagName;
    
    /**
     * 镜像大小（字节）
     */
    private Long imageSizeBytes;
    
    /**
     * 镜像大小（格式化字符串）
     */
    private String imageSize;
    
    /**
     * 最后推送时间
     */
    private LocalDateTime lastPushed;
    
    /**
     * 最后推送时间（格式化字符串）
     */
    private String lastPushedFormatted;
    
    /**
     * 镜像摘要
     */
    private String digest;
    
    /**
     * 架构信息
     */
    private String architecture;
    
    /**
     * 操作系统
     */
    private String os;
    
    /**
     * 是否为最新版本
     */
    private boolean isLatest;
}