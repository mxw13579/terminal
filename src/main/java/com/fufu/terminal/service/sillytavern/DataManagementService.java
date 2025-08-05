package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.dto.sillytavern.DataExportDto;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * SillyTavern 数据管理服务，负责数据的导入导出、备份、校验等操作。
 * 增强安全性，使用 ProcessBuilder 和命令白名单机制。
 *
 * @author
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataManagementService {

    private final SshCommandService sshCommandService;
    private final FileCleanupService fileCleanupService;

    @Value("${sillytavern.temp.directory:./temp}")
    private String tempDirectory;

    @Value("${sillytavern.data.max-export-size:5368709120}") // 5GB
    private long maxExportSizeBytes;

    private static final String CONTAINER_DATA_PATH = "/app/data";
    private static final String TEMP_EXPORT_PATH = "/tmp/sillytavern_export";
    private static final String TEMP_IMPORT_PATH = "/tmp/sillytavern_import";
    private static final Set<String> ALLOWED_COMMANDS = Set.of("unzip", "file", "du", "ls");
    private static final Pattern SAFE_PATH_PATTERN = Pattern.compile("^[a-zA-Z0-9_/\\.-]+$");

    /**
     * 导出容器内数据目录为 ZIP 文件。
     *
     * @param connection SSH 连接
     * @param containerName 容器名称
     * @param progressCallback 进度回调
     * @return 异步返回导出数据 DTO
     */
    public CompletableFuture<DataExportDto> exportData(SshConnection connection, String containerName,
                                                       Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String exportFileName = String.format("sillytavern_data_%s_%s.zip", containerName, timestamp);
                String localExportPath = new File(tempDirectory, exportFileName).getAbsolutePath();

                progressCallback.accept("正在检查容器数据大小...");

                // 检查数据目录大小
                long dataSizeBytes = getDataDirectorySize(connection, containerName);
                if (dataSizeBytes > maxExportSizeBytes) {
                    throw new RuntimeException(String.format(
                            "数据目录过大: %d bytes (最大: %d bytes)",
                            dataSizeBytes, maxExportSizeBytes));
                }

                progressCallback.accept("正在容器内创建数据归档...");

                // 容器内创建 ZIP 归档
                String containerZipPath = String.format("%s_%s.zip", TEMP_EXPORT_PATH, timestamp);
                executeCommand(connection, String.format(
                        "sudo docker exec %s sh -c 'cd / && zip -r %s app/data/'",
                        containerName, containerZipPath));

                progressCallback.accept("正在复制归档到主机...");

                // 从容器复制 ZIP 到主机
                String hostZipPath = String.format("/tmp/sillytavern_export_%s.zip", timestamp);
                executeCommand(connection, String.format(
                        "sudo docker cp %s:%s %s", containerName, containerZipPath, hostZipPath));

                progressCallback.accept("正在传输文件到 Web 服务器...");

                // 创建本地临时目录
                new File(tempDirectory).mkdirs();

                // 从远程主机下载文件到本地
                downloadFileFromRemote(connection, hostZipPath, localExportPath);

                // 清理远程临时文件
                progressCallback.accept("正在清理临时文件...");
                executeCommand(connection, String.format("sudo docker exec %s rm -f %s", containerName, containerZipPath));
                executeCommand(connection, String.format("rm -f %s", hostZipPath));

                // 获取最终文件大小
                File exportFile = new File(localExportPath);
                long fileSizeBytes = exportFile.length();

                // 构建导出 DTO
                DataExportDto exportDto = new DataExportDto();
                exportDto.setFileName(exportFileName);
                exportDto.setDownloadUrl("/api/sillytavern/download/" + exportFileName);
                exportDto.setSizeBytes(fileSizeBytes);
                exportDto.setCreatedAt(LocalDateTime.now());
                exportDto.setExpiresAt(LocalDateTime.now().plusHours(1));

                // 安排文件清理
                fileCleanupService.scheduleCleanup(localExportPath, 1);

                progressCallback.accept("导出完成");
                log.info("数据导出完成: {} ({} bytes)", exportFileName, fileSizeBytes);

                return exportDto;

            } catch (Exception e) {
                log.error("数据导出失败", e);
                throw new RuntimeException("数据导出失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 导入上传的 ZIP 文件到容器。
     *
     * @param connection SSH 连接
     * @param containerName 容器名称
     * @param uploadedFileName 上传文件名
     * @param progressCallback 进度回调
     * @return 异步返回导入是否成功
     */
    public CompletableFuture<Boolean> importData(SshConnection connection, String containerName,
                                                 String uploadedFileName, Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String localZipPath = new File(tempDirectory, uploadedFileName).getAbsolutePath();
                File zipFile = new File(localZipPath);

                if (!zipFile.exists()) {
                    throw new RuntimeException("未找到上传文件: " + uploadedFileName);
                }

                progressCallback.accept("正在验证上传文件...");

                // 验证 ZIP 文件
                if (!isValidDataZip(localZipPath)) {
                    throw new RuntimeException("数据 ZIP 文件格式无效");
                }

                progressCallback.accept("正在上传文件到远程服务器...");

                // 上传文件到远程主机
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String remoteZipPath = String.format("/tmp/sillytavern_import_%s.zip", timestamp);
                uploadFileToRemote(connection, localZipPath, remoteZipPath);

                progressCallback.accept("正在复制文件到容器...");

                // 复制 ZIP 到容器
                String containerZipPath = String.format("%s_%s.zip", TEMP_IMPORT_PATH, timestamp);
                executeCommand(connection, String.format(
                        "sudo docker cp %s %s:%s", remoteZipPath, containerName, containerZipPath));

                progressCallback.accept("创建自动备份...");

                // 创建增强备份
                String backupInfo = createEnhancedBackup(connection, containerName, timestamp);
                if (backupInfo == null) {
                    throw new RuntimeException("创建数据备份失败");
                }

                progressCallback.accept("验证备份完整性...");

                // 验证备份完整性
                if (!verifyBackupIntegrity(connection, containerName, backupInfo)) {
                    throw new RuntimeException("备份完整性验证失败");
                }

                progressCallback.accept("正在解压导入数据...");

                try {
                    // 清空原数据目录（保留备份）
                    executeCommand(connection, String.format(
                            "sudo docker exec %s sh -c 'rm -rf %s/*'", containerName, CONTAINER_DATA_PATH));

                    // 解压导入数据
                    executeCommand(connection, String.format(
                            "sudo docker exec %s sh -c 'cd / && unzip -o %s'", containerName, containerZipPath));

                    // 校验解压结果
                    String dataCheck = executeCommand(connection, String.format(
                            "sudo docker exec %s ls -la %s", containerName, CONTAINER_DATA_PATH));

                    if (dataCheck.trim().isEmpty()) {
                        throw new RuntimeException("数据解压校验失败，目录为空");
                    }

                    progressCallback.accept("导入完成");

                } catch (Exception e) {
                    // 自动回滚
                    progressCallback.accept("导入失败，正在自动回滚...");
                    performAutomaticRollback(connection, containerName, backupInfo);
                    throw e;
                }

                // 清理临时文件
                progressCallback.accept("清理临时文件...");
                executeCommand(connection, String.format("sudo docker exec %s rm -f %s", containerName, containerZipPath));
                executeCommand(connection, String.format("rm -f %s", remoteZipPath));

                // 清理旧备份，仅保留最近 3 个
                cleanupOldBackups(connection, containerName);

                // 安排本地上传文件清理
                fileCleanupService.scheduleCleanup(localZipPath, 0);

                log.info("数据导入完成: {}", containerName);
                return true;

            } catch (Exception e) {
                log.error("数据导入失败: {}", containerName, e);
                throw new RuntimeException("数据导入失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 获取容器内数据目录大小（字节）。
     *
     * @param connection SSH 连接
     * @param containerName 容器名称
     * @return 数据目录大小（字节）
     * @throws Exception 命令执行异常
     */
    public long getDataDirectorySize(SshConnection connection, String containerName) throws Exception {
        String sizeOutput = executeCommand(connection, String.format(
                "sudo docker exec %s du -sb %s | cut -f1", containerName, CONTAINER_DATA_PATH));
        try {
            return Long.parseLong(sizeOutput.trim());
        } catch (NumberFormatException e) {
            log.warn("无法解析数据目录大小: {}", sizeOutput);
            return 0;
        }
    }

    /**
     * 验证 ZIP 文件结构和内容。
     *
     * @param zipPath ZIP 文件路径
     * @return 是否有效
     */
    private boolean isValidDataZip(String zipPath) {
        try {
            log.debug("验证 ZIP 文件: {}", zipPath);

            // 1. 检查 ZIP 文件完整性
            String integrityCheck = executeLocalCommand(String.format("unzip -t '%s'", zipPath));
            if (!integrityCheck.contains("No errors detected")) {
                log.warn("ZIP 文件完整性检查失败: {}", integrityCheck);
                return false;
            }

            // 2. 检查 ZIP 文件内容结构
            String listContents = executeLocalCommand(String.format("unzip -l '%s'", zipPath));
            String[] requiredPaths = {
                    "app/data/",
                    "app/data/config.yaml",
                    "app/data/characters/",
                    "app/data/chats/"
            };
            for (String requiredPath : requiredPaths) {
                if (!listContents.contains(requiredPath)) {
                    log.warn("ZIP 文件缺少必要路径: {}", requiredPath);
                    return false;
                }
            }

            // 3. 检查文件大小限制
            File zipFile = new File(zipPath);
            long fileSizeBytes = zipFile.length();
            if (fileSizeBytes > maxExportSizeBytes) {
                log.warn("ZIP 文件过大: {} bytes (限制: {} bytes)", fileSizeBytes, maxExportSizeBytes);
                return false;
            }

            // 4. 检查压缩包内的可疑文件
            if (containsSuspiciousFiles(listContents)) {
                log.warn("ZIP 文件包含可疑文件");
                return false;
            }

            log.info("ZIP 文件验证通过: {}", zipPath);
            return true;

        } catch (Exception e) {
            log.error("ZIP 文件验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查 ZIP 内容是否包含可疑文件。
     *
     * @param zipContents ZIP 内容列表
     * @return 是否包含可疑文件
     */
    private boolean containsSuspiciousFiles(String zipContents) {
        String[] suspiciousExtensions = {".exe", ".bat", ".sh", ".cmd", ".scr", ".vbs", ".jar"};
        String lowerContents = zipContents.toLowerCase();
        for (String extension : suspiciousExtensions) {
            if (lowerContents.contains(extension)) {
                return true;
            }
        }
        String[] suspiciousPaths = {"../", "../../", "/etc/", "/bin/", "/usr/", "c:\\", "d:\\"};
        for (String path : suspiciousPaths) {
            if (lowerContents.contains(path.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建增强的数据备份。
     *
     * @param connection SSH 连接
     * @param containerName 容器名称
     * @param timestamp 时间戳
     * @return 备份路径
     */
    private String createEnhancedBackup(SshConnection connection, String containerName, String timestamp) {
        try {
            String backupPath = String.format("/app/data_backup_%s", timestamp);

            // 1. 创建备份目录
            executeCommand(connection, String.format(
                    "sudo docker exec %s cp -r %s %s", containerName, CONTAINER_DATA_PATH, backupPath));

            // 2. 创建备份压缩包
            executeCommand(connection, String.format(
                    "sudo docker exec %s sh -c 'cd /app && tar -czf %s.tar.gz data_backup_%s'",
                    containerName, backupPath, timestamp));

            // 3. 记录备份信息
            String backupInfoPath = String.format("/app/backup_info_%s.txt", timestamp);
            String backupInfo = String.format("backup_time=%s\ncontainer=%s\nbackup_path=%s\nbackup_size=%s",
                    timestamp, containerName, backupPath, getBackupSize(connection, containerName, backupPath));

            executeCommand(connection, String.format(
                    "sudo docker exec %s sh -c 'echo \"%s\" > %s'", containerName, backupInfo, backupInfoPath));

            log.info("增强备份创建成功: {}", backupPath);
            return backupPath;

        } catch (Exception e) {
            log.error("创建增强备份失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 验证备份完整性。
     *
     * @param connection SSH 连接
     * @param containerName 容器名称
     * @param backupPath 备份路径
     * @return 是否完整
     */
    private boolean verifyBackupIntegrity(SshConnection connection, String containerName, String backupPath) {
        try {
            String checkBackup = executeCommand(connection, String.format(
                    "sudo docker exec %s ls -la %s", containerName, backupPath));
            if (checkBackup.trim().isEmpty()) {
                log.warn("备份目录为空: {}", backupPath);
                return false;
            }
            String[] criticalFiles = {"config.yaml", "characters", "chats"};
            for (String file : criticalFiles) {
                try {
                    executeCommand(connection, String.format(
                            "sudo docker exec %s ls %s/%s", containerName, backupPath, file));
                } catch (Exception e) {
                    log.warn("备份缺少关键文件: {}", file);
                    return false;
                }
            }
            log.info("备份完整性验证通过: {}", backupPath);
            return true;
        } catch (Exception e) {
            log.error("备份完整性验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 执行自动回滚。
     *
     * @param connection SSH 连接
     * @param containerName 容器名称
     * @param backupPath 备份路径
     */
    private void performAutomaticRollback(SshConnection connection, String containerName, String backupPath) {
        try {
            log.info("开始自动回滚，备份路径: {}", backupPath);

            // 移除损坏数据
            executeCommand(connection, String.format(
                    "sudo docker exec %s rm -rf %s", containerName, CONTAINER_DATA_PATH));

            // 恢复备份
            executeCommand(connection, String.format(
                    "sudo docker exec %s mv %s %s", containerName, backupPath, CONTAINER_DATA_PATH));

            // 验证回滚
            String verifyRollback = executeCommand(connection, String.format(
                    "sudo docker exec %s ls -la %s", containerName, CONTAINER_DATA_PATH));
            if (verifyRollback.trim().isEmpty()) {
                throw new RuntimeException("回滚后数据目录为空");
            }
            log.info("自动回滚完成");
        } catch (Exception e) {
            log.error("自动回滚失败: {}", e.getMessage());
            throw new RuntimeException("自动回滚失败，数据可能已损坏，请手动恢复: " + e.getMessage());
        }
    }

    /**
     * 清理旧备份，仅保留最近 3 个。
     *
     * @param connection SSH 连接
     * @param containerName 容器名称
     */
    private void cleanupOldBackups(SshConnection connection, String containerName) {
        try {
            String backupsCommand = String.format(
                    "sudo docker exec %s sh -c 'ls -1t /app/ | grep \"data_backup_\" | head -10'",
                    containerName);
            String backupsList = executeCommand(connection, backupsCommand);
            String[] backups = backupsList.split("\n");
            if (backups.length > 3) {
                for (int i = 3; i < backups.length; i++) {
                    if (!backups[i].trim().isEmpty()) {
                        try {
                            executeCommand(connection, String.format(
                                    "sudo docker exec %s rm -rf /app/%s", containerName, backups[i].trim()));
                            log.info("已删除旧备份: {}", backups[i].trim());
                        } catch (Exception e) {
                            log.warn("删除旧备份失败: {} - {}", backups[i].trim(), e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("清理旧备份时出错: {}", e.getMessage());
        }
    }

    /**
     * 获取备份大小。
     *
     * @param connection SSH 连接
     * @param containerName 容器名称
     * @param backupPath 备份路径
     * @return 备份大小字符串
     */
    private String getBackupSize(SshConnection connection, String containerName, String backupPath) {
        try {
            String sizeOutput = executeCommand(connection, String.format(
                    "sudo docker exec %s du -sh %s | cut -f1", containerName, backupPath));
            return sizeOutput.trim();
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 通过 SFTP 从远程主机下载文件到本地。
     *
     * @param connection SSH 连接
     * @param remotePath 远程路径
     * @param localPath 本地路径
     * @throws Exception SFTP 异常
     */
    private void downloadFileFromRemote(SshConnection connection, String remotePath, String localPath) throws Exception {
        log.debug("下载文件: {} -> {}", remotePath, localPath);
        try {
            var sftpChannel = connection.getOrCreateSftpChannel();
            sftpChannel.get(remotePath, localPath);
            log.info("文件下载成功: {}", localPath);
        } catch (Exception e) {
            log.error("文件下载失败: {}", e.getMessage());
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 通过 SFTP 上传文件到远程主机。
     *
     * @param connection SSH 连接
     * @param localPath 本地路径
     * @param remotePath 远程路径
     * @throws Exception SFTP 异常
     */
    private void uploadFileToRemote(SshConnection connection, String localPath, String remotePath) throws Exception {
        log.debug("上传文件: {} -> {}", localPath, remotePath);
        try {
            var sftpChannel = connection.getOrCreateSftpChannel();
            sftpChannel.put(localPath, remotePath);
            log.info("文件上传成功: {}", remotePath);
        } catch (Exception e) {
            log.error("文件上传失败: {}", e.getMessage());
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 通过 SSH 连接执行命令。
     *
     * @param connection SSH 连接
     * @param command 命令字符串
     * @return 命令标准输出
     * @throws Exception 命令执行异常
     */
    private String executeCommand(SshConnection connection, String command) throws Exception {
        try {
            CommandResult result = sshCommandService.executeCommand(connection.getJschSession(), command);
            if (result.exitStatus() != 0) {
                String errorMsg = "命令失败，退出码 " + result.exitStatus() + ": " + result.stderr();
                log.warn("命令执行失败: {} - {}", command, errorMsg);
                throw new RuntimeException(errorMsg);
            }
            return result.stdout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("命令执行被中断: " + command, e);
        }
    }

    /**
     * 安全执行本地命令（白名单与路径校验）。
     *
     * @param command 命令字符串
     * @return 命令输出
     * @throws Exception 命令执行异常
     */
    private String executeLocalCommand(String command) throws Exception {
        log.debug("执行本地命令: {}", command);

        String[] commandParts = command.split("\\s+");
        if (commandParts.length == 0) {
            throw new IllegalArgumentException("命令不能为空");
        }
        String baseCommand = commandParts[0];
        if (!ALLOWED_COMMANDS.contains(baseCommand)) {
            throw new SecurityException("不允许执行的命令: " + baseCommand);
        }
        for (int i = 1; i < commandParts.length; i++) {
            String arg = commandParts[i];
            if (arg.startsWith("-")) continue;
            if (!SAFE_PATH_PATTERN.matcher(arg).matches()) {
                throw new SecurityException("不安全的路径参数: " + arg);
            }
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(commandParts);
            processBuilder.redirectErrorStream(true);
            processBuilder.directory(new File(System.getProperty("java.io.tmpdir")));
            Map<String, String> env = processBuilder.environment();
            env.clear();
            env.put("PATH", "/usr/bin:/bin:/usr/local/bin");
            env.put("HOME", System.getProperty("java.io.tmpdir"));

            Process process = processBuilder.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new Exception("命令执行超时（30秒）");
            }
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errorMsg = String.format("命令执行失败，退出码: %d，输出: %s", exitCode, output.toString());
                log.warn("本地命令执行失败: {} - {}", command, errorMsg);
                throw new RuntimeException(errorMsg);
            }
            log.debug("本地命令执行成功: {}", command);
            return output.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("命令执行被中断: " + command, e);
        } catch (IOException e) {
            throw new Exception("命令执行 IO 错误: " + e.getMessage(), e);
        }
    }
}
