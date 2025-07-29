package com.fufu.terminal.service.callback;

import com.fufu.terminal.model.ScriptExecutionProgress;

/**
 * 脚本执行进度回调接口
 */
public interface ProgressCallback {
    
    /**
     * 更新执行进度
     * @param progress 进度信息
     */
    void onProgressUpdate(ScriptExecutionProgress progress);
    
    /**
     * 步骤开始
     * @param sessionId 会话ID
     * @param stepName 步骤名称
     * @param message 消息
     */
    default void onStepStart(String sessionId, String stepName, String message) {
        // 子类可以选择实现
    }
    
    /**
     * 步骤进度更新
     * @param sessionId 会话ID
     * @param stepProgress 步骤进度 (0-100)
     * @param message 消息
     */
    default void onStepProgress(String sessionId, Integer stepProgress, String message) {
        // 子类可以选择实现
    }
    
    /**
     * 步骤完成
     * @param sessionId 会话ID
     * @param stepName 步骤名称
     * @param message 消息
     */
    default void onStepComplete(String sessionId, String stepName, String message) {
        // 子类可以选择实现
    }
    
    /**
     * 执行完成
     * @param sessionId 会话ID
     * @param success 是否成功
     * @param result 执行结果
     */
    default void onExecutionComplete(String sessionId, boolean success, Object result) {
        // 子类可以选择实现
    }
    
    /**
     * 执行失败
     * @param sessionId 会话ID
     * @param error 错误信息
     */
    default void onExecutionError(String sessionId, String error) {
        // 子类可以选择实现
    }
}