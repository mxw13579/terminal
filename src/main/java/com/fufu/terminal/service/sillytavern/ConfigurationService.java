package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.dto.sillytavern.ConfigurationDto;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SillyTavern配置文件管理服务 - 增强并发控制
 * 处理YAML文件的读取、写入和验证，确保线程安全
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurationService {
    
    private final SshCommandService sshCommandService;
    
    // 并发控制 - 为每个容器名称维护一个独立的锁
    private final Map<String, ReentrantLock> containerLocks = new ConcurrentHashMap<>();
    
    private static final String DEFAULT_CONFIG_PATH = "/app/data/config.yaml";
    private static final String BACKUP_PATH_TEMPLATE = "/app/data/config.yaml.backup.%s";
    
    /**
     * 获取容器操作锁
     */
    private ReentrantLock getContainerLock(String containerName) {
        return containerLocks.computeIfAbsent(containerName, k -> new ReentrantLock());
    }
    
    /**
     * Read current configuration from the container
     */
    public ConfigurationDto readConfiguration(SshConnection connection, String containerName) throws Exception {
        log.debug("Reading configuration for container: {}", containerName);
        
        try {
            // Get configuration file content from container
            String configContent = executeCommand(connection, 
                String.format("sudo docker exec %s cat %s", containerName, DEFAULT_CONFIG_PATH));
            
            ConfigurationDto config = parseConfiguration(configContent);
            config.setContainerName(containerName);
            
            return config;
            
        } catch (Exception e) {
            log.error("Failed to read configuration for container {}: {}", containerName, e.getMessage());
            // Return default configuration if file doesn't exist or can't be read
            ConfigurationDto defaultConfig = new ConfigurationDto();
            defaultConfig.setContainerName(containerName);
            defaultConfig.setUsername("admin");
            defaultConfig.setHasPassword(false);
            defaultConfig.setPort(8000);
            return defaultConfig;
        }
    }
    
    /**
     * 更新配置并在需要时自动重启容器 - 并发安全版本
     */
    public boolean updateConfigurationWithRestart(SshConnection connection, String containerName, 
                                                ConfigurationDto config) throws Exception {
        ReentrantLock lock = getContainerLock(containerName);
        lock.lock(); // 获取容器的排他锁
        
        try {
            log.info("开始更新配置并检查是否需要重启容器: {}", containerName);
            
            // 获取当前配置进行对比
            ConfigurationDto currentConfig = readConfiguration(connection, containerName);
            boolean needsRestart = isRestartRequired(currentConfig, config);
            
            // 更新配置
            boolean updated = updateConfiguration(connection, containerName, config);
            
            if (updated && needsRestart) {
                log.info("配置更新需要重启容器: {}", containerName);
                try {
                    // 检查容器是否运行中
                    boolean isRunning = isContainerRunning(connection, containerName);
                    
                    if (isRunning) {
                        log.info("正在重启容器以应用配置更改...");
                        restartContainer(connection, containerName);
                        log.info("容器重启完成");
                        
                        // 等待容器完全启动
                        Thread.sleep(3000);
                    } else {
                        log.info("容器未运行，配置更新完成，下次启动时将应用新配置");
                    }
                } catch (Exception e) {
                    log.error("重启容器失败: {}", e.getMessage());
                    throw new RuntimeException("配置更新成功但重启容器失败: " + e.getMessage(), e);
                }
            }
            
            log.info("配置更新操作完成，容器: {}", containerName);
            return updated;
            
        } finally {
            lock.unlock(); // 确保释放锁
        }
    }
    
    /**
     * 检查容器是否正在运行
     */
    private boolean isContainerRunning(SshConnection connection, String containerName) throws Exception {
        try {
            String result = executeCommand(connection, 
                String.format("sudo docker ps --filter name=%s --filter status=running --format '{{.ID}}'", containerName));
            return !result.trim().isEmpty();
        } catch (Exception e) {
            log.warn("检查容器状态失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 重启容器
     */
    private void restartContainer(SshConnection connection, String containerName) throws Exception {
        executeCommand(connection, String.format("sudo docker restart %s", containerName));
    }
    
    /**
     * 检查配置更改是否需要重启容器
     */
    private boolean isRestartRequired(ConfigurationDto currentConfig, ConfigurationDto newConfig) {
        // 用户名更改需要重启
        if (!java.util.Objects.equals(currentConfig.getUsername(), newConfig.getUsername())) {
            return true;
        }
        
        // 密码更改需要重启
        if (newConfig.getPassword() != null && !newConfig.getPassword().isEmpty()) {
            return true;
        }
        
        // 端口更改需要重启
        if (!java.util.Objects.equals(currentConfig.getPort(), newConfig.getPort())) {
            return true;
        }
        
        // 其他重要设置更改也需要重启
        if (!java.util.Objects.equals(currentConfig.getEnableExtensions(), newConfig.getEnableExtensions()) ||
            !java.util.Objects.equals(currentConfig.getAutoConnect(), newConfig.getAutoConnect())) {
            return true;
        }
        
        return false;
    }
    /**
     * 更新配置 - 线程安全版本
     */
    public boolean updateConfiguration(SshConnection connection, String containerName, 
                                     ConfigurationDto config) throws Exception {
        ReentrantLock lock = getContainerLock(containerName);
        lock.lock(); // 获取容器的排他锁
        
        try {
            log.info("开始更新配置，容器: {}", containerName);
            
            // 创建备份
            String backupPath = createBackup(connection, containerName);
            log.info("创建配置备份: {}", backupPath);
            
            try {
                // 生成新配置内容
                String newConfigContent = generateConfigurationContent(config);
                
                // 写入新配置到容器
                String tempFile = "/tmp/sillytavern_config_" + System.currentTimeMillis() + ".yaml";
                
                // 创建临时文件
                String escapedContent = newConfigContent.replace("\"", "\\\"").replace("\n", "\\n");
                executeCommand(connection, String.format("echo -e \"%s\" > %s", escapedContent, tempFile));
                
                // 复制临时文件到容器
                executeCommand(connection, 
                    String.format("sudo docker cp %s %s:%s", tempFile, containerName, DEFAULT_CONFIG_PATH));
                
                // 清理临时文件
                executeCommand(connection, "rm -f " + tempFile);
                
                log.info("配置更新成功，容器: {}", containerName);
                return true;
                
            } catch (Exception e) {
                log.error("更新配置失败，容器: {} - {}", containerName, e.getMessage());
                
                // 尝试恢复备份
                try {
                    restoreBackup(connection, containerName, backupPath);
                    log.info("因更新失败已恢复配置备份");
                } catch (Exception restoreError) {
                    log.error("恢复配置备份失败: {}", restoreError.getMessage());
                }
                
                throw new RuntimeException("配置更新失败: " + e.getMessage(), e);
            }
            
        } finally {
            lock.unlock(); // 确保释放锁
        }
    }
    
    /**
     * 验证配置结构和值
     * 技术规格要求：用户名不含数字验证，密码强度检查
     */
    public Map<String, String> validateConfiguration(ConfigurationDto config) {
        Map<String, String> errors = new HashMap<>();
        
        // 用户名验证 - 技术规格要求：不能包含数字
        if (config.getUsername() == null || config.getUsername().trim().isEmpty()) {
            errors.put("username", "用户名不能为空");
        } else if (config.getUsername().length() < 3) {
            errors.put("username", "用户名长度不能少于3个字符");
        } else if (config.getUsername().length() > 20) {
            errors.put("username", "用户名长度不能超过20个字符");
        } else if (config.getUsername().matches(".*\\d.*")) {
            errors.put("username", "用户名不能包含数字");
        } else if (!config.getUsername().matches("^[a-zA-Z_-]+$")) {
            errors.put("username", "用户名只能包含字母、下划线和短横线");
        }
        
        // 密码验证 - 增强密码强度检查
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            if (config.getPassword().length() < 6) {
                errors.put("password", "密码长度不能少于6个字符");
            } else if (config.getPassword().length() > 128) {
                errors.put("password", "密码长度不能超过128个字符");
            } else if (!isPasswordStrong(config.getPassword())) {
                errors.put("password", "密码强度不足，建议包含大小写字母、数字和特殊字符");
            }
        }
        
        // 端口验证
        if (config.getPort() != null) {
            if (config.getPort() < 1024 || config.getPort() > 65535) {
                errors.put("port", "端口必须在1024-65535之间");
            }
            
            // 检查常用端口冲突
            int[] reservedPorts = {22, 80, 443, 3306, 5432, 6379, 27017};
            for (int port : reservedPorts) {
                if (config.getPort() == port) {
                    errors.put("port", "端口 " + port + " 为系统保留端口，请选择其他端口");
                    break;
                }
            }
        }
        
        return errors;
    }
    
    /**
     * 检查密码强度
     */
    private boolean isPasswordStrong(String password) {
        // 至少包含一个小写字母、一个大写字母、一个数字
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        
        // 如果密码长度≥8且包含上述三种字符类型中的至少两种，认为是强密码
        return password.length() >= 8 && (
            (hasLower && hasUpper) || 
            (hasLower && hasDigit) || 
            (hasUpper && hasDigit)
        );
    }
    
    /**
     * Create a backup of the current configuration
     */
    public String createBackup(SshConnection connection, String containerName) throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupPath = String.format(BACKUP_PATH_TEMPLATE, timestamp);
        
        try {
            executeCommand(connection, 
                String.format("sudo docker exec %s cp %s %s", containerName, DEFAULT_CONFIG_PATH, backupPath));
            return backupPath;
        } catch (Exception e) {
            log.warn("Failed to create configuration backup: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Restore configuration from backup
     */
    private void restoreBackup(SshConnection connection, String containerName, String backupPath) throws Exception {
        if (backupPath == null) {
            throw new RuntimeException("No backup path provided");
        }
        
        executeCommand(connection, 
            String.format("sudo docker exec %s cp %s %s", containerName, backupPath, DEFAULT_CONFIG_PATH));
    }
    
    /**
     * Parse configuration content into ConfigurationDto
     */
    private ConfigurationDto parseConfiguration(String configContent) {
        ConfigurationDto config = new ConfigurationDto();
        
        // Parse username
        Pattern usernamePattern = Pattern.compile("username:\\s*['\"]?([^'\"\\n]+)['\"]?", Pattern.CASE_INSENSITIVE);
        Matcher usernameMatcher = usernamePattern.matcher(configContent);
        if (usernameMatcher.find()) {
            config.setUsername(usernameMatcher.group(1).trim());
        }
        
        // Check if password is set (look for password field)
        Pattern passwordPattern = Pattern.compile("password:\\s*['\"]?([^'\"\\n]*)['\"]?", Pattern.CASE_INSENSITIVE);
        Matcher passwordMatcher = passwordPattern.matcher(configContent);
        if (passwordMatcher.find()) {
            String password = passwordMatcher.group(1).trim();
            config.setHasPassword(!password.isEmpty());
        }
        
        // Parse port
        Pattern portPattern = Pattern.compile("port:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher portMatcher = portPattern.matcher(configContent);
        if (portMatcher.find()) {
            config.setPort(Integer.parseInt(portMatcher.group(1)));
        }
        
        // Parse other settings into a map
        Map<String, String> otherSettings = new HashMap<>();
        
        // Look for common settings
        String[] settingsToCheck = {"theme", "language", "autoSave", "enableLogging", "maxHistory"};
        for (String setting : settingsToCheck) {
            Pattern settingPattern = Pattern.compile(setting + ":\\s*['\"]?([^'\"\\n]+)['\"]?", Pattern.CASE_INSENSITIVE);
            Matcher settingMatcher = settingPattern.matcher(configContent);
            if (settingMatcher.find()) {
                otherSettings.put(setting, settingMatcher.group(1).trim());
            }
        }
        
        config.setOtherSettings(otherSettings);
        
        return config;
    }
    
    /**
     * Generate configuration file content from ConfigurationDto
     */
    private String generateConfigurationContent(ConfigurationDto config) {
        StringBuilder content = new StringBuilder();
        
        content.append("# SillyTavern Configuration\n");
        content.append("# Generated on: ").append(LocalDateTime.now()).append("\n\n");
        
        // Basic authentication settings
        content.append("# Authentication Settings\n");
        content.append("username: \"").append(config.getUsername()).append("\"\n");
        
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            content.append("password: \"").append(config.getPassword()).append("\"\n");
        } else {
            content.append("password: \"\"\n");
        }
        
        // Server settings
        content.append("\n# Server Settings\n");
        if (config.getPort() != null) {
            content.append("port: ").append(config.getPort()).append("\n");
        } else {
            content.append("port: 8000\n");
        }
        
        // Other settings
        if (config.getOtherSettings() != null && !config.getOtherSettings().isEmpty()) {
            content.append("\n# Additional Settings\n");
            for (Map.Entry<String, String> entry : config.getOtherSettings().entrySet()) {
                content.append(entry.getKey()).append(": \"").append(entry.getValue()).append("\"\n");
            }
        }
        
        return content.toString();
    }
    
    /**
     * Execute a command via SSH connection
     */
    private String executeCommand(SshConnection connection, String command) throws Exception {
        try {
            CommandResult result = sshCommandService.executeCommand(connection.getJschSession(), command);
            
            if (result.exitStatus() != 0) {
                String errorMsg = "Command failed with exit code " + result.exitStatus() + 
                               ": " + result.stderr();
                log.debug("Command execution failed: {} - {}", command, errorMsg);
                throw new RuntimeException(errorMsg);
            }
            
            return result.stdout();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Command execution was interrupted: " + command, e);
        }
    }
}