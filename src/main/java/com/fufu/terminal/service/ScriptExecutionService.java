package com.fufu.terminal.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.entity.*;
import com.fufu.terminal.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptExecutionService {

    private final ScriptExecutionRepository scriptExecutionRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final AtomicScriptRepository atomicScriptRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    
    // 用于监控的计数器
    private final AtomicInteger activeExecutionCount = new AtomicInteger(0);
    private final AtomicInteger queuedExecutionCount = new AtomicInteger(0);

    public ScriptExecution startExecution(Long scriptId, Long userId, String sessionId) {
        activeExecutionCount.incrementAndGet(); // 增加活跃执行计数
        
        ScriptExecution execution = new ScriptExecution();
        execution.setScriptId(scriptId);
        execution.setUserId(userId);
        execution.setSessionId(sessionId);
        execution.setStatus(ScriptExecution.Status.RUNNING);
        execution.setStartTime(LocalDateTime.now());

        return scriptExecutionRepository.save(execution);
    }

    public void logStep(Long executionId, String stepName, ExecutionLog.LogType logType, String message, Integer stepOrder) {
        ExecutionLog executionLog = new ExecutionLog();
        executionLog.setExecutionId(executionId);
        executionLog.setStepName(stepName);
        executionLog.setLogType(logType);
        executionLog.setMessage(message);
        executionLog.setStepOrder(stepOrder != null ? stepOrder : 0);
        executionLog.setTimestamp(LocalDateTime.now());

        ExecutionLog savedLog = executionLogRepository.save(executionLog);

        // 通过WebSocket实时推送日志到前端
        String topic = "/topic/execution/" + executionId;
        messagingTemplate.convertAndSend(topic, savedLog);

        log.info("Script execution log - ExecutionId: {}, Step: {}, Type: {}, Message: {}",
                executionId, stepName, logType, message);
    }

    public void completeExecution(Long executionId, ScriptExecution.Status status, String errorMessage) {
        ScriptExecution execution = scriptExecutionRepository.findById(executionId)
                .orElseThrow(() -> new RuntimeException("Execution not found"));

        execution.setStatus(status);
        execution.setEndTime(LocalDateTime.now());
        if (errorMessage != null) {
            execution.setErrorMessage(errorMessage);
        }

        scriptExecutionRepository.save(execution);

        // 发送执行完成通知
        String topic = "/topic/execution/" + executionId + "/status";
        messagingTemplate.convertAndSend(topic, execution);
    }

    public List<ExecutionLog> getExecutionLogs(Long executionId) {
        return executionLogRepository.findByExecutionIdOrderByStepOrderAscTimestampAsc(executionId);
    }

    public List<ScriptExecution> getExecutionHistory(Long scriptId) {
        return scriptExecutionRepository.findByScriptIdOrderByStartTimeDesc(scriptId);
    }

    public ScriptExecution getExecutionBySessionId(String sessionId) {
        return scriptExecutionRepository.findBySessionId(sessionId)
                .orElse(null);
    }

    /**
     * 执行聚合脚本
     */
    @Transactional
    public ScriptExecution executeAggregatedScript(AggregatedScript aggregatedScript, String connectionConfig) {
        // 创建执行记录
        ScriptExecution execution = new ScriptExecution();
        execution.setScriptId(aggregatedScript.getId());
        execution.setStatus(ScriptExecution.Status.RUNNING);
        execution.setStartTime(LocalDateTime.now());
        ScriptExecution savedExecution = scriptExecutionRepository.save(execution);

        // 异步执行脚本
        CompletableFuture.runAsync(() -> executeAggregatedScriptAsync(savedExecution, aggregatedScript, connectionConfig));

        return savedExecution;
    }

    /**
     * 异步执行聚合脚本
     */
    private void executeAggregatedScriptAsync(ScriptExecution execution, AggregatedScript aggregatedScript, String connectionConfig) {
        try {
            log.info("开始异步执行聚合脚本: {}", aggregatedScript.getName());
            
            // 解析脚本ID列表
            List<Long> scriptIds = parseScriptIds(aggregatedScript.getScriptIds());
            
            // 逐个执行原子脚本
            for (int i = 0; i < scriptIds.size(); i++) {
                Long scriptId = scriptIds.get(i);
                AtomicScript atomicScript = atomicScriptRepository.findById(scriptId).orElse(null);
                
                if (atomicScript == null) {
                    logStep(execution.getId(), "错误", ExecutionLog.LogType.ERROR, "原子脚本不存在: " + scriptId, i + 1);
                    continue;
                }
                
                logStep(execution.getId(), "步骤 " + (i + 1), ExecutionLog.LogType.INFO, "开始执行: " + atomicScript.getName(), i + 1);
                
                try {
                    // 执行原子脚本
                    boolean success = executeAtomicScript(atomicScript, execution.getId(), i + 1, connectionConfig);
                    
                    if (success) {
                        logStep(execution.getId(), "步骤 " + (i + 1), ExecutionLog.LogType.SUCCESS, "执行成功: " + atomicScript.getName(), i + 1);
                    } else {
                        logStep(execution.getId(), "步骤 " + (i + 1), ExecutionLog.LogType.ERROR, "执行失败: " + atomicScript.getName(), i + 1);
                        // 如果某个步骤失败，标记整个执行为失败
                        completeExecution(execution.getId(), ScriptExecution.Status.FAILED, "步骤执行失败");
                        return;
                    }
                    
                } catch (Exception e) {
                    log.error("执行原子脚本失败: " + atomicScript.getName(), e);
                    logStep(execution.getId(), "步骤 " + (i + 1), ExecutionLog.LogType.ERROR, "执行异常: " + e.getMessage(), i + 1);
                    completeExecution(execution.getId(), ScriptExecution.Status.FAILED, e.getMessage());
                    return;
                }
            }
            
            // 全部执行成功
            logStep(execution.getId(), "完成", ExecutionLog.LogType.SUCCESS, "聚合脚本执行完成", scriptIds.size() + 1);
            completeExecution(execution.getId(), ScriptExecution.Status.SUCCESS, null);
            
        } catch (Exception e) {
            log.error("执行聚合脚本异常", e);
            logStep(execution.getId(), "错误", ExecutionLog.LogType.ERROR, "聚合脚本执行异常: " + e.getMessage(), 0);
            completeExecution(execution.getId(), ScriptExecution.Status.FAILED, e.getMessage());
        }
    }

    /**
     * 执行单个原子脚本
     */
    private boolean executeAtomicScript(AtomicScript atomicScript, Long executionId, int stepOrder, String connectionConfig) {
        try {
            // 模拟脚本执行过程
            logStep(executionId, "步骤 " + stepOrder, ExecutionLog.LogType.INFO, "准备执行环境...", stepOrder);
            Thread.sleep(500);
            
            logStep(executionId, "步骤 " + stepOrder, ExecutionLog.LogType.INFO, "正在执行脚本内容...", stepOrder);
            Thread.sleep(1000);
            
            // 这里应该调用实际的脚本执行逻辑
            // 目前使用模拟执行，随机成功/失败
            boolean success = Math.random() > 0.1; // 90%成功率
            
            if (success) {
                logStep(executionId, "步骤 " + stepOrder, ExecutionLog.LogType.INFO, "脚本执行输出: 操作完成", stepOrder);
            } else {
                logStep(executionId, "步骤 " + stepOrder, ExecutionLog.LogType.ERROR, "脚本执行失败: 模拟错误", stepOrder);
            }
            
            return success;
            
        } catch (Exception e) {
            logStep(executionId, "步骤 " + stepOrder, ExecutionLog.LogType.ERROR, "执行异常: " + e.getMessage(), stepOrder);
            return false;
        }
    }

    /**
     * 解析脚本ID列表
     */
    private List<Long> parseScriptIds(String scriptIdsJson) {
        try {
            return objectMapper.readValue(scriptIdsJson, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.error("解析脚本ID列表失败", e);
            throw new RuntimeException("解析脚本ID列表失败", e);
        }
    }

    /**
     * 取消执行
     */
    @Transactional
    public void cancelExecution(Long executionId) {
        ScriptExecution execution = scriptExecutionRepository.findById(executionId).orElse(null);
        if (execution != null && execution.getStatus() == ScriptExecution.Status.RUNNING) {
            execution.setStatus(ScriptExecution.Status.CANCELLED);
            execution.setEndTime(LocalDateTime.now());
            scriptExecutionRepository.save(execution);
            
            logStep(executionId, "取消", ExecutionLog.LogType.WARN, "执行已被用户取消", 999);
            
            // 通过WebSocket发送状态更新
            messagingTemplate.convertAndSend("/topic/execution/" + executionId + "/status", execution);
        }
    }

    public List<ScriptExecution> getExecutionHistory() {
        return scriptExecutionRepository.findAllByOrderByStartTimeDesc();
    }

    public ScriptExecution getExecutionById(Long executionId) {
        return scriptExecutionRepository.findById(executionId).orElse(null);
    }
    
    // -------------------- 健康检查相关方法 --------------------
    
    /**
     * 获取当前活跃执行数量
     * @return 活跃执行数
     */
    public int getActiveExecutionCount() {
        return activeExecutionCount.get();
    }
    
    /**
     * 获取最大并发执行数量
     * @return 最大并发执行数
     */
    public int getMaxConcurrentExecutions() {
        return 10; // TODO: 从配置中获取实际值
    }
    
    /**
     * 获取排队执行数量
     * @return 排队执行数
     */
    public int getQueuedExecutionCount() {
        return queuedExecutionCount.get();
    }
    
    /**
     * 获取长时间运行的执行数量
     * @return 长时间运行的执行数
     */
    public int getLongRunningExecutionCount() {
        // 查询运行时间超过30分钟的执行
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        return scriptExecutionRepository.countByStatusAndStartTimeBefore(
            ScriptExecution.Status.RUNNING, threshold);
    }
}
