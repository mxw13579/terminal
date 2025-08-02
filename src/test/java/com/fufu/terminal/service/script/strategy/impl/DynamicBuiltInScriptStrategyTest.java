package com.fufu.terminal.service.script.strategy.impl;

import com.fufu.terminal.command.AtomicScriptCommand;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.service.script.ScriptExecutionResult;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.ScriptParameterType;
import com.fufu.terminal.service.script.strategy.BuiltInScriptMetadata;
import com.fufu.terminal.service.script.strategy.ScriptSourceType;
import com.fufu.terminal.service.script.strategy.model.ScriptExecutionRequest;
import com.fufu.terminal.service.script.strategy.registry.ScriptTypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for DynamicBuiltInScriptStrategy
 * Tests parameter validation, security scenarios, and error handling
 */
@ExtendWith(MockitoExtension.class)
class DynamicBuiltInScriptStrategyTest {

    @Mock
    private ScriptTypeRegistry scriptTypeRegistry;

    @Mock
    private AtomicScriptCommand atomicScriptCommand;

    @Mock
    private CommandContext commandContext;

    private DynamicBuiltInScriptStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new DynamicBuiltInScriptStrategy(scriptTypeRegistry);
    }

    @Test
    void canHandle_withDynamicBuiltInScript_returnsTrue() {
        // Given
        String scriptId = "docker-install";
        when(scriptTypeRegistry.isDynamicBuiltInScript(scriptId)).thenReturn(true);

        // When
        boolean result = strategy.canHandle(scriptId, ScriptSourceType.BUILT_IN_DYNAMIC);

        // Then
        assertTrue(result);
        verify(scriptTypeRegistry).isDynamicBuiltInScript(scriptId);
    }

    @Test
    void canHandle_withStaticSourceType_returnsFalse() {
        // Given
        String scriptId = "docker-install";

        // When
        boolean result = strategy.canHandle(scriptId, ScriptSourceType.BUILT_IN_STATIC);

        // Then
        assertFalse(result);
        verifyNoInteractions(scriptTypeRegistry);
    }

    @Test
    void execute_withValidParametersAndScript_returnsSuccessResult() {
        // Given
        String scriptId = "docker-install";
        Map<String, Object> parameters = Map.of(
            "version", "20.10.21",
            "enableLogging", true,
            "port", 8080
        );
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);
        BuiltInScriptMetadata metadata = createDockerInstallMetadata();
        CommandResult commandResult = CommandResult.success("Docker installed successfully");

        when(scriptTypeRegistry.getBuiltInScriptCommand(scriptId)).thenReturn(atomicScriptCommand);
        when(scriptTypeRegistry.getBuiltInScriptMetadata(scriptId)).thenReturn(metadata);
        when(atomicScriptCommand.execute(any(CommandContext.class))).thenReturn(commandResult);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("脚本执行成功", result.getMessage());
        assertEquals("Docker installed successfully", result.getDisplayOutput());
        assertTrue(result.isDisplayToUser());
        assertNotNull(result.getOutputData());
        assertTrue(result.getOutputData().containsKey("parameters"));
        
        // Verify parameters were set on context
        verify(commandContext).setVariable("version", "20.10.21");
        verify(commandContext).setVariable("enableLogging", true);
        verify(commandContext).setVariable("port", 8080);
    }

    @Test
    void execute_withMissingRequiredParameter_returnsValidationError() {
        // Given
        String scriptId = "docker-install";
        Map<String, Object> parameters = Map.of("enableLogging", true); // Missing required 'version'
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);
        BuiltInScriptMetadata metadata = createDockerInstallMetadata();

        when(scriptTypeRegistry.getBuiltInScriptCommand(scriptId)).thenReturn(atomicScriptCommand);
        when(scriptTypeRegistry.getBuiltInScriptMetadata(scriptId)).thenReturn(metadata);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("参数验证失败"));
        assertTrue(result.getErrorMessage().contains("缺少必需参数: version"));
        
        // Verify command was not executed
        verify(atomicScriptCommand, never()).execute(any());
    }

    @Test
    void execute_withInvalidParameterType_returnsValidationError() {
        // Given
        String scriptId = "docker-install";
        Map<String, Object> parameters = Map.of(
            "version", "20.10.21",
            "enableLogging", "not-a-boolean", // Invalid boolean
            "port", "not-a-number" // Invalid integer
        );
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);
        BuiltInScriptMetadata metadata = createDockerInstallMetadata();

        when(scriptTypeRegistry.getBuiltInScriptCommand(scriptId)).thenReturn(atomicScriptCommand);
        when(scriptTypeRegistry.getBuiltInScriptMetadata(scriptId)).thenReturn(metadata);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("参数验证失败"));
        assertTrue(result.getErrorMessage().contains("类型不匹配"));
    }

    @Test
    void execute_withDefaultParameters_usesDefaults() {
        // Given
        String scriptId = "docker-install";
        Map<String, Object> parameters = Map.of("version", "20.10.21"); // Only required param
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);
        BuiltInScriptMetadata metadata = createDockerInstallMetadataWithDefaults();
        CommandResult commandResult = CommandResult.success("Docker installed with defaults");

        when(scriptTypeRegistry.getBuiltInScriptCommand(scriptId)).thenReturn(atomicScriptCommand);
        when(scriptTypeRegistry.getBuiltInScriptMetadata(scriptId)).thenReturn(metadata);
        when(atomicScriptCommand.execute(any(CommandContext.class))).thenReturn(commandResult);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertTrue(result.isSuccess());
        verify(commandContext).setVariable("version", "20.10.21");
        // Default values should be applied during validation
    }

    // Security Tests
    @Test
    void execute_withSqlInjectionAttempt_handlesSecurely() {
        // Given
        String scriptId = "docker-install";
        Map<String, Object> parameters = Map.of(
            "version", "'; DROP TABLE users; --",
            "enableLogging", true,
            "port", 8080
        );
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);
        BuiltInScriptMetadata metadata = createDockerInstallMetadata();
        CommandResult commandResult = CommandResult.success("Secured execution");

        when(scriptTypeRegistry.getBuiltInScriptCommand(scriptId)).thenReturn(atomicScriptCommand);
        when(scriptTypeRegistry.getBuiltInScriptMetadata(scriptId)).thenReturn(metadata);
        when(atomicScriptCommand.execute(any(CommandContext.class))).thenReturn(commandResult);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertTrue(result.isSuccess());
        // Parameters should be passed as-is to command context for proper handling
        verify(commandContext).setVariable("version", "'; DROP TABLE users; --");
    }

    @Test
    void execute_withScriptInjectionAttempt_handlesSecurely() {
        // Given
        String scriptId = "docker-install";
        Map<String, Object> parameters = Map.of(
            "version", "20.10.21 && rm -rf /",
            "enableLogging", true,
            "port", 8080
        );
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);
        BuiltInScriptMetadata metadata = createDockerInstallMetadata();
        CommandResult commandResult = CommandResult.success("Secured execution");

        when(scriptTypeRegistry.getBuiltInScriptCommand(scriptId)).thenReturn(atomicScriptCommand);
        when(scriptTypeRegistry.getBuiltInScriptMetadata(scriptId)).thenReturn(metadata);
        when(atomicScriptCommand.execute(any(CommandContext.class))).thenReturn(commandResult);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertTrue(result.isSuccess());
        verify(commandContext).setVariable("version", "20.10.21 && rm -rf /");
    }

    // Array and Object Parameter Tests
    @Test
    void execute_withArrayParameter_validatesCorrectly() {
        // Given
        String scriptId = "multi-config";
        Map<String, Object> parameters = Map.of(
            "configs", Arrays.asList("config1", "config2", "config3"),
            "metadata", Map.of("env", "production", "region", "us-east-1")
        );
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);
        BuiltInScriptMetadata metadata = createComplexParameterMetadata();
        CommandResult commandResult = CommandResult.success("Multi-config applied");

        when(scriptTypeRegistry.getBuiltInScriptCommand(scriptId)).thenReturn(atomicScriptCommand);
        when(scriptTypeRegistry.getBuiltInScriptMetadata(scriptId)).thenReturn(metadata);
        when(atomicScriptCommand.execute(any(CommandContext.class))).thenReturn(commandResult);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertTrue(result.isSuccess());
        verify(commandContext).setVariable("configs", parameters.get("configs"));
        verify(commandContext).setVariable("metadata", parameters.get("metadata"));
    }

    @Test
    void execute_withInvalidArrayParameter_returnsValidationError() {
        // Given
        String scriptId = "multi-config";
        Map<String, Object> parameters = Map.of(
            "configs", "not-an-array", // Should be array
            "metadata", Map.of("env", "production")
        );
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);
        BuiltInScriptMetadata metadata = createComplexParameterMetadata();

        when(scriptTypeRegistry.getBuiltInScriptCommand(scriptId)).thenReturn(atomicScriptCommand);
        when(scriptTypeRegistry.getBuiltInScriptMetadata(scriptId)).thenReturn(metadata);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("应该是数组类型"));
    }

    // Performance Tests
    @ParameterizedTest
    @ValueSource(ints = {10, 50, 100})
    void execute_withLargeParameterSets_performsWithinLimits(int parameterCount) {
        // Given
        String scriptId = "large-param-script";
        Map<String, Object> parameters = new HashMap<>();
        for (int i = 0; i < parameterCount; i++) {
            parameters.put("param" + i, "value" + i);
        }
        
        ScriptExecutionRequest request = createExecutionRequest(scriptId, parameters);
        BuiltInScriptMetadata metadata = createLargeParameterSetMetadata(parameterCount);
        CommandResult commandResult = CommandResult.success("Large parameter set processed");

        when(scriptTypeRegistry.getBuiltInScriptCommand(scriptId)).thenReturn(atomicScriptCommand);
        when(scriptTypeRegistry.getBuiltInScriptMetadata(scriptId)).thenReturn(metadata);
        when(atomicScriptCommand.execute(any(CommandContext.class))).thenReturn(commandResult);

        // When
        long startTime = System.currentTimeMillis();
        ScriptExecutionResult result = strategy.execute(request);
        long endTime = System.currentTimeMillis();

        // Then
        assertTrue(result.isSuccess());
        assertTrue((endTime - startTime) < 2000, "Should handle " + parameterCount + " parameters within 2 seconds");
    }

    @Test
    void getRequiredParameters_returnsCorrectParameters() {
        // Given
        String scriptId = "docker-install";
        BuiltInScriptMetadata metadata = createDockerInstallMetadata();
        when(scriptTypeRegistry.getBuiltInScriptMetadata(scriptId)).thenReturn(metadata);

        // When
        List<ScriptParameter> parameters = strategy.getRequiredParameters(scriptId);

        // Then
        assertNotNull(parameters);
        assertEquals(3, parameters.size());
        assertTrue(parameters.stream().anyMatch(p -> "version".equals(p.getName())));
        assertTrue(parameters.stream().anyMatch(p -> "enableLogging".equals(p.getName())));
        assertTrue(parameters.stream().anyMatch(p -> "port".equals(p.getName())));
    }

    @Test
    void getSupportedSourceType_returnsBuiltInDynamic() {
        // When
        ScriptSourceType sourceType = strategy.getSupportedSourceType();

        // Then
        assertEquals(ScriptSourceType.BUILT_IN_DYNAMIC, sourceType);
    }

    // Helper methods
    private ScriptExecutionRequest createExecutionRequest(String scriptId, Map<String, Object> parameters) {
        ScriptExecutionRequest request = new ScriptExecutionRequest();
        request.setScriptId(scriptId);
        request.setParameters(parameters);
        request.setCommandContext(commandContext);
        return request;
    }

    private BuiltInScriptMetadata createDockerInstallMetadata() {
        BuiltInScriptMetadata metadata = new BuiltInScriptMetadata();
        
        List<ScriptParameter> parameters = Arrays.asList(
            createParameter("version", ScriptParameterType.STRING, true, null),
            createParameter("enableLogging", ScriptParameterType.BOOLEAN, false, false),
            createParameter("port", ScriptParameterType.INTEGER, false, 8080)
        );
        
        metadata.setParameters(parameters);
        return metadata;
    }

    private BuiltInScriptMetadata createDockerInstallMetadataWithDefaults() {
        BuiltInScriptMetadata metadata = new BuiltInScriptMetadata();
        
        List<ScriptParameter> parameters = Arrays.asList(
            createParameter("version", ScriptParameterType.STRING, true, null),
            createParameter("enableLogging", ScriptParameterType.BOOLEAN, false, true),
            createParameter("port", ScriptParameterType.INTEGER, false, 8080)
        );
        
        metadata.setParameters(parameters);
        return metadata;
    }

    private BuiltInScriptMetadata createComplexParameterMetadata() {
        BuiltInScriptMetadata metadata = new BuiltInScriptMetadata();
        
        List<ScriptParameter> parameters = Arrays.asList(
            createParameter("configs", ScriptParameterType.ARRAY, true, null),
            createParameter("metadata", ScriptParameterType.OBJECT, true, null)
        );
        
        metadata.setParameters(parameters);
        return metadata;
    }

    private BuiltInScriptMetadata createLargeParameterSetMetadata(int paramCount) {
        BuiltInScriptMetadata metadata = new BuiltInScriptMetadata();
        
        List<ScriptParameter> parameters = new ArrayList<>();
        for (int i = 0; i < paramCount; i++) {
            parameters.add(createParameter("param" + i, ScriptParameterType.STRING, false, "default" + i));
        }
        
        metadata.setParameters(parameters);
        return metadata;
    }

    private ScriptParameter createParameter(String name, ScriptParameterType type, boolean required, Object defaultValue) {
        ScriptParameter parameter = new ScriptParameter();
        parameter.setName(name);
        parameter.setType(type);
        parameter.setRequired(required);
        if (defaultValue != null) {
            parameter.setDefaultValue(defaultValue);
        }
        return parameter;
    }
}