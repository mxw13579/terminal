package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * DTO for data export operations.
 * Provides download URL and metadata for exported data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataExportDto {
    
    /**
     * URL to download the exported data
     */
    private String downloadUrl;
    
    /**
     * Filename of the exported data
     */
    private String filename;
    
    /**
     * Size of the exported file in bytes
     */
    private Long sizeBytes;
    
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