package com.fufu.terminal.service.script.strategy.impl;

import com.fufu.terminal.command.AtomicScriptCommand;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.service.script.ScriptExecutionResult;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.strategy.BuiltInScriptMetadata;
import com.fufu.terminal.service.script.strategy.ScriptSourceType;
import com.fufu.terminal.service.script.strategy.model.ScriptExecutionRequest;
import com.fufu.terminal.service.script.strategy.registry.ScriptTypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for StaticBuiltInScriptStrategy
 * Tests all execution paths, error scenarios, and edge cases
 */
@ExtendWith(MockitoExtension.class)
class StaticBuiltInScriptStrategyTest {

    @Mock
    private ScriptTypeRegistry scriptTypeRegistry;

    @Mock
    private AtomicScriptCommand atomicScriptCommand;

    @Mock
    private CommandContext commandContext;

    private StaticBuiltInScriptStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new StaticBuiltInScriptStrategy(scriptTypeRegistry);
    }

    @Test
    void canHandle_withStaticBuiltInScript_returnsTrue() {
        // Given
        String scriptId = "system-info";
        when(scriptTypeRegistry.isStaticBuiltInScript(scriptId)).thenReturn(true);

        // When
        boolean result = strategy.canHandle(scriptId, ScriptSourceType.BUILT_IN_STATIC);

        // Then
        assertTrue(result);
        verify(scriptTypeRegistry).isStaticBuiltInScript(scriptId);
    }

    @Test
    void canHandle_withDifferentSourceType_returnsFalse() {
        // Given
        String scriptId = "system-info";

        // When
        boolean result = strategy.canHandle(scriptId, ScriptSourceType.BUILT_IN_DYNAMIC);

        // Then
        assertFalse(result);
        verifyNoInteractions(scriptTypeRegistry);
    }

    @Test
    void canHandle_withNonStaticScript_returnsFalse() {
        // Given
        String scriptId = "non-static-script";
        when(scriptTypeRegistry.isStaticBuiltInScript(scriptId)).thenReturn(false);

        // When
        boolean result = strategy.canHandle(scriptId, ScriptSourceType.BUILT_IN_STATIC);

        // Then
        assertFalse(result);
        verify(scriptTypeRegistry).isStaticBuiltInScript(scriptId);
    }

    @Test
    void execute_withValidStaticScript_returnsSuccessResult() {
        // Given
        String scriptId = "system-info";
        ScriptExecutionRequest request = createExecutionRequest(scriptId);
        
        BuiltInScriptMetadata metadata = createStaticScriptMetadata();
        CommandResult commandResult = CommandResult.success("System info output");

        when(scriptTypeRegistry.getBuiltInScriptCommand(scriptId)).thenReturn(atomicScriptCommand);
        when(scriptTypeRegistry.getBuiltInScriptMetadata(scriptId)).thenReturn(metadata);
        when(atomicScriptCommand.execute(any(CommandContext.class))).thenReturn(commandResult);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("脚本执行成功", result.getMessage());
        assertEquals("System info output", result.getDisplayOutput());
        assertTrue(result.isDisplayToUser());
        assertNotNull(result.getStartTime());
        assertNotNull(result.getEndTime());
        assertTrue(result.getDuration() >= 0);
        assertNotNull(result.getOutputData());
        
        verify(atomicScriptCommand).execute(any(CommandContext.class));
    }

    @Test
    void execute_withCommandNotFound_returnsFailureResult() {
        // Given
        String scriptId = "non-existent-script";
        ScriptExecutionRequest request = createExecutionRequest(scriptId);
        
        when(scriptTypeRegistry.getBuiltInScriptCommand(scriptId)).thenReturn(null);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("未找到内置脚本命令: " + scriptId, result.getErrorMessage());
        assertTrue(result.isDisplayToUser());
        assertNotNull(result.getStartTime());
        assertNotNull(result.getEndTime());
    }

    @Test
    void execute_withMetadataNotFound_returnsFailureResult() {
        // Given
        String scriptId = "script-without-metadata";
        ScriptExecutionRequest request = createExecutionRequest(scriptId);
        
        when(scriptTypeRegistry.getBuiltInScriptCommand(scriptId)).thenReturn(atomicScriptCommand);
        when(scriptTypeRegistry.getBuiltInScriptMetadata(scriptId)).thenReturn(null);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("脚本不是静态脚本或需要参数: " + scriptId, result.getErrorMessage());
        assertTrue(result.isDisplayToUser());
    }

    @Test
    void execute_withNonStaticScript_returnsFailureResult() {
        // Given
        String scriptId = "dynamic-script";
        ScriptExecutionRequest request = createExecutionRequest(scriptId);
        
        BuiltInScriptMetadata metadata = createDynamicScriptMetadata();
        
        when(scriptTypeRegistry.getBuiltInScriptCommand(scriptId)).thenReturn(atomicScriptCommand);
        when(scriptTypeRegistry.getBuiltInScriptMetadata(scriptId)).thenReturn(metadata);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("脚本不是静态脚本或需要参数: " + scriptId, result.getErrorMessage());
    }

    @Test
    void execute_withCommandExecutionFailure_returnsFailureResult() {
        // Given
        String scriptId = "failing-script";
        ScriptExecutionRequest request = createExecutionRequest(scriptId);
        
        BuiltInScriptMetadata metadata = createStaticScriptMetadata();
        CommandResult commandResult = CommandResult.failure("Command execution failed", "Error output");

        when(scriptTypeRegistry.getBuiltInScriptCommand(scriptId)).thenReturn(atomicScriptCommand);
        when(scriptTypeRegistry.getBuiltInScriptMetadata(scriptId)).thenReturn(metadata);
        when(atomicScriptCommand.execute(any(CommandContext.class))).thenReturn(commandResult);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("脚本执行失败: Command execution failed"));
        assertEquals("Error output", result.getDisplayOutput());
        assertTrue(result.isDisplayToUser());
        assertTrue(result.getDuration() >= 0);
    }

    @Test
    void execute_withCommandExecutionException_returnsFailureResult() {
        // Given
        String scriptId = "exception-script";
        ScriptExecutionRequest request = createExecutionRequest(scriptId);
        
        BuiltInScriptMetadata metadata = createStaticScriptMetadata();

        when(scriptTypeRegistry.getBuiltInScriptCommand(scriptId)).thenReturn(atomicScriptCommand);
        when(scriptTypeRegistry.getBuiltInScriptMetadata(scriptId)).thenReturn(metadata);
        when(atomicScriptCommand.execute(any(CommandContext.class))).thenThrow(new RuntimeException("Execution exception"));

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("脚本执行异常: Execution exception"));
        assertTrue(result.isDisplayToUser());
    }

    @Test
    void getRequiredParameters_staticScript_returnsEmptyList() {
        // When
        List<ScriptParameter> parameters = strategy.getRequiredParameters("any-script-id");

        // Then
        assertNotNull(parameters);
        assertTrue(parameters.isEmpty());
    }

    @Test
    void getSupportedSourceType_returnsBuiltInStatic() {
        // When
        ScriptSourceType sourceType = strategy.getSupportedSourceType();

        // Then
        assertEquals(ScriptSourceType.BUILT_IN_STATIC, sourceType);
    }

    // Edge case tests
    @Test
    void execute_withNullScriptId_handlesGracefully() {
        // Given
        ScriptExecutionRequest request = createExecutionRequest(null);

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("未找到内置脚本命令"));
    }

    @Test
    void execute_withEmptyScriptId_handlesGracefully() {
        // Given
        ScriptExecutionRequest request = createExecutionRequest("");

        // When
        ScriptExecutionResult result = strategy.execute(request);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("未找到内置脚本命令"));
    }

    @Test
    void execute_performanceTest_completesWithinTimeLimit() {
        // Given
        String scriptId = "performance-test-script";
        ScriptExecutionRequest request = createExecutionRequest(scriptId);
        
        BuiltInScriptMetadata metadata = createStaticScriptMetadata();
        CommandResult commandResult = CommandResult.success("Quick execution");

        when(scriptTypeRegistry.getBuiltInScriptCommand(scriptId)).thenReturn(atomicScriptCommand);
        when(scriptTypeRegistry.getBuiltInScriptMetadata(scriptId)).thenReturn(metadata);
        when(atomicScriptCommand.execute(any(CommandContext.class))).thenReturn(commandResult);

        // When
        long startTime = System.currentTimeMillis();
        ScriptExecutionResult result = strategy.execute(request);
        long endTime = System.currentTimeMillis();

        // Then
        assertTrue(result.isSuccess());
        assertTrue((endTime - startTime) < 1000, "Execution should complete within 1 second");
    }

    private ScriptExecutionRequest createExecutionRequest(String scriptId) {
        ScriptExecutionRequest request = new ScriptExecutionRequest();
        request.setScriptId(scriptId);
        request.setCommandContext(commandContext);
        return request;
    }

    private BuiltInScriptMetadata createStaticScriptMetadata() {
        BuiltInScriptMetadata metadata = new BuiltInScriptMetadata();
        metadata.setParameters(Collections.emptyList());
        return metadata;
    }

    private BuiltInScriptMetadata createDynamicScriptMetadata() {
        BuiltInScriptMetadata metadata = new BuiltInScriptMetadata();
        ScriptParameter param = new ScriptParameter();
        param.setName("testParam");
        param.setRequired(true);
        metadata.setParameters(List.of(param));
        return metadata;
    }
}