package com.fufu.terminal.service.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.entity.AggregatedScript;
import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.entity.ScriptExecutionSession;
import com.fufu.terminal.entity.context.EnhancedScriptContext;
import com.fufu.terminal.entity.enums.ExecutionStatus;
import com.fufu.terminal.entity.enums.InteractionMode;
import com.fufu.terminal.entity.interaction.ExecutionMessage;
import com.fufu.terminal.entity.interaction.InteractionRequest;
import com.fufu.terminal.entity.interaction.InteractionResponse;
import com.fufu.terminal.repository.ScriptExecutionSessionRepository;
import com.fufu.terminal.service.AtomicScriptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 交互式脚本执行引擎
 */
@Slf4j
@Service
public class InteractiveScriptExecutor {
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private AtomicScriptService atomicScriptService;
    
    @Autowired
    private ScriptExecutionSessionRepository sessionRepository;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    // 存储等待用户响应的会话
    private final ConcurrentHashMap<String, CompletableFuture<InteractionResponse>> pendingInteractions = new ConcurrentHashMap<>();
    
    /**
     * 执行聚合脚本
     */
    public CompletableFuture<Void> executeAggregateScript(String sessionId, AggregatedScript script, EnhancedScriptContext context) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting execution of aggregate script: {} (session: {})", script.getName(), sessionId);
                
                // 创建执行会话记录
                ScriptExecutionSession session = createExecutionSession(sessionId, script, context);
                
                // 初始化系统变量
                context.initializeSystemVariables();
                
                // 发送执行开始消息
                sendExecutionMessage(sessionId, createStartMessage(script));
                
                // 获取原子脚本列表
                List<AtomicScript> atomicScripts = getAtomicScriptsFromAggregated(script);
                
                // 逐步执行原子脚本
                for (int i = 0; i < atomicScripts.size(); i++) {
                    AtomicScript atomicScript = atomicScripts.get(i);
                    String stepId = sessionId + "_step_" + i;
                    
                    executeAtomicScriptInteractively(sessionId, stepId, atomicScript, context);
                }
                
                // 更新会话状态为完成
                session.setStatus(ExecutionStatus.COMPLETED);
                session.setEndTime(LocalDateTime.now());
                sessionRepository.save(session);
                
                // 发送执行完成消息
                sendExecutionMessage(sessionId, createCompletionMessage(script));
                
                log.info("Completed execution of aggregate script: {} (session: {})", script.getName(), sessionId);
                
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    log.info("Execution was interrupted for session: {}", sessionId);
                    sendExecutionMessage(sessionId, createCancelledMessage());
                    Thread.currentThread().interrupt();
                } else {
                    log.error("Error executing aggregate script for session {}: ", sessionId, e);
                    sendExecutionMessage(sessionId, createErrorMessage(e));
                }
            }
        }, executorService);
    }
    
    /**
     * 交互式执行原子脚本
     */
    private void executeAtomicScriptInteractively(String sessionId, String stepId, AtomicScript script, EnhancedScriptContext context) {
        try {
            log.debug("Executing atomic script: {} (step: {})", script.getName(), stepId);
            
            // 发送步骤开始消息
            sendExecutionMessage(sessionId, createStepStartMessage(stepId, script));
            
            // 检查是否需要交互
            if (script.getInteractionMode() != InteractionMode.SILENT) {
                handleScriptInteraction(sessionId, stepId, script, context);
            }
            
            // 执行脚本
            if (script.getInteractionMode() == InteractionMode.REALTIME_OUTPUT) {
                executeWithRealtimeOutput(sessionId, stepId, script, context);
            } else {
                executeStandardScript(sessionId, stepId, script, context);
            }
            
            // 发送步骤完成消息
            sendExecutionMessage(sessionId, createStepCompletionMessage(stepId, script));
            
        } catch (Exception e) {
            log.error("Error executing atomic script {} (step: {}): ", script.getName(), stepId, e);
            sendExecutionMessage(sessionId, createStepErrorMessage(stepId, script, e));
        }
    }
    
    /**
     * 处理脚本交互
     */
    private void handleScriptInteraction(String sessionId, String stepId, AtomicScript script, EnhancedScriptContext context) {
        try {
            // 解析交互配置
            if (script.getInteractionConfig() != null && !script.getInteractionConfig().trim().isEmpty()) {
                InteractionRequest[] interactions = objectMapper.readValue(script.getInteractionConfig(), InteractionRequest[].class);
                
                for (InteractionRequest interaction : interactions) {
                    // 检查交互条件
                    if (shouldTriggerInteraction(interaction, context)) {
                        // 发送交互请求
                        sendInteractionRequest(sessionId, stepId, interaction);
                        
                        // 等待用户响应
                        InteractionResponse userResponse = waitForUserResponse(sessionId, stepId);
                        
                        // 处理用户响应
                        processUserResponse(interaction, userResponse, context);
                    }
                }
            }
        } catch (JsonProcessingException e) {
            log.error("Error parsing interaction config for script {}: ", script.getName(), e);
        }
    }
    
    /**
     * 检查是否应该触发交互
     */
    private boolean shouldTriggerInteraction(InteractionRequest interaction, EnhancedScriptContext context) {
        if (interaction.getCondition() == null || interaction.getCondition().trim().isEmpty()) {
            return true;
        }
        
        // 简单的条件表达式解析
        // 例如："SERVER_LOCATION == 'china'"
        String condition = context.resolveVariables(interaction.getCondition());
        
        // 这里可以实现更复杂的表达式解析逻辑
        // 暂时使用简单的字符串匹配
        return !condition.contains("false") && !condition.contains("null");
    }
    
    /**
     * 发送交互请求
     */
    private void sendInteractionRequest(String sessionId, String stepId, InteractionRequest interaction) {
        ExecutionMessage message = new ExecutionMessage();
        message.setStepId(stepId);
        message.setStatus(ExecutionStatus.WAITING_INPUT);
        message.setMessage("等待用户响应");
        message.setTimestamp(System.currentTimeMillis());
        message.setInteraction(interaction);
        
        sendExecutionMessage(sessionId, message);
    }
    
    /**
     * 等待用户响应
     */
    private InteractionResponse waitForUserResponse(String sessionId, String stepId) {
        CompletableFuture<InteractionResponse> future = new CompletableFuture<>();
        pendingInteractions.put(sessionId + "_" + stepId, future);
        
        try {
            return future.get(); // 阻塞等待用户响应
        } catch (Exception e) {
            log.error("Error waiting for user response: ", e);
            throw new RuntimeException("Failed to get user response", e);
        } finally {
            pendingInteractions.remove(sessionId + "_" + stepId);
        }
    }
    
    /**
     * 处理用户响应
     */
    public void handleUserResponse(String sessionId, InteractionResponse response) {
        String key = sessionId + "_" + response.getInteractionId();
        CompletableFuture<InteractionResponse> future = pendingInteractions.get(key);
        
        if (future != null) {
            future.complete(response);
            log.debug("Processed user response for session: {} interaction: {}", sessionId, response.getInteractionId());
        } else {
            log.warn("No pending interaction found for session: {} interaction: {}", sessionId, response.getInteractionId());
        }
    }
    
    /**
     * 处理用户响应到上下文
     */
    private void processUserResponse(InteractionRequest interaction, InteractionResponse userResponse, EnhancedScriptContext context) {
        // 将用户响应存储到上下文中
        if (userResponse.getResponse() != null) {
            context.setUserInteractionVariable(interaction.getInteractionId(), userResponse.getResponse());
        }
    }
    
    /**
     * 执行标准脚本
     */
    private void executeStandardScript(String sessionId, String stepId, AtomicScript script, EnhancedScriptContext context) {
        // 这里实现标准脚本执行逻辑
        // 可以调用现有的ScriptEngine或命令执行器
        log.debug("Executing standard script: {}", script.getName());
        
        // 模拟脚本执行
        try {
            Thread.sleep(1000); // 模拟执行时间
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 执行带实时输出的脚本
     */
    private void executeWithRealtimeOutput(String sessionId, String stepId, AtomicScript script, EnhancedScriptContext context) {
        log.debug("Executing script with realtime output: {}", script.getName());
        
        // 这里实现实时输出脚本执行逻辑
        // 模拟实时输出
        for (int i = 1; i <= 5; i++) {
            ExecutionMessage message = new ExecutionMessage();
            message.setStepId(stepId);
            message.setStatus(ExecutionStatus.EXECUTING);
            message.setMessage("执行中... " + (i * 20) + "%");
            message.setProgress(i * 20);
            message.setTimestamp(System.currentTimeMillis());
            
            sendExecutionMessage(sessionId, message);
            
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    // 辅助方法：创建各种消息
    private ExecutionMessage createStartMessage(AggregatedScript script) {
        ExecutionMessage message = new ExecutionMessage();
        message.setStepId("start");
        message.setStepName("开始执行");
        message.setStatus(ExecutionStatus.PREPARING);
        message.setMessage("开始执行聚合脚本: " + script.getName());
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }
    
    private ExecutionMessage createStepStartMessage(String stepId, AtomicScript script) {
        ExecutionMessage message = new ExecutionMessage();
        message.setStepId(stepId);
        message.setStepName(script.getName());
        message.setStatus(ExecutionStatus.EXECUTING);
        message.setMessage("正在执行: " + script.getName());
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }
    
    private ExecutionMessage createStepCompletionMessage(String stepId, AtomicScript script) {
        ExecutionMessage message = new ExecutionMessage();
        message.setStepId(stepId);
        message.setStepName(script.getName());
        message.setStatus(ExecutionStatus.COMPLETED);
        message.setMessage("完成执行: " + script.getName());
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }
    
    private ExecutionMessage createStepErrorMessage(String stepId, AtomicScript script, Exception e) {
        ExecutionMessage message = new ExecutionMessage();
        message.setStepId(stepId);
        message.setStepName(script.getName());
        message.setStatus(ExecutionStatus.FAILED);
        message.setMessage("执行失败: " + e.getMessage());
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }
    
    private ExecutionMessage createCompletionMessage(AggregatedScript script) {
        ExecutionMessage message = new ExecutionMessage();
        message.setStepId("complete");
        message.setStepName("执行完成");
        message.setStatus(ExecutionStatus.COMPLETED);
        message.setMessage("聚合脚本执行完成: " + script.getName());
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }
    
    private ExecutionMessage createCancelledMessage() {
        ExecutionMessage message = new ExecutionMessage();
        message.setStepId("cancelled");
        message.setStepName("执行取消");
        message.setStatus(ExecutionStatus.CANCELLED);
        message.setMessage("执行已被取消");
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }
    
    private ExecutionMessage createErrorMessage(Exception e) {
        ExecutionMessage message = new ExecutionMessage();
        message.setStepId("error");
        message.setStepName("执行错误");
        message.setStatus(ExecutionStatus.FAILED);
        message.setMessage("执行出错: " + e.getMessage());
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }
    
    /**
     * 发送执行消息到WebSocket
     */
    private void sendExecutionMessage(String sessionId, ExecutionMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/execution/" + sessionId, message);
            log.debug("Sent execution message to session {}: {}", sessionId, message.getMessage());
        } catch (Exception e) {
            log.error("Error sending execution message to session {}: ", sessionId, e);
        }
    }
    
    /**
     * 创建执行会话记录
     */
    private ScriptExecutionSession createExecutionSession(String sessionId, AggregatedScript script, EnhancedScriptContext context) {
        ScriptExecutionSession session = new ScriptExecutionSession();
        session.setId(sessionId);
        session.setAggregateScriptId(script.getId());
        session.setStatus(ExecutionStatus.PREPARING);
        session.setStartTime(LocalDateTime.now());
        
        try {
            // 序列化上下文数据（简化版本）
            session.setContextData(objectMapper.writeValueAsString(context.getAllVariableNames()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize context data: ", e);
        }
        
        return sessionRepository.save(session);
    }
    
    /**
     * 从聚合脚本获取原子脚本列表
     */
    private List<AtomicScript> getAtomicScriptsFromAggregated(AggregatedScript script) {
        // 这里需要根据实际的关联关系查询原子脚本
        // 暂时返回空列表，具体实现需要查询AggregateAtomicRelation表
        return atomicScriptService.getAtomicScriptsByAggregateId(script.getId());
    }
}