package com.fufu.terminal.service.script;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced script parameter definition with comprehensive metadata
 * Provides complete parameter information for validation and UI generation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnhancedScriptParameter {
    
    /**
     * Parameter name (unique within script)
     */
    private String name;
    
    /**
     * Parameter type
     */
    private ParameterType type;
    
    /**
     * Human-readable parameter description
     */
    private String description;
    
    /**
     * Whether parameter is required
     */
    private boolean required;
    
    /**
     * Default value (can be null)
     */
    private Object defaultValue;
    
    /**
     * Regular expression pattern for validation
     */
    private String pattern;
    
    /**
     * Human-readable pattern description
     */
    private String patternDescription;
    
    /**
     * Minimum value for numeric parameters
     */
    private Double minValue;
    
    /**
     * Maximum value for numeric parameters
     */
    private Double maxValue;
    
    /**
     * Minimum length for string parameters
     */
    private Integer minLength;
    
    /**
     * Maximum length for string parameters
     */
    private Integer maxLength;
    
    /**
     * List of allowed values (for enum-like parameters)
     */
    @Builder.Default
    private List<String> allowedValues = new ArrayList<>();
    
    /**
     * List of parameter names this parameter depends on
     */
    @Builder.Default
    private List<String> dependsOn = new ArrayList<>();
    
    /**
     * Help text for user guidance
     */
    private String helpText;
    
    /**
     * Example value for user reference
     */
    private String example;
    
    /**
     * Parameter group for UI organization
     */
    private String group;
    
    /**
     * Display order within group
     */
    private Integer order;
    
    /**
     * Whether parameter is sensitive (password, etc.)
     */
    private boolean sensitive;
    
    /**
     * Whether parameter should be displayed in UI
     */
    @Builder.Default
    private boolean visible = true;
    
    /**
     * Whether parameter can be edited by user
     */
    @Builder.Default
    private boolean editable = true;
    
    /**
     * Custom validation message
     */
    private String validationMessage;
    
    /**
     * Parameter type enumeration
     */
    public enum ParameterType {
        STRING("String", "text"),
        INTEGER("Integer", "number"),
        LONG("Long", "number"),
        DOUBLE("Double", "number"),
        BOOLEAN("Boolean", "checkbox"),
        PORT("Port", "number"),
        EMAIL("Email", "email"),
        URL("URL", "url"),
        PASSWORD("Password", "password"),
        TEXTAREA("Text Area", "textarea"),
        SELECT("Select", "select"),
        MULTISELECT("Multi Select", "multiselect"),
        FILE("File", "file"),
        DATE("Date", "date"),
        DATETIME("Date Time", "datetime-local"),
        JSON("JSON", "textarea"),
        ARRAY("Array", "textarea");
        
        private final String displayName;
        private final String htmlInputType;
        
        ParameterType(String displayName, String htmlInputType) {
            this.displayName = displayName;
            this.htmlInputType = htmlInputType;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getHtmlInputType() {
            return htmlInputType;
        }
        
        /**
         * Check if type is numeric
         */
        public boolean isNumeric() {
            return this == INTEGER || this == LONG || this == DOUBLE || this == PORT;
        }
        
        /**
         * Check if type supports min/max values
         */
        public boolean supportsRange() {
            return isNumeric();
        }
        
        /**
         * Check if type supports length constraints
         */
        public boolean supportsLength() {
            return this == STRING || this == TEXTAREA || this == PASSWORD || 
                   this == EMAIL || this == URL || this == JSON;
        }
        
        /**
         * Check if type supports allowed values
         */
        public boolean supportsAllowedValues() {
            return this == SELECT || this == MULTISELECT || this == STRING;
        }
    }
    
    /**
     * Get parameter type from string (case-insensitive)
     */
    public static ParameterType parseType(String typeString) {
        if (typeString == null || typeString.trim().isEmpty()) {
            return ParameterType.STRING;
        }
        
        try {
            return ParameterType.valueOf(typeString.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try to match common aliases
            String lowerType = typeString.toLowerCase();
            switch (lowerType) {
                case "int":
                case "number":
                    return ParameterType.INTEGER;
                case "bool":
                    return ParameterType.BOOLEAN;
                case "text":
                    return ParameterType.STRING;
                case "pass":
                case "pwd":
                    return ParameterType.PASSWORD;
                default:
                    return ParameterType.STRING;
            }
        }
    }
    
    /**
     * Validate that parameter configuration is consistent
     */
    public List<String> validateConfiguration() {
        List<String> issues = new ArrayList<>();
        
        if (name == null || name.trim().isEmpty()) {
            issues.add("Parameter name is required");
        }
        
        if (type == null) {
            issues.add("Parameter type is required");
        }
        
        if (type != null) {
            // Check type-specific validations
            if (type.supportsRange() && minValue != null && maxValue != null && minValue > maxValue) {
                issues.add("minValue cannot be greater than maxValue");
            }
            
            if (type.supportsLength() && minLength != null && maxLength != null && minLength > maxLength) {
                issues.add("minLength cannot be greater than maxLength");
            }
            
            if (!type.supportsRange() && (minValue != null || maxValue != null)) {
                issues.add("minValue/maxValue not supported for type " + type.getDisplayName());
            }
            
            if (!type.supportsLength() && (minLength != null || maxLength != null)) {
                issues.add("minLength/maxLength not supported for type " + type.getDisplayName());
            }
            
            if (!type.supportsAllowedValues() && !allowedValues.isEmpty()) {
                issues.add("allowedValues not supported for type " + type.getDisplayName());
            }
        }
        
        if (required && defaultValue != null) {
            issues.add("Required parameters should not have default values");
        }
        
        return issues;
    }
    
    /**
     * Check if parameter has validation constraints
     */
    public boolean hasValidationConstraints() {
        return pattern != null || 
               minValue != null || 
               maxValue != null || 
               minLength != null || 
               maxLength != null || 
               !allowedValues.isEmpty() ||
               required;
    }
    
    /**
     * Convert to legacy ScriptParameter for compatibility
     */
    public ScriptParameter toLegacyParameter() {
        ScriptParameter.ParameterType legacyType;
        switch (this.type) {
            case INTEGER:
            case LONG:
            case DOUBLE:
            case PORT:
                legacyType = ScriptParameter.ParameterType.INTEGER;
                break;
            case BOOLEAN:
                legacyType = ScriptParameter.ParameterType.BOOLEAN;
                break;
            case ARRAY:
            case MULTISELECT:
                legacyType = ScriptParameter.ParameterType.ARRAY;
                break;
            case JSON:
                legacyType = ScriptParameter.ParameterType.OBJECT;
                break;
            default:
                legacyType = ScriptParameter.ParameterType.STRING;
                break;
        }
        
        String validationRule = buildValidationRule();
        
        return new ScriptParameter(name, legacyType, description, required, defaultValue, validationRule);
    }
    
    /**
     * Build validation rule string for legacy compatibility
     */
    private String buildValidationRule() {
        List<String> rules = new ArrayList<>();
        
        if (pattern != null) {
            rules.add("pattern:" + pattern);
        }
        
        if (minValue != null) {
            rules.add("min:" + minValue);
        }
        
        if (maxValue != null) {
            rules.add("max:" + maxValue);
        }
        
        if (minLength != null) {
            rules.add("minLength:" + minLength);
        }
        
        if (maxLength != null) {
            rules.add("maxLength:" + maxLength);
        }
        
        if (!allowedValues.isEmpty()) {
            rules.add("values:" + String.join(",", allowedValues));
        }
        
        return rules.isEmpty() ? null : String.join("|", rules);
    }
}