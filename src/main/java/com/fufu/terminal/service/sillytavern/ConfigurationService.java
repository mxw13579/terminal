package com.fufu.terminal.service.sillytavern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.dto.sillytavern.ConfigurationDto;
import com.fufu.terminal.dto.sillytavern.DeploymentInfoDto;
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
 * SillyTavern 配置文件管理服务。
 * 提供配置的读取、写入、备份、恢复、校验等功能，支持多容器并发操作的线程安全控制。
 * <p>
 * Service for managing SillyTavern configuration files, including reading, writing, backup, restore, and validation.
 * Thread-safe for concurrent multi-container operations.
 *
 * @author
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurationService {

    private final SshCommandService sshCommandService;
    private final ObjectMapper objectMapper;

    /**
     * 针对每个容器维护独立锁，实现并发安全。
     * Thread-safe lock for each container.
     */
    private final Map<String, ReentrantLock> containerLocks = new ConcurrentHashMap<>();

    private static final String DEFAULT_CONFIG_PATH = "/data/docker/sillytavern/config/config.yaml";
    private static final String BACKUP_PATH_TEMPLATE = "/data/docker/sillytavern/config/config.yaml.backup.%s";
    private static final String DEPLOYMENT_INFO_PATH = "/data/docker/sillytavern/config/deployment-info.json";

    /**
     * 获取指定容器的配置文件路径。
     * Get the configuration file path for the specified container.
     *
     * @param containerName 容器名称
     * @return 配置文件路径
     */
    public String getConfigurationPath(String containerName) {
        return DEFAULT_CONFIG_PATH;
    }

    /**
     * 获取指定容器的独占锁。
     * Get the exclusive lock for a container.
     *
     * @param containerName 容器名称 Container name
     * @return 对应的 ReentrantLock The corresponding lock
     */
    private ReentrantLock getContainerLock(String containerName) {
        return containerLocks.computeIfAbsent(containerName, k -> new ReentrantLock());
    }

    /**
     * 读取指定容器中的配置文件内容并解析为 ConfigurationDto。
     * Read and parse configuration file from the specified container.
     *
     * @param connection    SSH 连接信息 SSH connection info
     * @param containerName 容器名称 Container name
     * @return 配置 DTO Configuration DTO
     * @throws Exception 读取或解析失败时抛出 Thrown if reading or parsing fails
     */
    public ConfigurationDto readConfiguration(SshConnection connection, String containerName) throws Exception {
        log.debug("读取容器配置: {}", containerName);
        // 1) 优先读取主机上的 deployment-info.json
        try {
            String deployInfoContent = executeCommand(
                    connection,
                    String.format("sudo cat %s", DEPLOYMENT_INFO_PATH)
            );
            ConfigurationDto config = parseDeploymentInfo(deployInfoContent);
            config.setContainerName(containerName);
            log.debug("成功从主机deployment-info.json读取配置: {}", DEPLOYMENT_INFO_PATH);
            return config;
        } catch (Exception e) {
            log.debug("主机deployment-info.json读取失败，将回退解析 config.yaml: {}", e.getMessage());
        }
        // 2) 回退解析主机上的 config.yaml 文件
        try {
            String configContent = executeCommand(
                    connection,
                    String.format("sudo cat %s", DEFAULT_CONFIG_PATH)
            );
            ConfigurationDto config = parseConfiguration(configContent);
            config.setContainerName(containerName);
            log.debug("成功从主机配置文件解析配置: {}", DEFAULT_CONFIG_PATH);
            return config;
        } catch (Exception e) {
            log.error("读取配置失败: {} - {}", containerName, e.getMessage());
            // 返回默认配置
            ConfigurationDto defaultConfig = new ConfigurationDto();
            defaultConfig.setContainerName(containerName);
            defaultConfig.setUsername("admin");
            defaultConfig.setPassword("password");
            defaultConfig.setHasPassword(false);
            defaultConfig.setPort(8000);
            log.debug("使用默认配置");
            return defaultConfig;
        }
    }

    /**
     * 从部署信息JSON文件解析配置
     * Parse configuration from deployment info JSON file
     *
     * @param deployInfoContent 部署信息JSON内容
     * @return 配置 DTO
     * @throws Exception 解析失败时抛出
     */
    private ConfigurationDto parseDeploymentInfo(String deployInfoContent) throws Exception {
        DeploymentInfoDto deploymentInfo = objectMapper.readValue(deployInfoContent, DeploymentInfoDto.class);

        ConfigurationDto config = new ConfigurationDto();

        if (deploymentInfo.getAuthentication() != null) {
            config.setUsername(deploymentInfo.getAuthentication().getUsername());
            config.setPassword(deploymentInfo.getAuthentication().getPassword());
            config.setHasPassword(deploymentInfo.getAuthentication().getPassword() != null &&
                                 !deploymentInfo.getAuthentication().getPassword().isEmpty());
        }

        if (deploymentInfo.getPorts() != null) {
            // 优先使用NAT外部端口，否则使用外部端口
            Integer displayPort = deploymentInfo.getPorts().getNatExternal() != null
                ? deploymentInfo.getPorts().getNatExternal()
                : deploymentInfo.getPorts().getExternal();
            config.setPort(displayPort);
        }

        // 存储完整的部署信息用于状态显示
        Map<String, String> otherSettings = new HashMap<>();
        if (deploymentInfo.getNetwork() != null) {
            if (deploymentInfo.getNetwork().getNatExternalHost() != null) {
                otherSettings.put("hostAddress", deploymentInfo.getNetwork().getNatExternalHost());
            } else {
                otherSettings.put("hostAddress", deploymentInfo.getNetwork().getExternalHost());
            }
        }
        if (deploymentInfo.getDeployment() != null) {
            otherSettings.put("environment", deploymentInfo.getDeployment().getEnvironment());
        }
        config.setOtherSettings(otherSettings);

        return config;
    }

    /**
     * 更新配置并在必要时自动重启容器，线程安全。
     * Update configuration and restart container if necessary (thread-safe).
     *
     * @param connection    SSH 连接 SSH connection
     * @param containerName 容器名称 Container name
     * @param config        新配置 New configuration
     * @return 是否更新成功 Whether update succeeded
     * @throws Exception 更新或重启失败时抛出 Thrown if update or restart fails
     */
    public boolean updateConfigurationWithRestart(SshConnection connection, String containerName,
                                                  ConfigurationDto config) throws Exception {
        ReentrantLock lock = getContainerLock(containerName);
        lock.lock();
        try {
            log.info("开始更新配置并检查是否需要重启容器: {}", containerName);
            ConfigurationDto currentConfig = readConfiguration(connection, containerName);
            boolean needsRestart = isRestartRequired(currentConfig, config);
            boolean updated = updateConfiguration(connection, containerName, config);
            if (updated && needsRestart) {
                log.info("配置更新需要重启容器: {}", containerName);
                if (isContainerRunning(connection, containerName)) {
                    log.info("正在重启容器...");
                    restartContainer(connection, containerName);
                    // 轮询等待容器运行，提升稳健性与平均等待时间
                    waitForContainerStartup(connection, containerName, 10_000L);
                    log.info("容器重启完成");
                } else {
                    log.info("容器未运行，配置将在下次启动时生效");
                }
            }
            log.info("配置更新操作完成，容器: {}", containerName);
            return updated;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 轮询等待容器在重启后进入运行状态，直到超时。
     *
     * @param connection    SSH 连接
     * @param containerName 容器名称
     * @param timeoutMs     超时时间（毫秒）
     * @throws Exception 超时或命令执行异常时抛出
     */
    private void waitForContainerStartup(SshConnection connection, String containerName, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (isContainerRunning(connection, containerName)) {
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new Exception("等待容器启动被中断", ie);
            }
        }
        throw new Exception("容器在指定时间内未启动: " + containerName);
    }

    /**
     * 检查容器是否正在运行。
     * Check if the container is running.
     *
     * @param connection    SSH 连接 SSH connection
     * @param containerName 容器名称 Container name
     * @return true=运行中 running, false=未运行 not running
     * @throws Exception SSH 命令执行异常 SSH command execution exception
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
     * 重启指定容器。
     * Restart the specified container.
     *
     * @param connection    SSH 连接 SSH connection
     * @param containerName 容器名称 Container name
     * @throws Exception SSH 命令执行异常 SSH command execution exception
     */
    private void restartContainer(SshConnection connection, String containerName) throws Exception {
        // 使用 docker compose 重启，这样可以确保配置重新加载
        String deploymentPath = "/data/docker/sillytavern";
        executeCommand(connection, String.format("cd %s && sudo docker compose restart", deploymentPath));
        log.info("使用 docker compose restart 重启了 SillyTavern 服务");
    }

    /**
     * 判断配置变更是否需要重启容器。
     * Determine if a restart is required after configuration change.
     *
     * @param currentConfig 当前配置 Current config
     * @param newConfig     新配置 New config
     * @return true=需要重启 restart required
     */
    private boolean isRestartRequired(ConfigurationDto currentConfig, ConfigurationDto newConfig) {
        // 用户名变化
        if (!java.util.Objects.equals(currentConfig.getUsername(), newConfig.getUsername())) {
            return true;
        }
        // 只要提供了新密码（非空），即认为需要重启
        if (newConfig.getPassword() != null && !newConfig.getPassword().isBlank()) {
            return true;
        }
        // 端口变化检测已移除 - 不再允许修改端口
        // Port change detection removed - port modification no longer allowed
        
        // 关键设置变化
        return !java.util.Objects.equals(currentConfig.getEnableExtensions(), newConfig.getEnableExtensions())
                || !java.util.Objects.equals(currentConfig.getAutoConnect(), newConfig.getAutoConnect());
    }

    /**
     * 更新配置文件，线程安全，失败时自动恢复备份。
     * Update configuration file (thread-safe), restore backup on failure.
     *
     * @param connection    SSH 连接 SSH connection
     * @param containerName 容器名称 Container name
     * @param config        新配置 New config
     * @return 是否更新成功 Whether update succeeded
     * @throws Exception 更新或恢复失败时抛出 Thrown if update or restore fails
     */
    public boolean updateConfiguration(SshConnection connection, String containerName,
                                       ConfigurationDto config) throws Exception {
        ReentrantLock lock = getContainerLock(containerName);
        lock.lock();
        try {
            log.info("开始更新配置，容器: {}", containerName);
            String backupPath = createBackup(connection, containerName);
            log.info("创建配置备份: {}", backupPath);
            try {
                // 读取现有配置文件并在其基础上修改
                String updatedConfigContent = updateExistingConfigurationContent(connection, config);
                String tempFile = "/tmp/sillytavern_config_" + System.currentTimeMillis() + ".yaml";
                
                // 通过 Base64 写入临时文件，避免特殊字符与换行转义问题
                String base64 = java.util.Base64.getEncoder()
                        .encodeToString(updatedConfigContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String writeCmd = String.format("printf '%%s' '%s' | base64 -d > %s", base64, tempFile);
                executeCommand(connection, writeCmd);
                
                // 确保配置目录存在
                executeCommand(connection, "sudo mkdir -p /data/docker/sillytavern/config");
                
                // 直接复制到主机文件系统的配置目录（而不是容器内部）
                executeCommand(connection, String.format("sudo cp %s %s", tempFile, DEFAULT_CONFIG_PATH));
                executeCommand(connection, "rm -f " + tempFile);
                
                log.info("配置文件已更新到主机路径: {}", DEFAULT_CONFIG_PATH);
                
                // 同步更新deployment-info.json文件
                try {
                    updateDeploymentInfoFromConfig(connection, containerName, config);
                    log.info("成功同步更新deployment-info.json文件");
                } catch (Exception e) {
                    log.warn("更新deployment-info.json失败，但配置文件更新成功: {}", e.getMessage());
                }
                
                return true;
            } catch (Exception e) {
                log.error("更新配置失败，容器: {} - {}", containerName, e.getMessage());
                try {
                    restoreBackup(connection, containerName, backupPath);
                    log.info("因更新失败已恢复配置备份");
                } catch (Exception restoreError) {
                    log.error("恢复配置备份失败: {}", restoreError.getMessage());
                }
                throw new Exception("配置更新失败: " + e.getMessage(), e);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 校验配置参数的结构和取值。
     * Validate configuration parameters.
     *
     * @param config 配置 DTO Configuration DTO
     * @return 校验错误信息 Map of field name to error message
     */
    public Map<String, String> validateConfiguration(ConfigurationDto config) {
        Map<String, String> errors = new HashMap<>();

        // 用户名校验 Username validation
        if (config.getUsername() == null || config.getUsername().trim().isEmpty()) {
            errors.put("username", "用户名不能为空");
        } else if (config.getUsername().length() < 3) {
            errors.put("username", "用户名长度不能少于3个字符");
        } else if (config.getUsername().length() > 20) {
            errors.put("username", "用户名长度不能超过20个字符");
        } else if (!config.getUsername().matches("^[a-zA-Z0-9_-]+$")) {
            errors.put("username", "用户名只能包含字母、数字、下划线和短横线");
        } else if (!config.getUsername().matches("^[a-zA-Z].*")) {
            errors.put("username", "用户名必须以字母开头");
        }

        // 密码校验 Password validation
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            if (config.getPassword().length() < 6) {
                errors.put("password", "密码长度不能少于6个字符");
            } else if (config.getPassword().length() > 64) {
                errors.put("password", "密码长度不能超过64个字符");
            }
        }

        // 端口校验已移除 - 不再允许修改端口
        // Port validation removed - port modification no longer allowed

        return errors;
    }

    /**
     * 密码强度检测：至少包含大小写字母和数字中的两种，且长度>=8。
     * 通过单次线性扫描替代多次正则匹配，降低常数开销。
     *
     * @param password 密码 Password
     * @return true=强密码 Strong password
     */
    private boolean isPasswordStrong(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean hasLower = false, hasUpper = false, hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (Character.isLowerCase(ch)) hasLower = true;
            else if (Character.isUpperCase(ch)) hasUpper = true;
            else if (Character.isDigit(ch)) hasDigit = true;
            if ((hasLower && hasUpper) || (hasLower && hasDigit) || (hasUpper && hasDigit)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建配置文件备份，返回备份路径。
     * Create a backup of the configuration file, return backup path.
     *
     * @param connection    SSH 连接 SSH connection
     * @param containerName 容器名称 Container name
     * @return 备份文件路径 Backup file path
     * @throws Exception 备份失败时抛出 Thrown if backup fails
     */
    public String createBackup(SshConnection connection, String containerName) throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupPath = String.format(BACKUP_PATH_TEMPLATE, timestamp);

        try {
            // 直接在主机文件系统创建备份
            executeCommand(connection,
                    String.format("sudo cp %s %s", DEFAULT_CONFIG_PATH, backupPath));
            log.info("创建配置备份成功: {}", backupPath);
            return backupPath;
        } catch (Exception e) {
            log.warn("创建配置备份失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 恢复配置文件备份。
     * Restore configuration file from backup.
     *
     * @param connection    SSH 连接 SSH connection
     * @param containerName 容器名称 Container name
     * @param backupPath    备份文件路径 Backup file path
     * @throws Exception 恢复失败时抛出 Thrown if restore fails
     */
    public void restoreBackup(SshConnection connection, String containerName, String backupPath) throws Exception {
        if (backupPath == null) {
            throw new Exception("未提供备份路径，无法恢复");
        }
        // 直接在主机文件系统恢复备份
        executeCommand(connection,
                String.format("sudo cp %s %s", backupPath, DEFAULT_CONFIG_PATH));
        log.info("配置备份恢复成功: {} -> {}", backupPath, DEFAULT_CONFIG_PATH);
    }

    /**
     * 解析配置文件内容为 ConfigurationDto。
     * Parse configuration file content to ConfigurationDto.
     *
     * @param configContent 配置文件内容 Config file content
     * @return 配置 DTO Configuration DTO
     */
    private ConfigurationDto parseConfiguration(String configContent) {
        ConfigurationDto config = new ConfigurationDto();

        // 解析用户名 Parse username
        Pattern usernamePattern = Pattern.compile("username:\\s*['\"]?([^'\"\\n]+)['\"]?", Pattern.CASE_INSENSITIVE);
        Matcher usernameMatcher = usernamePattern.matcher(configContent);
        if (usernameMatcher.find()) {
            config.setUsername(usernameMatcher.group(1).trim());
        }

        // 检查密码是否设置并获取密码值 Check if password is set and get password value
        Pattern passwordPattern = Pattern.compile("password:\\s*['\"]?([^'\"\\n]*)['\"]?", Pattern.CASE_INSENSITIVE);
        Matcher passwordMatcher = passwordPattern.matcher(configContent);
        if (passwordMatcher.find()) {
            String password = passwordMatcher.group(1).trim();
            config.setHasPassword(!password.isEmpty());
            config.setPassword(password); // 设置密码值用于状态显示
        }

        // 解析端口 Parse port
        Pattern portPattern = Pattern.compile("port:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher portMatcher = portPattern.matcher(configContent);
        if (portMatcher.find()) {
            config.setPort(Integer.parseInt(portMatcher.group(1)));
        }

        // 解析其他设置 Parse other settings
        Map<String, String> otherSettings = new HashMap<>();
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
     * 在现有配置文件基础上更新指定的配置项。
     * Update specific configuration items based on existing configuration file.
     *
     * @param connection SSH 连接 SSH connection
     * @param config     新配置 New config
     * @return 更新后的配置文件内容 Updated config file content
     * @throws Exception 读取或更新失败时抛出 Thrown if reading or updating fails
     */
    private String updateExistingConfigurationContent(SshConnection connection, ConfigurationDto config) throws Exception {
        try {
            // 读取现有配置文件
            String existingContent = executeCommand(connection, String.format("sudo cat %s", DEFAULT_CONFIG_PATH));
            log.debug("读取到现有配置文件，长度: {} 字符", existingContent.length());
            
            // 逐行处理配置文件，只更新指定的配置项
            StringBuilder updatedContent = new StringBuilder();
            String[] lines = existingContent.split("\n");
            
            // 跟踪我们是否在basicAuthUser块内
            boolean inBasicAuthUserBlock = false;
            int basicAuthUserIndentLevel = 0;
            
            for (String line : lines) {
                String trimmedLine = line.trim();
                String updatedLine = line; // 默认保持原有内容
                int currentLineIndent = getIndentLevel(line);
                
                // 检测是否进入或退出basicAuthUser块
                if (trimmedLine.equals("basicAuthUser:")) {
                    inBasicAuthUserBlock = true;
                    basicAuthUserIndentLevel = currentLineIndent;
                    log.debug("进入basicAuthUser块，缩进级别: {}", basicAuthUserIndentLevel);
                } else if (inBasicAuthUserBlock) {
                    // 检查是否退出basicAuthUser块：
                    // 1. 遇到空行不退出
                    // 2. 遇到注释行不退出
                    // 3. 遇到同级或更低级别的非用户名/密码字段时退出
                    if (!line.isEmpty() && !trimmedLine.startsWith("#") && 
                        currentLineIndent <= basicAuthUserIndentLevel && 
                        !trimmedLine.startsWith("username:") && 
                        !trimmedLine.startsWith("password:")) {
                        inBasicAuthUserBlock = false;
                        log.debug("退出basicAuthUser块，当前行: '{}', 缩进: {}", trimmedLine, currentLineIndent);
                    }
                }
                
                // 只更新用户名和密码，不修改端口
                // 更新顶层用户名（非basicAuthUser块内的）
                if (trimmedLine.startsWith("username:") && !inBasicAuthUserBlock && config.getUsername() != null) {
                    updatedLine = String.format("username: %s", config.getUsername());
                    log.debug("更新顶层用户名");
                }
                // 更新顶层密码（非basicAuthUser块内的）
                else if (trimmedLine.startsWith("password:") && !inBasicAuthUserBlock && config.getPassword() != null && !config.getPassword().isEmpty()) {
                    updatedLine = String.format("password: %s", config.getPassword());
                    log.debug("更新顶层密码");
                }
                // 不再更新端口 - 移除端口更新逻辑
                // 更新basicAuthMode
                else if (trimmedLine.startsWith("basicAuthMode:")) {
                    // 如果提供了密码，则启用basicAuth；否则禁用
                    boolean enableBasicAuth = config.getPassword() != null && !config.getPassword().isEmpty();
                    updatedLine = String.format("basicAuthMode: %s", enableBasicAuth);
                    log.debug("更新basicAuthMode: {}", enableBasicAuth);
                }
                // 更新basicAuthUser块内的username
                else if (trimmedLine.startsWith("username:") && inBasicAuthUserBlock && config.getUsername() != null) {
                    String indent = getIndentString(line);
                    updatedLine = String.format("%susername: %s", indent, config.getUsername());
                    log.debug("更新basicAuthUser块内用户名，缩进: '{}'", indent);
                }
                // 更新basicAuthUser块内的password
                else if (trimmedLine.startsWith("password:") && inBasicAuthUserBlock && config.getPassword() != null && !config.getPassword().isEmpty()) {
                    String indent = getIndentString(line);
                    updatedLine = String.format("%spassword: %s", indent, config.getPassword());
                    log.debug("更新basicAuthUser块内密码，缩进: '{}'", indent);
                }
                
                updatedContent.append(updatedLine).append("\n");
            }
            
            String result = updatedContent.toString();
            log.debug("配置文件更新完成，新长度: {} 字符", result.length());
            return result;
            
        } catch (Exception e) {
            log.warn("读取现有配置文件失败，将使用默认配置模板: {}", e.getMessage());
            // 如果读取失败，则使用默认模板
            return generateDefaultConfigurationContent(config);
        }
    }
    
    /**
     * 获取行的缩进级别
     */
    private int getIndentLevel(String line) {
        int indent = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') {
                indent++;
            } else if (c == '\t') {
                indent += 4; // 假设tab等于4个空格
            } else {
                break;
            }
        }
        return indent;
    }
    
    /**
     * 获取行的缩进字符串
     */
    private String getIndentString(String line) {
        StringBuilder indent = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == ' ' || c == '\t') {
                indent.append(c);
            } else {
                break;
            }
        }
        return indent.toString();
    }

    /**
     * 生成默认配置文件内容（当无法读取现有配置时使用）。
     * Generate default configuration file content (used when existing config cannot be read).
     *
     * @param config 配置 DTO Configuration DTO
     * @return 默认配置文件内容 Default config file content
     */
    private String generateDefaultConfigurationContent(ConfigurationDto config) {
        StringBuilder content = new StringBuilder();
        
        // 基础配置模板
        content.append("# SillyTavern Configuration\n");
        content.append("# Updated on: ").append(LocalDateTime.now()).append("\n\n");
        
        content.append("dataRoot: ./data\n");
        content.append("listen: false\n");
        content.append("listenAddress:\n");
        content.append("  ipv4: 0.0.0.0\n");
        content.append("  ipv6: \"[::]\"\n");
        
        // 端口设置
        if (config.getPort() != null) {
            content.append(String.format("port: %d\n", config.getPort()));
        } else {
            content.append("port: 8000\n");
        }
        
        content.append("whitelistMode: false\n");
        content.append("enableForwardedWhitelist: true\n");
        content.append("whitelist:\n");
        content.append("  - ::1\n");
        content.append("  - 127.0.0.1\n");
        content.append("whitelistDockerHosts: true\n");
        
        // 身份验证设置
        boolean hasPassword = config.getPassword() != null && !config.getPassword().isEmpty();
        content.append(String.format("basicAuthMode: %s\n", hasPassword));
        content.append("basicAuthUser:\n");
        content.append(String.format("  username: %s\n", config.getUsername() != null ? config.getUsername() : "admin"));
        if (hasPassword) {
            content.append(String.format("  password: %s\n", config.getPassword()));
        } else {
            content.append("  password: password\n");
        }
        
        // 其他基本设置
        content.append("enableCorsProxy: false\n");
        content.append("enableUserAccounts: false\n");
        content.append("sessionTimeout: -1\n");
        content.append("allowKeysExposure: false\n");
        content.append("skipContentCheck: false\n");
        
        return content.toString();
    }

    /**
     * 根据配置更新deployment-info.json文件。
     * Update deployment-info.json file based on configuration.
     *
     * @param connection    SSH 连接 SSH connection
     * @param containerName 容器名称 Container name
     * @param config        新配置 New config
     * @throws Exception 更新失败时抛出 Thrown if update fails
     */
    private void updateDeploymentInfoFromConfig(SshConnection connection, String containerName, ConfigurationDto config) throws Exception {
        try {
            // 读取现有的deployment-info.json
            DeploymentInfoDto deploymentInfo = null;
            try {
                String deployInfoContent = executeCommand(connection, String.format("sudo cat %s", DEPLOYMENT_INFO_PATH));
                if (!deployInfoContent.trim().isEmpty()) {
                    deploymentInfo = objectMapper.readValue(deployInfoContent, DeploymentInfoDto.class);
                }
            } catch (Exception e) {
                log.debug("读取现有deployment-info.json失败，将创建新配置: {}", e.getMessage());
            }
            
            // 如果没有现有配置，创建基本结构
            if (deploymentInfo == null) {
                deploymentInfo = DeploymentInfoDto.builder()
                        .deployment(DeploymentInfoDto.DeploymentInfo.builder()
                                .time(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                                .environment("production")
                                .build())
                        .build();
            }
            
            // 更新身份验证信息
            if (deploymentInfo.getAuthentication() == null) {
                deploymentInfo.setAuthentication(DeploymentInfoDto.AuthenticationInfo.builder().build());
            }
            
            if (config.getUsername() != null) {
                deploymentInfo.getAuthentication().setUsername(config.getUsername());
            }
            
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                deploymentInfo.getAuthentication().setPassword(config.getPassword());
            }
            
            // 端口信息更新已移除 - 不再允许修改端口
            // Port information update removed - port modification no longer allowed
            
            // 将更新后的配置写入文件
            String updatedJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(deploymentInfo);
            
            // 使用 Base64 编码写入临时文件，避免特殊字符问题
            String tempFile = "/tmp/deployment-info-config-" + System.currentTimeMillis() + ".json";
            String base64 = java.util.Base64.getEncoder()
                    .encodeToString(updatedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String writeCmd = String.format("printf '%%s' '%s' | base64 -d > %s", base64, tempFile);
            executeCommand(connection, writeCmd);
            
            // 确保配置目录存在
            executeCommand(connection, "sudo mkdir -p /data/docker/sillytavern/config");
            
            // 复制到正式位置
            executeCommand(connection, String.format("sudo cp %s %s", tempFile, DEPLOYMENT_INFO_PATH));
            
            // 清理临时文件
            executeCommand(connection, String.format("rm -f %s", tempFile));
            
            log.info("成功更新deployment-info.json中的配置信息，文件路径: {}", DEPLOYMENT_INFO_PATH);
            
        } catch (Exception e) {
            log.error("更新deployment-info.json失败: {}", e.getMessage(), e);
            throw new Exception("更新deployment-info.json失败: " + e.getMessage(), e);
        }
    }

    /**
     * 修复损坏的配置文件，从备份恢复或创建默认配置。
     * Fix corrupted configuration file by restoring from backup or creating default config.
     *
     * @param connection    SSH 连接 SSH connection
     * @param containerName 容器名称 Container name
     * @throws Exception 修复失败时抛出 Thrown if repair fails
     */
    public void repairConfigurationFile(SshConnection connection, String containerName) throws Exception {
        log.info("开始修复损坏的配置文件: {}", containerName);
        
        try {
            // 1. 先尝试从最新的备份恢复
            String findBackupCommand = String.format(
                "find /data/docker/sillytavern/config -name 'config.yaml.backup.*' -type f | sort -r | head -1"
            );
            CommandResult findResult = sshCommandService.executeInternal(connection.getJschSession(), findBackupCommand);
            
            if (findResult.exitStatus() == 0 && !findResult.stdout().trim().isEmpty()) {
                String latestBackup = findResult.stdout().trim();
                log.info("找到最新备份文件: {}", latestBackup);
                
                // 恢复备份
                executeCommand(connection, String.format("sudo cp %s %s", latestBackup, DEFAULT_CONFIG_PATH));
                log.info("从备份恢复配置文件成功");
                
                // 验证恢复后的文件
                if (validateYamlFile(connection, DEFAULT_CONFIG_PATH)) {
                    log.info("配置文件恢复成功且格式正确");
                    return;
                }
            }
            
            // 2. 如果备份不存在或也损坏，则创建默认配置
            log.warn("未找到有效备份，创建默认配置文件");
            createDefaultConfigFile(connection, containerName);
            
        } catch (Exception e) {
            log.error("修复配置文件失败: {}", e.getMessage(), e);
            throw new Exception("修复配置文件失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建默认配置文件。
     * Create default configuration file.
     *
     * @param connection    SSH 连接 SSH connection
     * @param containerName 容器名称 Container name
     * @throws Exception 创建失败时抛出 Thrown if creation fails
     */
    private void createDefaultConfigFile(SshConnection connection, String containerName) throws Exception {
        // 生成基础配置
        ConfigurationDto defaultConfig = new ConfigurationDto();
        defaultConfig.setUsername("admin");
        defaultConfig.setPassword("password123");
        defaultConfig.setPort(8000);
        
        String defaultContent = generateMinimalConfigurationContent(defaultConfig);
        
        // 写入文件
        String tempFile = "/tmp/default_config_" + System.currentTimeMillis() + ".yaml";
        String base64 = java.util.Base64.getEncoder()
                .encodeToString(defaultContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String writeCmd = String.format("printf '%%s' '%s' | base64 -d > %s", base64, tempFile);
        executeCommand(connection, writeCmd);
        
        // 确保配置目录存在
        executeCommand(connection, "sudo mkdir -p /data/docker/sillytavern/config");
        
        // 复制到正式位置
        executeCommand(connection, String.format("sudo cp %s %s", tempFile, DEFAULT_CONFIG_PATH));
        executeCommand(connection, String.format("rm -f %s", tempFile));
        
        log.info("创建默认配置文件成功: {}", DEFAULT_CONFIG_PATH);
    }
    
    /**
     * 生成精简版本的配置文件内容。
     * Generate minimal configuration file content.
     *
     * @param config 配置 DTO Configuration DTO
     * @return 精简配置文件内容 Minimal config file content
     */
    private String generateMinimalConfigurationContent(ConfigurationDto config) {
        StringBuilder content = new StringBuilder();
        
        content.append("# SillyTavern Configuration\n");
        content.append("# Auto-generated on: ").append(LocalDateTime.now()).append("\n\n");
        
        content.append("dataRoot: ./data\n");
        content.append("listen: false\n");
        content.append("listenAddress:\n");
        content.append("  ipv4: 0.0.0.0\n");
        content.append("  ipv6: \"[::]\"\n");
        content.append("protocol:\n");
        content.append("  ipv4: true\n");
        content.append("  ipv6: false\n");
        content.append("dnsPreferIPv6: false\n");
        
        // 端口设置
        content.append(String.format("port: %d\n", config.getPort() != null ? config.getPort() : 8000));
        
        content.append("whitelistMode: false\n");
        content.append("enableForwardedWhitelist: true\n");
        content.append("whitelist:\n");
        content.append("  - ::1\n");
        content.append("  - 127.0.0.1\n");
        content.append("whitelistDockerHosts: true\n");
        
        // 身份验证设置
        boolean hasPassword = config.getPassword() != null && !config.getPassword().isEmpty();
        content.append(String.format("basicAuthMode: %s\n", hasPassword));
        content.append("basicAuthUser:\n");
        content.append(String.format("  username: %s\n", config.getUsername() != null ? config.getUsername() : "admin"));
        content.append(String.format("  password: %s\n", hasPassword ? config.getPassword() : "password"));
        
        // 其他基本设置
        content.append("enableCorsProxy: false\n");
        content.append("enableUserAccounts: false\n");
        content.append("enableDiscreetLogin: false\n");
        content.append("autheliaAuth: false\n");
        content.append("perUserBasicAuth: false\n");
        content.append("sessionTimeout: -1\n");
        content.append("disableCsrfProtection: false\n");
        content.append("securityOverride: false\n");
        content.append("allowKeysExposure: false\n");
        content.append("skipContentCheck: false\n");
        content.append("enableDownloadableTokenizers: true\n");
        content.append("enableServerPlugins: false\n");
        content.append("enableServerPluginsAutoUpdate: true\n");
        
        return content.toString();
    }
    
    /**
     * 验证YAML文件格式是否正确。
     * Validate YAML file format.
     *
     * @param connection SSH 连接 SSH connection
     * @param filePath   文件路径 File path
     * @return 是否有效 Whether valid
     */
    private boolean validateYamlFile(SshConnection connection, String filePath) {
        try {
            // 使用python验证YAML格式
            String validateCmd = String.format(
                "python3 -c \"import yaml; yaml.safe_load(open('%s'))\" 2>/dev/null", filePath
            );
            CommandResult result = sshCommandService.executeInternal(connection.getJschSession(), validateCmd);
            return result.exitStatus() == 0;
        } catch (Exception e) {
            log.debug("YAML验证失败: {}", e.getMessage());
            return false;
        }
    }
    private String generateConfigurationContent(ConfigurationDto config) {
        StringBuilder content = new StringBuilder();

        content.append("# SillyTavern Configuration\n");
        content.append("# Generated on: ").append(LocalDateTime.now()).append("\n\n");

        // 认证设置 Authentication settings
        content.append("# Authentication Settings\n");
        content.append(String.format("username: \"%s\"\n", config.getUsername()));
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            content.append(String.format("password: \"%s\"\n", config.getPassword()));
        } else {
            content.append("password: \"\"\n");
        }

        // 服务器设置 Server settings
        content.append("\n# Server Settings\n");
        if (config.getPort() != null) {
            content.append(String.format("port: %d\n", config.getPort()));
        } else {
            content.append("port: 8000\n");
        }

        // 其他设置 Other settings
        if (config.getOtherSettings() != null && !config.getOtherSettings().isEmpty()) {
            content.append("\n# Additional Settings\n");
            config.getOtherSettings().forEach((key, value) ->
                    content.append(String.format("%s: \"%s\"\n", key, value)));
        }

        return content.toString();
    }

    /**
     * 通过 SSH 执行命令并返回标准输出内容（使用统一SSH命令服务API）。
     * Execute command via SSH and return stdout.
     *
     * @param connection SSH 连接 SSH connection
     * @param command    执行命令 Command to execute
     * @return 命令标准输出 Command stdout
     * @throws Exception 命令执行失败时抛出 Thrown if command fails
     */
    private String executeCommand(SshConnection connection, String command) throws Exception {
        try {
            // 使用内部API - 无安全检查（因为所有命令都是后端代码生成的）
            CommandResult result = sshCommandService.executeInternal(connection.getJschSession(), command);
            if (result.exitStatus() != 0) {
                String errorMsg = result.stderr().trim();
                if (errorMsg.isEmpty()) {
                    errorMsg = "命令执行失败，退出码: " + result.exitStatus();
                }
                throw new Exception(errorMsg);
            }
            return result.stdout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("命令执行被中断: " + command, e);
        } catch (RuntimeException re) {
            // 统一API已包含详细错误处理
            throw new Exception(re.getMessage(), re);
        }
    }

    /**
     * 更新deployment-info.json中的版本信息
     * Update the version information in deployment-info.json
     *
     * @param connection SSH 连接 SSH connection
     * @param containerName 容器名称 Container name
     * @param newVersion 新版本号 New version
     * @throws Exception 更新失败时抛出 Thrown if update fails
     */
    public void updateDeploymentVersion(SshConnection connection, String containerName, String newVersion) throws Exception {
        log.info("更新deployment-info.json中的版本信息: {} -> {}", containerName, newVersion);

        ReentrantLock lock = getContainerLock(containerName);
        lock.lock();
        try {
            // 读取现有的deployment-info.json（从主机文件系统）
            DeploymentInfoDto deploymentInfo = null;
            try {
                String deployInfoContent = executeCommand(connection,
                        String.format("sudo cat %s", DEPLOYMENT_INFO_PATH)
                );

                if (!deployInfoContent.trim().isEmpty()) {
                    deploymentInfo = objectMapper.readValue(deployInfoContent, DeploymentInfoDto.class);
                    log.debug("成功读取现有deployment-info.json");
                }
            } catch (Exception e) {
                log.warn("读取现有deployment-info.json失败，将创建新的配置: {}", e.getMessage());
            }

            // 如果没有现有配置，创建基本结构
            if (deploymentInfo == null) {
                deploymentInfo = DeploymentInfoDto.builder()
                        .deployment(DeploymentInfoDto.DeploymentInfo.builder()
                                .version(newVersion)
                                .time(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                                .environment("direct")
                                .build())
                        .build();
            } else {
                // 更新现有配置中的版本信息
                if (deploymentInfo.getDeployment() != null) {
                    deploymentInfo.getDeployment().setVersion(newVersion);
                    deploymentInfo.getDeployment().setTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                } else {
                    deploymentInfo.setDeployment(DeploymentInfoDto.DeploymentInfo.builder()
                            .version(newVersion)
                            .time(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                            .environment("direct")
                            .build());
                }
            }

            // 将更新后的配置写入文件
            String updatedJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(deploymentInfo);

            // 使用 Base64 编码写入临时文件，避免特殊字符问题
            String tempFile = "/tmp/deployment-info-" + System.currentTimeMillis() + ".json";
            String base64 = java.util.Base64.getEncoder()
                    .encodeToString(updatedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String writeCmd = String.format("printf '%%s' '%s' | base64 -d > %s", base64, tempFile);
            executeCommand(connection, writeCmd);

            // 确保配置目录存在
            executeCommand(connection, "sudo mkdir -p /data/docker/sillytavern/config");
            
            // 直接复制到主机文件系统的配置目录（而不是容器内部）
            executeCommand(connection, String.format("sudo cp %s %s", tempFile, DEPLOYMENT_INFO_PATH));
            
            // 清理临时文件
            executeCommand(connection, String.format("rm -f %s", tempFile));
            
            // 使用 docker compose 重启服务以应用更改
            String deploymentPath = "/data/docker/sillytavern";
            executeCommand(connection, String.format("cd %s && sudo docker compose restart", deploymentPath));

            log.info("成功更新deployment-info.json中的版本信息: {}，文件路径: {}", newVersion, DEPLOYMENT_INFO_PATH);

        } finally {
            lock.unlock();
        }
    }
}
