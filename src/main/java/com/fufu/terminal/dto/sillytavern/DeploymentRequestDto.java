package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * SillyTavern部署请求DTO
 * 包含部署新SillyTavern容器所需的所有配置信息
 * 
 * @author lizelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentRequestDto {
    
    /**
     * 用户名
     * 用于SillyTavern登录认证，不能为空白字符串
     */
    @NotBlank(message = "Username is required")
    private String username;
    
    /**
     * 密码
     * 用于SillyTavern登录认证，不能为空白字符串
     */
    @NotBlank(message = "Password is required")
    private String password;
    
    /**
     * 端口号
     * SillyTavern服务监听的端口号，必须在1024-65535范围内，默认为8000
     */
    @Min(value = 1024, message = "Port must be at least 1024")
    @Max(value = 65535, message = "Port must be at most 65535")
    private Integer port = 8000;
    
    /**
     * 数据路径
     * SillyTavern数据存储路径，默认为"./sillytavern-data"
     */
    private String dataPath = "./sillytavern-data";
    
    /**
     * Docker镜像
     * 用于部署的Docker镜像名称，默认为"ghcr.io/sillytavern/sillytavern:latest"
     */
    private String dockerImage = "ghcr.io/sillytavern/sillytavern:latest";
    
    /**
     * 容器名称
     * Docker容器的名称，默认为"sillytavern"
     */
    private String containerName = "sillytavern";
}