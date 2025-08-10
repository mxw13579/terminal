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

    private static final String DEFAULT_CONFIG_PATH = "/data/docker/sillytavern/config.yaml";
    private static final String BACKUP_PATH_TEMPLATE = "/data/docker/sillytavern/config.yaml.backup.%s";
    private static final String DEPLOYMENT_INFO_PATH = "/data/docker/sillytavern/deployment-info.json";

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
        // 1) 优先读取容器内部署信息（修复路径问题）
        try {
            // 先尝试从config目录读取，如果失败则尝试应用根目录
            String deployInfoContent = executeCommand(
                    connection,
                    String.format("sudo docker exec %s sh -c 'cat /home/node/app/config/deployment-info.json 2>/dev/null || cat /home/node/app/deployment-info.json 2>/dev/null'", containerName)
            );
            ConfigurationDto config = parseDeploymentInfo(deployInfoContent);
            config.setContainerName(containerName);
            log.debug("成功从 deployment-info.json 读取配置");
            return config;
        } catch (Exception e) {
            log.debug("部署信息读取失败，将回退解析 config.yaml: {}", e.getMessage());
        }
        // 2) 回退解析容器内的 config.yaml（修复了原先 String.format 用法错误）
        try {
            String configContent = executeCommand(
                    connection,
                    String.format("sudo docker exec %s cat %s", containerName, DEFAULT_CONFIG_PATH)
            );
            ConfigurationDto config = parseConfiguration(configContent);
            config.setContainerName(containerName);
            log.debug("成功从 config.yaml 解析配置");
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
        executeCommand(connection, String.format("sudo docker restart %s", containerName));
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
        // 端口变化
        if (!java.util.Objects.equals(currentConfig.getPort(), newConfig.getPort())) {
            return true;
        }
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
                String newConfigContent = generateConfigurationContent(config);
                String tempFile = "/tmp/sillytavern_config_" + System.currentTimeMillis() + ".yaml";
                // 通过 Base64 写入临时文件，避免特殊字符与换行转义问题
                String base64 = java.util.Base64.getEncoder()
                        .encodeToString(newConfigContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String writeCmd = String.format("printf '%%s' '%s' | base64 -d > %s", base64, tempFile);
                executeCommand(connection, writeCmd);
                executeCommand(connection,
                        String.format("sudo docker cp %s %s:%s", tempFile, containerName, DEFAULT_CONFIG_PATH));
                executeCommand(connection, "rm -f " + tempFile);
                log.info("配置更新成功，容器: {}", containerName);
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
            } else if (config.getPassword().length() > 128) {
                errors.put("password", "密码长度不能超过128个字符");
            } else if (!isPasswordStrong(config.getPassword())) {
                errors.put("password", "密码强度不足，建议包含大小写字母、数字和特殊字符");
            }
        }

        // 端口校验 Port validation
        if (config.getPort() != null) {
            if (config.getPort() < 1024 || config.getPort() > 65535) {
                errors.put("port", "端口必须在1024-65535之间");
            }
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
            executeCommand(connection,
                    String.format("sudo docker exec %s cp %s %s", containerName, DEFAULT_CONFIG_PATH, backupPath));
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
        executeCommand(connection,
                String.format("sudo docker exec %s cp %s %s", containerName, backupPath, DEFAULT_CONFIG_PATH));
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
     * 根据配置 DTO 生成配置文件内容。
     * Generate configuration file content from DTO.
     *
     * @param config 配置 DTO Configuration DTO
     * @return 配置文件内容字符串 Config file content string
     */
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
}
