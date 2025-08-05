package com.fufu.terminal.dto;

import lombok.Data;

/**
 * 监控停止请求DTO
 * 用于停止系统监控服务的请求参数
 * 
 * @author lizelin
 */
@Data
public class MonitorStopDto {
    
    /**
     * 空的DTO构造器
     * 停止监控服务不需要任何参数，此DTO仅用于标识停止操作的请求
     */
    public MonitorStopDto() {
        // 空的构造器 - 停止操作不需要参数
    }
}