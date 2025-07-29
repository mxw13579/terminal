package com.fufu.terminal.service;

import com.fufu.terminal.model.ScriptExecutionProgress;
import com.fufu.terminal.model.ScriptExecutionProgress.ExecutionStatus;
import com.fufu.terminal.service.callback.ProgressCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 脚本执行进度管理服务
 * 负责管理和广播脚本执行进度
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressManagerService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    // 存储所有会话的进度信息
    private final Map<String, ScriptExecutionProgress> progressMap = new ConcurrentHashMap<>();
    
    // 存储每个会话的回调监听器
    private final Map<String, CopyOnWriteArrayList<ProgressCallback>> callbackMap = new ConcurrentHashMap<>();
    
    /**
     * 初始化执行进度
     */
    public ScriptExecutionProgress initializeProgress(String sessionId, Long executionId, Integer totalSteps) {
        ScriptExecutionProgress progress = ScriptExecutionProgress.createInitial(sessionId, executionId, totalSteps);
        progressMap.put(sessionId, progress);
        
        // 广播初始进度
        broadcastProgress(progress);
        notifyCallbacks(sessionId, progress);
        
        log.debug("初始化执行进度: sessionId={}, executionId={}, totalSteps={}", sessionId, executionId, totalSteps);
        return progress;
    }
    
    /**
     * 更新当前执行步骤
     */
    public void updateCurrentStep(String sessionId, String stepName, String message) {
        ScriptExecutionProgress progress = progressMap.get(sessionId);
        if (progress != null) {
            progress.updateCurrentStep(stepName, message);
            
            // 广播进度更新
            broadcastProgress(progress);
            notifyCallbacks(sessionId, progress);
            
            // 通知步骤开始
            notifyStepStart(sessionId, stepName, message);
            
            log.debug("更新执行步骤: sessionId={}, stepName={}, message={}", sessionId, stepName, message);
        }
    }
    
    /**
     * 更新当前步骤进度
     */
    public void updateStepProgress(String sessionId, Integer stepProgress, String message) {
        ScriptExecutionProgress progress = progressMap.get(sessionId);
        if (progress != null) {
            progress.updateStepProgress(stepProgress, message);
            
            // 广播进度更新
            broadcastProgress(progress);
            notifyCallbacks(sessionId, progress);
            
            // 通知步骤进度
            notifyStepProgress(sessionId, stepProgress, message);
            
            log.debug("更新步骤进度: sessionId={}, stepProgress={}, message={}", sessionId, stepProgress, message);
        }
    }
    
    /**
     * 完成当前步骤
     */
    public void completeCurrentStep(String sessionId, String message) {
        ScriptExecutionProgress progress = progressMap.get(sessionId);
        if (progress != null) {
            String stepName = progress.getCurrentStep();
            progress.completeCurrentStep(message);
            
            // 广播进度更新
            broadcastProgress(progress);
            notifyCallbacks(sessionId, progress);
            
            // 通知步骤完成
            notifyStepComplete(sessionId, stepName, message);
            
            // 检查是否全部完成
            if (ExecutionStatus.SUCCESS.equals(progress.getStatus())) {
                notifyExecutionComplete(sessionId, true, progress.getResultData());
            }
            
            log.debug("完成执行步骤: sessionId={}, stepName={}, message={}", sessionId, stepName, message);
        }
    }
    
    /**
     * 设置执行失败
     */
    public void setExecutionFailed(String sessionId, String errorMessage) {
        ScriptExecutionProgress progress = progressMap.get(sessionId);
        if (progress != null) {
            progress.setFailed(errorMessage);
            
            // 广播进度更新
            broadcastProgress(progress);
            notifyCallbacks(sessionId, progress);
            
            // 通知执行失败
            notifyExecutionError(sessionId, errorMessage);
            
            log.error("执行失败: sessionId={}, error={}", sessionId, errorMessage);
        }
    }
    
    /**
     * 设置执行取消
     */
    public void setExecutionCancelled(String sessionId, String message) {
        ScriptExecutionProgress progress = progressMap.get(sessionId);
        if (progress != null) {
            progress.setCancelled(message);
            
            // 广播进度更新
            broadcastProgress(progress);
            notifyCallbacks(sessionId, progress);
            
            log.info("执行取消: sessionId={}, message={}", sessionId, message);
        }
    }
    
    /**
     * 设置执行结果数据
     */
    public void setResultData(String sessionId, Object resultData) {
        ScriptExecutionProgress progress = progressMap.get(sessionId);
        if (progress != null) {
            progress.setResultData(resultData);
            
            // 广播进度更新
            broadcastProgress(progress);
            notifyCallbacks(sessionId, progress);
        }
    }
    
    /**
     * 获取执行进度
     */
    public ScriptExecutionProgress getProgress(String sessionId) {
        return progressMap.get(sessionId);
    }
    
    /**
     * 清理执行进度
     */
    public void cleanupProgress(String sessionId) {
        progressMap.remove(sessionId);
        callbackMap.remove(sessionId);
        log.debug("清理执行进度: sessionId={}", sessionId);
    }
    
    /**
     * 注册进度回调
     */
    public void registerCallback(String sessionId, ProgressCallback callback) {
        callbackMap.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(callback);
    }
    
    /**
     * 移除进度回调
     */
    public void removeCallback(String sessionId, ProgressCallback callback) {
        CopyOnWriteArrayList<ProgressCallback> callbacks = callbackMap.get(sessionId);
        if (callbacks != null) {
            callbacks.remove(callback);
        }
    }
    
    /**
     * 广播进度到WebSocket客户端
     */
    private void broadcastProgress(ScriptExecutionProgress progress) {
        try {
            String destination = "/topic/execution/progress/" + progress.getSessionId();
            messagingTemplate.convertAndSend(destination, progress);
        } catch (Exception e) {
            log.error("广播进度失败: sessionId={}", progress.getSessionId(), e);
        }
    }
    
    /**
     * 通知所有回调监听器
     */
    private void notifyCallbacks(String sessionId, ScriptExecutionProgress progress) {
        CopyOnWriteArrayList<ProgressCallback> callbacks = callbackMap.get(sessionId);
        if (callbacks != null) {
            for (ProgressCallback callback : callbacks) {
                try {
                    callback.onProgressUpdate(progress);
                } catch (Exception e) {
                    log.error("进度回调失败: sessionId={}", sessionId, e);
                }
            }
        }
    }
    
    /**
     * 通知步骤开始
     */
    private void notifyStepStart(String sessionId, String stepName, String message) {
        CopyOnWriteArrayList<ProgressCallback> callbacks = callbackMap.get(sessionId);
        if (callbacks != null) {
            for (ProgressCallback callback : callbacks) {
                try {
                    callback.onStepStart(sessionId, stepName, message);
                } catch (Exception e) {
                    log.error("步骤开始回调失败: sessionId={}", sessionId, e);
                }
            }
        }
    }
    
    /**
     * 通知步骤进度
     */
    private void notifyStepProgress(String sessionId, Integer stepProgress, String message) {
        CopyOnWriteArrayList<ProgressCallback> callbacks = callbackMap.get(sessionId);
        if (callbacks != null) {
            for (ProgressCallback callback : callbacks) {
                try {
                    callback.onStepProgress(sessionId, stepProgress, message);
                } catch (Exception e) {
                    log.error("步骤进度回调失败: sessionId={}", sessionId, e);
                }
            }
        }
    }
    
    /**
     * 通知步骤完成
     */
    private void notifyStepComplete(String sessionId, String stepName, String message) {
        CopyOnWriteArrayList<ProgressCallback> callbacks = callbackMap.get(sessionId);
        if (callbacks != null) {
            for (ProgressCallback callback : callbacks) {
                try {
                    callback.onStepComplete(sessionId, stepName, message);
                } catch (Exception e) {
                    log.error("步骤完成回调失败: sessionId={}", sessionId, e);
                }
            }
        }
    }
    
    /**
     * 通知执行完成
     */
    private void notifyExecutionComplete(String sessionId, boolean success, Object result) {
        CopyOnWriteArrayList<ProgressCallback> callbacks = callbackMap.get(sessionId);
        if (callbacks != null) {
            for (ProgressCallback callback : callbacks) {
                try {
                    callback.onExecutionComplete(sessionId, success, result);
                } catch (Exception e) {
                    log.error("执行完成回调失败: sessionId={}", sessionId, e);
                }
            }
        }
    }
    
    /**
     * 通知执行错误
     */
    private void notifyExecutionError(String sessionId, String error) {
        CopyOnWriteArrayList<ProgressCallback> callbacks = callbackMap.get(sessionId);
        if (callbacks != null) {
            for (ProgressCallback callback : callbacks) {
                try {
                    callback.onExecutionError(sessionId, error);
                } catch (Exception e) {
                    log.error("执行错误回调失败: sessionId={}", sessionId, e);
                }
            }
        }
    }
}