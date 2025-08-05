package com.fufu.terminal.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 简单的错误响应DTO
 * 
 * @author lizelin
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorDto {
    
    /**
     * 错误代码
     * 用于标识错误类型的唯一代码
     */
    private String code;
    
    /**
     * 错误消息
     * 向用户展示的错误描述信息
     */
    private String message;
    
    /**
     * 错误详情
     * 包含更详细的错误信息，用于调试和排查问题
     */
    private String details;
    
    /**
     * 构造函数（代码和消息）
     * 创建包含错误代码和消息的错误DTO
     * 
     * @param code 错误代码
     * @param message 错误消息
     */
    public ErrorDto(String code, String message) {
        this.code = code;
        this.message = message;
    }
}