package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.dto.sillytavern.DataExportDto;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Service for managing SillyTavern data export and import operations.
 * Handles ZIP file creation, data transfer, and validation.
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
                
                progressCallback.accept("Creating backup of existing data...");
                
                // Create backup of existing data
                String backupPath = String.format("/app/data_backup_%s", timestamp);
                executeCommand(connection, String.format(
                    "sudo docker exec %s cp -r %s %s", containerName, CONTAINER_DATA_PATH, backupPath));
                
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
                    // Restore backup on failure
                    progressCallback.accept("Import failed, restoring backup...");
                    executeCommand(connection, String.format(
                        "sudo docker exec %s sh -c 'rm -rf %s && mv %s %s'", 
                        containerName, CONTAINER_DATA_PATH, backupPath, CONTAINER_DATA_PATH));
                    throw e;
                }
                
                // Clean up temporary files
                progressCallback.accept("Cleaning up temporary files...");
                executeCommand(connection, String.format("sudo docker exec %s rm -f %s", containerName, containerZipPath));
                executeCommand(connection, String.format("sudo docker exec %s rm -rf %s", containerName, backupPath));
                executeCommand(connection, String.format("rm -f %s", remoteZipPath));
                
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
     * Validate ZIP file structure
     */
    private boolean isValidDataZip(String zipPath) {
        try {
            // Use system command to check ZIP file integrity and structure
            String result = executeLocalCommand(String.format("unzip -t '%s'", zipPath));
            return result.contains("No errors detected");
        } catch (Exception e) {
            log.warn("ZIP validation failed: {}", e.getMessage());
            return false;
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
    
    /**
     * Execute a local command (for file validation)
     */
    private String executeLocalCommand(String command) throws Exception {
        try {
            Process process = Runtime.getRuntime().exec(command);
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));
            
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            process.waitFor();
            return output.toString();
            
        } catch (Exception e) {
            throw new Exception("Local command execution failed: " + e.getMessage(), e);
        }
    }
}