package com.fufu.terminal.dto;

import lombok.Data;
import jakarta.validation.constraints.Min;

/**
 * 监控启动请求DTO
 * 用于启动系统监控服务的请求参数
 * 
 * @author lizelin
 */
@Data
public class MonitorStartDto {
    
    /**
     * 监控频率（秒）
     * 系统监控的数据采集频率，单位为秒，必须大于等于1，默认为5秒
     */
    @Min(value = 1, message = "监控频率必须大于等于1秒")
    private int frequencySeconds = 5;
}