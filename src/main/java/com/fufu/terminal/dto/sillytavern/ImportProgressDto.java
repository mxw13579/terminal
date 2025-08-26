package com.fufu.terminal.dto.sillytavern;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 数据导入进度DTO
 * 提供结构化的导入进度信息，包含阶段状态、字节传输量、时间等详细数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportProgressDto {
    
    /**
     * 当前导入阶段
     */
    private String stage;
    
    /**
     * 阶段描述消息
     */
    private String message;
    
    /**
     * 当前阶段进度百分比 (0-100)
     */
    private Integer stageProgress;
    
    /**
     * 总体进度百分比 (0-100)
     */
    private Integer totalProgress;
    
    /**
     * 已处理字节数
     */
    private Long processedBytes;
    
    /**
     * 总字节数
     */
    private Long totalBytes;
    
    /**
     * 处理速度 (bytes/second)
     */
    private Long processingSpeed;
    
    /**
     * 格式化的处理速度字符串
     */
    private String speedFormatted;
    
    /**
     * 已用时间 (毫秒)
     */
    private Long elapsedTimeMs;
    
    /**
     * 估算剩余时间 (毫秒)
     */
    private Long estimatedRemainingMs;
    
    /**
     * 当前操作的文件名
     */
    private String currentFile;
    
    /**
     * 导入开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 是否已完成
     */
    private Boolean completed;
    
    /**
     * 是否发生错误
     */
    private Boolean error;
    
    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 便捷构造函数 - 创建基本进度信息
     */
    public ImportProgressDto(String stage, String message, Integer totalProgress) {
        this.stage = stage;
        this.message = message;
        this.totalProgress = totalProgress;
        this.stageProgress = 0;
        this.completed = false;
        this.error = false;
    }

    /**
     * 便捷构造函数 - 创建带字节信息的进度
     */
    public ImportProgressDto(String stage, String message, Integer totalProgress, 
                           Long processedBytes, Long totalBytes) {
        this(stage, message, totalProgress);
        this.processedBytes = processedBytes;
        this.totalBytes = totalBytes;
    }

    /**
     * 便捷构造函数 - 创建完整的进度信息
     */
    public ImportProgressDto(String stage, String message, Integer stageProgress, Integer totalProgress,
                           Long processedBytes, Long totalBytes, Long processingSpeed, String speedFormatted,
                           Long elapsedTimeMs, String currentFile) {
        this.stage = stage;
        this.message = message;
        this.stageProgress = stageProgress;
        this.totalProgress = totalProgress;
        this.processedBytes = processedBytes;
        this.totalBytes = totalBytes;
        this.processingSpeed = processingSpeed;
        this.speedFormatted = speedFormatted;
        this.elapsedTimeMs = elapsedTimeMs;
        this.currentFile = currentFile;
        this.completed = false;
        this.error = false;
    }

    /**
     * 创建错误状态的进度信息
     */
    public static ImportProgressDto createError(String stage, String errorMessage) {
        ImportProgressDto dto = new ImportProgressDto(stage, "ERROR: " + errorMessage, 0);
        dto.error = true;
        dto.errorMessage = errorMessage;
        return dto;
    }

    /**
     * 创建完成状态的进度信息
     */
    public static ImportProgressDto createCompleted(String message) {
        ImportProgressDto dto = new ImportProgressDto("completed", message, 100);
        dto.completed = true;
        dto.error = false;
        return dto;
    }
}