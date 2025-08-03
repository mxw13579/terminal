package com.fufu.terminal.ssh;

/**
 * SSH Connection Exception
 * 
 * Exception thrown when SSH connection operations fail.
 */
public class SshConnectionException extends RuntimeException {
    
    public SshConnectionException(String message) {
        super(message);
    }
    
    public SshConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public SshConnectionException(Throwable cause) {
        super(cause);
    }
}