package com.fufu.terminal.dto;

import lombok.Data;

/**
 * 简单的SFTP列表请求DTO
 * 
 * @author lizelin
 */
@Data
public class SftpListDto {
    private String path = ".";
}