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
    
    /**
     * 上传路径
     * 不能为空白字符串
     */
    @NotBlank
    private String path;
    
    /**
     * 文件名
     * 不能为空白字符串
     */
    @NotBlank
    private String filename;
    
    /**
     * 分片索引
     * 从0开始计数，必须大于等于0
     */
    @Min(0)
    private int chunkIndex;
    
    /**
     * 总分片数
     * 必须大于等于1
     */
    @Min(1)
    private int totalChunks;
    
    /**
     * 分片内容
     * 文件分片的Base64编码内容，不能为空白字符串
     */
    @NotBlank
    private String content;
}