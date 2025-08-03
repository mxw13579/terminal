package com.fufu.terminal.entity.enums;

import java.util.Set;

/**
 * Enhanced 4-Type Script Classification System
 * Replaces the old script type system with four distinct types aligned with simplified architecture
 */
public enum ScriptType {
    STATIC_BUILTIN("Static Built-in", "No parameters, immediate execution", 
                   Set.of(Feature.QUICK_EXECUTION, Feature.NO_PARAMS, Feature.CODE_MANAGED)),
    
    CONFIGURABLE_BUILTIN("Configurable Built-in", "Parameters with intelligent decision making",
                         Set.of(Feature.PARAMETERS, Feature.INTELLIGENT_DECISIONS, Feature.GEOGRAPHIC_AWARENESS, Feature.CODE_MANAGED)),
    
    INTERACTIVE_BUILTIN("Interactive Built-in", "Real-time user interaction during execution",
                        Set.of(Feature.REAL_TIME_INTERACTION, Feature.DYNAMIC_PROMPTS, Feature.USER_INPUT, Feature.CODE_MANAGED)),
    
    USER_SCRIPT("User Script", "Admin-configurable scripts stored in database",
                Set.of(Feature.ADMIN_CONFIGURABLE, Feature.CUSTOM_PARAMETERS, Feature.DATABASE_STORED));
    
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
    
    public boolean hasFeature(Feature feature) {
        return supportedFeatures.contains(feature);
    }
    
    public boolean isBuiltIn() {
        return this == STATIC_BUILTIN || this == CONFIGURABLE_BUILTIN || this == INTERACTIVE_BUILTIN;
    }
    
    public boolean requiresParameters() {
        return hasFeature(Feature.PARAMETERS) || hasFeature(Feature.CUSTOM_PARAMETERS);
    }
    
    public boolean supportsInteraction() {
        return hasFeature(Feature.REAL_TIME_INTERACTION) || hasFeature(Feature.USER_INPUT);
    }
    
    /**
     * Script feature capabilities
     */
    public enum Feature {
        // Execution characteristics
        QUICK_EXECUTION,         // Fast execution without complex setup
        NO_PARAMS,              // No parameters required
        PARAMETERS,             // Accepts parameters
        CUSTOM_PARAMETERS,      // User-defined parameters
        
        // Intelligence features
        INTELLIGENT_DECISIONS,   // Makes context-based decisions
        GEOGRAPHIC_AWARENESS,    // Location-aware mirror selection
        
        // Interaction capabilities
        REAL_TIME_INTERACTION,   // Real-time user interaction
        DYNAMIC_PROMPTS,        // Dynamic user prompts during execution
        USER_INPUT,             // Supports user input collection
        
        // Management characteristics
        ADMIN_CONFIGURABLE,     // Can be configured via admin interface
        CODE_MANAGED,          // Managed in code (built-in scripts)
        DATABASE_STORED        // Stored in database (user scripts)
    }
}