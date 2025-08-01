package com.fufu.terminal.service.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.entity.AggregatedScript;
import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.entity.ScriptExecutionSession;
import com.fufu.terminal.entity.ScriptInteraction;
import com.fufu.terminal.entity.context.EnhancedScriptContext;
import com.fufu.terminal.entity.enums.ExecutionStatus;
import com.fufu.terminal.entity.enums.InteractionMode;
import com.fufu.terminal.entity.enums.InteractionType;
import com.fufu.terminal.entity.interaction.ExecutionMessage;
import com.fufu.terminal.entity.enums.MessageType;
import com.fufu.terminal.entity.interaction.InteractionRequest;
import com.fufu.terminal.entity.interaction.InteractionResponse;
import com.fufu.terminal.repository.ScriptExecutionSessionRepository;
import com.fufu.terminal.service.AtomicScriptService;
import com.fufu.terminal.service.ScriptInteractionService;
import com.fufu.terminal.service.validation.ScriptValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * 交互式脚本执行引擎 (已重构，支持上下文持久化)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractiveScriptExecutor {

    // Thread pool configuration constants
    private static final int THREAD_POOL_CORE_SIZE = 5;
    private static final int THREAD_POOL_MAX_SIZE = 20;
    private static final long THREAD_POOL_KEEP_ALIVE_TIME = 60L;
    private static final int THREAD_POOL_QUEUE_CAPACITY = 100;
    
    // Condition evaluation constants
    private static final String CONDITION_TRUE = "true";
    
    // Response constants
    private static final String RESPONSE_YES = "yes";

    private final SimpMessagingTemplate messagingTemplate;
    private final AtomicScriptService atomicScriptService;
    private final ScriptExecutionSessionRepository sessionRepository;
    private final ScriptInteractionService interactionService;
    private final ScriptValidationService validationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ThreadPoolExecutor executorService;

    private final ConcurrentHashMap<Long, CompletableFuture<InteractionResponse>> pendingInteractions = new ConcurrentHashMap<>();

    @PostConstruct
    private void initializeThreadPool() {
        // Create a properly configured thread pool with bounded queue and rejection policy
        ThreadFactory threadFactory = new ThreadFactory() {
            private int threadNumber = 1;
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "script-executor-" + threadNumber++);
                thread.setDaemon(false); // Prevent JVM from shutting down while scripts are running
                return thread;
            }
        };

        this.executorService = new ThreadPoolExecutor(
            THREAD_POOL_CORE_SIZE,
            THREAD_POOL_MAX_SIZE,
            THREAD_POOL_KEEP_ALIVE_TIME,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(THREAD_POOL_QUEUE_CAPACITY),
            threadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy() // Handle rejected tasks by running in caller thread
        );
        
        log.info("Script execution thread pool initialized with core={}, max={}, queue={}",
            THREAD_POOL_CORE_SIZE, THREAD_POOL_MAX_SIZE, THREAD_POOL_QUEUE_CAPACITY);
    }

    @PreDestroy
    private void shutdownThreadPool() {
        if (executorService != null && !executorService.isShutdown()) {
            log.info("Shutting down script execution thread pool...");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("Thread pool did not terminate gracefully, forcing shutdown...");
                    executorService.shutdownNow();
                    if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                        log.error("Thread pool did not terminate after forced shutdown");
                    }
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for thread pool shutdown");
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

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

    private void handleScriptInteraction(ScriptExecutionSession session, AtomicScript script, EnhancedScriptContext context) throws Exception {
        log.info("Handling script interaction for script: {} (session: {})", script.getName(), session.getId());
        
        // Create interaction request based on script configuration
        InteractionRequest request = createInteractionRequest(script, context);
        
        // Save interaction to database
        ScriptInteraction interaction = interactionService.createInteraction(
            session, script, request);
        
        // Send WebSocket message to frontend
        sendInteractionRequest(session.getId(), request);
        
        // Wait for user response with timeout
        CompletableFuture<InteractionResponse> future = new CompletableFuture<>();
        pendingInteractions.put(interaction.getId(), future);
        
        try {
            InteractionResponse userResponse = future.get(5, TimeUnit.MINUTES);
            
            // Process user response and update context
            processUserResponse(request, userResponse, context);
            
            // Update interaction record with response
            interaction.setUserResponse(objectMapper.writeValueAsString(userResponse));
            interaction.setResponseTime(LocalDateTime.now());
            interactionService.updateInteraction(interaction);
            
            log.info("User interaction completed for script: {}", script.getName());
            
        } catch (TimeoutException e) {
            log.warn("User interaction timeout for script: {} (session: {})", script.getName(), session.getId());
            throw new RuntimeException("User interaction timeout", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interaction was interrupted", e);
        } finally {
            pendingInteractions.remove(interaction.getId());
        }
    }

    /**
     * 由Controller调用，处理来自前端的用户响应 (已重构)
     */
    public void handleUserResponse(InteractionResponse response) {
        log.info("Handling user response for interactionId: {}", response.getInteractionId());
        
        CompletableFuture<InteractionResponse> future = pendingInteractions.get(response.getInteractionId());
        if (future != null) {
            future.complete(response);
        } else {
            log.warn("No pending interaction found for interactionId: {}", response.getInteractionId());
        }
    }
    
    /**
     * Alternative method signature to handle WebSocket responses
     */
    public void handleUserResponse(String sessionId, InteractionResponse response) {
        handleUserResponse(response);
    }

    /**
     * 处理用户响应到上下文 (已重构，添加验证)
     */
    private void processUserResponse(InteractionRequest interaction, InteractionResponse userResponse, EnhancedScriptContext context) {
        if (userResponse.getResponse() != null) {
            String rawResponse = userResponse.getResponse().toString();
            
            // Validate and sanitize user input based on interaction type
            ScriptValidationService.ValidationResult validationResult = 
                validationService.validateUserInput(rawResponse, interaction.getType());
            
            if (!validationResult.isValid()) {
                log.warn("Invalid user response for interaction {}: {}", 
                    interaction.getInteractionId(), validationResult.getErrors());
                // For invalid responses, use sanitized input as fallback
                rawResponse = validationService.sanitizeUserInput(rawResponse, interaction.getType());
            }
            
            // Sanitize the response before storing in context
            String sanitizedResponse = validationService.sanitizeUserInput(rawResponse, interaction.getType());
            
            log.debug("Processing user response - Original: '{}', Sanitized: '{}', Type: {}", 
                rawResponse, sanitizedResponse, interaction.getType());
            
            // Store both raw and sanitized responses in context for different use cases
            context.setVariable(interaction.getInteractionId(), sanitizedResponse);
            context.setVariable(interaction.getInteractionId() + "_raw", rawResponse);
            
            // For confirmation types, also store boolean representation
            if (isConfirmationType(interaction.getType())) {
                boolean confirmed = RESPONSE_YES.equalsIgnoreCase(sanitizedResponse);
                context.setVariable(interaction.getInteractionId() + "_confirmed", confirmed);
            }
        }
    }
    
    /**
     * Checks if the interaction type is a confirmation type
     */
    private boolean isConfirmationType(InteractionType type) {
        return type == InteractionType.CONFIRMATION || 
               type == InteractionType.CONFIRM_YES_NO || 
               type == InteractionType.CONFIRM_RECOMMENDATION;
    }

    private boolean shouldExecuteStep(AtomicScript script, EnhancedScriptContext context) {
        if (script.getConditionExpression() == null || script.getConditionExpression().trim().isEmpty()) {
            return true; // 没有条件则默认执行
        }
        String resolvedCondition = context.resolveVariables(script.getConditionExpression());
        log.debug("Evaluating condition for step '{}': Original: '{}' -> Resolved: '{}'", script.getName(), script.getConditionExpression(), resolvedCondition);
        // 简化的条件评估：我们假设条件解析后，结果为 "true" (不区分大小写) 才执行
        // 复杂的逻辑可以使用脚本引擎如 MVEL, SpEL, or Nashorn.
        return CONDITION_TRUE.equalsIgnoreCase(resolvedCondition);
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

    private InteractionRequest createInteractionRequest(AtomicScript script, EnhancedScriptContext context) {
        InteractionRequest request = new InteractionRequest();
        
        // Parse interaction configuration from script
        if (script.getInteractionConfig() != null && !script.getInteractionConfig().trim().isEmpty()) {
            try {
                Map<String, Object> config = objectMapper.readValue(script.getInteractionConfig(), new TypeReference<Map<String, Object>>() {});
                request.setType(InteractionType.valueOf(((String) config.get("type")).toUpperCase()));
                request.setPrompt((String) config.get("prompt"));
                
                // Resolve variables in prompt message
                if (context != null && request.getPrompt() != null) {
                    request.setPrompt(context.resolveVariables(request.getPrompt()));
                }
            } catch (Exception e) {
                log.warn("Failed to parse interaction config for script: {}, using defaults", script.getName(), e);
                request.setType(InteractionType.CONFIRMATION);
                request.setPrompt("Continue with " + script.getName() + "?");
            }
        } else {
            // Default interaction configuration
            request.setType(InteractionType.CONFIRMATION);
            request.setPrompt("Continue with " + script.getName() + "?");
        }
        
        request.setInteractionId(UUID.randomUUID().toString());
        // InteractionRequest doesn't have setTimestamp method, will set timestamp in ExecutionMessage
        
        return request;
    }
    
    private void sendInteractionRequest(String sessionId, InteractionRequest request) {
        ExecutionMessage message = new ExecutionMessage();
        message.setMessageType(MessageType.INTERACTION_REQUEST);
        message.setMessage("Waiting for user input: " + request.getPrompt());
        message.setOutput(request.getPrompt());
        message.setTimestamp(System.currentTimeMillis());
        message.setInteractionRequest(request);
        sendExecutionMessage(sessionId, message);
    }

    private List<AtomicScript> getAtomicScriptsFromAggregated(AggregatedScript script) {
        return script.getAtomicScriptRelations().stream()
                .map(relation -> relation.getAtomicScript())
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
        ExecutionMessage message = new ExecutionMessage();
        message.setMessageType(MessageType.WARNING);
        message.setMessage("Skipping step: " + script.getName());
        message.setOutput(script.getName());
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }

    private ExecutionMessage createCancelledMessage() {
        ExecutionMessage message = new ExecutionMessage();
        message.setMessageType(MessageType.WARNING);
        message.setMessage("Execution was cancelled.");
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }

    private ExecutionMessage createErrorMessage(Exception e) {
        ExecutionMessage message = new ExecutionMessage();
        message.setMessageType(MessageType.ERROR);
        message.setMessage("An error occurred: " + e.getMessage());
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }

    private ExecutionMessage createStartMessage(AggregatedScript script) {
        ExecutionMessage message = new ExecutionMessage();
        message.setMessageType(MessageType.INFO);
        message.setMessage("Starting script: " + script.getName());
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }

    private ExecutionMessage createCompletionMessage(AggregatedScript script) {
        ExecutionMessage message = new ExecutionMessage();
        message.setMessageType(MessageType.SUCCESS);
        message.setMessage("Finished script: " + script.getName());
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }

    private ExecutionMessage createStepStartMessage(AtomicScript script) {
        ExecutionMessage message = new ExecutionMessage();
        message.setMessageType(MessageType.STEP_START);
        message.setMessage("Starting step: " + script.getName());
        message.setOutput(script.getName());
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }

    private ExecutionMessage createStepCompletionMessage(AtomicScript script) {
        ExecutionMessage message = new ExecutionMessage();
        message.setMessageType(MessageType.STEP_COMPLETE);
        message.setMessage("Finished step: " + script.getName());
        message.setOutput(script.getName());
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }

    private void sendExecutionMessage(String sessionId, ExecutionMessage message) {
        messagingTemplate.convertAndSend("/topic/execution/" + sessionId, message);
    }

    // ... [Copy all unchanged helper methods from the previous version here] ...
}