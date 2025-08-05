package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实时日志数据传输对象
 * 用于WebSocket实时日志推送
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealTimeLogDto {
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 容器名称
     */
    private String containerName;
    
    /**
     * 日志行列表
     */
    private List<String> lines;
    
    /**
     * 总行数（缓存中的总数）
     */
    private Integer totalLines;
    
    /**
     * 最大允许缓存的行数
     */
    private Integer maxLines;
    
    /**
     * 时间戳
     */
    private LocalDateTime timestamp;
    
    /**
     * 是否为实时日志（true: 实时推送, false: 历史日志）
     */
    private Boolean isRealTime = false;
    
    /**
     * 是否为完整日志（用于标识日志流结束）
     */
    private Boolean isComplete = false;
    
    /**
     * 日志级别过滤
     */
    private String logLevel;
    
    /**
     * 错误信息（如果获取日志失败）
     */
    private String error;
    
    /**
     * 内存使用情况
     */
    private MemoryInfo memoryInfo;
    
    /**
     * 内存使用信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryInfo {
        /**
         * 当前缓存的日志行数
         */
        private Integer cachedLines;
        
        /**
         * 最大允许缓存行数
         */
        private Integer maxLines;
        
        /**
         * 内存使用百分比
         */
        private Double memoryUsagePercent;
        
        /**
         * 是否需要清理内存
         */
        private Boolean needsCleanup;
    }
    
    /**
     * 创建错误状态的日志DTO
     */
    public static RealTimeLogDto error(String sessionId, String containerName, String errorMessage) {
        return RealTimeLogDto.builder()
            .sessionId(sessionId)
            .containerName(containerName)
            .error(errorMessage)
            .timestamp(LocalDateTime.now())
            .isRealTime(false)
            .isComplete(true)
            .build();
    }
    
    /**
     * 创建空日志DTO（用于心跳或状态更新）
     */
    public static RealTimeLogDto empty(String sessionId, String containerName) {
        return RealTimeLogDto.builder()
            .sessionId(sessionId)
            .containerName(containerName)
            .lines(List.of())
            .totalLines(0)
            .timestamp(LocalDateTime.now())
            .isRealTime(true)
            .isComplete(false)
            .build();
    }
}