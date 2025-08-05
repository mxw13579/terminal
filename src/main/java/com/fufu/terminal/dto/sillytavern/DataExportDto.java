package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 数据导出操作DTO
 * 提供导出数据的下载URL和元数据信息
 * 
 * @author lizelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataExportDto {
    
    /**
     * Container name for which data was exported
     */
    private String containerName;
    
    /**
     * URL to download the exported data
     */
    private String downloadUrl;
    
    /**
     * Filename of the exported data
     */
    private String filename;
    
    /**
     * Alternative property name for filename (for compatibility)
     */
    private String fileName;
    
    /**
     * Size of exported file in bytes
     */
    private Long sizeBytes;
    
    /**
     * Size of compressed/final file in bytes
     */
    private Long compressedSize;
    
    /**
     * Export path on the server
     */
    private String exportPath;
    
    /**
     * When the export expires and will be automatically deleted
     */
    private LocalDateTime expiresAt;
    
    /**
     * Export creation timestamp
     */
    private LocalDateTime createdAt;
    
    /**
     * Export progress (0-100) if still in progress
     */
    private Integer progress = 100;
    
    /**
     * Whether export is complete
     */
    private Boolean completed = true;
    
    /**
     * Error message if export failed
     */
    private String error;
    
    /**
     * Human-readable file size
     */
    private String formattedSize;
    
    // Getter/Setter for compatibility
    public String getFileName() {
        return fileName != null ? fileName : filename;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
        if (this.filename == null) {
            this.filename = fileName;
        }
    }
    
    public static DataExportDto inProgress(int progress, String message) {
        DataExportDto dto = new DataExportDto();
        dto.setProgress(progress);
        dto.setCompleted(false);
        dto.setCreatedAt(LocalDateTime.now());
        return dto;
    }
    
    public static DataExportDto error(String error) {
        DataExportDto dto = new DataExportDto();
        dto.setError(error);
        dto.setCompleted(true);
        dto.setProgress(0);
        dto.setCreatedAt(LocalDateTime.now());
        return dto;
    }
}