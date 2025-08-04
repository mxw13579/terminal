package com.fufu.terminal.dto;

import lombok.Data;
import jakarta.validation.constraints.Min;

/**
 * 简单的监控启动DTO
 * 
 * @author lizelin
 */
@Data
public class MonitorStartDto {
    @Min(1)
    private int frequencySeconds = 5;
}