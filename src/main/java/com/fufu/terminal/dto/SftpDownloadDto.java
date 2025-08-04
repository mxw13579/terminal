package com.fufu.terminal.dto;

import lombok.Data;
import java.util.List;

/**
 * 简单的SFTP下载请求DTO
 * 
 * @author lizelin
 */
@Data
public class SftpDownloadDto {
    private List<String> paths;
}