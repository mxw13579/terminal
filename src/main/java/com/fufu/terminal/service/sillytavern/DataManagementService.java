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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

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
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String BACKUP_PREFIX = "data_backup_";


    /**
     * 异步导出指定Docker容器内的数据为ZIP文件。
     * <p>
     * 流程包括：
     * 1. 检查数据大小是否超限。
     * 2. 在容器内将数据目录压缩成ZIP文件。
     * 3. 将ZIP文件从容器复制到宿主机。
     * 4. 从宿主机下载ZIP文件到本应用服务器。
     * 5. 清理远程临时文件。
     * 6. 返回包含下载链接和文件信息的DTO，并安排本地文件的定时清理。
     *
     * @param connection       SSH连接对象，用于与远程主机通信。
     * @param containerName    目标Docker容器的名称。
     * @param progressCallback 用于报告操作进度的回调函数。
     * @return 一个 {@link CompletableFuture}，其结果为包含导出文件信息的 {@link DataExportDto}。
     */
    public CompletableFuture<DataExportDto> exportData(SshConnection connection, String containerName,
                                                       Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
                String exportFileName = generateExportFileName(containerName, timestamp);
                Path localExportPath = Paths.get(tempDirectory, exportFileName);
                progressCallback.accept("正在检查容器数据大小...");
                long dataSizeBytes = getDataDirectorySize(connection, containerName);
                if (dataSizeBytes > maxExportSizeBytes) {
                    throw new RuntimeException(String.format(
                            "数据目录过大: %,d bytes (最大: %,d bytes)",
                            dataSizeBytes, maxExportSizeBytes));
                }
                progressCallback.accept("正在容器内创建数据归档...");
                String containerZipPath = String.format("%s_%s.zip", TEMP_EXPORT_PATH, timestamp);
                executeCommand(connection, String.format(
                        "sudo docker exec %s sh -c 'cd / && zip -r %s app/data/'",
                        containerName, containerZipPath));
                progressCallback.accept("正在复制归档到主机...");
                String hostZipPath = String.format("/tmp/%s", exportFileName);
                executeCommand(connection, String.format(
                        "sudo docker cp %s:%s %s", containerName, containerZipPath, hostZipPath));
                progressCallback.accept("正在传输文件到 Web 服务器...");
                Files.createDirectories(localExportPath.getParent());
                downloadFileFromRemote(connection, hostZipPath, localExportPath.toString());
                progressCallback.accept("正在清理临时文件...");
                executeCommand(connection, String.format("sudo docker exec %s rm -f %s", containerName, containerZipPath));
                executeCommand(connection, String.format("rm -f %s", hostZipPath));
                long fileSizeBytes = Files.size(localExportPath);
                DataExportDto exportDto = new DataExportDto();
                exportDto.setFileName(exportFileName);
                exportDto.setDownloadUrl("/api/sillytavern/download/" + exportFileName);
                exportDto.setSizeBytes(fileSizeBytes);
                exportDto.setCreatedAt(LocalDateTime.now());
                exportDto.setExpiresAt(LocalDateTime.now().plusHours(1));
                fileCleanupService.scheduleCleanup(localExportPath.toString(), 1);
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
     * 生成导出文件的标准名称。
     *
     * @param containerName 容器名。
     * @param timestamp     时间戳字符串。
     * @return 格式化的文件名。
     */
    private String generateExportFileName(String containerName, String timestamp) {
        return String.format("sillytavern_data_%s_%s.zip", containerName, timestamp);
    }

    /**
     * 异步导入上传的ZIP文件到指定的Docker容器。
     * <p>
     * 流程包括：
     * 1. 验证本地上传的ZIP文件是否有效（结构、内容安全）。
     * 2. 上传ZIP文件到远程宿主机。
     * 3. 将ZIP文件从宿主机复制到容器内。
     * 4. 在容器内创建当前数据的备份。
     * 5. 验证备份的完整性。
     * 6. 清空旧数据并解压新数据。
     * 7. 如果解压失败，则自动从备份回滚。
     * 8. 清理所有临时文件和过时的备份。
     *
     * @param connection       SSH连接对象。
     * @param containerName    目标Docker容器的名称。
     * @param uploadedFileName 已上传到本应用服务器的ZIP文件名。
     * @param progressCallback 用于报告操作进度的回调函数。
     * @return 一个 {@link CompletableFuture}，其结果为布尔值，表示导入是否成功。
     */
    public CompletableFuture<Boolean> importData(SshConnection connection, String containerName,
                                                 String uploadedFileName, Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            Path localZipPath = Paths.get(tempDirectory, uploadedFileName);
            try {
                if (!Files.exists(localZipPath)) {
                    throw new RuntimeException("未找到上传文件: " + uploadedFileName);
                }
                progressCallback.accept("正在验证上传文件...");
                if (!isValidDataZip(localZipPath)) {
                    throw new RuntimeException("数据 ZIP 文件格式无效或包含不安全内容");
                }
                progressCallback.accept("正在上传文件到远程服务器...");
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
                String remoteZipPath = String.format("/tmp/sillytavern_import_%s.zip", timestamp);
                uploadFileToRemote(connection, localZipPath.toString(), remoteZipPath);
                progressCallback.accept("正在复制文件到容器...");
                String containerZipPath = String.format("%s_%s.zip", TEMP_IMPORT_PATH, timestamp);
                executeCommand(connection, String.format(
                        "sudo docker cp %s %s:%s", remoteZipPath, containerName, containerZipPath));
                progressCallback.accept("创建自动备份...");
                String backupPath = createEnhancedBackup(connection, containerName, timestamp);
                progressCallback.accept("验证备份完整性...");
                if (!verifyBackupIntegrity(connection, containerName, backupPath)) {
                    throw new RuntimeException("备份完整性验证失败");
                }
                progressCallback.accept("正在解压导入数据...");
                try {
                    executeCommand(connection, String.format(
                            "sudo docker exec %s sh -c 'rm -rf %s/*'", containerName, CONTAINER_DATA_PATH));
                    executeCommand(connection, String.format(
                            "sudo docker exec %s sh -c 'cd / && unzip -o %s'", containerName, containerZipPath));
                    String dataCheck = executeCommand(connection, String.format(
                            "sudo docker exec %s ls -A %s", containerName, CONTAINER_DATA_PATH));
                    if (dataCheck.trim().isEmpty()) {
                        throw new RuntimeException("数据解压校验失败，目录为空");
                    }
                    progressCallback.accept("导入完成");
                } catch (Exception e) {
                    progressCallback.accept("导入失败，正在自动回滚...");
                    performAutomaticRollback(connection, containerName, backupPath);
                    throw e;
                }
                progressCallback.accept("清理临时文件...");
                executeCommand(connection, String.format("sudo docker exec %s rm -f %s", containerName, containerZipPath));
                executeCommand(connection, String.format("rm -f %s", remoteZipPath));
                cleanupOldBackups(connection, containerName, 3);
                fileCleanupService.scheduleCleanup(localZipPath.toString(), 0);
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
     * 使用纯Java API验证ZIP文件的完整性、结构和内容安全性。
     *
     * @param zipPath 指向待验证ZIP文件的路径。
     * @return 如果文件有效且安全，则返回true；否则返回false。
     */
    private boolean isValidDataZip(Path zipPath) {
        final Set<String> requiredDirs = Set.of("app/data/", "app/data/characters/", "app/data/chats/");
        final Set<String> suspiciousExtensions = Set.of(".exe", ".bat", ".sh", ".cmd", ".scr", ".vbs", ".jar");
        try {
            // 1. 检查文件大小
            if (Files.size(zipPath) > maxExportSizeBytes) {
                log.warn("ZIP文件过大: {} bytes (限制: {} bytes)", Files.size(zipPath), maxExportSizeBytes);
                return false;
            }
            // 2. 使用ZipFile API进行验证，可同时检查完整性
            try (ZipFile zf = new ZipFile(zipPath.toFile())) {
                Set<String> entryNames = zf.stream()
                        .map(java.util.zip.ZipEntry::getName)
                        .collect(Collectors.toSet());
                // 3. 检查必需的目录结构
                for (String requiredDir : requiredDirs) {
                    if (entryNames.stream().noneMatch(name -> name.startsWith(requiredDir))) {
                        log.warn("ZIP文件缺少必要路径: {}", requiredDir);
                        return false;
                    }
                }
                // 4. 检查是否包含可疑文件（基于扩展名和路径遍历攻击）
                for (String entryName : entryNames) {
                    String lowerCaseName = entryName.toLowerCase();
                    // 检查路径遍历
                    if (lowerCaseName.contains("../") || lowerCaseName.contains("..\\")) {
                        log.warn("ZIP文件包含潜在的路径遍历攻击: {}", entryName);
                        return false;
                    }
                    // 检查可疑扩展名
                    for (String ext : suspiciousExtensions) {
                        if (lowerCaseName.endsWith(ext)) {
                            log.warn("ZIP文件包含可疑文件类型: {}", entryName);
                            return false;
                        }
                    }
                }
            }
            log.info("ZIP文件验证通过: {}", zipPath);
            return true;
        } catch (ZipException e) {
            log.warn("ZIP文件完整性检查失败 (文件可能已损坏): {}", zipPath, e);
            return false;
        } catch (IOException e) {
            log.error("验证ZIP文件时发生IO错误: {}", zipPath, e);
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
     * 创建一个数据备份。
     *
     * @param connection    SSH连接。
     * @param containerName 容器名称。
     * @param timestamp     用于命名备份的时间戳。
     * @return 备份目录在容器内的路径。
     */
    private String createEnhancedBackup(SshConnection connection, String containerName, String timestamp) {
        try {
            String backupPath = String.format("/app/%s%s", BACKUP_PREFIX, timestamp);
            executeCommand(connection, String.format(
                    "sudo docker exec %s cp -r %s %s", containerName, CONTAINER_DATA_PATH, backupPath));
            log.info("数据备份成功创建于: {}", backupPath);
            return backupPath;
        } catch (Exception e) {
            log.error("创建数据备份失败: {}", e.getMessage(), e);
            throw new RuntimeException("创建数据备份失败", e);
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
     * 清理旧的备份，只保留指定数量的最新备份。
     *
     * @param connection    SSH连接。
     * @param containerName 容器名称。
     * @param keepCount     要保留的最新备份数量。
     */
    private void cleanupOldBackups(SshConnection connection, String containerName, int keepCount) {
        try {
            // 1. 列出所有备份目录
            String command = String.format(
                    "sudo docker exec %s sh -c 'ls -1d /app/%s*'", containerName, BACKUP_PREFIX);
            String lsOutput = executeCommand(connection, command);
            // 2. 在Java中解析、排序并确定要删除的备份
            String[] backupPaths = lsOutput.split("\n");
            if (backupPaths.length <= keepCount) {
                return; // 备份数量未达到上限，无需清理
            }
            // 按名称（时间戳）降序排序，最新的在前
            Arrays.sort(backupPaths, Comparator.reverseOrder());
            // 3. 删除多余的旧备份
            for (int i = keepCount; i < backupPaths.length; i++) {
                String pathToDelete = backupPaths[i].trim();
                if (!pathToDelete.isEmpty()) {
                    try {
                        executeCommand(connection, String.format(
                                "sudo docker exec %s rm -rf %s", containerName, pathToDelete));
                        log.info("已删除旧备份: {}", pathToDelete);
                    } catch (Exception e) {
                        log.warn("删除旧备份失败: {} - {}", pathToDelete, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            // 如果列出备份的命令失败（例如没有备份存在），则静默处理
            if (e.getMessage().contains("No such file or directory")) {
                log.info("没有找到旧备份需要清理。");
            } else {
                log.warn("清理旧备份时出错: {}", e.getMessage());
            }
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
            CommandResult result = sshCommandService.executeInternal(connection.getJschSession(), command);
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
