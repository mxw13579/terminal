package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * SillyTavern配置管理DTO
 * 用于读取和更新SillyTavern的配置设置
 * 
 * @author lizelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigurationDto {
    
    /**
     * 容器名称
     * SillyTavern容器的唯一标识符
     */
    private String containerName;
    
    /**
     * 用户名
     * 用于SillyTavern登录认证的用户名
     */
    private String username;
    
    /**
     * 密码
     * 用于SillyTavern登录认证的密码，仅在更新时使用，读取时不会返回
     */
    private String password;  // Only used when updating, never returned when reading
    
    /**
     * 是否已设置密码
     * 标识是否已设置密码（用于读取操作），默认为false
     */
    private Boolean hasPassword = false;  // Indicates if password is set (for read operations)
    
    /**
     * 端口号
     * SillyTavern服务监听的端口号
     */
    private Integer port;
    
    /**
     * 服务器名称
     * SillyTavern服务器的名称
     */
    private String serverName;
    
    /**
     * 是否启用扩展功能
     * 标识是否启用SillyTavern的扩展功能，默认为true
     */
    private Boolean enableExtensions = true;
    
    /**
     * 主题
     * SillyTavern的用户界面主题
     */
    private String theme;
    
    /**
     * 语言设置
     * SillyTavern的界面语言，默认为"en"
     */
    private String language = "en";
    
    /**
     * 是否自动连接
     * 标识是否自动连接到服务器，默认为false
     */
    private Boolean autoConnect = false;
    
    /**
     * 数据路径
     * SillyTavern数据存储的路径
     */
    private String dataPath;
    
    /**
     * 备份路径
     * 配置备份路径（更新时使用）
     */
    private String backupPath;
    
    /**
     * 是否需要重启容器
     * 标识配置更改是否需要重启容器，默认为false
     */
    private Boolean requiresRestart = false;
    
    /**
     * 其他配置设置
     * 未明确定义的其他配置设置的映射表
     */
    private java.util.Map<String, String> otherSettings;
    
    /**
     * 创建用于更新配置的DTO
     * 创建一个包含用户名和密码的配置DTO，用于更新操作
     * 
     * @param username 用户名
     * @param password 密码
     * @return 包含用户名和密码的配置DTO
     */
    public static ConfigurationDto forUpdate(String username, String password) {
        ConfigurationDto config = new ConfigurationDto();
        config.setUsername(username);
        config.setPassword(password);
        config.setRequiresRestart(true);
        return config;
    }
}