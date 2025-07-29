package com.fufu.terminal.service;

import com.fufu.terminal.entity.ExecutionLog;
import com.fufu.terminal.entity.ScriptExecution;
import com.fufu.terminal.repository.ExecutionLogRepository;
import com.fufu.terminal.repository.ScriptExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptExecutionService {

    private final ScriptExecutionRepository scriptExecutionRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ScriptExecution startExecution(Long scriptId, Long userId, String sessionId) {
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
}
