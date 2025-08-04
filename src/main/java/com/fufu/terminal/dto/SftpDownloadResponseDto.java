package com.fufu.terminal.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 简单的SFTP下载响应DTO
 * 
 * @author lizelin
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SftpDownloadResponseDto {
    private String filename;
    private String content;
    private long size;
    private String mimeType;
    
    public SftpDownloadResponseDto(String filename, String content) {
        this.filename = filename;
        this.content = content;
    }
    
    public SftpDownloadResponseDto(String filename, String content, long size) {
        this.filename = filename;
        this.content = content;
        this.size = size;
    }
}