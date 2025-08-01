package com.fufu.terminal.exception;

/**
 * SSH连接和操作相关异常
 * @author lizelin
 */
public class SshException extends BaseException {
    
    public SshException(String message) {
        super("SSH_ERROR", message, 500);
    }
    
    public SshException(String message, Throwable cause) {
        super("SSH_ERROR", message, 500, cause);
    }
    
    /**
     * SSH连接异常
     */
    public static class ConnectionException extends SshException {
        public ConnectionException(String message) {
            super("SSH connection failed: " + message);
        }
        
        public ConnectionException(String message, Throwable cause) {
            super("SSH connection failed: " + message, cause);
        }
    }
    
    /**
     * SSH认证异常
     */
    public static class AuthenticationException extends SshException {
        public AuthenticationException(String message) {
            super("SSH authentication failed: " + message);
        }
        
        public AuthenticationException(String message, Throwable cause) {
            super("SSH authentication failed: " + message, cause);
        }
    }
    
    /**
     * SSH命令执行异常
     */
    public static class CommandExecutionException extends SshException {
        public CommandExecutionException(String message) {
            super("SSH command execution failed: " + message);
        }
        
        public CommandExecutionException(String message, Throwable cause) {
            super("SSH command execution failed: " + message, cause);
        }
    }
    
    /**
     * SFTP操作异常
     */
    public static class SftpException extends SshException {
        public SftpException(String message) {
            super("SFTP operation failed: " + message);
        }
        
        public SftpException(String message, Throwable cause) {
            super("SFTP operation failed: " + message, cause);
        }
    }
}