package com.fufu.terminal.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 简单的SFTP上传进度DTO
 * 
 * @author lizelin
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SftpUploadProgressDto {
    private String filename;
    private int chunkIndex;
    private int totalChunks;
    private double percentComplete;
    private String status;
    
    public SftpUploadProgressDto(String filename, int chunkIndex, int totalChunks) {
        this.filename = filename;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.percentComplete = totalChunks > 0 ? (double) (chunkIndex + 1) / totalChunks * 100 : 0;
        this.status = chunkIndex + 1 == totalChunks ? "complete" : "uploading";
    }
}