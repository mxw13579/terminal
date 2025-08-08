package com.fufu.terminal.dto.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公钥响应DTO。
 * 用于向前端提供RSA公钥，前端使用此公钥加密敏感凭据。
 * 
 * @author lizelin
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicKeyResponse {
    
    /**
     * Base64编码的RSA公钥
     */
    private String publicKey;
    
    /**
     * 密钥算法信息（可选，用于调试）
     */
    private String algorithm;
    
    /**
     * 密钥长度（可选，用于调试）
     */
    private Integer keyLength;
    
    /**
     * 响应时间戳
     */
    private long timestamp = System.currentTimeMillis();
    
    /**
     * 简化构造函数，仅包含公钥
     * 
     * @param publicKey Base64编码的公钥
     */
    public PublicKeyResponse(String publicKey) {
        this.publicKey = publicKey;
        this.timestamp = System.currentTimeMillis();
    }
}