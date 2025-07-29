package com.fufu.terminal.command.model;

import lombok.Data;

/**
 * SSH连接配置信息
 * @author lizelin
 */
@Data
public class SshConnectionConfig {
    /**
     * 服务器主机地址
     */
    private String host;
    
    /**
     * SSH端口，默认22
     */
    private int port = 22;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 密码
     */
    private String password;
    
    /**
     * 连接超时时间（毫秒），默认30秒
     */
    private int connectTimeout = 30000;
    
    /**
     * 通道连接超时时间（毫秒），默认3秒
     */
    private int channelTimeout = 3000;
    
    /**
     * 验证配置是否完整
     */
    public boolean isValid() {
        return host != null && !host.trim().isEmpty() &&
               username != null && !username.trim().isEmpty() &&
               password != null && !password.trim().isEmpty() &&
               port > 0 && port <= 65535;
    }
}