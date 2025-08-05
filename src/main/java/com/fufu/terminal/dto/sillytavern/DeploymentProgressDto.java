package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 部署进度更新DTO
 * 在部署过程中发送，提供实时的进度反馈信息
 * 
 * @author lizelin
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentProgressDto {
    
    /**
     * 当前部署阶段
     * 部署过程的当前阶段名称
     */
    private String stage;
    
    /**
     * 进度百分比
     * 部署进度的百分比（0-100）
     */
    private Integer progress;
    
    /**
     * 进度消息
     * 人类可读的进度描述消息
     */
    private String message;
    
    /**
     * 错误信息
     * 如果部署失败，显示错误消息
     */
    private String error;
    
    /**
     * 是否完成
     * 标识部署是否已完成，默认为false
     */
    private Boolean completed = false;
    
    /**
     * 是否成功
     * 标识部署是否成功，默认为false
     */
    private Boolean success = false;
    
    /**
     * 创建成功的部署进度DTO
     * 创建一个表示部署阶段成功的DTO对象
     * 
     * @param stage 部署阶段
     * @param progress 进度百分比（0-100）
     * @param message 成功消息
     * @return 成功的部署进度DTO
     */
    public static DeploymentProgressDto success(String stage, int progress, String message) {
        return new DeploymentProgressDto(stage, progress, message, null, false, true);
    }
    
    /**
     * 创建错误的部署进度DTO
     * 创建一个表示部署阶段失败的DTO对象
     * 
     * @param stage 部署阶段
     * @param error 错误消息
     * @return 错误的部署进度DTO
     */
    public static DeploymentProgressDto error(String stage, String error) {
        return new DeploymentProgressDto(stage, 0, null, error, true, false);
    }
    
    /**
     * 创建已完成的部署进度DTO
     * 创建一个表示部署已完成的DTO对象
     * 
     * @param message 完成消息
     * @return 已完成的部署进度DTO
     */
    public static DeploymentProgressDto completed(String message) {
        return new DeploymentProgressDto("completed", 100, message, null, true, true);
    }
}