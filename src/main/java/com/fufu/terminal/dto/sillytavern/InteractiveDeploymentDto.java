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
        /**
         * 部署模式
         * 部署模式，可选值："trusted" | "confirmation"
         */
        @NotBlank(message = "部署模式不能为空")
        private String deploymentMode; // "trusted" | "confirmation"
        
        /**
         * 用户自定义配置
         * 用户提供的自定义配置映射表
         */
        private Map<String, Object> customConfig; // 用户自定义配置
        
        /**
         * 是否启用实时日志
         * 标识是否启用部署过程的实时日志记录，默认为true
         */
        @Builder.Default
        private boolean enableLogging = true; // 是否启用实时日志
        
        /**
         * 步骤超时时间
         * 每个部署步骤的超时时间（秒），默认为300秒
         */
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
        /**
         * 步骤ID
         * 步骤的唯一标识符
         */
        @NotBlank(message = "步骤ID不能为空")
        private String stepId; // 步骤标识
        
        /**
         * 步骤名称
         * 步骤的显示名称
         */
        @NotBlank(message = "步骤名称不能为空")
        private String stepName; // 步骤名称
        
        /**
         * 步骤状态
         * 步骤的当前状态，可选值："pending" | "running" | "completed" | "failed" | "waiting_confirmation"
         */
        @NotBlank(message = "步骤状态不能为空")
        private String status; // pending | running | completed | failed | waiting_confirmation
        
        /**
         * 进度百分比
         * 步骤完成的进度百分比（0-100），默认为0
         */
        @Builder.Default
        private int progress = 0; // 进度百分比 0-100
        
        /**
         * 状态描述
         * 步骤当前状态的详细描述信息
         */
        private String message; // 状态描述
        
        /**
         * 是否需要用户确认
         * 标识此步骤是否需要用户确认才能继续，默认为false
         */
        @Builder.Default
        private boolean requiresConfirmation = false; // 是否需要用户确认
        
        /**
         * 确认相关数据
         * 用户确认所需的相关数据映射表
         */
        private Map<String, Object> confirmationData; // 确认相关数据
        
        /**
         * 步骤日志
         * 步骤执行过程中的日志信息列表
         */
        private List<String> logs; // 步骤日志
        
        /**
         * 时间戳
         * 步骤状态更新的时间戳
         */
        private long timestamp; // 时间戳
        
        /**
         * 错误信息
         * 如果步骤执行失败，显示错误信息
         */
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
        /**
         * 步骤ID
         * 要确认的步骤的唯一标识符
         */
        @NotBlank(message = "步骤ID不能为空")
        private String stepId; // 步骤标识
        
        /**
         * 用户操作
         * 用户的确认操作类型，可选值："confirm" | "skip" | "cancel"
         */
        @NotBlank(message = "用户操作不能为空")
        private String action; // "confirm" | "skip" | "cancel"
        
        /**
         * 用户选择的配置
         * 用户选择的配置选项映射表
         */
        private Map<String, Object> userChoice; // 用户选择的配置
        
        /**
         * 用户操作原因
         * 用户执行此操作的原因说明
         */
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
        /**
         * 会话ID
         * 部署会话的唯一标识符
         */
        @NotBlank(message = "会话ID不能为空")
        private String sessionId; // 会话标识
        
        /**
         * 部署模式
         * 部署的模式类型
         */
        @NotBlank(message = "部署模式不能为空")
        private String deploymentMode; // 部署模式
        
        /**
         * 步骤列表
         * 所有部署步骤状态的列表
         */
        @NotNull(message = "步骤列表不能为空")
        private List<StepDto> steps; // 所有步骤状态
        
        /**
         * 当前步骤索引
         * 当前正在执行的步骤索引，默认为0
         */
        @Builder.Default
        private int currentStepIndex = 0; // 当前步骤索引
        
        /**
         * 是否正在运行
         * 标识部署过程是否正在运行，默认为false
         */
        @Builder.Default
        private boolean isRunning = false; // 是否正在运行
        
        /**
         * 是否已完成
         * 标识部署过程是否已完成，默认为false
         */
        @Builder.Default
        private boolean isCompleted = false; // 是否已完成
        
        /**
         * 是否成功
         * 标识部署过程是否成功完成，默认为false
         */
        @Builder.Default
        private boolean isSuccess = false; // 是否成功
        
        /**
         * 整体错误信息
         * 部署过程中出现的整体错误信息
         */
        private String errorMessage; // 整体错误信息
        
        /**
         * 开始时间
         * 部署过程的开始时间戳
         */
        private long startTime; // 开始时间
        
        /**
         * 结束时间
         * 部署过程的结束时间戳
         */
        private long endTime; // 结束时间
        
        /**
         * 最终结果
         * 部署过程的最终结果映射表
         */
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
        /**
         * 国家代码
         * 检测到的国家或地区的ISO代码
         */
        private String countryCode; // 国家代码
        
        /**
         * 国家名称
         * 检测到的国家或地区的完整名称
         */
        private String countryName; // 国家名称
        
        /**
         * 是否在中国
         * 标识检测到的位置是否在中国境内
         */
        private boolean isChina; // 是否在中国
        
        /**
         * 推荐的镜像源
         * 根据地理位置推荐的Docker镜像源
         */
        private String recommendedMirror; // 推荐的镜像源
        
        /**
         * 检测方式
         * 地理位置检测使用的方法
         */
        private String detectionMethod; // 检测方式
        
        /**
         * 检测是否成功
         * 标识地理位置检测是否成功
         */
        private boolean detectionSuccess; // 检测是否成功
        
        /**
         * 检测错误信息
         * 如果地理位置检测失败，显示错误信息
         */
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
        /**
         * 步骤ID
         * 需要用户确认的步骤的唯一标识符
         */
        @NotBlank(message = "步骤ID不能为空")
        private String stepId; // 步骤标识
        
        /**
         * 步骤名称
         * 需要用户确认的步骤的显示名称
         */
        @NotBlank(message = "步骤名称不能为空")
        private String stepName; // 步骤名称
        
        /**
         * 确认消息
         * 向用户显示的确认消息
         */
        @NotBlank(message = "确认消息不能为空")
        private String message; // 确认消息
        
        /**
         * 确认选项
         * 用户可以选择的确认选项列表
         */
        private List<ConfirmationOptionDto> options; // 确认选项
        
        /**
         * 默认选择
         * 默认推荐的选项值
         */
        private String defaultChoice; // 默认选择
        
        /**
         * 确认超时时间
         * 用户确认的超时时间（秒），默认为180秒
         */
        @Builder.Default
        private int timeoutSeconds = 180; // 确认超时时间
        
        /**
         * 附加数据
         * 与确认请求相关的附加数据映射表
         */
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
        /**
         * 选项键
         * 选项的唯一标识键
         */
        @NotBlank(message = "选项键不能为空")
        private String key; // 选项键
        
        /**
         * 选项标签
         * 选项的显示标签
         */
        @NotBlank(message = "选项标签不能为空")
        private String label; // 选项标签
        
        /**
         * 选项描述
         * 选项的详细描述信息
         */
        private String description; // 选项描述
        
        /**
         * 是否推荐
         * 标识此选项是否为推荐选项，默认为false
         */
        @Builder.Default
        private boolean isRecommended = false; // 是否推荐
        
        /**
         * 是否有风险
         * 标识此选项是否带有风险，默认为false
         */
        @Builder.Default
        private boolean isRisky = false; // 是否有风险
        
        /**
         * 选项元数据
         * 选项的附加元数据映射表
         */
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
        /**
         * 会话ID
         * 部署会话的唯一标识符
         */
        @NotBlank(message = "会话ID不能为空")
        private String sessionId; // 会话标识
        
        /**
         * 当前步骤
         * 当前正在执行的部署步骤
         */
        private StepDto currentStep; // 当前步骤
        
        /**
         * 总步骤数
         * 部署过程的总步骤数，默认为9
         */
        @Builder.Default
        private int totalSteps = 9; // 总步骤数
        
        /**
         * 已完成步骤数
         * 已完成的部署步骤数量，默认为0
         */
        @Builder.Default
        private int completedSteps = 0; // 已完成步骤数
        
        /**
         * 整体进度百分比
         * 部署过程的整体进度百分比，默认为0
         */
        @Builder.Default
        private int overallProgress = 0; // 整体进度百分比
        
        /**
         * 最近的日志
         * 部署过程最近的日志信息列表
         */
        private List<String> recentLogs; // 最近的日志
        
        /**
         * 是否等待用户确认
         * 标识是否正在等待用户确认才能继续
         */
        private boolean waitingForConfirmation; // 是否等待用户确认
        
        /**
         * 待确认的请求
         * 当前等待用户确认的请求信息
         */
        private ConfirmationRequestDto pendingConfirmation; // 待确认的请求
    }
}