package com.fufu.terminal.script.context;

import com.fufu.terminal.entity.enums.VariableScope;
import com.fufu.terminal.ssh.SshConnection;
import com.fufu.terminal.script.model.TypedVariable;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced execution context for script execution with variable management and scope support
 * Provides SSH connection, variable storage, and user interaction capabilities
 */
@Slf4j
public class ExecutionContext {
    
    private final String sessionId;
    private final String userId;
    private final SshConnection sshConnection;
    private final Map<VariableScope, Map<String, TypedVariable>> variablesByScope = new ConcurrentHashMap<>();
    private final long createdAt;
    
    // Interaction and progress reporting (to be implemented later)
    // private UserInteractionHandler interactionHandler;
    // private WebSocketProgressReporter progressReporter;
    
    public ExecutionContext(String sessionId, String userId, SshConnection sshConnection) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.sshConnection = sshConnection;
        this.createdAt = System.currentTimeMillis();
        
        // Initialize variable scopes
        Arrays.stream(VariableScope.values())
            .forEach(scope -> variablesByScope.put(scope, new ConcurrentHashMap<>()));
    }
    
    // Getters
    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public SshConnection getSshConnection() { return sshConnection; }
    public long getCreatedAt() { return createdAt; }
    
    // Variable management with type safety and scope precedence
    public void setVariable(String name, Object value, VariableScope scope) {
        TypedVariable typedVar = TypedVariable.of(name, value);
        variablesByScope.get(scope).put(name, typedVar);
        log.debug("Set variable {} = {} in scope {}", name, value, scope);
    }
    
    public void setVariables(Map<String, Object> variables, VariableScope scope) {
        variables.forEach((name, value) -> setVariable(name, value, scope));
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String name, Class<T> type) {
        // Check scopes in precedence order: SCRIPT > SESSION > GLOBAL
        for (VariableScope scope : VariableScope.values()) {
            TypedVariable variable = variablesByScope.get(scope).get(name);
            if (variable != null) {
                return variable.getValue(type);
            }
        }
        return null;
    }
    
    public <T> T getVariableOrDefault(String name, Class<T> type, T defaultValue) {
        T value = getVariable(name, type);
        return value != null ? value : defaultValue;
    }
    
    public boolean hasVariable(String name) {
        return variablesByScope.values().stream()
            .anyMatch(scopeVars -> scopeVars.containsKey(name));
    }
    
    public Map<String, Object> getAllVariables() {
        Map<String, Object> allVars = new HashMap<>();
        
        // Add in reverse precedence order so higher precedence overwrites
        for (VariableScope scope : VariableScope.getReverseValues()) {
            variablesByScope.get(scope).forEach((name, typedVar) -> 
                allVars.put(name, typedVar.getRawValue()));
        }
        
        return allVars;
    }
    
    // SSH command execution (simplified for now)
    public CommandResult executeCommand(String command) {
        return executeCommand(command, null);
    }
    
    public CommandResult executeCommand(String command, Duration timeout) {
        try {
            if (progressReporter != null) {
                progressReporter.reportProgress(sessionId, "Executing: " + command, null);
            }
            
            // This will be properly implemented when we integrate with SSH service
            log.debug("Executing command: {}", command);
            
            // Placeholder implementation - will be replaced with actual SSH execution
            return new CommandResult(0, "Command executed successfully", "");
            
        } catch (Exception e) {
            log.error("Failed to execute command: {}", command, e);
            throw new RuntimeException("Command execution failed: " + command, e);
        }
    }
    
    // User interaction (will be properly implemented later)
    public String promptUser(String message, InputType type) {
        return promptUser(message, type, null, Duration.ofMinutes(5));
    }
    
    public String promptUser(String message, InputType type, String defaultValue, Duration timeout) {
        if (interactionHandler != null) {
            // Will be implemented with proper WebSocket interaction
            return interactionHandler.promptUser(sessionId, message, type, defaultValue, timeout);
        }
        
        // Fallback to default value for now
        log.warn("No interaction handler available, using default value: {}", defaultValue);
        return defaultValue;
    }
    
    public boolean confirmAction(String message) {
        String response = promptUser(message + " (y/n)", InputType.BOOLEAN, "n", Duration.ofMinutes(2));
        return "y".equalsIgnoreCase(response) || "yes".equalsIgnoreCase(response);
    }
    
    // Cleanup resources
    public void cleanup() {
        try {
            if (sshConnection != null && sshConnection.isConnected()) {
                sshConnection.close();
            }
        } catch (Exception e) {
            log.warn("Error cleaning up SSH connection for session {}", sessionId, e);
        }
        
        variablesByScope.clear();
    }
    
    // Placeholder classes - will be properly implemented later
    public static class CommandResult {
        private final int exitCode;
        private final String output;
        private final String errorOutput;
        
        public CommandResult(int exitCode, String output, String errorOutput) {
            this.exitCode = exitCode;
            this.output = output;
            this.errorOutput = errorOutput;
        }
        
        public int getExitCode() { return exitCode; }
        public String getOutput() { return output; }
        public String getErrorOutput() { return errorOutput; }
    }
    
    public enum InputType {
        TEXT, NUMBER, BOOLEAN, CHOICE
    }
    
    // These interfaces will be properly implemented later
    private interface UserInteractionHandler {
        String promptUser(String sessionId, String message, InputType type, String defaultValue, Duration timeout);
    }
    
    private interface WebSocketProgressReporter {
        void reportProgress(String sessionId, String message, Integer percentage);
    }
}