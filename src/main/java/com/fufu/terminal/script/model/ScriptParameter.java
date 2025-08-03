package com.fufu.terminal.script.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Script parameter definition with validation rules and metadata
 * Supports the enhanced parameter system for configurable and user scripts
 */
@Data
@Builder
public class ScriptParameter {
    private String name;
    private String displayName;
    private String description;
    private ParameterType type;
    private boolean required;
    private Object defaultValue;
    private String placeholder;
    private String helpText;
    
    // Validation rules
    private Integer minLength;
    private Integer maxLength;
    private Number minValue;
    private Number maxValue;
    private String pattern; // Regex pattern for validation
    private List<String> allowedValues; // For choice parameters
    
    // UI hints
    private boolean sensitive; // For password fields
    private boolean multiline; // For text areas
    private Map<String, Object> uiHints; // Additional UI customization
    
    public boolean isValid(Object value) {
        if (value == null) {
            return !required;
        }
        
        switch (type) {
            case STRING:
                return validateString(value.toString());
            case INTEGER:
                return validateInteger(value);
            case BOOLEAN:
                return value instanceof Boolean;
            case CHOICE:
                return allowedValues != null && allowedValues.contains(value.toString());
            case JSON:
                return validateJson(value);
            default:
                return true;
        }
    }
    
    private boolean validateString(String str) {
        if (minLength != null && str.length() < minLength) return false;
        if (maxLength != null && str.length() > maxLength) return false;
        if (pattern != null && !str.matches(pattern)) return false;
        return true;
    }
    
    private boolean validateInteger(Object value) {
        try {
            Number num = (Number) value;
            if (minValue != null && num.doubleValue() < minValue.doubleValue()) return false;
            if (maxValue != null && num.doubleValue() > maxValue.doubleValue()) return false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean validateJson(Object value) {
        // Basic JSON validation - could be enhanced with actual JSON parsing
        return value instanceof Map || value instanceof List || value instanceof String;
    }
}