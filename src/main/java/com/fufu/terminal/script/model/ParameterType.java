package com.fufu.terminal.script.model;

/**
 * Parameter type enumeration for script parameters
 * Supports type-safe parameter handling and validation
 */
public enum ParameterType {
    STRING("String", "Text input", String.class),
    INTEGER("Integer", "Numeric input", Integer.class),
    BOOLEAN("Boolean", "True/false checkbox", Boolean.class),
    CHOICE("Choice", "Selection from predefined options", String.class),
    JSON("JSON", "JSON object or array", Object.class),
    PASSWORD("Password", "Secure password input", String.class),
    FILE_PATH("File Path", "File system path", String.class),
    URL("URL", "Web URL or endpoint", String.class);
    
    private final String displayName;
    private final String description;
    private final Class<?> javaType;
    
    ParameterType(String displayName, String description, Class<?> javaType) {
        this.displayName = displayName;
        this.description = description;
        this.javaType = javaType;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public Class<?> getJavaType() {
        return javaType;
    }
    
    public boolean isCompatibleWith(Object value) {
        if (value == null) return true;
        
        switch (this) {
            case INTEGER:
                return value instanceof Number;
            case BOOLEAN:
                return value instanceof Boolean || 
                       (value instanceof String && 
                        ("true".equalsIgnoreCase(value.toString()) || 
                         "false".equalsIgnoreCase(value.toString())));
            case STRING:
            case PASSWORD:
            case FILE_PATH:
            case URL:
            case CHOICE:
                return value instanceof String;
            case JSON:
                return true; // JSON can be any object
            default:
                return javaType.isAssignableFrom(value.getClass());
        }
    }
}