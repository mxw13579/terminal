package com.fufu.terminal.exception;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一错误响应格式
 * @author lizelin
 */
@Data
@AllArgsConstructor
public class ErrorResponse {
    
    private String errorCode;
    private String message;
    private int status;
    private String path;
    private LocalDateTime timestamp;
    private Object data;
    
    public ErrorResponse(String errorCode, String message, int status, String path) {
        this(errorCode, message, status, path, LocalDateTime.now(), null);
    }
    
    public ErrorResponse(String errorCode, String message, int status, String path, Object data) {
        this(errorCode, message, status, path, LocalDateTime.now(), data);
    }
}