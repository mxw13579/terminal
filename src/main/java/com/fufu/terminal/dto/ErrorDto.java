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
    private String code;
    private String message;
    private String details;
    
    public ErrorDto(String code, String message) {
        this.code = code;
        this.message = message;
    }
}