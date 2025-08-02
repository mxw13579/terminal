package com.fufu.terminal.service.script;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 脚本执行结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScriptExecutionResult {
    
    /**
     * 执行是否成功
     */
    private boolean success;
    
    /**
     * 执行消息
     */
    private String message;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 执行开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 执行结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 执行耗时（毫秒）
     */
    private long duration;
    
    /**
     * 输出数据
     */
    private Map<String, Object> outputData;
    
    /**
     * 控制台输出（显示给用户的内容）
     */
    private String displayOutput;
    
    /**
     * 是否需要显示到前台
     */
    private boolean displayToUser;
    
    /**
     * 是否需要用户交互
     */
    private boolean requiresInteraction = false;

    /**
     * 交互数据
     */
    private Map<String, Object> interactionData;
    
    /**
     * 创建成功结果
     */
    public static ScriptExecutionResult success(String message, Map<String, Object> outputData) {
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setSuccess(true);
        result.setMessage(message);
        result.setOutputData(outputData);
        result.setEndTime(LocalDateTime.now());
        return result;
    }
    
    /**
     * 创建成功结果（带前台显示）
     */
    public static ScriptExecutionResult success(String message, Map<String, Object> outputData, String displayOutput) {
        ScriptExecutionResult result = success(message, outputData);
        result.setDisplayOutput(displayOutput);
        result.setDisplayToUser(true);
        return result;
    }
    
    /**
     * 创建失败结果
     */
    public static ScriptExecutionResult failure(String errorMessage) {
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        result.setEndTime(LocalDateTime.now());
        return result;
    }
    
    /**
     * 创建失败结果（带前台显示）
     */
    public static ScriptExecutionResult failure(String errorMessage, String displayOutput) {
        ScriptExecutionResult result = failure(errorMessage);
        result.setDisplayOutput(displayOutput);
        result.setDisplayToUser(true);
        return result;
    }
}