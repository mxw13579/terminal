package com.fufu.terminal.exception;

/**
 * 资源相关异常
 * @author lizelin
 */
public class ResourceException extends BaseException {
    
    public ResourceException(String message) {
        super("RESOURCE_ERROR", message, 500);
    }
    
    public ResourceException(String message, Throwable cause) {
        super("RESOURCE_ERROR", message, 500, cause);
    }
    
    /**
     * 资源不存在异常
     */
    public static class NotFoundException extends ResourceException {
        public NotFoundException(String message) {
            super("RESOURCE_NOT_FOUND", "Resource not found: " + message, 404);
        }
    }
    
    /**
     * 资源冲突异常
     */
    public static class ConflictException extends ResourceException {
        public ConflictException(String message) {
            super("RESOURCE_CONFLICT", "Resource conflict: " + message, 409);
        }
    }
    
    /**
     * 资源访问异常
     */
    public static class AccessException extends ResourceException {
        public AccessException(String message) {
            super("RESOURCE_ACCESS_DENIED", "Resource access denied: " + message, 403);
        }
    }
}