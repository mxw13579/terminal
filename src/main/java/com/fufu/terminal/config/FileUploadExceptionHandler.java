package com.fufu.terminal.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器
 * 专门处理文件上传相关异常
 */
@Slf4j
@ControllerAdvice
public class FileUploadExceptionHandler {

    /**
     * 处理文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.error("文件上传大小超限: {}", e.getMessage());
        
        // 提取更友好的错误信息
        long maxSize = e.getMaxUploadSize();
        String maxSizeStr = formatFileSize(maxSize);
        
        String errorMessage = String.format(
            "文件大小超出限制。最大允许上传: %s。请分批上传较小的文件。", 
            maxSizeStr
        );
        
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body("{\"error\":\"" + errorMessage + "\",\"maxSize\":" + maxSize + "}");
    }
    
    /**
     * 格式化文件大小显示
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}