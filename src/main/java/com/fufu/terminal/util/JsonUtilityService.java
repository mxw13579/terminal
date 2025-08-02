package com.fufu.terminal.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Centralized JSON utility service for consistent JSON serialization/deserialization
 * across the application. Uses Jackson ObjectMapper for compatibility with Spring Boot.
 * 
 * @author Claude
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JsonUtilityService {
    
    private final ObjectMapper objectMapper;
    
    /**
     * Safely converts an object to JSON string with proper error handling.
     * 
     * @param object The object to serialize
     * @return JSON string representation, or null if object is null or serialization fails
     */
    public String toJsonString(Object object) {
        if (object == null) {
            return null;
        }
        
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON: {}", object.getClass().getSimpleName(), e);
            return null;
        }
    }
    
    /**
     * Safely converts an object to JSON string with fallback value.
     * 
     * @param object The object to serialize
     * @param fallback Fallback value if serialization fails
     * @return JSON string representation, or fallback value if serialization fails
     */
    public String toJsonString(Object object, String fallback) {
        String result = toJsonString(object);
        return result != null ? result : fallback;
    }
    
    /**
     * Safely parses JSON string to specified type with proper error handling.
     * 
     * @param <T> The target type
     * @param jsonString The JSON string to parse
     * @param targetClass The target class type
     * @return Parsed object, or null if parsing fails
     */
    public <T> T fromJsonString(String jsonString, Class<T> targetClass) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        
        try {
            return objectMapper.readValue(jsonString, targetClass);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON string to {}: {}", targetClass.getSimpleName(), jsonString, e);
            return null;
        }
    }
    
    /**
     * Validates if a string is valid JSON.
     * 
     * @param jsonString The string to validate
     * @return true if valid JSON, false otherwise
     */
    public boolean isValidJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return false;
        }
        
        try {
            objectMapper.readTree(jsonString);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}