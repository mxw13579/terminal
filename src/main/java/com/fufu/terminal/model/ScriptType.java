package com.fufu.terminal.model;

import java.util.Set;

/**
 * Enhanced Script Type Enumeration
 * 
 * Defines the four types of scripts in the SSH Terminal Management System
 * with specific behaviors and execution patterns.
 */
public enum ScriptType {
    STATIC_BUILTIN("Static Built-in", "No parameters, immediate execution", 
                   Set.of(Feature.QUICK_EXECUTION, Feature.NO_PARAMS, Feature.CODE_MANAGED)),
    
    CONFIGURABLE_BUILTIN("Configurable Built-in", "Parameters with intelligent decision making",
                         Set.of(Feature.PARAMETERS, Feature.INTELLIGENT_DECISIONS, 
                               Feature.GEOGRAPHIC_AWARENESS, Feature.CODE_MANAGED)),
    
    INTERACTIVE_BUILTIN("Interactive Built-in", "Real-time user interaction during execution",
                        Set.of(Feature.REAL_TIME_INTERACTION, Feature.DYNAMIC_PROMPTS, 
                              Feature.USER_INPUT, Feature.CODE_MANAGED)),
    
    USER_SCRIPT("User Script", "Admin-configurable scripts stored in database",
                Set.of(Feature.ADMIN_CONFIGURABLE, Feature.CUSTOM_PARAMETERS, 
                      Feature.DATABASE_STORED, Feature.PARAMETERS));
    
    private final String displayName;
    private final String description;
    private final Set<Feature> supportedFeatures;
    
    ScriptType(String displayName, String description, Set<Feature> supportedFeatures) {
        this.displayName = displayName;
        this.description = description;
        this.supportedFeatures = supportedFeatures;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public Set<Feature> getSupportedFeatures() {
        return supportedFeatures;
    }
    
    /**
     * Check if this script type supports a specific feature
     */
    public boolean supports(Feature feature) {
        return supportedFeatures.contains(feature);
    }
    
    /**
     * Check if this script type requires parameters
     */
    public boolean requiresParameters() {
        return supportedFeatures.contains(Feature.PARAMETERS) || 
               supportedFeatures.contains(Feature.CUSTOM_PARAMETERS);
    }
    
    /**
     * Check if this script type supports real-time interaction
     */
    public boolean supportsInteraction() {
        return supportedFeatures.contains(Feature.REAL_TIME_INTERACTION) ||
               supportedFeatures.contains(Feature.USER_INPUT);
    }
    
    /**
     * Check if this script type is code-managed (built-in)
     */
    public boolean isCodeManaged() {
        return supportedFeatures.contains(Feature.CODE_MANAGED);
    }
    
    /**
     * Check if this script type is database-stored
     */
    public boolean isDatabaseStored() {
        return supportedFeatures.contains(Feature.DATABASE_STORED);
    }
    
    /**
     * Feature enumeration for script capabilities
     */
    public enum Feature {
        // Execution characteristics
        QUICK_EXECUTION("Quick execution without setup"),
        NO_PARAMS("No parameters required"),
        PARAMETERS("Accepts parameters"),
        CUSTOM_PARAMETERS("Admin-configurable parameters"),
        
        // Intelligence features
        INTELLIGENT_DECISIONS("Makes intelligent decisions based on context"),
        GEOGRAPHIC_AWARENESS("Geographically aware decisions"),
        
        // Interaction features
        REAL_TIME_INTERACTION("Real-time user interaction"),
        DYNAMIC_PROMPTS("Dynamic user prompts"),
        USER_INPUT("User input during execution"),
        
        // Management features
        ADMIN_CONFIGURABLE("Configurable by administrators"),
        CODE_MANAGED("Managed through code"),
        DATABASE_STORED("Stored in database");
        
        private final String description;
        
        Feature(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}