package com.fufu.terminal.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * SFTP下载响应DTO
 * 用于返回SFTP文件下载的结果数据
 * 
 * @author lizelin
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SftpDownloadResponseDto {
    
    /**
     * 文件名
     * 下载的文件名称
     */
    private String filename;
    
    /**
     * 文件内容
     * 文件的Base64编码内容
     */
    private String content;
    
    /**
     * 文件大小
     * 文件的字节大小
     */
    private long size;
    
    /**
     * MIME类型
     * 文件的MIME类型，用于标识文件格式
     */
    private String mimeType;
    
    /**
     * 构造函数（文件名和内容）
     * 创建包含文件名和内容的响应DTO
     * 
     * @param filename 文件名
     * @param content 文件内容
     */
    public SftpDownloadResponseDto(String filename, String content) {
        this.filename = filename;
        this.content = content;
    }
    
    /**
     * 构造函数（文件名、内容和大小）
     * 创建包含文件名、内容和文件大小的响应DTO
     * 
     * @param filename 文件名
     * @param content 文件内容
     * @param size 文件大小
     */
    public SftpDownloadResponseDto(String filename, String content, long size) {
        this.filename = filename;
        this.content = content;
        this.size = size;
    }
}