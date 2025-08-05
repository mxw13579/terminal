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
 * SillyTavern 配置文件管理服务，提供配置的读取、写入、备份、恢复、校验等功能。
 * 支持多容器并发操作的线程安全控制。
 *
 * @author
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurationService {

    private final SshCommandService sshCommandService;

    /**
     * 针对每个容器维护独立锁，实现并发安全。
     */
    private final Map<String, ReentrantLock> containerLocks = new ConcurrentHashMap<>();

    private static final String DEFAULT_CONFIG_PATH = "/app/data/config.yaml";
    private static final String BACKUP_PATH_TEMPLATE = "/app/data/config.yaml.backup.%s";

    /**
     * 获取指定容器的独占锁。
     *
     * @param containerName 容器名称
     * @return 对应的 ReentrantLock
     */
    private ReentrantLock getContainerLock(String containerName) {
        return containerLocks.computeIfAbsent(containerName, k -> new ReentrantLock());
    }

    /**
     * 读取指定容器中的配置文件内容并解析为 ConfigurationDto。
     *
     * @param connection    SSH 连接信息
     * @param containerName 容器名称
     * @return 配置 DTO
     * @throws Exception 读取或解析失败时抛出
     */
    public ConfigurationDto readConfiguration(SshConnection connection, String containerName) throws Exception {
        log.debug("读取容器配置: {}", containerName);

        try {
            String configContent = executeCommand(connection,
                    String.format("sudo docker exec %s cat %s", containerName, DEFAULT_CONFIG_PATH));
            ConfigurationDto config = parseConfiguration(configContent);
            config.setContainerName(containerName);
            return config;
        } catch (Exception e) {
            log.error("读取配置失败: {} - {}", containerName, e.getMessage());
            // 返回默认配置
            ConfigurationDto defaultConfig = new ConfigurationDto();
            defaultConfig.setContainerName(containerName);
            defaultConfig.setUsername("admin");
            defaultConfig.setHasPassword(false);
            defaultConfig.setPort(8000);
            return defaultConfig;
        }
    }

    /**
     * 更新配置并在必要时自动重启容器，线程安全。
     *
     * @param connection    SSH 连接
     * @param containerName 容器名称
     * @param config        新配置
     * @return 是否更新成功
     * @throws Exception 更新或重启失败时抛出
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
                try {
                    if (isContainerRunning(connection, containerName)) {
                        log.info("正在重启容器...");
                        restartContainer(connection, containerName);
                        log.info("容器重启完成");
                        Thread.sleep(3000); // 等待容器启动
                    } else {
                        log.info("容器未运行，配置将在下次启动时生效");
                    }
                } catch (Exception e) {
                    log.error("重启容器失败: {}", e.getMessage());
                    throw new RuntimeException("配置更新成功但重启容器失败: " + e.getMessage(), e);
                }
            }

            log.info("配置更新操作完成，容器: {}", containerName);
            return updated;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 检查容器是否正在运行。
     *
     * @param connection    SSH 连接
     * @param containerName 容器名称
     * @return true=运行中，false=未运行
     * @throws Exception SSH 命令执行异常
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
     *
     * @param connection    SSH 连接
     * @param containerName 容器名称
     * @throws Exception SSH 命令执行异常
     */
    private void restartContainer(SshConnection connection, String containerName) throws Exception {
        executeCommand(connection, String.format("sudo docker restart %s", containerName));
    }

    /**
     * 判断配置变更是否需要重启容器。
     *
     * @param currentConfig 当前配置
     * @param newConfig     新配置
     * @return true=需要重启
     */
    private boolean isRestartRequired(ConfigurationDto currentConfig, ConfigurationDto newConfig) {
        // 用户名、密码、端口、关键设置变更需重启
        if (!java.util.Objects.equals(currentConfig.getUsername(), newConfig.getUsername())) {
            return true;
        }
        if (newConfig.getPassword() != null && !newConfig.getPassword().isEmpty()) {
            return true;
        }
        if (!java.util.Objects.equals(currentConfig.getPort(), newConfig.getPort())) {
            return true;
        }
        if (!java.util.Objects.equals(currentConfig.getEnableExtensions(), newConfig.getEnableExtensions()) ||
                !java.util.Objects.equals(currentConfig.getAutoConnect(), newConfig.getAutoConnect())) {
            return true;
        }
        return false;
    }

    /**
     * 更新配置文件，线程安全，失败时自动恢复备份。
     *
     * @param connection    SSH 连接
     * @param containerName 容器名称
     * @param config        新配置
     * @return 是否更新成功
     * @throws Exception 更新或恢复失败时抛出
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
                String escapedContent = newConfigContent.replace("\"", "\\\"").replace("\n", "\\n");
                executeCommand(connection, String.format("echo -e \"%s\" > %s", escapedContent, tempFile));
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
                throw new RuntimeException("配置更新失败: " + e.getMessage(), e);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 校验配置参数的结构和取值。
     * 用户名不含数字，密码强度检测，端口范围及冲突检测。
     *
     * @param config 配置 DTO
     * @return 校验错误信息，key=字段名，value=错误描述
     */
    public Map<String, String> validateConfiguration(ConfigurationDto config) {
        Map<String, String> errors = new HashMap<>();

        // 用户名校验
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

        // 密码校验
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            if (config.getPassword().length() < 6) {
                errors.put("password", "密码长度不能少于6个字符");
            } else if (config.getPassword().length() > 128) {
                errors.put("password", "密码长度不能超过128个字符");
            } else if (!isPasswordStrong(config.getPassword())) {
                errors.put("password", "密码强度不足，建议包含大小写字母、数字和特殊字符");
            }
        }

        // 端口校验
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
     *
     * @param password 密码
     * @return true=强密码
     */
    private boolean isPasswordStrong(String password) {
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        return password.length() >= 8 && (
                (hasLower && hasUpper) ||
                        (hasLower && hasDigit) ||
                        (hasUpper && hasDigit)
        );
    }

    /**
     * 创建配置文件备份，返回备份路径。
     *
     * @param connection    SSH 连接
     * @param containerName 容器名称
     * @return 备份文件路径
     * @throws Exception 备份失败时抛出
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
     *
     * @param connection    SSH 连接
     * @param containerName 容器名称
     * @param backupPath    备份文件路径
     * @throws Exception 恢复失败时抛出
     */
    private void restoreBackup(SshConnection connection, String containerName, String backupPath) throws Exception {
        if (backupPath == null) {
            throw new RuntimeException("未提供备份路径，无法恢复");
        }
        executeCommand(connection,
                String.format("sudo docker exec %s cp %s %s", containerName, backupPath, DEFAULT_CONFIG_PATH));
    }

    /**
     * 解析配置文件内容为 ConfigurationDto。
     *
     * @param configContent 配置文件内容
     * @return 配置 DTO
     */
    private ConfigurationDto parseConfiguration(String configContent) {
        ConfigurationDto config = new ConfigurationDto();

        // 解析用户名
        Pattern usernamePattern = Pattern.compile("username:\\s*['\"]?([^'\"\\n]+)['\"]?", Pattern.CASE_INSENSITIVE);
        Matcher usernameMatcher = usernamePattern.matcher(configContent);
        if (usernameMatcher.find()) {
            config.setUsername(usernameMatcher.group(1).trim());
        }

        // 检查密码是否设置
        Pattern passwordPattern = Pattern.compile("password:\\s*['\"]?([^'\"\\n]*)['\"]?", Pattern.CASE_INSENSITIVE);
        Matcher passwordMatcher = passwordPattern.matcher(configContent);
        if (passwordMatcher.find()) {
            String password = passwordMatcher.group(1).trim();
            config.setHasPassword(!password.isEmpty());
        }

        // 解析端口
        Pattern portPattern = Pattern.compile("port:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher portMatcher = portPattern.matcher(configContent);
        if (portMatcher.find()) {
            config.setPort(Integer.parseInt(portMatcher.group(1)));
        }

        // 解析其他设置
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
     *
     * @param config 配置 DTO
     * @return 配置文件内容字符串
     */
    private String generateConfigurationContent(ConfigurationDto config) {
        StringBuilder content = new StringBuilder();

        content.append("# SillyTavern Configuration\n");
        content.append("# Generated on: ").append(LocalDateTime.now()).append("\n\n");

        // 认证设置
        content.append("# Authentication Settings\n");
        content.append("username: \"").append(config.getUsername()).append("\"\n");
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            content.append("password: \"").append(config.getPassword()).append("\"\n");
        } else {
            content.append("password: \"\"\n");
        }

        // 服务器设置
        content.append("\n# Server Settings\n");
        if (config.getPort() != null) {
            content.append("port: ").append(config.getPort()).append("\n");
        } else {
            content.append("port: 8000\n");
        }

        // 其他设置
        if (config.getOtherSettings() != null && !config.getOtherSettings().isEmpty()) {
            content.append("\n# Additional Settings\n");
            for (Map.Entry<String, String> entry : config.getOtherSettings().entrySet()) {
                content.append(entry.getKey()).append(": \"").append(entry.getValue()).append("\"\n");
            }
        }

        return content.toString();
    }

    /**
     * 通过 SSH 执行命令并返回标准输出内容。
     *
     * @param connection SSH 连接
     * @param command    执行命令
     * @return 命令标准输出
     * @throws Exception 命令执行失败时抛出
     */
    private String executeCommand(SshConnection connection, String command) throws Exception {
        try {
            CommandResult result = sshCommandService.executeCommand(connection.getJschSession(), command);
            if (result.exitStatus() != 0) {
                String errorMsg = "命令执行失败，退出码 " + result.exitStatus() +
                        ": " + result.stderr();
                log.debug("命令执行失败: {} - {}", command, errorMsg);
                throw new RuntimeException(errorMsg);
            }
            return result.stdout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("命令执行被中断: " + command, e);
        }
    }
}
