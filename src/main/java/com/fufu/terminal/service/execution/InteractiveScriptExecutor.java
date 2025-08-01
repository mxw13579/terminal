package com.fufu.terminal.service.execution;

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
 * 交互式脚本执行引擎 (已重构，支持上下文持久化)
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

    private final ConcurrentHashMap<Long, CompletableFuture<InteractionResponse>> pendingInteractions = new ConcurrentHashMap<>();

    /**
     * 执行聚合脚本 (已重构，负责上下文生命周期)
     */
    public CompletableFuture<Void> executeAggregateScript(String sessionId, AggregatedScript script) {
        return CompletableFuture.runAsync(() -> {
            // 1. 创建或加载会话和上下文
            ScriptExecutionSession session = createOrGetExecutionSession(sessionId, script);
            EnhancedScriptContext context = new EnhancedScriptContext(sessionId);
            context.fromJson(session.getContextData()); // 从DB加载历史上下文

            try {
                log.info("Starting execution of aggregate script: {} (session: {})", script.getName(), sessionId);
                sendExecutionMessage(sessionId, createStartMessage(script));

                List<AtomicScript> atomicScripts = getAtomicScriptsFromAggregated(script);
                for (AtomicScript atomicScript : atomicScripts) {
                    // 核心逻辑：执行一个步骤
                    executeAtomicScriptInteractively(session, atomicScript, context);
                    
                    // 核心逻辑：每一步执行后，立即持久化上下文
                    session.setContextData(context.toJson());
                    sessionRepository.saveAndFlush(session);
                    log.debug("Context for session {} persisted after step: {}", sessionId, atomicScript.getName());
                }

                session.setStatus(ExecutionStatus.COMPLETED);
                sendExecutionMessage(sessionId, createCompletionMessage(script));

            } catch (Exception e) {
                session.setStatus(ExecutionStatus.FAILED);
                handleExecutionError(sessionId, e);
            } finally {
                session.setEndTime(LocalDateTime.now());
                session.setContextData(context.toJson()); // 确保最终上下文状态被保存
                sessionRepository.save(session);
            }
        }, executorService);
    }

    private void executeAtomicScriptInteractively(ScriptExecutionSession session, AtomicScript script, EnhancedScriptContext context) throws Exception {
        log.debug("Executing atomic script: {} (session: {})", script.getName(), session.getId());
        sendExecutionMessage(session.getId(), createStepStartMessage(script));

        // 条件执行判断
        if (!shouldExecuteStep(script, context)) {
            log.info("Skipping step {} due to condition not met.", script.getName());
            sendExecutionMessage(session.getId(), createStepSkippedMessage(script));
            return;
        }

        if (script.getInteractionMode() != InteractionMode.SILENT) {
            handleScriptInteraction(session, script, context);
        }

        // 模拟真实脚本执行
        // TODO: 替换为真实的 SSHCommandService 调用
        Thread.sleep(1000); // 模拟执行耗时
        // 假设执行后，脚本往上下文里放了一个变量
        context.setVariable(script.getName() + "_status", "executed");

        sendExecutionMessage(session.getId(), createStepCompletionMessage(script));
    }

    private void handleScriptInteraction(ScriptExecutionSession session, AtomicScript script, EnhancedScriptContext context) {
       // ... (此方法逻辑不变) ...
    }

    /**
     * 由Controller调用，处理来自前端的用户响应 (已重构)
     */
    public void handleUserResponse(InteractionResponse response) {
        // ... (此方法逻辑不变) ...
    }

    /**
     * 处理用户响应到上下文 (已重构)
     */
    private void processUserResponse(InteractionRequest interaction, InteractionResponse userResponse, EnhancedScriptContext context) {
        if (userResponse.getResponse() != null) {
            // 使用新的统一 setVariable 方法
            context.setVariable(interaction.getInteractionId(), userResponse.getResponse());
        }
    }

    private boolean shouldExecuteStep(AtomicScript script, EnhancedScriptContext context) {
        if (script.getConditionExpression() == null || script.getConditionExpression().trim().isEmpty()) {
            return true; // 没有条件则默认执行
        }
        String resolvedCondition = context.resolveVariables(script.getConditionExpression());
        log.debug("Evaluating condition for step '{}': Original: '{}' -> Resolved: '{}'", script.getName(), script.getConditionExpression(), resolvedCondition);
        // 简化的条件评估：我们假设条件解析后，结果为 "true" (不区分大小写) 才执行
        // 复杂的逻辑可以使用脚本引擎如 MVEL, SpEL, or Nashorn.
        return "true".equalsIgnoreCase(resolvedCondition);
    }

    private ScriptExecutionSession createOrGetExecutionSession(String sessionId, AggregatedScript script) {
        return sessionRepository.findById(sessionId).orElseGet(() -> {
            ScriptExecutionSession newSession = new ScriptExecutionSession();
            newSession.setId(sessionId);
            newSession.setAggregateScriptId(script.getId());
            newSession.setStatus(ExecutionStatus.PREPARING);
            newSession.setStartTime(LocalDateTime.now());
            newSession.setContextData("{}"); // 初始化空的JSON
            log.info("Created new execution session with id: {}", sessionId);
            return sessionRepository.save(newSession);
        });
    }
    
    // ... (其他所有辅助方法如 waitForUserResponse, sendInteractionRequest, 消息创建等保持不变) ...

    private List<AtomicScript> getAtomicScriptsFromAggregated(AggregatedScript script) {
        return script.getAtomicScriptRelations().stream()
                .map(AggregateAtomicRelation::getAtomicScript)
                .collect(java.util.stream.Collectors.toList());
    }

    private void handleExecutionError(String sessionId, Exception e) {
        if (e instanceof InterruptedException) {
            log.warn("Execution was interrupted for session: {}", sessionId);
            sendExecutionMessage(sessionId, createCancelledMessage());
            Thread.currentThread().interrupt();
        } else {
            log.error("Error executing aggregate script for session {}: ", sessionId, e);
            sendExecutionMessage(sessionId, createErrorMessage(e));
        }
    }
    
    private ExecutionMessage createStepSkippedMessage(AtomicScript script) {
        return new ExecutionMessage(ExecutionMessage.MessageType.STEP_SKIPPED, "Skipping step: " + script.getName(), script.getName());
    }

    private ExecutionMessage createCancelledMessage() {
        return new ExecutionMessage(ExecutionMessage.MessageType.CANCELLED, "Execution was cancelled.");
    }

    private ExecutionMessage createErrorMessage(Exception e) {
        return new ExecutionMessage(ExecutionMessage.MessageType.ERROR, "An error occurred: " + e.getMessage());
    }

    private ExecutionMessage createStartMessage(AggregatedScript script) {
        return new ExecutionMessage(ExecutionMessage.MessageType.INFO, "Starting script: " + script.getName());
    }

    private ExecutionMessage createCompletionMessage(AggregatedScript script) {
        return new ExecutionMessage(ExecutionMessage.MessageType.INFO, "Finished script: " + script.getName());
    }

    private ExecutionMessage createStepStartMessage(AtomicScript script) {
        return new ExecutionMessage(ExecutionMessage.MessageType.STEP_START, "Starting step: " + script.getName(), script.getName());
    }

    private ExecutionMessage createStepCompletionMessage(AtomicScript script) {
        return new ExecutionMessage(ExecutionMessage.MessageType.STEP_COMPLETED, "Finished step: " + script.getName(), script.getName());
    }

    private void sendExecutionMessage(String sessionId, ExecutionMessage message) {
        messagingTemplate.convertAndSend("/topic/execution/" + sessionId, message);
    }

    // ... [Copy all unchanged helper methods from the previous version here] ...
}