package com.fufu.terminal.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 脚本执行进度模型
 */
@Data
@NoArgsConstructor 
@AllArgsConstructor
public class ScriptExecutionProgress {
    
    /**
     * 执行会话ID
     */
    private String sessionId;
    
    /**
     * 脚本执行ID
     */
    private Long executionId;
    
    /**
     * 当前执行的步骤名称
     */
    private String currentStep;
    
    /**
     * 当前步骤的进度百分比 (0-100)
     */
    private Integer currentStepProgress;
    
    /**
     * 总体进度百分比 (0-100)
     */
    private Integer overallProgress;
    
    /**
     * 已完成的步骤数
     */
    private Integer completedSteps;
    
    /**
     * 总步骤数
     */
    private Integer totalSteps;
    
    /**
     * 当前状态
     */
    private ExecutionStatus status;
    
    /**
     * 状态消息
     */
    private String message;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdateTime;
    
    /**
     * 预计剩余时间（秒）
     */
    private Long estimatedRemainingTime;
    
    /**
     * 执行结果数据
     */
    private Object resultData;
    
    public enum ExecutionStatus {
        PENDING("等待中"),
        RUNNING("执行中"),
        SUCCESS("成功"),
        FAILED("失败"),
        CANCELLED("已取消"),
        TIMEOUT("超时");
        
        private final String description;
        
        ExecutionStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 创建初始进度
     */
    public static ScriptExecutionProgress createInitial(String sessionId, Long executionId, Integer totalSteps) {
        ScriptExecutionProgress progress = new ScriptExecutionProgress();
        progress.setSessionId(sessionId);
        progress.setExecutionId(executionId);
        progress.setTotalSteps(totalSteps);
        progress.setCompletedSteps(0);
        progress.setCurrentStepProgress(0);
        progress.setOverallProgress(0);
        progress.setStatus(ExecutionStatus.PENDING);
        progress.setStartTime(LocalDateTime.now());
        progress.setLastUpdateTime(LocalDateTime.now());
        return progress;
    }
    
    /**
     * 更新当前步骤
     */
    public void updateCurrentStep(String stepName, String message) {
        this.currentStep = stepName;
        this.message = message;
        this.currentStepProgress = 0;
        this.status = ExecutionStatus.RUNNING;
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    /**
     * 更新当前步骤进度
     */
    public void updateStepProgress(Integer stepProgress, String message) {
        this.currentStepProgress = Math.min(100, Math.max(0, stepProgress));
        if (message != null) {
            this.message = message;
        }
        this.lastUpdateTime = LocalDateTime.now();
        
        // 计算总体进度
        calculateOverallProgress();
    }
    
    /**
     * 完成当前步骤
     */
    public void completeCurrentStep(String message) {
        this.completedSteps++;
        this.currentStepProgress = 100;
        if (message != null) {
            this.message = message;
        }
        this.lastUpdateTime = LocalDateTime.now();
        
        // 计算总体进度
        calculateOverallProgress();
        
        // 检查是否全部完成
        if (completedSteps >= totalSteps) {
            this.status = ExecutionStatus.SUCCESS;
            this.overallProgress = 100;
        }
    }
    
    /**
     * 设置执行失败
     */
    public void setFailed(String errorMessage) {
        this.status = ExecutionStatus.FAILED;
        this.errorMessage = errorMessage;
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    /**
     * 设置执行取消
     */
    public void setCancelled(String message) {
        this.status = ExecutionStatus.CANCELLED;
        this.message = message;
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    /**
     * 计算总体进度
     */
    private void calculateOverallProgress() {
        if (totalSteps == null || totalSteps == 0) {
            this.overallProgress = 0;
            return;
        }
        
        double stepWeight = 100.0 / totalSteps;
        double completedProgress = completedSteps * stepWeight;
        double currentStepContribution = (currentStepProgress / 100.0) * stepWeight;
        
        this.overallProgress = (int) Math.min(100, completedProgress + currentStepContribution);
    }
}