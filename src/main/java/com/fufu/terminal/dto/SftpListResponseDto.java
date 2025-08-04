package com.fufu.terminal.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

/**
 * 简单的SFTP文件列表响应DTO
 * 
 * @author lizelin
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SftpListResponseDto {
    private String path;
    private List<Map<String, Object>> files;
}