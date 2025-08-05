package com.fufu.terminal.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

/**
 * SFTP文件列表响应DTO
 * 用于返回远程服务器指定目录的文件列表信息
 * 
 * @author lizelin
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SftpListResponseDto {
    
    /**
     * 目录路径
     * 列出文件的目录路径
     */
    private String path;
    
    /**
     * 文件列表
     * 包含文件详细信息的列表，每个文件信息都是一个映射表
     * 包含文件名、大小、修改时间、权限等信息
     */
    private List<Map<String, Object>> files;
    
    /**
     * 构造函数
     * 创建包含目录路径和文件列表的响应DTO
     * 
     * @param path 目录路径
     * @param files 文件列表
     */
    public SftpListResponseDto(String path, List<Map<String, Object>> files) {
        this.path = path;
        this.files = files;
    }
    
    /**
     * 默认构造函数
     * 创建空的文件列表响应DTO
     */
    public SftpListResponseDto() {
        // 默认构造器
    }
}