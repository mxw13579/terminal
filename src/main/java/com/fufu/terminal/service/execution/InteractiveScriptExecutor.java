package com.fufu.terminal.service.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.entity.AggregatedScript;
import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.entity.ScriptExecutionSession;
import com.fufu.terminal.entity.ScriptInteraction;
import com.fufu.terminal.entity.context.EnhancedScriptContext;
import com.fufu.terminal.entity.enums.ExecutionStatus;
import com.fufu.terminal.entity.enums.InteractionMode;
import com.fufu.terminal.entity.interaction.ExecutionMessage;
import com.fufu.terminal.entity.interaction.InteractionRequest;
import com.fufu.terminal.entity.interaction.InteractionResponse;
import com.fufu.terminal.repository.ScriptExecutionSessionRepository;
import com.fufu.terminal.service.AtomicScriptService;
import com.fufu.terminal.service.ScriptInteractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

/**
 * 交互式脚本执行引擎 (已重构，支持持久化交互)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractiveScriptExecutor {

    private final SimpMessagingTemplate messagingTemplate;
    private final AtomicScriptService atomicScriptService;
    private final ScriptExecutionSessionRepository sessionRepository;
    private final ScriptInteractionService interactionService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    // 存储等待用户响应的会话，Key: 交互ID, Value: CompletableFuture
    private final ConcurrentHashMap<Long, CompletableFuture<InteractionResponse>> pendingInteractions = new ConcurrentHashMap<>();

    /**
     * 执行聚合脚本
     */
    public CompletableFuture<Void> executeAggregateScript(String sessionId, AggregatedScript script, EnhancedScriptContext context) {
        return CompletableFuture.runAsync(() -> {
            ScriptExecutionSession session = createExecutionSession(sessionId, script, context);
            try {
                log.info("Starting execution of aggregate script: {} (session: {})", script.getName(), sessionId);
                context.initializeSystemVariables();
                sendExecutionMessage(sessionId, createStartMessage(script));

                List<AtomicScript> atomicScripts = getAtomicScriptsFromAggregated(script);
                for (int i = 0; i < atomicScripts.size(); i++) {
                    AtomicScript atomicScript = atomicScripts.get(i);
                    executeAtomicScriptInteractively(session, atomicScript, context);
                }

                session.setStatus(ExecutionStatus.COMPLETED);
                sendExecutionMessage(sessionId, createCompletionMessage(script));
            } catch (Exception e) {
                session.setStatus(ExecutionStatus.FAILED);
                if (e instanceof InterruptedException) {
                    log.warn("Execution was interrupted for session: {}", sessionId);
                    sendExecutionMessage(sessionId, createCancelledMessage());
                    Thread.currentThread().interrupt();
                } else {
                    log.error("Error executing aggregate script for session {}: ", sessionId, e);
                    sendExecutionMessage(sessionId, createErrorMessage(e));
                }
            } finally {
                session.setEndTime(LocalDateTime.now());
                sessionRepository.save(session);
            }
        }, executorService);
    }

    /**
     * 交互式执行原子脚本
     */
    private void executeAtomicScriptInteractively(ScriptExecutionSession session, AtomicScript script, EnhancedScriptContext context) throws Exception {
        log.debug("Executing atomic script: {} (session: {})", script.getName(), session.getId());
        sendExecutionMessage(session.getId(), createStepStartMessage(script));

        if (script.getInteractionMode() != InteractionMode.SILENT) {
            handleScriptInteraction(session, script, context);
        }

        if (script.getInteractionMode() == InteractionMode.REALTIME_OUTPUT) {
            executeWithRealtimeOutput(session.getId(), script, context);
        } else {
            executeStandardScript(session.getId(), script, context);
        }

        sendExecutionMessage(session.getId(), createStepCompletionMessage(script));
    }

    /**
     * 处理脚本交互 (已重构，使用持久化)
     */
    private void handleScriptInteraction(ScriptExecutionSession session, AtomicScript script, EnhancedScriptContext context) {
        try {
            if (script.getInteractionConfig() != null && !script.getInteractionConfig().trim().isEmpty()) {
                InteractionRequest[] interactions = objectMapper.readValue(script.getInteractionConfig(), InteractionRequest[].class);
                for (InteractionRequest request : interactions) {
                    if (shouldTriggerInteraction(request, context)) {
                        // 1. 创建并持久化交互记录
                        ScriptInteraction interaction = interactionService.createInteraction(session, script, request);
                        session.setStatus(ExecutionStatus.WAITING_INPUT);
                        sessionRepository.save(session);

                        // 2. 发送交互请求到前端
                        sendInteractionRequest(session.getId(), interaction);

                        // 3. 异步等待用户响应
                        InteractionResponse userResponse = waitForUserResponse(interaction.getId());

                        // 4. 处理用户响应
                        interactionService.completeInteraction(interaction.getId(), userResponse);
                        processUserResponse(request, userResponse, context);
                        session.setStatus(ExecutionStatus.EXECUTING);
                        sessionRepository.save(session);
                    }
                }
            }
        } catch (JsonProcessingException e) {
            log.error("Error parsing interaction config for script {}: ", script.getName(), e);
        }
    }

    /**
     * 等待用户响应 (已重构)
     */
    private InteractionResponse waitForUserResponse(Long interactionId) {
        CompletableFuture<InteractionResponse> future = new CompletableFuture<>();
        pendingInteractions.put(interactionId, future);
        try {
            // 设置超时，例如5分钟
            return future.get(5, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error("Error or timeout waiting for user response for interaction {}: ", interactionId, e);
            throw new RuntimeException("Failed to get user response for interaction: " + interactionId, e);
        } finally {
            pendingInteractions.remove(interactionId);
        }
    }

    /**
     * 由Controller调用，处理来自前端的用户响应 (已重构)
     */
    public void handleUserResponse(InteractionResponse response) {
        Long interactionId = response.getInteractionId();
        CompletableFuture<InteractionResponse> future = pendingInteractions.get(interactionId);
        if (future != null) {
            future.complete(response);
            log.debug("Processed user response for interaction: {}", interactionId);
        } else {
            log.warn("No pending interaction found for interactionId: {}", interactionId);
        }
    }
    
    /**
     * 发送交互请求 (已重构)
     */
    private void sendInteractionRequest(String sessionId, ScriptInteraction interaction) {
        ExecutionMessage message = new ExecutionMessage();
        message.setStatus(ExecutionStatus.WAITING_INPUT);
        message.setMessage(interaction.getPromptMessage());
        message.setTimestamp(System.currentTimeMillis());
        
        InteractionRequest requestPayload = new InteractionRequest();
        requestPayload.setInteractionId(String.valueOf(interaction.getId()));
        requestPayload.setType(interaction.getInteractionType().name());
        requestPayload.setPrompt(interaction.getPromptMessage());
        message.setInteraction(requestPayload);

        sendExecutionMessage(sessionId, message);
    }
    
    // ... 其他方法保持不变 (shouldTrigger, processUserResponse, script execution, message creation) ...

    private boolean shouldTriggerInteraction(InteractionRequest interaction, EnhancedScriptContext context) {
        if (interaction.getCondition() == null || interaction.getCondition().trim().isEmpty()) {
            return true;
        }
        String condition = context.resolveVariables(interaction.getCondition());
        return !condition.contains("false") && !condition.contains("null");
    }

    private void processUserResponse(InteractionRequest interaction, InteractionResponse userResponse, EnhancedScriptContext context) {
        if (userResponse.getResponse() != null) {
            context.setUserInteractionVariable(interaction.getInteractionId(), userResponse.getResponse());
        }
    }

    private void executeStandardScript(String sessionId, AtomicScript script, EnhancedScriptContext context) throws InterruptedException {
        log.debug("Executing standard script: {}", script.getName());
        Thread.sleep(1000); // 模拟执行
    }

    private void executeWithRealtimeOutput(String sessionId, AtomicScript script, EnhancedScriptContext context) throws InterruptedException {
        log.debug("Executing script with realtime output: {}", script.getName());
        for (int i = 1; i <= 5; i++) {
            sendExecutionMessage(sessionId, new ExecutionMessage(script.getName(), ExecutionStatus.EXECUTING, "执行中... " + (i * 20) + "%"));
            Thread.sleep(500);
        }
    }

    private void sendExecutionMessage(String sessionId, ExecutionMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/execution/" + sessionId, message);
        } catch (Exception e) {
            log.error("Error sending execution message to session {}: ", sessionId, e);
        }
    }

    private ScriptExecutionSession createExecutionSession(String sessionId, AggregatedScript script, EnhancedScriptContext context) {
        ScriptExecutionSession session = new ScriptExecutionSession();
        session.setId(sessionId);
        session.setAggregateScriptId(script.getId());
        session.setStatus(ExecutionStatus.PREPARING);
        session.setStartTime(LocalDateTime.now());
        try {
            session.setContextData(objectMapper.writeValueAsString(context.getAllVariableNames()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize context data: ", e);
        }
        return sessionRepository.save(session);
    }

    private List<AtomicScript> getAtomicScriptsFromAggregated(AggregatedScript script) {
        return atomicScriptService.getAtomicScriptsByAggregateId(script.getId());
    }
    
    // Helper methods for creating messages
    private ExecutionMessage createStartMessage(AggregatedScript script) {
        return new ExecutionMessage("start", "开始执行", ExecutionStatus.PREPARING, "开始执行聚合脚本: " + script.getName());
    }

    private ExecutionMessage createStepStartMessage(AtomicScript script) {
        return new ExecutionMessage(script.getName(), ExecutionStatus.EXECUTING, "正在执行: " + script.getName());
    }

    private ExecutionMessage createStepCompletionMessage(AtomicScript script) {
        return new ExecutionMessage(script.getName(), ExecutionStatus.COMPLETED, "完成执行: " + script.getName());
    }

    private ExecutionMessage createCompletionMessage(AggregatedScript script) {
        return new ExecutionMessage("complete", "执行完成", ExecutionStatus.COMPLETED, "聚合脚本执行完成: " + script.getName());
    }

    private ExecutionMessage createCancelledMessage() {
        return new ExecutionMessage("cancelled", "执行取消", ExecutionStatus.CANCELLED, "执行已被取消");
    }

    private ExecutionMessage createErrorMessage(Exception e) {
        return new ExecutionMessage("error", "执行错误", ExecutionStatus.FAILED, "执行出错: " + e.getMessage());
    }
}