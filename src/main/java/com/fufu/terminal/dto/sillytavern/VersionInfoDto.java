package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Docker版本信息DTO
 * 包含当前版本、最新版本和可用版本列表信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionInfoDto {
    
    /**
     * 容器名称
     */
    private String containerName;
    
    /**
     * 当前运行的版本
     */
    private String currentVersion;
    
    /**
     * 最新可用版本
     */
    private String latestVersion;
    
    /**
     * 是否有更新可用
     */
    private Boolean hasUpdate = false;
    
    /**
     * 可用版本列表（最多显示最新的3个版本）
     */
    private List<String> availableVersions;
    
    /**
     * 版本信息最后检查时间
     */
    private LocalDateTime lastChecked;
    
    /**
     * 当前版本的发布时间
     */
    private LocalDateTime currentVersionReleaseDate;
    
    /**
     * 最新版本的发布时间
     */
    private LocalDateTime latestVersionReleaseDate;
    
    /**
     * 版本更新描述
     */
    private String updateDescription;
    
    /**
     * 错误信息（如果查询版本信息失败）
     */
    private String error;
    
    /**
     * 创建一个错误状态的版本信息
     */
    public static VersionInfoDto error(String containerName, String errorMessage) {
        return VersionInfoDto.builder()
            .containerName(containerName)
            .currentVersion("unknown")
            .hasUpdate(false)
            .error(errorMessage)
            .lastChecked(LocalDateTime.now())
            .build();
    }
    
    /**
     * 创建一个基本的版本信息
     */
    public static VersionInfoDto basic(String containerName, String currentVersion) {
        return VersionInfoDto.builder()
            .containerName(containerName)
            .currentVersion(currentVersion)
            .hasUpdate(false)
            .lastChecked(LocalDateTime.now())
            .build();
    }
}