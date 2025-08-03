package com.fufu.terminal.script;

import com.fufu.terminal.entity.enums.ScriptType;
import com.fufu.terminal.script.model.ScriptParameter;
import com.fufu.terminal.script.model.ScriptResult;
import com.fufu.terminal.script.model.ValidationResult;
import com.fufu.terminal.script.context.ExecutionContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced ExecutableScript interface for the 4-type script classification system
 * Supports all script types: Static Built-in, Configurable Built-in, Interactive Built-in, and User Scripts
 */
public interface ExecutableScript {
    
    /**
     * Get unique script identifier
     */
    String getId();
    
    /**
     * Get human-readable script name
     */
    String getName();
    
    /**
     * Get detailed script description
     */
    String getDescription();
    
    /**
     * Get script category for organization
     */
    String getCategory();
    
    /**
     * Get script type from the 4-type classification system
     */
    ScriptType getType();
    
    /**
     * Get script parameters with validation rules
     */
    List<ScriptParameter> getParameters();
    
    /**
     * Get variables required from execution context
     */
    Set<String> getRequiredVariables();
    
    /**
     * Get variables that this script outputs to context
     */
    Set<String> getOutputVariables();
    
    /**
     * Check if script should execute based on current context
     * Useful for configurable scripts with intelligent decision making
     */
    default boolean shouldExecute(ExecutionContext context) {
        return true;
    }
    
    /**
     * Execute the script asynchronously with full context
     * This is the main execution method for all script types
     */
    CompletableFuture<ScriptResult> executeAsync(ExecutionContext context);
    
    /**
     * Validate parameters before execution
     * Returns validation result with detailed error information
     */
    default ValidationResult validateParameters(Map<String, Object> parameters) {
        // Default implementation - can be overridden for custom validation
        for (ScriptParameter param : getParameters()) {
            if (param.isRequired() && !parameters.containsKey(param.getName())) {
                return ValidationResult.invalid("Required parameter missing: " + param.getName());
            }
        }
        return ValidationResult.valid();
    }
    
    /**
     * Check if script supports real-time user interaction
     * True for Interactive Built-in scripts
     */
    default boolean supportsInteraction() {
        return getType().supportsInteraction();
    }
    
    /**
     * Get estimated execution time in seconds
     * Used for progress reporting and timeout calculation
     */
    default Optional<Integer> getEstimatedExecutionTime() {
        return Optional.empty();
    }
    
    /**
     * Get script version for change tracking
     */
    default String getVersion() {
        return "1.0.0";
    }
    
    /**
     * Check if script requires elevated privileges
     */
    default boolean requiresElevatedPrivileges() {
        return false;
    }
    
    /**
     * Get supported operating systems
     * Empty set means all operating systems are supported
     */
    default Set<String> getSupportedOperatingSystems() {
        return Set.of(); // Empty = all supported
    }
    
    /**
     * Get script tags for filtering and categorization
     */
    default Set<String> getTags() {
        return Set.of();
    }
}