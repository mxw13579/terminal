package com.fufu.terminal.script.model;

import lombok.Builder;
import lombok.Value;

/**
 * Type-safe variable wrapper with automatic type conversion
 * Supports the variable passing system between scripts
 */
@Value
@Builder
public class TypedVariable {
    String name;
    Object rawValue;
    Class<?> type;
    long timestamp;
    
    public static TypedVariable of(String name, Object value) {
        return TypedVariable.builder()
            .name(name)
            .rawValue(value)
            .type(value != null ? value.getClass() : Object.class)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getValue(Class<T> expectedType) {
        if (rawValue == null) {
            return null;
        }
        
        if (expectedType.isAssignableFrom(type)) {
            return (T) rawValue;
        }
        
        // Attempt type conversion for common cases
        return TypeConverter.convert(rawValue, expectedType);
    }
    
    public Object getRawValue() {
        return rawValue;
    }
    
    /**
     * Type converter for common type conversions
     */
    public static class TypeConverter {
        
        @SuppressWarnings("unchecked")
        public static <T> T convert(Object value, Class<T> targetType) {
            if (value == null) {
                return null;
            }
            
            if (targetType.isAssignableFrom(value.getClass())) {
                return (T) value;
            }
            
            String strValue = value.toString();
            
            try {
                if (targetType == String.class) {
                    return (T) strValue;
                } else if (targetType == Integer.class || targetType == int.class) {
                    return (T) Integer.valueOf(strValue);
                } else if (targetType == Long.class || targetType == long.class) {
                    return (T) Long.valueOf(strValue);
                } else if (targetType == Double.class || targetType == double.class) {
                    return (T) Double.valueOf(strValue);
                } else if (targetType == Boolean.class || targetType == boolean.class) {
                    return (T) Boolean.valueOf(strValue);
                } else {
                    throw new IllegalArgumentException("Cannot convert " + value.getClass().getSimpleName() + 
                        " to " + targetType.getSimpleName());
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Cannot convert '" + strValue + "' to " + targetType.getSimpleName(), e);
            }
        }
    }
}