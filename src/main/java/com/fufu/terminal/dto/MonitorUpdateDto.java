package com.fufu.terminal.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * 监控数据更新DTO
 * 用于传输系统监控的更新数据
 *
 * @author lizelin
 */
@Data
@NoArgsConstructor
public class MonitorUpdateDto {

    /**
     * 监控数据
     * 包含系统监控各项指标的数据映射表，键为指标名称，值为指标数据
     */
    private Map<String, Object> data;

    /**
     * 构造函数
     * 创建包含监控数据的更新DTO
     *
     * @param data 监控数据映射表
     */
    public MonitorUpdateDto(Map<String, Object> data) {
        this.data = data;
    }
}
