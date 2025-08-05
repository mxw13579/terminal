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
    
    /**
     * 日志天数
     * 要检索的日志天数，必须在1-30范围内，默认为1
     */
    @Min(value = 1, message = "天数至少为1")
    @Max(value = 30, message = "天数不能超过30")
    private Integer days = 1;
    
    /**
     * 尾部行数
     * 要检索的日志尾部行数，必须在10-10000范围内，默认为100
     */
    @Min(value = 10, message = "尾部行数至少为10")
    @Max(value = 10000, message = "尾部行数不能超过10000")
    private Integer tailLines = 100;
    
    /**
     * 容器名称
     * 要检索日志的容器名称，默认为"sillytavern"
     */
    private String containerName = "sillytavern";
    
    /**
     * 是否实时跟踪
     * 用于实时日志流跟踪，默认为false
     */
    private Boolean follow = false;  // For real-time log streaming
    
    /**
     * 日志级别
     * 如果指定，则按日志级别过滤日志
     */
    private String logLevel;  // Filter by log level if specified
    
    /**
     * 创建默认的日志请求DTO
     * 创建一个使用默认参数的日志请求DTO
     * 
     * @return 默认的日志请求DTO
     */
    public static LogRequestDto defaultRequest() {
        return new LogRequestDto(1, 100, "sillytavern", false, null);
    }
    
    /**
     * 创建最近一小时的日志请求DTO
     * 创建一个用于获取最近一小时日志的请求DTO
     * 
     * @return 最近一小时的日志请求DTO
     */
    public static LogRequestDto lastHour() {
        return new LogRequestDto(1, 500, "sillytavern", false, null);
    }
    
    /**
     * 创建实时日志请求DTO
     * 创建一个用于实时日志流跟踪的请求DTO
     * 
     * @return 实时日志请求DTO
     */
    public static LogRequestDto realTime() {
        return new LogRequestDto(1, 50, "sillytavern", true, null);
    }
}