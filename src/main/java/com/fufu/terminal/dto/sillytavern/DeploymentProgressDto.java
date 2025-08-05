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
     * Current deployment stage
     */
    private String stage;
    
    /**
     * Progress percentage (0-100)
     */
    private Integer progress;
    
    /**
     * Human-readable progress message
     */
    private String message;
    
    /**
     * Error message if deployment failed
     */
    private String error;
    
    /**
     * Whether deployment is complete
     */
    private Boolean completed = false;
    
    /**
     * Whether deployment was successful
     */
    private Boolean success = false;
    
    public static DeploymentProgressDto success(String stage, int progress, String message) {
        return new DeploymentProgressDto(stage, progress, message, null, false, true);
    }
    
    public static DeploymentProgressDto error(String stage, String error) {
        return new DeploymentProgressDto(stage, 0, null, error, true, false);
    }
    
    public static DeploymentProgressDto completed(String message) {
        return new DeploymentProgressDto("completed", 100, message, null, true, true);
    }
}