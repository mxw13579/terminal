package com.fufu.terminal.command;

import com.fufu.terminal.model.ScriptType;
import com.fufu.terminal.model.ScriptParameter;
import com.fufu.terminal.model.ScriptResult;
import com.fufu.terminal.model.ExecutionContext;
import com.fufu.terminal.model.ValidationResult;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced ExecutableScript Interface
 * 
 * Defines the contract for all executable scripts in the SSH Terminal Management System.
 * Supports async execution, variable management, and type-specific behaviors.
 */
public interface ExecutableScript {
    
    // Basic script identification
    String getId();
    String getName();
    String getDescription();
    String getCategory();
    ScriptType getType();
    
    // Parameter and variable management
    List<ScriptParameter> getParameters();
    Set<String> getRequiredVariables();
    Set<String> getOutputVariables();
    
    /**
     * Check if script should execute based on context
     * @param context Current execution context
     * @return true if script should execute, false otherwise
     */
    boolean shouldExecute(ExecutionContext context);
    
    /**
     * Execute the script asynchronously with full context
     * @param context Execution context with SSH connection, variables, etc.
     * @return CompletableFuture containing the script result
     */
    CompletableFuture<ScriptResult> executeAsync(ExecutionContext context);
    
    /**
     * Validate parameters before execution
     * @param parameters Parameters to validate
     * @return ValidationResult with success/failure and error details
     */
    ValidationResult validateParameters(Map<String, Object> parameters);
    
    /**
     * Check if script supports real-time interaction
     * @return true if script can interact with users during execution
     */
    default boolean supportsInteraction() {
        return getType().supportsInteraction();
    }
    
    /**
     * Get estimated execution time in seconds
     * @return Optional containing estimated time, empty if unknown
     */
    Optional<Integer> getEstimatedExecutionTime();
    
    /**
     * Get script priority for execution ordering
     * @return Priority level (1-10, where 1 is highest priority)
     */
    default int getPriority() {
        return 5; // Default medium priority
    }
    
    /**
     * Check if script requires specific permissions
     * @return Set of required permissions
     */
    default Set<String> getRequiredPermissions() {
        return Set.of();
    }
    
    /**
     * Get script version for tracking changes
     * @return Version string
     */
    default String getVersion() {
        return "1.0.0";
    }
    
    /**
     * Check if script can run in parallel with other scripts
     * @return true if parallel execution is safe
     */
    default boolean allowsParallelExecution() {
        return true;
    }
    
    /**
     * Get script timeout in milliseconds
     * @return timeout value, or empty for no timeout
     */
    default Optional<Long> getTimeoutMs() {
        return Optional.empty();
    }
    
    /**
     * Get script tags for categorization and filtering
     * @return Set of tags
     */
    default Set<String> getTags() {
        return Set.of();
    }
}