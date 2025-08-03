package com.fufu.terminal.entity.enums;

/**
 * Enhanced variable scope enumeration with precedence rules
 * Supports the new variable passing system between scripts
 */
public enum VariableScope {
    SCRIPT(1, "Script Variable", "Available only within current script"),    // Highest precedence
    SESSION(2, "Session Variable", "Available throughout entire session"),   // Medium precedence  
    GLOBAL(3, "Global Variable", "Available across all executions for user"); // Lowest precedence

    private final int precedence;
    private final String displayName;
    private final String description;

    VariableScope(int precedence, String displayName, String description) {
        this.precedence = precedence;
        this.displayName = displayName;
        this.description = description;
    }

    public int getPrecedence() {
        return precedence;
    }
    
    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
    
    /**
     * Get scopes in reverse precedence order (GLOBAL, SESSION, SCRIPT)
     * Used for variable resolution where higher precedence overwrites lower precedence
     */
    public static VariableScope[] getReverseValues() {
        return new VariableScope[]{GLOBAL, SESSION, SCRIPT};
    }
    
    /**
     * Check if this scope has higher precedence than another scope
     */
    public boolean hasHigherPrecedenceThan(VariableScope other) {
        return this.precedence < other.precedence; // Lower number = higher precedence
    }
}