package com.fufu.terminal.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * SFTP文件列表请求DTO
 * 用于请求列出远程服务器指定目录的文件
 * 
 * @author lizelin
 */
@Data
public class SftpListDto {
    
    /**
     * 目录路径
     * 要列出文件的目录路径，默认为当前目录"."，不能为空白字符串
     */
    @NotBlank(message = "目录路径不能为空")
    private String path = ".";
    
    /**
     * 构造函数
     * 创建包含目录路径的列表请求DTO
     * 
     * @param path 目录路径
     */
    public SftpListDto(String path) {
        this.path = path;
    }
    
    /**
     * 默认构造函数
     * 创建使用默认路径的列表请求DTO
     */
    public SftpListDto() {
        // 使用默认路径当前目录
    }
}