package com.fufu.terminal.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * 简单的监控更新DTO
 * 
 * @author lizelin
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MonitorUpdateDto {
    private Map<String, Object> data;
}