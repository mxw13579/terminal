package com.fufu.terminal.config;

import cn.dev33.satoken.exception.NotLoginException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Auth and System Exception Handler
 * Handles Sa-Token authentication exceptions, SSH exceptions, database exceptions and other system-level exceptions
 */
@Slf4j
@RestControllerAdvice
public class AuthAndSystemExceptionHandler {
    
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Map<String, Object>> handleNotLoginException(NotLoginException e) {
        log.warn("Authentication failure: {}", e.getMessage());
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("code", 401);
        result.put("message", "未登录或登录已过期");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }
    
    /**
     * Handle SSH connection failures
     */
    @ExceptionHandler({com.jcraft.jsch.JSchException.class})
    public ResponseEntity<Map<String, Object>> handleSshException(Exception e) {
        log.error("SSH connection failure: {}", e.getMessage(), e);
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("code", 503);
        result.put("message", "SSH连接失败: " + e.getMessage());
        result.put("type", "SSH_CONNECTION_ERROR");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
    }
    
    /**
     * Handle database connection and query failures
     */
    @ExceptionHandler({DataAccessException.class, SQLException.class})
    public ResponseEntity<Map<String, Object>> handleDatabaseException(Exception e) {
        log.error("Database operation failure: {}", e.getMessage(), e);
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("code", 500);
        result.put("message", "数据库操作失败，请稍后重试");
        result.put("type", "DATABASE_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
    
    /**
     * Handle script execution timeouts
     */
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleTimeoutException(TimeoutException e) {
        log.warn("Operation timeout: {}", e.getMessage());
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("code", 408);
        result.put("message", "操作超时，请检查网络连接或重试");
        result.put("type", "TIMEOUT_ERROR");
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(result);
    }
}