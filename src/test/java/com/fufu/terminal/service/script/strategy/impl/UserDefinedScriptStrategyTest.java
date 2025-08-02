package com.fufu.terminal.service.script.strategy.impl;

import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.service.script.ScriptExecutionResult;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.ScriptParameterType;
import com.fufu.terminal.service.script.UnifiedAtomicScript;
import com.fufu.terminal.service.script.UnifiedScriptRegistry;
import com.fufu.terminal.service.script.strategy.ScriptSourceType;
import com.fufu.terminal.service.script.strategy.model.ScriptExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for UserDefinedScriptStrategy
 * Tests user script execution, parameter validation, and error scenarios
 */
@ExtendWith(MockitoExtension.class)
class UserDefinedScriptStrategyTest {

    @Mock
    private UnifiedScriptRegistry unifiedScriptRegistry;

    @Mock
    private UnifiedAtomicScript unifiedAtomicScript;

    @Mock
    private CommandContext commandContext;

    private UserDefinedScriptStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new UserDefinedScriptStrategy(unifiedScriptRegistry);
    }

    @Test
    void canHandle_withUserDefinedScript_returnsTrue() {
        // Given
        String scriptId = "user-script-123";
        when(unifiedScriptRegistry.getScript(scriptId)).thenReturn(unifiedAtomicScript);

        // When
        boolean result = strategy.canHandle(scriptId, ScriptSourceType.USER_DEFINED);

        // Then
        assertTrue(result);
        verify(unifiedScriptRegistry).getScript(scriptId);
    }

    @Test
    void canHandle_withNonUserDefinedSourceType_returnsFalse() {
        // Given
        String scriptId = "script-123";

        // When
        boolean result = strategy.canHandle(scriptId, ScriptSourceType.BUILT_IN_STATIC);

        // Then
        assertFalse(result);
        verifyNoInteractions(unifiedScriptRegistry);
    }

    @Test
    void canHandle_withNonExistentScript_returnsFalse() {
        // Given
        String scriptId = "non-existent-script";
        when(unifiedScriptRegistry.getScript(scriptId)).thenReturn(null);

        // When
        boolean result = strategy.canHandle(scriptId, ScriptSourceType.USER_DEFINED);

        // Then
        assertFalse(result);
        verify(unifiedScriptRegistry).getScript(scriptId);
    }

    @Test
    void execute_withValidUserScript_returnsSuccessResult() {
        // Given
        String scriptId = "user-script-123";
        Map<String, Object> parameters = Map.of("inputParam", "testValue");
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);
        ScriptExecutionResult expectedResult = createSuccessResult("User script executed successfully");

        when(unifiedScriptRegistry.getScript(scriptId)).thenReturn(unifiedAtomicScript);
        when(unifiedAtomicScript.getInputParameters()).thenReturn(createUserScriptParameters());
        when(unifiedAtomicScript.execute(any(CommandContext.class))).thenReturn(expectedResult);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("User script executed successfully", result.getDisplayOutput());
        assertNotNull(result.getStartTime());
        assertNotNull(result.getEndTime());
        assertTrue(result.getDuration() >= 0);
        
        // Verify parameters were set on context
        verify(commandContext).setVariable("inputParam", "testValue");
        verify(unifiedAtomicScript).execute(commandContext);
    }

    @Test
    void execute_withScriptNotFound_returnsFailureResult() {
        // Given
        String scriptId = "non-existent-script";
        ScriptExecutionRequest request = createExecutionRequest(scriptId, Collections.emptyMap());

        when(unifiedScriptRegistry.getScript(scriptId)).thenReturn(null);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("未找到用户定义脚本: " + scriptId, result.getErrorMessage());
        assertTrue(result.isDisplayToUser());
        
        verify(unifiedAtomicScript, never()).execute(any());
    }

    @Test
    void execute_withMissingRequiredParameter_returnsValidationError() {
        // Given
        String scriptId = "user-script-123";
        Map<String, Object> parameters = Collections.emptyMap(); // Missing required parameter
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);

        when(unifiedScriptRegistry.getScript(scriptId)).thenReturn(unifiedAtomicScript);
        when(unifiedAtomicScript.getInputParameters()).thenReturn(createRequiredUserScriptParameters());

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("参数验证失败"));
        assertTrue(result.getErrorMessage().contains("缺少必需参数: requiredParam"));
        
        verify(unifiedAtomicScript, never()).execute(any());
    }

    @Test
    void execute_withInvalidParameterType_returnsValidationError() {
        // Given
        String scriptId = "user-script-123";
        Map<String, Object> parameters = Map.of("numericParam", "not-a-number");
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);

        when(unifiedScriptRegistry.getScript(scriptId)).thenReturn(unifiedAtomicScript);
        when(unifiedAtomicScript.getInputParameters()).thenReturn(createNumericParameterScript());

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("参数验证失败"));
        assertTrue(result.getErrorMessage().contains("类型不匹配"));
        
        verify(unifiedAtomicScript, never()).execute(any());
    }

    @Test
    void execute_withScriptExecutionException_returnsFailureResult() {
        // Given
        String scriptId = "failing-user-script";
        Map<String, Object> parameters = Map.of("inputParam", "testValue");
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);

        when(unifiedScriptRegistry.getScript(scriptId)).thenReturn(unifiedAtomicScript);
        when(unifiedAtomicScript.getInputParameters()).thenReturn(createUserScriptParameters());
        when(unifiedAtomicScript.execute(any(CommandContext.class))).thenThrow(new RuntimeException("Script execution failed"));

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("脚本执行异常: Script execution failed"));
        assertTrue(result.isDisplayToUser());
    }

    @Test
    void execute_withNullScriptResult_returnsFailureResult() {
        // Given
        String scriptId = "null-result-script";
        Map<String, Object> parameters = Map.of("inputParam", "testValue");
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);

        when(unifiedScriptRegistry.getScript(scriptId)).thenReturn(unifiedAtomicScript);
        when(unifiedAtomicScript.getInputParameters()).thenReturn(createUserScriptParameters());
        when(unifiedAtomicScript.execute(any(CommandContext.class))).thenReturn(null);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("脚本执行返回空结果", result.getErrorMessage());
        assertTrue(result.isDisplayToUser());
    }

    @Test
    void execute_withIncompleteResultTimingInfo_enrichesResult() {
        // Given
        String scriptId = "incomplete-result-script";
        Map<String, Object> parameters = Map.of("inputParam", "testValue");
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);
        ScriptExecutionResult incompleteResult = new ScriptExecutionResult();
        incompleteResult.setSuccess(true);
        incompleteResult.setDisplayOutput("Incomplete result");
        // Missing timing information

        when(unifiedScriptRegistry.getScript(scriptId)).thenReturn(unifiedAtomicScript);
        when(unifiedAtomicScript.getInputParameters()).thenReturn(createUserScriptParameters());
        when(unifiedAtomicScript.execute(any(CommandContext.class))).thenReturn(incompleteResult);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getStartTime());
        assertNotNull(result.getEndTime());
        assertTrue(result.getDuration() >= 0);
    }

    // Complex Parameter Validation Tests
    @Test
    void execute_withComplexParameterTypes_validatesCorrectly() {
        // Given
        String scriptId = "complex-param-script";
        Map<String, Object> parameters = Map.of(
            "arrayParam", Arrays.asList("item1", "item2", "item3"),
            "objectParam", Map.of("key1", "value1", "key2", "value2"),
            "booleanParam", true,
            "integerParam", 42
        );
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);
        ScriptExecutionResult expectedResult = createSuccessResult("Complex parameters processed");

        when(unifiedScriptRegistry.getScript(scriptId)).thenReturn(unifiedAtomicScript);
        when(unifiedAtomicScript.getInputParameters()).thenReturn(createComplexParameterScript());
        when(unifiedAtomicScript.execute(any(CommandContext.class))).thenReturn(expectedResult);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertTrue(result.isSuccess());
        verify(commandContext).setVariable("arrayParam", parameters.get("arrayParam"));
        verify(commandContext).setVariable("objectParam", parameters.get("objectParam"));
        verify(commandContext).setVariable("booleanParam", true);
        verify(commandContext).setVariable("integerParam", 42);
    }

    // Security Tests
    @Test
    void execute_withMaliciousParameters_handlesSecurely() {
        // Given
        String scriptId = "security-test-script";
        Map<String, Object> parameters = Map.of(
            "scriptInjection", "; rm -rf /; echo 'hacked'",
            "sqlInjection", "'; DROP TABLE users; --",
            "crossSiteScript", "<script>alert('xss')</script>"
        );
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);
        ScriptExecutionResult expectedResult = createSuccessResult("Security parameters handled");

        when(unifiedScriptRegistry.getScript(scriptId)).thenReturn(unifiedAtomicScript);
        when(unifiedAtomicScript.getInputParameters()).thenReturn(createSecurityTestParameters());
        when(unifiedAtomicScript.execute(any(CommandContext.class))).thenReturn(expectedResult);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertTrue(result.isSuccess());
        // Parameters should be passed through for proper handling by the script
        verify(commandContext).setVariable("scriptInjection", "; rm -rf /; echo 'hacked'");
        verify(commandContext).setVariable("sqlInjection", "'; DROP TABLE users; --");
        verify(commandContext).setVariable("crossSiteScript", "<script>alert('xss')</script>");
    }

    @Test
    void getRequiredParameters_withScript_returnsParameterArray() {
        // Given
        String scriptId = "user-script-123";
        ScriptParameter[] expectedParams = createUserScriptParameters();

        when(unifiedScriptRegistry.getScript(scriptId)).thenReturn(unifiedAtomicScript);
        when(unifiedAtomicScript.getInputParameters()).thenReturn(expectedParams);

        // When
        List<ScriptParameter> parameters = strategy.getRequiredParameters(scriptId);

        // Then
        assertNotNull(parameters);
        assertEquals(1, parameters.size());
        assertEquals("inputParam", parameters.get(0).getName());
    }

    @Test
    void getRequiredParameters_withNonExistentScript_returnsEmptyList() {
        // Given
        String scriptId = "non-existent-script";
        when(unifiedScriptRegistry.getScript(scriptId)).thenReturn(null);

        // When
        List<ScriptParameter> parameters = strategy.getRequiredParameters(scriptId);

        // Then
        assertNotNull(parameters);
        assertTrue(parameters.isEmpty());
    }

    @Test
    void getRequiredParameters_withScriptWithoutParams_returnsEmptyList() {
        // Given
        String scriptId = "no-param-script";
        when(unifiedScriptRegistry.getScript(scriptId)).thenReturn(unifiedAtomicScript);
        when(unifiedAtomicScript.getInputParameters()).thenReturn(null);

        // When
        List<ScriptParameter> parameters = strategy.getRequiredParameters(scriptId);

        // Then
        assertNotNull(parameters);
        assertTrue(parameters.isEmpty());
    }

    @Test
    void getSupportedSourceType_returnsUserDefined() {
        // When
        ScriptSourceType sourceType = strategy.getSupportedSourceType();

        // Then
        assertEquals(ScriptSourceType.USER_DEFINED, sourceType);
    }

    // Performance Tests
    @Test
    void execute_performanceTest_completesWithinTimeLimit() {
        // Given
        String scriptId = "performance-user-script";
        Map<String, Object> parameters = Map.of("inputParam", "performance-test");
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);
        ScriptExecutionResult expectedResult = createSuccessResult("Performance test completed");

        when(unifiedScriptRegistry.getScript(scriptId)).thenReturn(unifiedAtomicScript);
        when(unifiedAtomicScript.getInputParameters()).thenReturn(createUserScriptParameters());
        when(unifiedAtomicScript.execute(any(CommandContext.class))).thenReturn(expectedResult);

        // When
        long startTime = System.currentTimeMillis();
        ScriptExecutionResult result = strategy.execute(request);
        long endTime = System.currentTimeMillis();

        // Then
        assertTrue(result.isSuccess());
        assertTrue((endTime - startTime) < 1000, "User script execution should complete within 1 second");
    }

    // Helper methods
    private ScriptExecutionRequest createExecutionRequest(String scriptId, Map<String, Object> parameters) {
        ScriptExecutionRequest request = new ScriptExecutionRequest();
        request.setScriptId(scriptId);
        request.setParameters(parameters);
        request.setCommandContext(commandContext);
        return request;
    }

    private ScriptExecutionResult createSuccessResult(String output) {
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setSuccess(true);
        result.setDisplayOutput(output);
        result.setStartTime(LocalDateTime.now().minusSeconds(1));
        result.setEndTime(LocalDateTime.now());
        result.setDuration(1000);
        return result;
    }

    private ScriptParameter[] createUserScriptParameters() {
        ScriptParameter param = new ScriptParameter();
        param.setName("inputParam");
        param.setType(ScriptParameterType.STRING);
        param.setRequired(false);
        return new ScriptParameter[]{param};
    }

    private ScriptParameter[] createRequiredUserScriptParameters() {
        ScriptParameter param = new ScriptParameter();
        param.setName("requiredParam");
        param.setType(ScriptParameterType.STRING);
        param.setRequired(true);
        return new ScriptParameter[]{param};
    }

    private ScriptParameter[] createNumericParameterScript() {
        ScriptParameter param = new ScriptParameter();
        param.setName("numericParam");
        param.setType(ScriptParameterType.INTEGER);
        param.setRequired(true);
        return new ScriptParameter[]{param};
    }

    private ScriptParameter[] createComplexParameterScript() {
        return new ScriptParameter[]{
            createParameter("arrayParam", ScriptParameterType.ARRAY, true),
            createParameter("objectParam", ScriptParameterType.OBJECT, true),
            createParameter("booleanParam", ScriptParameterType.BOOLEAN, false),
            createParameter("integerParam", ScriptParameterType.INTEGER, false)
        };
    }

    private ScriptParameter[] createSecurityTestParameters() {
        return new ScriptParameter[]{
            createParameter("scriptInjection", ScriptParameterType.STRING, false),
            createParameter("sqlInjection", ScriptParameterType.STRING, false),
            createParameter("crossSiteScript", ScriptParameterType.STRING, false)
        };
    }

    private ScriptParameter createParameter(String name, ScriptParameterType type, boolean required) {
        ScriptParameter parameter = new ScriptParameter();
        parameter.setName(name);
        parameter.setType(type);
        parameter.setRequired(required);
        return parameter;
    }
}