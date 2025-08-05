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
 * 数据管理服务 - 负责SillyTavern数据的导出和导入操作
 * 增强安全性，使用ProcessBuilder和命令白名单机制
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataManagementService {
    
    private final SshCommandService sshCommandService;
    private final FileCleanupService fileCleanupService;
    
    @Value("${sillytavern.temp.directory:./temp}")
    private String tempDirectory;
    
    @Value("${sillytavern.data.max-export-size:5368709120}") // 5GB in bytes
    private long maxExportSizeBytes;
    
    private static final String CONTAINER_DATA_PATH = "/app/data";
    private static final String TEMP_EXPORT_PATH = "/tmp/sillytavern_export";
    private static final String TEMP_IMPORT_PATH = "/tmp/sillytavern_import";
    
    /**
     * Export data directory from container as ZIP file
     */
    public CompletableFuture<DataExportDto> exportData(SshConnection connection, String containerName, 
                                                      Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String exportFileName = String.format("sillytavern_data_%s_%s.zip", containerName, timestamp);
                String localExportPath = new File(tempDirectory, exportFileName).getAbsolutePath();
                
                progressCallback.accept("Checking container data size...");
                
                // Check data directory size
                long dataSizeBytes = getDataDirectorySize(connection, containerName);
                if (dataSizeBytes > maxExportSizeBytes) {
                    throw new RuntimeException(String.format(
                        "Data directory too large: %d bytes (max: %d bytes)", 
                        dataSizeBytes, maxExportSizeBytes));
                }
                
                progressCallback.accept("Creating data archive in container...");
                
                // Create ZIP archive inside container
                String containerZipPath = String.format("%s_%s.zip", TEMP_EXPORT_PATH, timestamp);
                executeCommand(connection, String.format(
                    "sudo docker exec %s sh -c 'cd / && zip -r %s app/data/'", 
                    containerName, containerZipPath));
                
                progressCallback.accept("Copying archive to local system...");
                
                // Copy ZIP from container to host
                String hostZipPath = String.format("/tmp/sillytavern_export_%s.zip", timestamp);
                executeCommand(connection, String.format(
                    "sudo docker cp %s:%s %s", containerName, containerZipPath, hostZipPath));
                
                progressCallback.accept("Transferring file to web server...");
                
                // Create local temp directory if it doesn't exist
                new File(tempDirectory).mkdirs();
                
                // Download file from remote host to local temp directory
                // This would typically use SFTP, but for now we'll simulate it
                downloadFileFromRemote(connection, hostZipPath, localExportPath);
                
                // Clean up remote files
                progressCallback.accept("Cleaning up temporary files...");
                executeCommand(connection, String.format("sudo docker exec %s rm -f %s", containerName, containerZipPath));
                executeCommand(connection, String.format("rm -f %s", hostZipPath));
                
                // Get final file size
                File exportFile = new File(localExportPath);
                long fileSizeBytes = exportFile.length();
                
                // Create data export DTO
                DataExportDto exportDto = new DataExportDto();
                exportDto.setFilename(exportFileName);
                exportDto.setDownloadUrl("/api/sillytavern/download/" + exportFileName);
                exportDto.setSizeBytes(fileSizeBytes);
                exportDto.setCreatedAt(LocalDateTime.now());
                exportDto.setExpiresAt(LocalDateTime.now().plusHours(1)); // 1 hour expiry
                
                // Schedule file cleanup
                fileCleanupService.scheduleCleanup(localExportPath, 1); // 1 hour
                
                progressCallback.accept("Export completed successfully");
                log.info("Data export completed: {} ({} bytes)", exportFileName, fileSizeBytes);
                
                return exportDto;
                
            } catch (Exception e) {
                log.error("Data export failed", e);
                throw new RuntimeException("Data export failed: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Import data from uploaded ZIP file to container
     */
    public CompletableFuture<Boolean> importData(SshConnection connection, String containerName, 
                                               String uploadedFileName, Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String localZipPath = new File(tempDirectory, uploadedFileName).getAbsolutePath();
                File zipFile = new File(localZipPath);
                
                if (!zipFile.exists()) {
                    throw new RuntimeException("Uploaded file not found: " + uploadedFileName);
                }
                
                progressCallback.accept("Validating uploaded file...");
                
                // Validate ZIP file
                if (!isValidDataZip(localZipPath)) {
                    throw new RuntimeException("Invalid data ZIP file format");
                }
                
                progressCallback.accept("Uploading file to remote server...");
                
                // Upload file to remote host
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String remoteZipPath = String.format("/tmp/sillytavern_import_%s.zip", timestamp);
                uploadFileToRemote(connection, localZipPath, remoteZipPath);
                
                progressCallback.accept("Copying file to container...");
                
                // Copy ZIP to container
                String containerZipPath = String.format("%s_%s.zip", TEMP_IMPORT_PATH, timestamp);
                executeCommand(connection, String.format(
                    "sudo docker cp %s %s:%s", remoteZipPath, containerName, containerZipPath));
                
                progressCallback.accept("创建自动备份...");
                
                // 增强的备份创建
                String backupInfo = createEnhancedBackup(connection, containerName, timestamp);
                if (backupInfo == null) {
                    throw new RuntimeException("创建数据备份失败");
                }
                
                progressCallback.accept("验证备份完整性...");
                
                // 验证备份完整性
                if (!verifyBackupIntegrity(connection, containerName, backupInfo)) {
                    throw new RuntimeException("备份完整性验证失败");
                }
                
                progressCallback.accept("Extracting imported data...");
                
                try {
                    // Clear existing data directory (keeping backup)
                    executeCommand(connection, String.format(
                        "sudo docker exec %s sh -c 'rm -rf %s/*'", containerName, CONTAINER_DATA_PATH));
                    
                    // Extract imported data
                    executeCommand(connection, String.format(
                        "sudo docker exec %s sh -c 'cd / && unzip -o %s'", containerName, containerZipPath));
                    
                    // Verify extraction
                    String dataCheck = executeCommand(connection, String.format(
                        "sudo docker exec %s ls -la %s", containerName, CONTAINER_DATA_PATH));
                    
                    if (dataCheck.trim().isEmpty()) {
                        throw new RuntimeException("Data extraction verification failed - directory is empty");
                    }
                    
                    progressCallback.accept("Import completed successfully");
                    
                } catch (Exception e) {
                    // 增强的自动回滚
                    progressCallback.accept("导入失败，正在执行自动回滚...");
                    performAutomaticRollback(connection, containerName, backupInfo);
                    throw e;
                }
                
                // 清理临时文件
                progressCallback.accept("清理临时文件...");
                executeCommand(connection, String.format("sudo docker exec %s rm -f %s", containerName, containerZipPath));
                executeCommand(connection, String.format("rm -f %s", remoteZipPath));
                
                // 清理成功后的备份（保留最近3个备份）
                cleanupOldBackups(connection, containerName);
                
                // Schedule cleanup of local uploaded file
                fileCleanupService.scheduleCleanup(localZipPath, 0); // Immediate cleanup
                
                log.info("Data import completed successfully for container: {}", containerName);
                return true;
                
            } catch (Exception e) {
                log.error("Data import failed for container: {}", containerName, e);
                throw new RuntimeException("Data import failed: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Get data directory size in bytes
     */
    public long getDataDirectorySize(SshConnection connection, String containerName) throws Exception {
        String sizeOutput = executeCommand(connection, String.format(
            "sudo docker exec %s du -sb %s | cut -f1", containerName, CONTAINER_DATA_PATH));
        
        try {
            return Long.parseLong(sizeOutput.trim());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse data directory size: {}", sizeOutput);
            return 0;
        }
    }
    
    /**
     * 验证ZIP文件结构和内容
     */
    private boolean isValidDataZip(String zipPath) {
        try {
            log.debug("验证ZIP文件: {}", zipPath);
            
            // 1. 检查ZIP文件完整性
            String integrityCheck = executeLocalCommand(String.format("unzip -t '%s'", zipPath));
            if (!integrityCheck.contains("No errors detected")) {
                log.warn("ZIP文件完整性检查失败: {}", integrityCheck);
                return false;
            }
            
            // 2. 检查ZIP文件内容结构
            String listContents = executeLocalCommand(String.format("unzip -l '%s'", zipPath));
            
            // 必须包含的SillyTavern数据目录结构
            String[] requiredPaths = {
                "app/data/",
                "app/data/config.yaml",
                "app/data/characters/",
                "app/data/chats/"
            };
            
            boolean hasValidStructure = true;
            for (String requiredPath : requiredPaths) {
                if (!listContents.contains(requiredPath)) {
                    log.warn("ZIP文件缺少必要路径: {}", requiredPath);
                    hasValidStructure = false;
                }
            }
            
            if (!hasValidStructure) {
                return false;
            }
            
            // 3. 检查文件大小限制
            File zipFile = new File(zipPath);
            long fileSizeBytes = zipFile.length();
            if (fileSizeBytes > maxExportSizeBytes) {
                log.warn("ZIP文件过大: {} bytes (限制: {} bytes)", fileSizeBytes, maxExportSizeBytes);
                return false;
            }
            
            // 4. 检查压缩包内的可疑文件
            if (containsSuspiciousFiles(listContents)) {
                log.warn("ZIP文件包含可疑文件");
                return false;
            }
            
            log.info("ZIP文件验证通过: {}", zipPath);
            return true;
            
        } catch (Exception e) {
            log.error("ZIP文件验证失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查ZIP内容是否包含可疑文件
     */
    private boolean containsSuspiciousFiles(String zipContents) {
        // 检查可疑文件扩展名
        String[] suspiciousExtensions = {".exe", ".bat", ".sh", ".cmd", ".scr", ".vbs", ".jar"};
        String lowerContents = zipContents.toLowerCase();
        
        for (String extension : suspiciousExtensions) {
            if (lowerContents.contains(extension)) {
                return true;
            }
        }
        
        // 检查可疑文件路径
        String[] suspiciousPaths = {"../", "../../", "/etc/", "/bin/", "/usr/", "c:\\", "d:\\"};
        for (String path : suspiciousPaths) {
            if (lowerContents.contains(path.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 创建增强的数据备份
     */
    private String createEnhancedBackup(SshConnection connection, String containerName, String timestamp) {
        try {
            String backupPath = String.format("/app/data_backup_%s", timestamp);
            String backupZipPath = String.format("/app/data_backup_%s.zip", timestamp);
            
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
            
            // 写入备份信息文件
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
     * 验证备份完整性
     */
    private boolean verifyBackupIntegrity(SshConnection connection, String containerName, String backupPath) {
        try {
            // 检查备份目录是否存在且非空
            String checkBackup = executeCommand(connection, String.format(
                "sudo docker exec %s ls -la %s", containerName, backupPath));
            
            if (checkBackup.trim().isEmpty()) {
                log.warn("备份目录为空: {}", backupPath);
                return false;
            }
            
            // 检查必要文件是否存在
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
     * 执行自动回滚
     */
    private void performAutomaticRollback(SshConnection connection, String containerName, String backupPath) {
        try {
            log.info("开始自动回滚，备份路径: {}", backupPath);
            
            // 1. 移除损坏的数据
            executeCommand(connection, String.format(
                "sudo docker exec %s rm -rf %s", containerName, CONTAINER_DATA_PATH));
            
            // 2. 恢复备份数据
            executeCommand(connection, String.format(
                "sudo docker exec %s mv %s %s", containerName, backupPath, CONTAINER_DATA_PATH));
            
            // 3. 验证回滚结果
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
     * 清理旧备份，保留最近3个
     */
    private void cleanupOldBackups(SshConnection connection, String containerName) {
        try {
            // 获取所有备份目录
            String backupsCommand = String.format(
                "sudo docker exec %s sh -c 'ls -1t /app/ | grep \"data_backup_\" | head -10'", 
                containerName);
            
            String backupsList = executeCommand(connection, backupsCommand);
            String[] backups = backupsList.split("\n");
            
            // 删除超过3个的旧备份
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
     * 获取备份大小
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
     * Download file from remote host using SFTP
     * This is a placeholder - in a real implementation, would use the SshConnection's SFTP channel
     */
    private void downloadFileFromRemote(SshConnection connection, String remotePath, String localPath) throws Exception {
        // This is a simplified implementation
        // In a real scenario, you would use the SFTP channel from the SSH connection
        log.debug("Downloading file from {} to {}", remotePath, localPath);
        
        try {
            var sftpChannel = connection.getOrCreateSftpChannel();
            sftpChannel.get(remotePath, localPath);
            log.info("File downloaded successfully: {}", localPath);
        } catch (Exception e) {
            log.error("Failed to download file: {}", e.getMessage());
            throw new RuntimeException("File download failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Upload file to remote host using SFTP
     */
    private void uploadFileToRemote(SshConnection connection, String localPath, String remotePath) throws Exception {
        log.debug("Uploading file from {} to {}", localPath, remotePath);
        
        try {
            var sftpChannel = connection.getOrCreateSftpChannel();
            sftpChannel.put(localPath, remotePath);
            log.info("File uploaded successfully: {}", remotePath);
        } catch (Exception e) {
            log.error("Failed to upload file: {}", e.getMessage());
            throw new RuntimeException("File upload failed: " + e.getMessage(), e);
        }
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
                log.warn("Command execution failed: {} - {}", command, errorMsg);
                throw new RuntimeException(errorMsg);
            }
            
            return result.stdout();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Command execution was interrupted: " + command, e);
        }
    }
    
    // 安全的本地命令白名单
    private static final Set<String> ALLOWED_COMMANDS = Set.of("unzip", "file", "du", "ls");
    private static final Pattern SAFE_PATH_PATTERN = Pattern.compile("^[a-zA-Z0-9_/\\.-]+$");
    
    /**
     * 执行本地命令 - 使用ProcessBuilder和命令白名单确保安全性
     */
    private String executeLocalCommand(String command) throws Exception {
        log.debug("执行本地命令: {}", command);
        
        // 解析命令参数
        String[] commandParts = command.split("\\s+");
        if (commandParts.length == 0) {
            throw new IllegalArgumentException("命令不能为空");
        }
        
        String baseCommand = commandParts[0];
        
        // 检查命令是否在白名单中
        if (!ALLOWED_COMMANDS.contains(baseCommand)) {
            throw new SecurityException("不允许执行的命令: " + baseCommand);
        }
        
        // 验证所有参数的安全性
        for (int i = 1; i < commandParts.length; i++) {
            String arg = commandParts[i];
            // 跳过选项参数（以-开头）
            if (arg.startsWith("-")) {
                continue;
            }
            // 验证文件路径参数
            if (!SAFE_PATH_PATTERN.matcher(arg).matches()) {
                throw new SecurityException("不安全的路径参数: " + arg);
            }
        }
        
        try {
            // 使用ProcessBuilder代替Runtime.exec()以提高安全性
            ProcessBuilder processBuilder = new ProcessBuilder(commandParts);
            processBuilder.redirectErrorStream(true); // 合并标准输出和错误输出
            
            // 设置工作目录为临时目录，避免访问敏感目录
            processBuilder.directory(new File(System.getProperty("java.io.tmpdir")));
            
            // 清理环境变量，只保留必要的
            Map<String, String> env = processBuilder.environment();
            env.clear();
            env.put("PATH", "/usr/bin:/bin:/usr/local/bin"); // 限制PATH
            env.put("HOME", System.getProperty("java.io.tmpdir"));
            
            Process process = processBuilder.start();
            
            // 设置超时时间，避免长时间阻塞
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new Exception("命令执行超时（30秒）");
            }
            
            // 读取输出
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
            throw new Exception("命令执行IO错误: " + e.getMessage(), e);
        }
    }
}