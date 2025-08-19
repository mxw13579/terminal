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
import java.util.zip.GZIPInputStream;
import java.io.FileInputStream;
import java.util.zip.ZipInputStream;

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
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
            String exportFileName = generateExportFileName(containerName, timestamp);
            String remoteZipPath = String.format("/tmp/sillytavern_export_%s.tar.gz", timestamp);

            try {
                progressCallback.accept("正在获取docker-compose路径...");
                String dockerComposePath = findDockerComposePath(connection, containerName);
                String hostDataPath = dockerComposePath + "/data";

                progressCallback.accept("正在检查数据目录大小...");
                long dataSizeBytes = getHostDataDirectorySize(connection, hostDataPath);
                if (dataSizeBytes > maxExportSizeBytes) {
                    throw new RuntimeException(String.format(
                            "数据目录过大: %,d bytes (最大: %,d bytes)",
                            dataSizeBytes, maxExportSizeBytes));
                }

                progressCallback.accept("正在打包数据目录...");
                // 使用tar命令打包data目录，tar在所有Linux系统上都有
                // 为路径添加引号以防特殊字符
                executeCommand(connection, String.format(
                        "cd '%s' && tar -czf '%s' data/", dockerComposePath, remoteZipPath));

                progressCallback.accept("正在准备流式下载...");
                long zipFileSize = getRemoteFileSize(connection, remoteZipPath);

                DataExportDto exportDto = new DataExportDto();
                exportDto.setFileName(exportFileName);
                exportDto.setRemotePath(remoteZipPath);
                exportDto.setDownloadUrl("/api/sillytavern/download-stream/" + timestamp);
                exportDto.setSizeBytes(zipFileSize);
                exportDto.setCreatedAt(LocalDateTime.now());
                exportDto.setExpiresAt(LocalDateTime.now().plusHours(1));

                // 安排远程文件清理
                scheduleRemoteCleanup(connection, remoteZipPath, 1);

                progressCallback.accept("导出完成，准备下载");
                log.info("数据导出完成: {} ({} bytes)", exportFileName, zipFileSize);
                return exportDto;
            } catch (Exception e) {
                log.error("数据导出失败", e);
                // 清理可能已创建的临时文件
                try {
                    executeCommand(connection, String.format("rm -f '%s'", remoteZipPath));
                } catch (Exception cleanupEx) {
                    log.warn("清理导出临时文件失败: {}", cleanupEx.getMessage());
                }
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
        return String.format("sillytavern_data_sillytavern_%s.tar.gz", timestamp);
    }

    /**
     * 简化的数据导入流程 - 直接操作宿主机挂载的data目录
     * <p>
     * 优化的流程：
     * 1. 验证远程上传文件是否有效（基本检查）
     * 2. 在临时目录解压并验证data目录结构
     * 3. 备份现有data目录
     * 4. 全量拷贝新数据到data目录
     * 5. 重启SillyTavern容器
     * 6. 清理所有临时文件
     *
     * @param connection       SSH连接对象
     * @param containerName    目标Docker容器的名称
     * @param uploadedFileName 已上传到远程服务器的文件名
     * @param progressCallback 用于报告操作进度的回调函数
     * @return 一个 {@link CompletableFuture}，其结果为布尔值，表示导入是否成功
     */
    public CompletableFuture<Boolean> importData(SshConnection connection, String containerName,
                                                 String uploadedFileName, Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
            String remoteUploadedPath = "/tmp/" + uploadedFileName;
            String extractTempPath = String.format("/tmp/sillytavern_extract_%s", timestamp);

            try {
                progressCallback.accept("正在验证上传文件...");

                // 调试：检查文件信息
                try {
                    String fileInfo = executeCommand(connection, String.format("ls -la '%s'", remoteUploadedPath));
                    String fileSizeInfo = executeCommand(connection, String.format("stat -c '%%s' '%s'", remoteUploadedPath));
                    String fileTypeInfo = executeCommand(connection, String.format("file '%s'", remoteUploadedPath));

                    log.info("上传文件详细信息:");
                    log.info("文件列表: {}", fileInfo);
                    log.info("文件大小: {} bytes", fileSizeInfo.trim());
                    log.info("文件类型: {}", fileTypeInfo);

                    // 检查文件大小是否为0或异常小
                    long actualFileSize = Long.parseLong(fileSizeInfo.trim());
                    log.info("实际文件大小: {} bytes", actualFileSize);

                    if (actualFileSize < 1000) {
                        log.error("文件大小异常：实际大小 {} bytes，这可能表明文件上传未完成或损坏", actualFileSize);
                        throw new RuntimeException(String.format("上传的文件大小异常: %d bytes，文件可能损坏或上传不完整", actualFileSize));
                    }
                } catch (Exception e) {
                    log.warn("获取文件信息失败: {}", e.getMessage());
                }

                // 1. 直接在远程验证文件
                if (!isValidRemoteArchive(connection, remoteUploadedPath)) {
                    throw new RuntimeException("数据归档文件格式无效或包含不安全内容");
                }

                progressCallback.accept("正在获取docker-compose路径...");
                String dockerComposePath = findDockerComposePath(connection, containerName);
                String hostDataPath = dockerComposePath + "/data";

                progressCallback.accept("正在创建数据备份...");
                String backupPath = createDataBackup(connection, hostDataPath, timestamp);

                try {
                    progressCallback.accept("正在临时目录解压...");
                    // 创建临时解压目录
                    executeCommand(connection, String.format("mkdir -p '%s'", extractTempPath));

                    // 在解压前验证文件完整性
                    progressCallback.accept("正在验证文件完整性...");
                    try {
                        if (uploadedFileName.toLowerCase().endsWith(".zip")) {
                            // 验证ZIP文件
                            executeCommand(connection, String.format("unzip -t '%s'", remoteUploadedPath));
                        } else {
                            // 对于TAR.GZ文件，使用tar命令验证（更可靠）
                            // 因为前面的isValidRemoteArchive已经用tar -tzf验证过了，这里再次确认
                            executeCommand(connection, String.format("tar -tzf '%s' > /dev/null", remoteUploadedPath));
                        }
                        log.info("文件完整性验证通过: {}", remoteUploadedPath);
                    } catch (Exception e) {
                        log.error("文件完整性验证失败: {}", remoteUploadedPath, e);
                        throw new RuntimeException("上传的文件已损坏，无法解压。请重新上传文件。");
                    }

                    // 检测文件类型并使用相应的解压命令
                    if (uploadedFileName.toLowerCase().endsWith(".zip")) {
                        // ZIP文件：使用unzip命令（如果可用）或Python解压
                        try {
                            executeCommand(connection, String.format("cd '%s' && unzip -o '%s'", extractTempPath, remoteUploadedPath));
                        } catch (Exception e) {
                            if (e.getMessage().contains("unzip: command not found")) {
                                // unzip不可用，使用python解压
                                executeCommand(connection, String.format(
                                    "cd '%s' && python3 -c \"import zipfile; zipfile.ZipFile('%s').extractall('.')\"",
                                    extractTempPath, remoteUploadedPath));
                            } else {
                                throw e;
                            }
                        }
                    } else {
                        // TAR.GZ文件：使用tar命令
                        executeCommand(connection, String.format("cd '%s' && tar -xzf '%s'", extractTempPath, remoteUploadedPath));
                    }

                    // 验证解压结果
                    String extractedDataPath = extractTempPath + "/data";
                    String checkExtracted = executeCommand(connection, String.format("ls -A '%s'", extractedDataPath));
                    if (checkExtracted.trim().isEmpty()) {
                        throw new RuntimeException("解压失败：未找到data目录");
                    }

                    progressCallback.accept("正在备份现有数据...");
                    // 先删除现有data目录内容
                    executeCommand(connection, String.format("rm -rf '%s'/*", hostDataPath));

                    progressCallback.accept("正在导入新数据...");
                    // 将解压的data目录内容拷贝到挂载目录
                    executeCommand(connection, String.format("cp -r '%s'/* '%s'/", extractedDataPath, hostDataPath));

                    // 设置正确的权限
                    executeCommand(connection, String.format("chown -R 1000:1000 '%s'", hostDataPath));

                    progressCallback.accept("正在重启SillyTavern容器...");
                    restartSillyTavernContainer(connection, dockerComposePath);

                    progressCallback.accept("导入完成");
                    return true;

                } catch (Exception e) {
                    progressCallback.accept("导入失败，正在回滚...");
                    performDataRollback(connection, backupPath, hostDataPath);
                    throw e;
                }

            } catch (Exception e) {
                log.error("数据导入失败: {}", containerName, e);
                throw new RuntimeException("数据导入失败: " + e.getMessage(), e);
            } finally {
                // 临时禁用文件清理，保留文件以供检查
                log.info("临时禁用文件清理，保留文件: {}", remoteUploadedPath);
                // cleanupImportTempFiles(connection, remoteUploadedPath, null, extractTempPath, null);
            }
        });
    }

    /**
     * 从远程SSH服务器下载上传的文件到本地临时目录
     */
    private String downloadUploadedFileToLocal(SshConnection connection, String remoteUploadedPath, String uploadedFileName) throws Exception {
        // 确保本地临时目录存在
        Path tempDir = Paths.get(tempDirectory);
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }

        String localFilePath = Paths.get(tempDirectory, uploadedFileName).toString();

        log.info("从远程下载文件: {} -> {}", remoteUploadedPath, localFilePath);
        downloadFileFromRemote(connection, remoteUploadedPath, localFilePath);

        return localFilePath;
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
     * 在远程服务器上验证归档文件的基本有效性
     * 使用远程命令检查文件格式和基本结构，避免不必要的文件传输
     *
     * @param connection SSH连接对象
     * @param remotePath 远程文件路径
     * @return 如果文件有效且安全，则返回true；否则返回false
     */
    private boolean isValidRemoteArchive(SshConnection connection, String remotePath) {
        try {
            // 1. 检查文件是否存在和大小
            String fileInfo = executeCommand(connection, String.format("stat -c '%%s' '%s' 2>/dev/null || echo 'not_found'", remotePath));
            if ("not_found".equals(fileInfo.trim())) {
                log.warn("远程归档文件不存在: {}", remotePath);
                return false;
            }

            long fileSize = Long.parseLong(fileInfo.trim());
            if (fileSize > maxExportSizeBytes) {
                log.warn("远程归档文件过大: {} bytes (限制: {} bytes)", fileSize, maxExportSizeBytes);
                return false;
            }

            // 2. 检查文件扩展名和格式
            String fileName = remotePath.toLowerCase();
            boolean isZip = fileName.endsWith(".zip");
            boolean isTarGz = fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz");

            if (!isZip && !isTarGz) {
                log.warn("不支持的远程归档文件格式: {}", remotePath);
                return false;
            }

            // 3. 使用相应的命令进行基本格式验证
            if (isZip) {
                // 验证ZIP文件头和基本结构
                try {
                    String zipTest = executeCommand(connection, String.format("unzip -t '%s' | head -10", remotePath));
                    if (!zipTest.contains("testing:") && !zipTest.contains("OK")) {

                        log.warn("ZIP文件格式验证失败: {},zipTest{}", remotePath,zipTest);
                        return false;
                    }
                } catch (Exception e) {
                    if (e.getMessage().contains("unzip: command not found")) {
                        // 如果unzip不可用，使用file命令检查
                        String fileType = executeCommand(connection, String.format("file '%s'", remotePath));
                        if (!fileType.toLowerCase().contains("zip")) {
                            log.warn("ZIP文件类型验证失败: {}", remotePath);
                            return false;
                        }
                    } else {
                        log.warn("ZIP文件验证异常: {}", e.getMessage());
                        return false;
                    }
                }
            } else {
                // 验证TAR.GZ文件头和基本结构
                try {
                    String tarTest = executeCommand(connection, String.format("tar -tzf '%s' | head -10", remotePath));
                    if (tarTest.trim().isEmpty()) {
                        log.warn("TAR.GZ文件内容为空: {}", remotePath);
                        return false;
                    }

                    // 检查是否包含data/目录
                    if (!tarTest.contains("data/")) {
                        log.warn("TAR.GZ文件不包含data/目录: {}", remotePath);
                        return false;
                    }
                } catch (Exception e) {
                    log.warn("TAR.GZ文件验证失败: {} - {}", remotePath, e.getMessage());
                    return false;
                }
            }

            // 4. 检查文件内容是否包含可疑路径
            try {
                String listCommand = isZip ?
                    String.format("unzip -l '%s' | head -20", remotePath) :
                    String.format("tar -tzf '%s' | head -20", remotePath);

                String fileList = executeCommand(connection, listCommand);

                // 检查路径遍历攻击
                if (fileList.contains("../") || fileList.contains("..\\")) {
                    log.warn("归档文件包含潜在的路径遍历攻击: {}", remotePath);
                    return false;
                }

                // 检查是否所有文件都在data/目录下
                String[] lines = fileList.split("\n");
                for (String line : lines) {
                    if (line.trim().isEmpty() || line.contains("Archive:") || line.contains("Length") || line.contains("---")) {
                        continue;
                    }

                    // 提取文件路径部分（处理不同格式的列表输出）
                    String filePath = "";
                    if (isZip) {
                        // unzip -l输出格式处理
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 4) {
                            filePath = parts[parts.length - 1];
                        }
                    } else {
                        // tar -tzf输出格式处理
                        filePath = line.trim();
                    }

                    if (!filePath.isEmpty() && !filePath.equals("data/") && !filePath.startsWith("data/")) {
                        log.warn("归档文件包含data/目录外的文件: {} in {}", filePath, remotePath);
                        return false;
                    }
                }
            } catch (Exception e) {
                log.warn("检查归档文件内容时出错: {} - {}", remotePath, e.getMessage());
                // 这里不直接返回false，允许基本验证通过的文件继续处理
            }

            log.info("远程归档文件验证通过: {}", remotePath);
            return true;

        } catch (NumberFormatException e) {
            log.warn("解析远程文件大小失败: {}", remotePath);
            return false;
        } catch (Exception e) {
            log.error("验证远程归档文件时发生错误: {} - {}", remotePath, e.getMessage());
            return false;
        }
    }

    /**
     * 验证归档文件（ZIP或TAR.GZ）的完整性、结构和内容安全性。
     * 确保压缩包根目录必须是data文件夹
     *
     * @param archivePath 指向待验证归档文件的路径
     * @return 如果文件有效且安全，则返回true；否则返回false
     */
    private boolean isValidDataArchive(Path archivePath) {
        final Set<String> requiredDirs = Set.of("data/", "data/characters/", "data/chats/");
        final Set<String> suspiciousExtensions = Set.of(".exe", ".bat", ".sh", ".cmd", ".scr", ".vbs", ".jar");

        try {
            // 1. 检查文件大小
            if (Files.size(archivePath) > maxExportSizeBytes) {
                log.warn("归档文件过大: {} bytes (限制: {} bytes)", Files.size(archivePath), maxExportSizeBytes);
                return false;
            }

            String fileName = archivePath.getFileName().toString().toLowerCase();

            // 2. 根据文件扩展名选择相应的验证方法
            if (fileName.endsWith(".zip")) {
                return validateZipArchive(archivePath, requiredDirs, suspiciousExtensions);
            } else if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) {
                return validateTarGzArchive(archivePath, requiredDirs, suspiciousExtensions);
            } else {
                log.warn("不支持的归档文件格式: {}", fileName);
                return false;
            }

        } catch (IOException e) {
            log.error("验证归档文件时发生IO错误: {}", archivePath, e);
            return false;
        }
    }

    /**
     * 验证ZIP归档文件
     */
    private boolean validateZipArchive(Path zipPath, Set<String> requiredDirs, Set<String> suspiciousExtensions) {
        try (ZipFile zf = new ZipFile(zipPath.toFile())) {
            Set<String> entryNames = zf.stream()
                    .map(java.util.zip.ZipEntry::getName)
                    .collect(Collectors.toSet());

            return validateArchiveEntries(entryNames, requiredDirs, suspiciousExtensions, "ZIP");

        } catch (ZipException e) {
            log.warn("ZIP文件完整性检查失败 (文件可能已损坏): {}", zipPath, e);
            return false;
        } catch (IOException e) {
            log.error("验证ZIP文件时发生IO错误: {}", zipPath, e);
            return false;
        }
    }

    /**
     * 验证TAR.GZ归档文件
     * 注意：这是简化的验证，只检查基本的文件结构，不做完整的tar解析
     */
    private boolean validateTarGzArchive(Path tarGzPath, Set<String> requiredDirs, Set<String> suspiciousExtensions) {
        try {
            // 基本的GZIP格式验证 - 检查文件头
            try (FileInputStream fis = new FileInputStream(tarGzPath.toFile());
                 GZIPInputStream gzis = new GZIPInputStream(fis)) {

                // 尝试读取前几个字节以验证GZIP格式
                byte[] buffer = new byte[1024];
                int bytesRead = gzis.read(buffer);
                if (bytesRead <= 0) {
                    log.warn("TAR.GZ文件为空或格式无效: {}", tarGzPath);
                    return false;
                }

                // 简单的内容检查 - 查找关键的目录路径标识
                String content = new String(buffer, 0, Math.min(bytesRead, 1024), StandardCharsets.UTF_8);

                // 检查是否包含data/目录结构
                boolean hasDataDir = content.contains("data/") || content.contains("data\\");
                if (!hasDataDir) {
                    // 如果前1024字节没有找到，读取更多内容进行检查
                    byte[] largerBuffer = new byte[8192];
                    int totalBytesRead = bytesRead;
                    int additionalBytes = gzis.read(largerBuffer);
                    if (additionalBytes > 0) {
                        String largerContent = new String(largerBuffer, 0, additionalBytes, StandardCharsets.UTF_8);
                        hasDataDir = largerContent.contains("data/") || largerContent.contains("data\\");
                    }
                }

                if (!hasDataDir) {
                    log.warn("TAR.GZ文件未包含data/目录结构: {}", tarGzPath);
                    return false;
                }

                // 检查可疑内容
                String fullContent = content + (bytesRead < 8192 ? "" : new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
                for (String ext : suspiciousExtensions) {
                    if (fullContent.toLowerCase().contains(ext)) {
                        log.warn("TAR.GZ文件包含可疑文件类型: {} in {}", ext, tarGzPath);
                        return false;
                    }
                }

                // 检查路径遍历攻击
                if (fullContent.contains("../") || fullContent.contains("..\\")) {
                    log.warn("TAR.GZ文件包含潜在的路径遍历攻击: {}", tarGzPath);
                    return false;
                }

                log.info("TAR.GZ文件基本验证通过: {}", tarGzPath);
                return true;
            }
        } catch (IOException e) {
            log.warn("TAR.GZ文件完整性检查失败: {}", tarGzPath, e);
            return false;
        }
    }

    /**
     * 验证归档文件条目的通用方法
     */
    private boolean validateArchiveEntries(Set<String> entryNames, Set<String> requiredDirs,
                                         Set<String> suspiciousExtensions, String archiveType) {
        // 3. 关键验证：确保压缩包根目录必须是data文件夹
        boolean hasDataAsRoot = entryNames.contains("data/");
        boolean allEntriesUnderData = entryNames.stream()
                .filter(name -> !name.equals("data/"))
                .allMatch(name -> name.startsWith("data/"));

        if (!hasDataAsRoot) {
            log.warn("{}文件根目录必须包含data/文件夹", archiveType);
            return false;
        }

        if (!allEntriesUnderData) {
            log.warn("{}文件中存在data/目录外的文件，压缩包根目录必须只有data文件夹", archiveType);
            return false;
        }

        // 4. 检查必需的目录结构
        for (String requiredDir : requiredDirs) {
            if (entryNames.stream().noneMatch(name -> name.startsWith(requiredDir))) {
                log.warn("{}文件缺少必要路径: {}", archiveType, requiredDir);
                return false;
            }
        }

        // 5. 检查是否包含可疑文件（基于扩展名和路径遍历攻击）
        for (String entryName : entryNames) {
            String lowerCaseName = entryName.toLowerCase();

            // 检查路径遍历
            if (lowerCaseName.contains("../") || lowerCaseName.contains("..\\")) {
                log.warn("{}文件包含潜在的路径遍历攻击: {}", archiveType, entryName);
                return false;
            }

            // 检查可疑扩展名
            for (String ext : suspiciousExtensions) {
                if (lowerCaseName.endsWith(ext)) {
                    log.warn("{}文件包含可疑文件类型: {}", archiveType, entryName);
                    return false;
                }
            }
        }

        log.info("{}文件验证通过", archiveType);
        return true;
    }

    /**
     * 使用纯Java API验证ZIP文件的完整性、结构和内容安全性。
     * 确保压缩包根目录必须是data文件夹
     *
     * @param zipPath 指向待验证ZIP文件的路径。
     * @return 如果文件有效且安全，则返回true；否则返回false。
     */
    private boolean isValidDataZip(Path zipPath) {
        final Set<String> requiredDirs = Set.of("data/", "data/characters/", "data/chats/");
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

                // 3. 关键验证：确保压缩包根目录必须是data文件夹
                boolean hasDataAsRoot = entryNames.contains("data/");
                boolean allEntriesUnderData = entryNames.stream()
                        .filter(name -> !name.equals("data/"))
                        .allMatch(name -> name.startsWith("data/"));

                if (!hasDataAsRoot) {
                    log.warn("ZIP文件根目录必须包含data/文件夹");
                    return false;
                }

                if (!allEntriesUnderData) {
                    log.warn("ZIP文件中存在data/目录外的文件，压缩包根目录必须只有data文件夹");
                    return false;
                }

                // 4. 检查必需的目录结构
                for (String requiredDir : requiredDirs) {
                    if (entryNames.stream().noneMatch(name -> name.startsWith(requiredDir))) {
                        log.warn("ZIP文件缺少必要路径: {}", requiredDir);
                        return false;
                    }
                }

                // 5. 检查是否包含可疑文件（基于扩展名和路径遍历攻击）
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

    /**
     * 查找docker-compose.yaml所在目录（通过容器挂载信息）
     */
    private String findDockerComposePath(SshConnection connection, String containerName) throws Exception {
        String inspectOutput = executeCommand(connection,
            String.format("docker inspect %s --format='{{range .Mounts}}{{if eq .Destination \"/home/node/app/data\"}}{{.Source}}{{end}}{{end}}'", containerName));

        if (inspectOutput.trim().isEmpty()) {
            throw new RuntimeException("未找到data目录挂载路径");
        }

        String mountedDataPath = inspectOutput.trim();
        log.info("发现挂载路径: {}", mountedDataPath);

        // 挂载路径是 /path/to/compose/data，父目录就是docker-compose.yaml所在目录
        // 直接使用字符串操作而不是Paths.get，避免Windows路径分隔符问题
        String dockerComposePath;
        if (mountedDataPath.endsWith("/data")) {
            dockerComposePath = mountedDataPath.substring(0, mountedDataPath.length() - 5); // 移除 "/data"
        } else {
            // 如果路径不是以 /data 结尾，使用最后一个 / 之前的部分
            int lastSlash = mountedDataPath.lastIndexOf('/');
            if (lastSlash > 0) {
                dockerComposePath = mountedDataPath.substring(0, lastSlash);
            } else {
                throw new RuntimeException("无法解析docker-compose路径，挂载路径格式异常: " + mountedDataPath);
            }
        }

        log.info("解析的docker-compose路径: {}", dockerComposePath);
        return dockerComposePath;
    }

    /**
     * 获取宿主机data目录大小
     */
    private long getHostDataDirectorySize(SshConnection connection, String hostDataPath) throws Exception {
        String sizeOutput = executeCommand(connection, String.format("du -sb '%s' | cut -f1", hostDataPath));
        try {
            return Long.parseLong(sizeOutput.trim());
        } catch (NumberFormatException e) {
            log.warn("无法解析数据目录大小: {}", sizeOutput);
            return 0;
        }
    }

    /**
     * 获取远程文件大小
     */
    private long getRemoteFileSize(SshConnection connection, String remoteFilePath) throws Exception {
        String sizeOutput = executeCommand(connection, String.format("stat -c%%s '%s'", remoteFilePath));
        try {
            return Long.parseLong(sizeOutput.trim());
        } catch (NumberFormatException e) {
            log.warn("无法解析文件大小: {}", sizeOutput);
            return 0;
        }
    }

    /**
     * 安排远程文件清理
     */
    private void scheduleRemoteCleanup(SshConnection connection, String remoteFilePath, int delayHours) {
        CompletableFuture.delayedExecutor(delayHours, TimeUnit.HOURS)
            .execute(() -> {
                try {
                    executeCommand(connection, String.format("rm -f '%s'", remoteFilePath));
                    log.info("远程文件清理完成: {}", remoteFilePath);
                } catch (Exception e) {
                    log.warn("远程文件清理失败: {}", remoteFilePath, e);
                }
            });
    }

    /**
     * 创建宿主机数据备份
     */
    private String createDataBackup(SshConnection connection, String hostDataPath, String timestamp) throws Exception {
        String backupPath = hostDataPath + "_backup_" + timestamp;
        executeCommand(connection, String.format("cp -r '%s' '%s'", hostDataPath, backupPath));
        log.info("数据备份创建完成: {}", backupPath);
        return backupPath;
    }

    /**
     * 执行数据回滚
     */
    private void performDataRollback(SshConnection connection, String backupPath, String hostDataPath) {
        try {
            executeCommand(connection, String.format("rm -rf '%s'", hostDataPath));
            executeCommand(connection, String.format("mv '%s' '%s'", backupPath, hostDataPath));
            log.info("数据回滚成功");
        } catch (Exception e) {
            log.error("数据回滚失败: {}", e.getMessage());
            throw new RuntimeException("数据回滚失败，请手动恢复数据");
        }
    }

    /**
     * 重启容器
     */
    private void restartSillyTavernContainer(SshConnection connection, String dockerComposePath) throws Exception {
        // 停止容器
        executeCommand(connection, String.format("cd '%s' && docker compose stop", dockerComposePath));

        // 等待2秒确保完全停止
        Thread.sleep(2000);

        // 启动容器
        executeCommand(connection, String.format("cd '%s' && docker compose up -d", dockerComposePath));

        log.info("SillyTavern容器重启完成");
    }

    /**
     * 清理导入临时文件
     */
    private void cleanupImportTempFiles(SshConnection connection, String remoteUploadedPath, String remoteZipPath,
                                       String extractTempPath, String localZipPath) {
        // 清理被控服务器临时文件
        try {
            if (remoteUploadedPath != null) {
                executeCommand(connection, String.format("rm -f '%s'", remoteUploadedPath));
            }
            if (remoteZipPath != null) {
                executeCommand(connection, String.format("rm -f '%s'", remoteZipPath));
            }
            if (extractTempPath != null) {
                executeCommand(connection, String.format("rm -rf '%s'", extractTempPath));
            }
            log.info("被控服务器临时文件清理完成");
        } catch (Exception e) {
            log.warn("清理被控服务器临时文件失败: {}", e.getMessage());
        }

        // 清理Server端临时文件
        if (localZipPath != null) {
            try {
                Files.deleteIfExists(Paths.get(localZipPath));
                log.info("本地临时文件清理完成: {}", localZipPath);
            } catch (IOException e) {
                log.warn("清理本地临时文件失败: {}", e.getMessage());
            }
        }
    }
}
