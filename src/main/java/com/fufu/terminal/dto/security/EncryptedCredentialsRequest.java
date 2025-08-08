package com.fufu.terminal.dto.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

/**
 * 加密凭据请求DTO。
 * 前端发送RSA加密后的凭据数据，请求生成会话令牌。
 * 
 * @author lizelin
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EncryptedCredentialsRequest {
    
    /**
     * RSA加密后的凭据数据（Base64编码）
     * 解密后应包含：host, port, user, password 等字段的JSON
     */
    @NotBlank(message = "加密凭据数据不能为空")
    private String encryptedCredentials;
    
    /**
     * 客户端时间戳（可选，用于防重放攻击）
     */
    private Long clientTimestamp;
}