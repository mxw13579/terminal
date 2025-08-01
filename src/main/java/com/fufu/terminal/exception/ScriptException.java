package com.fufu.terminal.exception;

/**
 * 脚本执行相关异常
 * @author lizelin
 */
public class ScriptException extends BaseException {
    
    public ScriptException(String message) {
        super("SCRIPT_ERROR", message, 500);
    }
    
    public ScriptException(String message, Throwable cause) {
        super("SCRIPT_ERROR", message, 500, cause);
    }
    
    /**
     * 脚本不存在异常
     */
    public static class ScriptNotFoundException extends ScriptException {
        public ScriptNotFoundException(String message) {
            super("Script not found: " + message);
        }
    }
    
    /**
     * 脚本执行异常
     */
    public static class ExecutionException extends ScriptException {
        public ExecutionException(String message) {
            super("Script execution failed: " + message);
        }
        
        public ExecutionException(String message, Throwable cause) {
            super("Script execution failed: " + message, cause);
        }
    }
    
    /**
     * 脚本超时异常
     */
    public static class TimeoutException extends ScriptException {
        public TimeoutException(String message) {
            super("Script execution timeout: " + message);
        }
    }
    
    /**
     * 脚本验证异常
     */
    public static class ValidationException extends ScriptException {
        public ValidationException(String message) {
            super("Script validation failed: " + message);
        }
        
        public ValidationException(String message, Object data) {
            super("Script validation failed: " + message);
        }
    }
    
    /**
     * 脚本交互异常
     */
    public static class InteractionException extends ScriptException {
        public InteractionException(String message) {
            super("Script interaction failed: " + message);
        }
    }
}