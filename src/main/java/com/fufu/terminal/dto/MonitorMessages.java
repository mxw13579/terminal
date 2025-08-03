package com.fufu.terminal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 监控相关消息DTO
 *
 * @author lizelin
 */
public class MonitorMessages {

    @Data
    @AllArgsConstructor
    public static class StartRequest {
    }

    @Data
    @AllArgsConstructor
    public static class StopRequest {
        // 监控停止请求，暂时无需参数
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonitorUpdate {
        private String type = "monitor_update";
        private Object payload;
    }
}
