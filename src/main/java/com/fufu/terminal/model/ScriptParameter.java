package com.fufu.terminal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Script Parameter Definition
 * 
 * Defines a parameter that can be passed to a script with validation rules,
 * type information, and display properties.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ScriptParameter {
    
    @NotBlank
    private String name;
    
    @NotNull
    private ParameterType type;
    
    @NotBlank
    private String displayName;
    
    private String description;
    
    private Object defaultValue;
    
    private boolean required;
    
    // Validation rules specific to the parameter type
    private Map<String, Object> validationRules;
    
    // For CHOICE type parameters
    private List<String> choices;
    
    // Display hints for UI
    private String placeholder;
    private String helpText;
    private boolean sensitive; // For passwords or secrets
    
    // Conditional display logic
    private String dependsOn; // Show only if another parameter has specific value
    private Object dependsOnValue;
    
    /**
     * Parameter Type Enumeration
     */
    public enum ParameterType {
        STRING("Text input"),
        INTEGER("Whole number"),
        DECIMAL("Decimal number"),
        BOOLEAN("True/false or yes/no"),
        CHOICE("Selection from predefined options"),
        PASSWORD("Password input (masked)"),
        EMAIL("Email address"),
        PATH("File or directory path"),
        URL("Web URL"),
        JSON("JSON object"),
        MULTILINE_TEXT("Multi-line text input");
        
        private final String description;
        
        ParameterType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        /**
         * Check if this type supports validation rules
         */
        public boolean supportsValidationRules() {
            return this != BOOLEAN && this != CHOICE;
        }
        
        /**
         * Get default validation rules for this type
         */
        public Map<String, Object> getDefaultValidationRules() {
            return switch (this) {
                case STRING, MULTILINE_TEXT -> Map.of(
                    "minLength", 0,
                    "maxLength", 1000
                );
                case INTEGER -> Map.of(
                    "min", Integer.MIN_VALUE,
                    "max", Integer.MAX_VALUE
                );
                case DECIMAL -> Map.of(
                    "min", Double.MIN_VALUE,
                    "max", Double.MAX_VALUE
                );
                case PASSWORD -> Map.of(
                    "minLength", 8,
                    "maxLength", 128,
                    "requireUppercase", false,
                    "requireLowercase", false,
                    "requireNumbers", false,
                    "requireSpecialChars", false
                );
                case EMAIL -> Map.of(
                    "maxLength", 254
                );
                case PATH -> Map.of(
                    "requireAbsolute", false,
                    "mustExist", false
                );
                case URL -> Map.of(
                    "schemes", List.of("http", "https")
                );
                default -> Map.of();
            };
        }
    }
    
    /**
     * Create a simple text parameter
     */
    public static ScriptParameter text(String name, String displayName) {
        return ScriptParameter.builder()
            .name(name)
            .type(ParameterType.STRING)
            .displayName(displayName)
            .required(false)
            .build();
    }
    
    /**
     * Create a required text parameter
     */
    public static ScriptParameter requiredText(String name, String displayName) {
        return ScriptParameter.builder()
            .name(name)
            .type(ParameterType.STRING)
            .displayName(displayName)
            .required(true)
            .build();
    }
    
    /**
     * Create a choice parameter
     */
    public static ScriptParameter choice(String name, String displayName, List<String> choices) {
        return ScriptParameter.builder()
            .name(name)
            .type(ParameterType.CHOICE)
            .displayName(displayName)
            .choices(choices)
            .required(true)
            .build();
    }
    
    /**
     * Create a boolean parameter
     */
    public static ScriptParameter bool(String name, String displayName, boolean defaultValue) {
        return ScriptParameter.builder()
            .name(name)
            .type(ParameterType.BOOLEAN)
            .displayName(displayName)
            .defaultValue(defaultValue)
            .required(false)
            .build();
    }
    
    /**
     * Create a password parameter
     */
    public static ScriptParameter password(String name, String displayName) {
        return ScriptParameter.builder()
            .name(name)
            .type(ParameterType.PASSWORD)
            .displayName(displayName)
            .sensitive(true)
            .required(true)
            .validationRules(ParameterType.PASSWORD.getDefaultValidationRules())
            .build();
    }
    
    /**
     * Create an integer parameter with range
     */
    public static ScriptParameter integer(String name, String displayName, int min, int max) {
        return ScriptParameter.builder()
            .name(name)
            .type(ParameterType.INTEGER)
            .displayName(displayName)
            .required(false)
            .validationRules(Map.of("min", min, "max", max))
            .build();
    }
    
    /**
     * Validate if this parameter should be shown based on dependencies
     */
    public boolean shouldDisplay(Map<String, Object> currentValues) {
        if (dependsOn == null) {
            return true;
        }
        
        Object currentValue = currentValues.get(dependsOn);
        if (currentValue == null) {
            return false;
        }
        
        return dependsOnValue == null || dependsOnValue.equals(currentValue);
    }
}