package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * 日志查看请求DTO
 * 指定要检索的日志数量和时间范围
 * 
 * @author lizelin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogRequestDto {
    
    @Min(value = 1, message = "Days must be at least 1")
    @Max(value = 30, message = "Days cannot exceed 30")
    private Integer days = 1;
    
    @Min(value = 10, message = "Tail lines must be at least 10")
    @Max(value = 10000, message = "Tail lines cannot exceed 10000")
    private Integer tailLines = 100;
    
    private String containerName = "sillytavern";
    
    private Boolean follow = false;  // For real-time log streaming
    
    private String logLevel;  // Filter by log level if specified
    
    public static LogRequestDto defaultRequest() {
        return new LogRequestDto(1, 100, "sillytavern", false, null);
    }
    
    public static LogRequestDto lastHour() {
        return new LogRequestDto(1, 500, "sillytavern", false, null);
    }
    
    public static LogRequestDto realTime() {
        return new LogRequestDto(1, 50, "sillytavern", true, null);
    }
}