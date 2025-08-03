package com.fufu.terminal.service.refactored;

import com.fufu.terminal.controller.dto.*;
import com.fufu.terminal.entity.enums.ScriptType;
import com.fufu.terminal.script.ExecutableScript;
import com.fufu.terminal.script.registry.BuiltinScriptRegistry;
import com.fufu.terminal.script.context.ExecutionContext;
import com.fufu.terminal.script.model.ScriptResult;
import com.fufu.terminal.command.model.SshConnectionConfig;
import com.fufu.terminal.ssh.SshConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Refactored Unified Script Execution Service
 * Consolidates functionality from AtomicScriptService, ScriptEngineService, and TaskExecutionService
 * Implements type-agnostic script execution pipeline with enhanced error handling
 * 
 * This service replaces the old ScriptExecutionService with enhanced capabilities:
 * - Support for 4-type script classification system
 * - Variable passing between scripts
 * - Geographic-based intelligent decisions
 * - Real-time user interaction
 * - Unified execution pipeline
 */
@Service("refactoredScriptExecutionService")
@Slf4j
@RequiredArgsConstructor
public class RefactoredScriptExecutionService {
    
    private final BuiltinScriptRegistry builtinRegistry;
    
    // Execution tracking
    private final ConcurrentHashMap<String, ExecutionProgress> activeExecutions = new ConcurrentHashMap<>();
    private final AtomicInteger activeExecutionCount = new AtomicInteger(0);
    
    // Service dependencies (will be properly injected as we implement them)
    // private final UserScriptService userScriptService;
    // private final ExecutionContextManager contextManager;
    // private final WebSocketProgressReporter progressReporter;
    // private final GeographicMirrorSelector mirrorSelector;
    // private final UserInteractionHandler interactionHandler;
    // private final ExecutionHistoryService historyService;
    
    /**
     * Execute script with unified handling for all types
     */
    public String executeScript(String scriptId, ExecutionRequest request, String userId) {
        String sessionId = UUID.randomUUID().toString();
        
        try {
            log.info("Starting script execution: scriptId={}, userId={}, sessionId={}", 
                scriptId, userId, sessionId);
            
            // 1. Resolve script by ID and type
            ExecutableScript script = resolveScript(scriptId, userId);
            if (script == null) {
                throw new IllegalArgumentException("Script not found: " + scriptId);
            }
            
            // 2. Validate script execution request
            ValidationResponse validation = validateScriptExecution(scriptId, request, userId);
            if (!validation.isValid()) {
                throw new IllegalArgumentException("Validation failed: " + validation.getMessage());
            }
            
            // 3. Initialize execution tracking
            ExecutionProgress progress = ExecutionProgress.builder()
                .sessionId(sessionId)
                .status("INITIALIZING")
                .currentStep("Preparing execution environment")
                .currentStepNumber(1)
                .totalSteps(estimateTotalSteps(script))
                .percentage(0)
                .message("Starting script execution...")
                .startTime(System.currentTimeMillis())
                .build();
            
            activeExecutions.put(sessionId, progress);
            activeExecutionCount.incrementAndGet();
            
            // 4. Start async execution
            CompletableFuture.runAsync(() -> executeScriptAsync(sessionId, script, request, userId))
                .exceptionally(throwable -> {
                    log.error("Script execution failed for session {}", sessionId, throwable);
                    updateExecutionProgress(sessionId, "FAILED", 
                        "Execution Failed", 100, "Error: " + throwable.getMessage());
                    return null;
                });
            
            return sessionId;
            
        } catch (Exception e) {
            activeExecutions.remove(sessionId);
            activeExecutionCount.decrementAndGet();
            log.error("Failed to start script execution for scriptId: {}", scriptId, e);
            throw new RuntimeException("Failed to start script execution", e);
        }
    }
    
    /**
     * Async script execution with type-specific handling
     */
    private void executeScriptAsync(String sessionId, ExecutableScript script, ExecutionRequest request, String userId) {
        try {
            updateExecutionProgress(sessionId, "RUNNING", 
                "Establishing SSH connection", 10, "Connecting to server...");
            
            // Create execution context
            ExecutionContext context = createExecutionContext(sessionId, userId, request.getSshConfig());
            
            updateExecutionProgress(sessionId, "RUNNING", 
                "Executing script", 30, "Script execution in progress...");
            
            // Execute script with type-specific handling
            ScriptResult result = executeWithTypeSpecificHandling(script, context, request);
            
            if (result.isSuccess()) {
                updateExecutionProgress(sessionId, "COMPLETED", 
                    "Execution completed", 100, "Script executed successfully");
            } else {
                updateExecutionProgress(sessionId, "FAILED", 
                    "Execution failed", 100, result.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("Script execution failed for session {}", sessionId, e);
            updateExecutionProgress(sessionId, "FAILED", 
                "Execution error", 100, "Error: " + e.getMessage());
        } finally {
            // Cleanup after 30 seconds
            CompletableFuture.delayedExecutor(30, java.util.concurrent.TimeUnit.SECONDS)
                .execute(() -> cleanupExecution(sessionId));
        }
    }
    
    /**
     * Execute script with type-specific handling
     */
    private ScriptResult executeWithTypeSpecificHandling(ExecutableScript script, ExecutionContext context, ExecutionRequest request) {
        switch (script.getType()) {
            case STATIC_BUILTIN:
                return executeStaticScript(script, context);
                
            case CONFIGURABLE_BUILTIN:
                return executeConfigurableScript(script, context, request);
                
            case INTERACTIVE_BUILTIN:
                return executeInteractiveScript(script, context, request);
                
            case USER_SCRIPT:
                return executeUserScript(script, context, request);
                
            default:
                throw new UnsupportedOperationException("Unsupported script type: " + script.getType());
        }
    }
    
    private ScriptResult executeStaticScript(ExecutableScript script, ExecutionContext context) {
        try {
            log.info("Executing static built-in script: {}", script.getName());
            return script.executeAsync(context).get();
        } catch (Exception e) {
            log.error("Failed to execute static script: {}", script.getName(), e);
            return ScriptResult.failure("Static script execution failed: " + e.getMessage());
        }
    }
    
    private ScriptResult executeConfigurableScript(ExecutableScript script, ExecutionContext context, ExecutionRequest request) {
        try {
            log.info("Executing configurable built-in script: {}", script.getName());
            
            // Apply intelligent decisions (geographic-based mirror selection, etc.)
            // This will be implemented when GeographicMirrorSelector is available
            
            return script.executeAsync(context).get();
        } catch (Exception e) {
            log.error("Failed to execute configurable script: {}", script.getName(), e);
            return ScriptResult.failure("Configurable script execution failed: " + e.getMessage());
        }
    }
    
    private ScriptResult executeInteractiveScript(ExecutableScript script, ExecutionContext context, ExecutionRequest request) {
        try {
            log.info("Executing interactive built-in script: {}", script.getName());
            
            // Interactive scripts will handle user prompts during execution
            // This will be implemented when UserInteractionHandler is available
            
            return script.executeAsync(context).get();
        } catch (Exception e) {
            log.error("Failed to execute interactive script: {}", script.getName(), e);
            return ScriptResult.failure("Interactive script execution failed: " + e.getMessage());
        }
    }
    
    private ScriptResult executeUserScript(ExecutableScript script, ExecutionContext context, ExecutionRequest request) {
        try {
            log.info("Executing user script: {}", script.getName());
            
            // User scripts are database-stored and admin-configurable
            // This will be implemented when UserScriptService is available
            
            return script.executeAsync(context).get();
        } catch (Exception e) {
            log.error("Failed to execute user script: {}", script.getName(), e);
            return ScriptResult.failure("User script execution failed: " + e.getMessage());
        }
    }
    
    /**
     * Create execution context with SSH connection
     */
    private ExecutionContext createExecutionContext(String sessionId, String userId, SshConnectionConfig sshConfig) {
        try {
            SshConnection sshConnection = SshConnectionUtil.createConnection(sshConfig);
            return new ExecutionContext(sessionId, userId, sshConnection);
        } catch (Exception e) {
            log.error("Failed to create execution context for session {}", sessionId, e);
            throw new RuntimeException("Failed to create execution context", e);
        }
    }
    
    /**
     * Get execution progress
     */
    public ExecutionProgress getProgress(String sessionId, String userId) {
        log.debug("Getting progress for session: {}, user: {}", sessionId, userId);
        return activeExecutions.get(sessionId);
    }
    
    /**
     * Handle user interaction (placeholder implementation)
     */
    public InteractionResponse handleUserInteraction(String sessionId, InteractionRequest request, String userId) {
        log.debug("Handling interaction for session: {}, user: {}", sessionId, userId);
        
        // This will be properly implemented when UserInteractionHandler is available
        return InteractionResponse.accepted();
    }
    
    /**
     * Get scripts by type
     */
    public List<ScriptInfo> getScriptsByType(ScriptType type, String category, String userId) {
        log.debug("Getting scripts by type: {}, category: {}, user: {}", type, category, userId);
        
        List<ScriptInfo> scripts = new ArrayList<>();
        
        // Get built-in scripts
        if (type.isBuiltIn()) {
            List<ExecutableScript> builtinScripts = category != null 
                ? builtinRegistry.getScriptsByCategory(category)
                : builtinRegistry.getScriptsByType(type);
                
            scripts.addAll(builtinScripts.stream()
                .map(this::convertToScriptInfo)
                .collect(Collectors.toList()));
        }
        
        // TODO: Add user scripts when UserScriptService is available
        
        return scripts;
    }
    
    /**
     * Get all scripts
     */
    public List<ScriptInfo> getAllScripts(String category, String search, String userId) {
        log.debug("Getting all scripts: category={}, search={}, user={}", category, search, userId);
        
        List<ScriptInfo> scripts = new ArrayList<>();
        
        // Get all built-in scripts
        List<ExecutableScript> builtinScripts = search != null && !search.trim().isEmpty()
            ? builtinRegistry.searchScripts(search)
            : (category != null 
                ? builtinRegistry.getScriptsByCategory(category)
                : builtinRegistry.getAllScripts());
                
        scripts.addAll(builtinScripts.stream()
            .map(this::convertToScriptInfo)
            .collect(Collectors.toList()));
        
        // TODO: Add user scripts when UserScriptService is available
        
        return scripts;
    }
    
    /**
     * Cancel script execution
     */
    public void cancelExecution(String sessionId, String userId) {
        log.info("Cancelling execution: sessionId={}, user={}", sessionId, userId);
        
        ExecutionProgress progress = activeExecutions.get(sessionId);
        if (progress != null) {
            updateExecutionProgress(sessionId, "CANCELLED", 
                "Execution cancelled", progress.getPercentage(), "Cancelled by user");
            
            // TODO: Implement actual cancellation logic for running scripts
        } else {
            throw new IllegalArgumentException("Execution session not found: " + sessionId);
        }
    }
    
    /**
     * Get script details
     */
    public ScriptInfo getScriptDetails(String scriptId, String userId) {
        log.debug("Getting script details: scriptId={}, user={}", scriptId, userId);
        
        // Check built-in scripts first
        return builtinRegistry.getScript(scriptId)
            .map(this::convertToScriptInfo)
            .orElse(null);
        
        // TODO: Check user scripts when UserScriptService is available
    }
    
    /**
     * Validate script execution request
     */
    public ValidationResponse validateScriptExecution(String scriptId, ExecutionRequest request, String userId) {
        log.debug("Validating script execution: scriptId={}, user={}", scriptId, userId);
        
        try {
            // 1. Check if script exists
            ExecutableScript script = resolveScript(scriptId, userId);
            if (script == null) {
                return ValidationResponse.invalid("Script not found: " + scriptId, 
                    List.of("Verify the script ID is correct", "Check if you have access to this script"));
            }
            
            // 2. Validate SSH configuration
            if (request.getSshConfig() == null || !request.getSshConfig().isValid()) {
                return ValidationResponse.invalid("Invalid SSH configuration", 
                    List.of("Provide valid SSH host and credentials", "Test SSH connection separately"));
            }
            
            // 3. Validate parameters
            if (script.requiresParameters() && (request.getParameters() == null || request.getParameters().isEmpty())) {
                return ValidationResponse.invalid("Required parameters missing", 
                    List.of("Review script parameter requirements", "Provide all required parameters"));
            }
            
            return ValidationResponse.valid();
            
        } catch (Exception e) {
            log.error("Validation error for script {}", scriptId, e);
            return ValidationResponse.invalid("Validation failed: " + e.getMessage(), 
                List.of("Check system logs for details", "Contact support if the issue persists"));
        }
    }
    
    /**
     * Resolve script by ID (built-in or user script)
     */
    private ExecutableScript resolveScript(String scriptId, String userId) {
        // Check built-in scripts first
        return builtinRegistry.getScript(scriptId).orElse(null);
        
        // TODO: Check user scripts when UserScriptService is available
    }
    
    /**
     * Convert ExecutableScript to ScriptInfo DTO
     */
    private ScriptInfo convertToScriptInfo(ExecutableScript script) {
        return ScriptInfo.builder()
            .id(script.getId())
            .name(script.getName())
            .description(script.getDescription())
            .category(script.getCategory())
            .type(script.getType())
            .version(script.getVersion())
            .parameters(script.getParameters())
            .requiredVariables(script.getRequiredVariables())
            .outputVariables(script.getOutputVariables())
            .tags(script.getTags())
            .estimatedExecutionTime(script.getEstimatedExecutionTime().orElse(null))
            .requiresElevatedPrivileges(script.requiresElevatedPrivileges())
            .supportedOperatingSystems(script.getSupportedOperatingSystems())
            .build();
    }
    
    /**
     * Update execution progress
     */
    private void updateExecutionProgress(String sessionId, String status, String currentStep, 
                                       int percentage, String message) {
        ExecutionProgress progress = activeExecutions.get(sessionId);
        if (progress != null) {
            ExecutionProgress updated = ExecutionProgress.builder()
                .sessionId(sessionId)
                .status(status)
                .currentStep(currentStep)
                .currentStepNumber(progress.getCurrentStepNumber())
                .totalSteps(progress.getTotalSteps())
                .percentage(percentage)
                .message(message)
                .startTime(progress.getStartTime())
                .estimatedEndTime(calculateEstimatedEndTime(progress.getStartTime(), percentage))
                .resultData(progress.getResultData())
                .errorMessage("FAILED".equals(status) ? message : null)
                .build();
            
            activeExecutions.put(sessionId, updated);
        }
    }
    
    /**
     * Estimate total steps for script execution
     */
    private int estimateTotalSteps(ExecutableScript script) {
        // Default estimation based on script type
        switch (script.getType()) {
            case STATIC_BUILTIN:
                return 2; // Connect + Execute
            case CONFIGURABLE_BUILTIN:
                return 4; // Connect + Configure + Execute + Cleanup
            case INTERACTIVE_BUILTIN:
                return 5; // Connect + Setup + Interact + Execute + Cleanup
            case USER_SCRIPT:
                return 3; // Connect + Validate + Execute
            default:
                return 3;
        }
    }
    
    /**
     * Calculate estimated end time based on progress
     */
    private Long calculateEstimatedEndTime(Long startTime, int percentage) {
        if (startTime == null || percentage <= 0) {
            return null;
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        long totalEstimated = (elapsed * 100) / percentage;
        return startTime + totalEstimated;
    }
    
    /**
     * Cleanup execution data
     */
    private void cleanupExecution(String sessionId) {
        activeExecutions.remove(sessionId);
        activeExecutionCount.decrementAndGet();
        log.debug("Cleaned up execution session: {}", sessionId);
    }
    
    /**
     * Get active execution count for monitoring
     */
    public int getActiveExecutionCount() {
        return activeExecutionCount.get();
    }
}