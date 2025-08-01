package com.fufu.terminal.exception;

/**
 * 安全相关异常
 * @author lizelin
 */
public class SecurityException extends BaseException {
    
    public SecurityException(String message) {
        super("SECURITY_ERROR", message, 403);
    }
    
    public SecurityException(String message, Throwable cause) {
        super("SECURITY_ERROR", message, 403, cause);
    }
    
    /**
     * 认证失败异常
     */
    public static class AuthenticationException extends SecurityException {
        public AuthenticationException(String message) {
            super("Authentication failed: " + message);
        }
        
        public AuthenticationException(String message, Throwable cause) {
            super("Authentication failed: " + message, cause);
        }
    }
    
    /**
     * 授权失败异常
     */
    public static class AuthorizationException extends SecurityException {
        public AuthorizationException(String message) {
            super("Authorization failed: " + message);
        }
    }
    
    /**
     * 输入验证异常
     */
    public static class ValidationException extends SecurityException {
        public ValidationException(String message) {
            super("Input validation failed: " + message);
        }
        
        public ValidationException(String message, Object data) {
            super("VALIDATION_ERROR", "Input validation failed: " + message, 400, null, data);
        }
    }
    
    /**
     * 限流异常
     */
    public static class RateLimitException extends SecurityException {
        public RateLimitException(String message) {
            super("RATE_LIMIT_EXCEEDED", "Rate limit exceeded: " + message, 429);
        }
    }
}