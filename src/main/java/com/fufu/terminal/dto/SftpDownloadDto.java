package com.fufu.terminal.dto;

import lombok.Data;
import java.util.List;

/**
 * SFTP下载请求DTO
 * 用于请求从远程服务器下载文件的参数
 * 
 * @author lizelin
 */
@Data
public class SftpDownloadDto {
    
    /**
     * 文件路径列表
     * 要下载的远程文件路径列表，不能为null
     */
    private List<String> paths;
    
    /**
     * 构造函数
     * 创建包含文件路径列表的下载请求DTO
     * 
     * @param paths 文件路径列表
     */
    public SftpDownloadDto(List<String> paths) {
        this.paths = paths;
    }
    
    /**
     * 默认构造函数
     * 创建空的下载请求DTO
     */
    public SftpDownloadDto() {
        // 默认构造器
    }
}