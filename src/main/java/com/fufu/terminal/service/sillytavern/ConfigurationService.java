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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for managing SillyTavern configuration files.
 * Handles YAML file reading, writing, and validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurationService {
    
    private final SshCommandService sshCommandService;
    
    private static final String DEFAULT_CONFIG_PATH = "/app/data/config.yaml";
    private static final String BACKUP_PATH_TEMPLATE = "/app/data/config.yaml.backup.%s";
    
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
     * Update configuration with new values
     */
    public boolean updateConfiguration(SshConnection connection, String containerName, 
                                     ConfigurationDto config) throws Exception {
        log.info("Updating configuration for container: {}", containerName);
        
        // Create backup first
        String backupPath = createBackup(connection, containerName);
        log.info("Created configuration backup at: {}", backupPath);
        
        try {
            // Generate new configuration content
            String newConfigContent = generateConfigurationContent(config);
            
            // Write new configuration to container
            String tempFile = "/tmp/sillytavern_config_" + System.currentTimeMillis() + ".yaml";
            
            // Create temp file with new content
            String escapedContent = newConfigContent.replace("\"", "\\\"").replace("\n", "\\n");
            executeCommand(connection, String.format("echo -e \"%s\" > %s", escapedContent, tempFile));
            
            // Copy temp file to container
            executeCommand(connection, 
                String.format("sudo docker cp %s %s:%s", tempFile, containerName, DEFAULT_CONFIG_PATH));
            
            // Clean up temp file
            executeCommand(connection, "rm -f " + tempFile);
            
            log.info("Configuration updated successfully for container: {}", containerName);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to update configuration for container {}: {}", containerName, e.getMessage());
            
            // Attempt to restore backup
            try {
                restoreBackup(connection, containerName, backupPath);
                log.info("Configuration restored from backup due to update failure");
            } catch (Exception restoreError) {
                log.error("Failed to restore configuration backup: {}", restoreError.getMessage());
            }
            
            throw new RuntimeException("Configuration update failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate configuration structure and values
     */
    public Map<String, String> validateConfiguration(ConfigurationDto config) {
        Map<String, String> errors = new HashMap<>();
        
        if (config.getUsername() == null || config.getUsername().trim().isEmpty()) {
            errors.put("username", "Username cannot be empty");
        } else if (config.getUsername().length() < 3) {
            errors.put("username", "Username must be at least 3 characters long");
        } else if (!config.getUsername().matches("^[a-zA-Z0-9_-]+$")) {
            errors.put("username", "Username can only contain letters, numbers, underscore, and dash");
        }
        
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            if (config.getPassword().length() < 6) {
                errors.put("password", "Password must be at least 6 characters long");
            }
        }
        
        if (config.getPort() != null) {
            if (config.getPort() < 1024 || config.getPort() > 65535) {
                errors.put("port", "Port must be between 1024 and 65535");
            }
        }
        
        return errors;
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