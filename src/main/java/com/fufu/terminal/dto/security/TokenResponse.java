package com.fufu.terminal.dto.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 令牌响应DTO。
 * 服务端生成会话令牌后返回给前端，用于后续WebSocket连接认证。
 * 
 * @author lizelin
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
    
    /**
     * 生成的会话令牌
     */
    private String token;
    
    /**
     * 令牌过期时间（秒）
     */
    private Integer expiresInSec;
    
    /**
     * 响应时间戳
     */
    private long timestamp = System.currentTimeMillis();
    
    /**
     * 成功状态标识
     */
    private boolean success = true;
    
    /**
     * 成功响应的简化构造函数
     * 
     * @param token 会话令牌
     * @param expiresInSec 过期秒数
     */
    public TokenResponse(String token, Integer expiresInSec) {
        this.token = token;
        this.expiresInSec = expiresInSec;
        this.timestamp = System.currentTimeMillis();
        this.success = true;
    }
}