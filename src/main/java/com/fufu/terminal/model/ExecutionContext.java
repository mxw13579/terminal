package com.fufu.terminal.model;

import com.fufu.terminal.ssh.SshConnection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Execution Context
 * 
 * Contains all the context information needed for script execution including
 * SSH connections, variables, user information, and session data.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionContext {
    
    // Session and user information
    private String sessionId;
    private String userId;
    private String username;
    
    // SSH connection information
    private SshConnection sshConnection;
    private SshConfig sshConfig;
    
    // Variable management with different scopes
    @Builder.Default
    private Map<String, Object> sessionVariables = new ConcurrentHashMap<>();
    
    @Builder.Default
    private Map<String, Object> scriptVariables = new ConcurrentHashMap<>();
    
    @Builder.Default
    private Map<String, Object> globalVariables = new ConcurrentHashMap<>();
    
    // Script execution parameters
    @Builder.Default
    private Map<String, Object> parameters = new ConcurrentHashMap<>();
    
    // Geographic and environment context
    private String serverLocation;
    private String serverCountry;
    private String osType;
    private String architecture;
    
    // Execution metadata
    private long startTime;
    private boolean dryRun;
    private int timeoutSeconds;
    
    /**
     * Variable Scope Enumeration
     */
    public enum VariableScope {
        GLOBAL,   // Available across all sessions and scripts
        SESSION,  // Available for the current user session
        SCRIPT    // Available only for the current script execution
    }
    
    /**
     * Get a variable with scope precedence (SCRIPT > SESSION > GLOBAL)
     */
    public Object getVariable(String name) {
        if (scriptVariables.containsKey(name)) {
            return scriptVariables.get(name);
        }
        if (sessionVariables.containsKey(name)) {
            return sessionVariables.get(name);
        }
        return globalVariables.get(name);
    }
    
    /**
     * Set a variable in the specified scope
     */
    public void setVariable(String name, Object value, VariableScope scope) {
        switch (scope) {
            case SCRIPT -> scriptVariables.put(name, value);
            case SESSION -> sessionVariables.put(name, value);
            case GLOBAL -> globalVariables.put(name, value);
        }
    }
    
    /**
     * Set a variable in script scope (default)
     */
    public void setVariable(String name, Object value) {
        setVariable(name, value, VariableScope.SCRIPT);
    }
    
    /**
     * Check if a variable exists in any scope
     */
    public boolean hasVariable(String name) {
        return scriptVariables.containsKey(name) ||
               sessionVariables.containsKey(name) ||
               globalVariables.containsKey(name);
    }
    
    /**
     * Get all variables merged with scope precedence
     */
    public Map<String, Object> getAllVariables() {
        Map<String, Object> merged = new ConcurrentHashMap<>();
        merged.putAll(globalVariables);
        merged.putAll(sessionVariables);
        merged.putAll(scriptVariables);
        return merged;
    }
    
    /**
     * Clear variables in a specific scope
     */
    public void clearVariables(VariableScope scope) {
        switch (scope) {
            case SCRIPT -> scriptVariables.clear();
            case SESSION -> sessionVariables.clear();
            case GLOBAL -> globalVariables.clear();
        }
    }
    
    /**
     * Get a parameter value
     */
    public Object getParameter(String name) {
        return parameters.get(name);
    }
    
    /**
     * Get a parameter value with default
     */
    public <T> T getParameter(String name, T defaultValue) {
        @SuppressWarnings("unchecked")
        T value = (T) parameters.get(name);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Set a parameter value
     */
    public void setParameter(String name, Object value) {
        parameters.put(name, value);
    }
    
    /**
     * Check if a parameter exists
     */
    public boolean hasParameter(String name) {
        return parameters.containsKey(name);
    }
    
    /**
     * Check if SSH connection is available and connected
     */
    public boolean hasSshConnection() {
        return sshConnection != null && sshConnection.isConnected();
    }
    
    /**
     * Get execution timeout in milliseconds
     */
    public long getTimeoutMs() {
        return timeoutSeconds * 1000L;
    }
    
    /**
     * Check if execution has timed out
     */
    public boolean hasTimedOut() {
        if (timeoutSeconds <= 0) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed > getTimeoutMs();
    }
    
    /**
     * Get remaining execution time in milliseconds
     */
    public long getRemainingTimeMs() {
        if (timeoutSeconds <= 0) {
            return Long.MAX_VALUE;
        }
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.max(0, getTimeoutMs() - elapsed);
    }
    
    /**
     * Create a copy of this context for parallel execution
     */
    public ExecutionContext createChildContext(String childSessionId) {
        return ExecutionContext.builder()
            .sessionId(childSessionId)
            .userId(userId)
            .username(username)
            .sshConnection(sshConnection)
            .sshConfig(sshConfig)
            .sessionVariables(new ConcurrentHashMap<>(sessionVariables))
            .globalVariables(globalVariables) // Share global variables
            .scriptVariables(new ConcurrentHashMap<>()) // New script scope
            .parameters(new ConcurrentHashMap<>(parameters))
            .serverLocation(serverLocation)
            .serverCountry(serverCountry)
            .osType(osType)
            .architecture(architecture)
            .startTime(System.currentTimeMillis())
            .dryRun(dryRun)
            .timeoutSeconds(timeoutSeconds)
            .build();
    }
}