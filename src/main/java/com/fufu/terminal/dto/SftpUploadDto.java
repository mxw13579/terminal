package com.fufu.terminal.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

/**
 * 简单的SFTP上传分片DTO
 * 
 * @author lizelin
 */
@Data
public class SftpUploadDto {
    @NotBlank
    private String path;
    
    @NotBlank
    private String filename;
    
    @Min(0)
    private int chunkIndex;
    
    @Min(1)
    private int totalChunks;
    
    @NotBlank
    private String content;
}