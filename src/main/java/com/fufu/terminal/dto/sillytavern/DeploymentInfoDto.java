package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * SillyTavern部署信息DTO
 * 用于存储和读取部署时的配置信息，支持NAT环境
 * 
 * @author lizelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentInfoDto {
    
    /**
     * 认证信息
     */
    private AuthenticationInfo authentication;
    
    /**
     * 端口配置信息
     */
    private PortInfo ports;
    
    /**
     * 网络配置信息
     */
    private NetworkInfo network;
    
    /**
     * 部署信息
     */
    private DeploymentInfo deployment;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthenticationInfo {
        /**
         * 登录用户名
         */
        private String username;
        
        /**
         * 登录密码
         */
        private String password;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortInfo {
        /**
         * 容器内部端口 (通常是8000)
         */
        private Integer internal;
        
        /**
         * 服务器上映射的端口
         */
        private Integer external;
        
        /**
         * NAT环境下的外部端口 (如果存在)
         */
        private Integer natExternal;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkInfo {
        /**
         * 容器内部IP地址
         */
        private String internalHost;
        
        /**
         * 服务器公网IP地址
         */
        private String externalHost;
        
        /**
         * NAT环境下的外部IP地址 (如果存在)
         */
        private String natExternalHost;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeploymentInfo {
        /**
         * 部署时间
         */
        private String time;
        
        /**
         * 部署版本
         */
        private String version;
        
        /**
         * 部署环境类型: "direct", "nat", "proxy"
         */
        private String environment;
    }
}