package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.List;

/**
 * 交互式部署相关的数据传输对象集合
 * 
 * @author lizelin
 */
public class InteractiveDeploymentDto {

    /**
     * 交互式部署请求DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestDto {
        @NotBlank(message = "部署模式不能为空")
        private String deploymentMode; // "trusted" | "confirmation"
        
        private Map<String, Object> customConfig; // 用户自定义配置
        
        @Builder.Default
        private boolean enableLogging = true; // 是否启用实时日志
        
        @Builder.Default
        private int timeoutSeconds = 300; // 步骤超时时间
    }

    /**
     * 部署步骤状态DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepDto {
        @NotBlank(message = "步骤ID不能为空")
        private String stepId; // 步骤标识
        
        @NotBlank(message = "步骤名称不能为空")
        private String stepName; // 步骤名称
        
        @NotBlank(message = "步骤状态不能为空")
        private String status; // pending | running | completed | failed | waiting_confirmation
        
        @Builder.Default
        private int progress = 0; // 进度百分比 0-100
        
        private String message; // 状态描述
        
        @Builder.Default
        private boolean requiresConfirmation = false; // 是否需要用户确认
        
        private Map<String, Object> confirmationData; // 确认相关数据
        
        private List<String> logs; // 步骤日志
        
        private long timestamp; // 时间戳
        
        private String errorMessage; // 错误信息
    }

    /**
     * 用户确认响应DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmationDto {
        @NotBlank(message = "步骤ID不能为空")
        private String stepId; // 步骤标识
        
        @NotBlank(message = "用户操作不能为空")
        private String action; // "confirm" | "skip" | "cancel"
        
        private Map<String, Object> userChoice; // 用户选择的配置
        
        private String reason; // 用户操作原因
    }

    /**
     * 部署状态DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusDto {
        @NotBlank(message = "会话ID不能为空")
        private String sessionId; // 会话标识
        
        @NotBlank(message = "部署模式不能为空")
        private String deploymentMode; // 部署模式
        
        @NotNull(message = "步骤列表不能为空")
        private List<StepDto> steps; // 所有步骤状态
        
        @Builder.Default
        private int currentStepIndex = 0; // 当前步骤索引
        
        @Builder.Default
        private boolean isRunning = false; // 是否正在运行
        
        @Builder.Default
        private boolean isCompleted = false; // 是否已完成
        
        @Builder.Default
        private boolean isSuccess = false; // 是否成功
        
        private String errorMessage; // 整体错误信息
        
        private long startTime; // 开始时间
        
        private long endTime; // 结束时间
        
        private Map<String, Object> finalResult; // 最终结果
    }

    /**
     * 地理位置检测结果DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeolocationResultDto {
        private String countryCode; // 国家代码
        private String countryName; // 国家名称
        private boolean isChina; // 是否在中国
        private String recommendedMirror; // 推荐的镜像源
        private String detectionMethod; // 检测方式
        private boolean detectionSuccess; // 检测是否成功
        private String errorMessage; // 检测错误信息
    }

    /**
     * 确认请求数据DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmationRequestDto {
        @NotBlank(message = "步骤ID不能为空")
        private String stepId; // 步骤标识
        
        @NotBlank(message = "步骤名称不能为空")
        private String stepName; // 步骤名称
        
        @NotBlank(message = "确认消息不能为空")
        private String message; // 确认消息
        
        private List<ConfirmationOptionDto> options; // 确认选项
        
        private String defaultChoice; // 默认选择
        
        @Builder.Default
        private int timeoutSeconds = 180; // 确认超时时间
        
        private Map<String, Object> additionalData; // 附加数据
    }

    /**
     * 确认选项DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmationOptionDto {
        @NotBlank(message = "选项键不能为空")
        private String key; // 选项键
        
        @NotBlank(message = "选项标签不能为空")
        private String label; // 选项标签
        
        private String description; // 选项描述
        
        @Builder.Default
        private boolean isRecommended = false; // 是否推荐
        
        @Builder.Default
        private boolean isRisky = false; // 是否有风险
        
        private Map<String, Object> metadata; // 选项元数据
    }

    /**
     * 部署进度DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgressDto {
        @NotBlank(message = "会话ID不能为空")
        private String sessionId; // 会话标识
        
        private StepDto currentStep; // 当前步骤
        
        @Builder.Default
        private int totalSteps = 9; // 总步骤数
        
        @Builder.Default
        private int completedSteps = 0; // 已完成步骤数
        
        @Builder.Default
        private int overallProgress = 0; // 整体进度百分比
        
        private List<String> recentLogs; // 最近的日志
        
        private boolean waitingForConfirmation; // 是否等待用户确认
        
        private ConfirmationRequestDto pendingConfirmation; // 待确认的请求
    }
}