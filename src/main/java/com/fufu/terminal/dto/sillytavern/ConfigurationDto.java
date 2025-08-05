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
    
    private String containerName;
    
    private String username;
    
    private String password;  // Only used when updating, never returned when reading
    
    private Boolean hasPassword = false;  // Indicates if password is set (for read operations)
    
    private Integer port;
    
    private String serverName;
    
    private Boolean enableExtensions = true;
    
    private String theme;
    
    private String language = "en";
    
    private Boolean autoConnect = false;
    
    private String dataPath;
    
    /**
     * Configuration backup path (when updating)
     */
    private String backupPath;
    
    /**
     * Whether changes require container restart
     */
    private Boolean requiresRestart = false;
    
    /**
     * Other configuration settings not explicitly defined
     */
    private java.util.Map<String, String> otherSettings;
    
    public static ConfigurationDto forUpdate(String username, String password) {
        ConfigurationDto config = new ConfigurationDto();
        config.setUsername(username);
        config.setPassword(password);
        config.setRequiresRestart(true);
        return config;
    }
}