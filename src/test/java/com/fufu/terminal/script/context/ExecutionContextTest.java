package com.fufu.terminal.script.context;

import com.fufu.terminal.entity.enums.ScriptType;
import com.fufu.terminal.model.SshConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExecutionContext
 * Tests variable scoping system (SCRIPT > SESSION > GLOBAL)
 */
@ExtendWith(MockitoExtension.class)
class ExecutionContextTest {

    @Mock
    private SshConnection sshConnection;

    private ExecutionContext executionContext;
    private String sessionId;
    private String userId;

    @BeforeEach
    void setUp() {
        sessionId = "test-session-123";
        userId = "test-user";
        executionContext = new ExecutionContext(sessionId, userId, sshConnection);
    }

    @Test
    @DisplayName("Should initialize with correct session information")
    void shouldInitializeWithCorrectSessionInformation() {
        assertThat(executionContext.getSessionId()).isEqualTo(sessionId);
        assertThat(executionContext.getUserId()).isEqualTo(userId);
        assertThat(executionContext.getSshConnection()).isEqualTo(sshConnection);
        assertThat(executionContext.getStartTime()).isNotNull();
    }

    @Test
    @DisplayName("Should handle script-scoped variables")
    void shouldHandleScriptScopedVariables() {
        // Arrange
        String scriptId = "test-script";
        
        // Act
        executionContext.setScriptVariable(scriptId, "script_var", "script_value");
        
        // Assert
        assertThat(executionContext.getScriptVariable(scriptId, "script_var"))
            .isPresent()
            .hasValue("script_value");
        
        assertThat(executionContext.getScriptVariable("other-script", "script_var"))
            .isEmpty();
    }

    @Test
    @DisplayName("Should handle session-scoped variables")
    void shouldHandleSessionScopedVariables() {
        // Act
        executionContext.setSessionVariable("session_var", "session_value");
        
        // Assert
        assertThat(executionContext.getSessionVariable("session_var"))
            .isPresent()
            .hasValue("session_value");
        
        assertThat(executionContext.getSessionVariable("non_existent"))
            .isEmpty();
    }

    @Test
    @DisplayName("Should handle global variables")
    void shouldHandleGlobalVariables() {
        // Act
        executionContext.setGlobalVariable("global_var", "global_value");
        
        // Assert
        assertThat(executionContext.getGlobalVariable("global_var"))
            .isPresent()
            .hasValue("global_value");
        
        assertThat(executionContext.getGlobalVariable("non_existent"))
            .isEmpty();
    }

    @Test
    @DisplayName("Should implement variable scoping hierarchy: SCRIPT > SESSION > GLOBAL")
    void shouldImplementVariableScopingHierarchy() {
        // Arrange
        String varName = "test_var";
        String scriptId = "test-script";
        
        executionContext.setGlobalVariable(varName, "global_value");
        executionContext.setSessionVariable(varName, "session_value");
        executionContext.setScriptVariable(scriptId, varName, "script_value");
        
        // Act
        Optional<Object> result = executionContext.getVariable(scriptId, varName);
        
        // Assert - Script variable should take precedence
        assertThat(result).isPresent().hasValue("script_value");
    }

    @Test
    @DisplayName("Should fall back to session variable when script variable not found")
    void shouldFallBackToSessionVariableWhenScriptVariableNotFound() {
        // Arrange
        String varName = "test_var";
        String scriptId = "test-script";
        
        executionContext.setGlobalVariable(varName, "global_value");
        executionContext.setSessionVariable(varName, "session_value");
        
        // Act
        Optional<Object> result = executionContext.getVariable(scriptId, varName);
        
        // Assert - Session variable should be returned
        assertThat(result).isPresent().hasValue("session_value");
    }

    @Test
    @DisplayName("Should fall back to global variable when script and session variables not found")
    void shouldFallBackToGlobalVariableWhenScriptAndSessionVariablesNotFound() {
        // Arrange
        String varName = "test_var";
        String scriptId = "test-script";
        
        executionContext.setGlobalVariable(varName, "global_value");
        
        // Act
        Optional<Object> result = executionContext.getVariable(scriptId, varName);
        
        // Assert - Global variable should be returned
        assertThat(result).isPresent().hasValue("global_value");
    }

    @Test
    @DisplayName("Should return empty when variable not found in any scope")
    void shouldReturnEmptyWhenVariableNotFoundInAnyScope() {
        // Act
        Optional<Object> result = executionContext.getVariable("test-script", "non_existent_var");
        
        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should handle different variable types")
    void shouldHandleDifferentVariableTypes() {
        // Arrange
        String scriptId = "test-script";
        
        // Act
        executionContext.setScriptVariable(scriptId, "string_var", "string_value");
        executionContext.setScriptVariable(scriptId, "int_var", 42);
        executionContext.setScriptVariable(scriptId, "boolean_var", true);
        executionContext.setScriptVariable(scriptId, "map_var", Map.of("key", "value"));
        
        // Assert
        assertThat(executionContext.getScriptVariable(scriptId, "string_var"))
            .isPresent().hasValue("string_value");
        assertThat(executionContext.getScriptVariable(scriptId, "int_var"))
            .isPresent().hasValue(42);
        assertThat(executionContext.getScriptVariable(scriptId, "boolean_var"))
            .isPresent().hasValue(true);
        assertThat(executionContext.getScriptVariable(scriptId, "map_var"))
            .isPresent().hasValue(Map.of("key", "value"));
    }

    @Test
    @DisplayName("Should provide type-safe variable getters")
    void shouldProvideTypeSafeVariableGetters() {
        // Arrange
        String scriptId = "test-script";
        executionContext.setScriptVariable(scriptId, "string_var", "test_string");
        executionContext.setScriptVariable(scriptId, "int_var", 123);
        executionContext.setScriptVariable(scriptId, "boolean_var", false);
        
        // Act & Assert
        assertThat(executionContext.getVariableAsString(scriptId, "string_var"))
            .isPresent().hasValue("test_string");
        
        assertThat(executionContext.getVariableAsInteger(scriptId, "int_var"))
            .isPresent().hasValue(123);
        
        assertThat(executionContext.getVariableAsBoolean(scriptId, "boolean_var"))
            .isPresent().hasValue(false);
    }

    @Test
    @DisplayName("Should handle type conversion errors gracefully")
    void shouldHandleTypeConversionErrorsGracefully() {
        // Arrange
        String scriptId = "test-script";
        executionContext.setScriptVariable(scriptId, "string_var", "not_a_number");
        
        // Act & Assert
        assertThat(executionContext.getVariableAsInteger(scriptId, "string_var"))
            .isEmpty();
    }

    @Test
    @DisplayName("Should clear script variables")
    void shouldClearScriptVariables() {
        // Arrange
        String scriptId = "test-script";
        executionContext.setScriptVariable(scriptId, "var1", "value1");
        executionContext.setScriptVariable(scriptId, "var2", "value2");
        
        // Act
        executionContext.clearScriptVariables(scriptId);
        
        // Assert
        assertThat(executionContext.getScriptVariable(scriptId, "var1")).isEmpty();
        assertThat(executionContext.getScriptVariable(scriptId, "var2")).isEmpty();
    }

    @Test
    @DisplayName("Should get all variables for a script")
    void shouldGetAllVariablesForScript() {
        // Arrange
        String scriptId = "test-script";
        executionContext.setGlobalVariable("global_var", "global_value");
        executionContext.setSessionVariable("session_var", "session_value");
        executionContext.setScriptVariable(scriptId, "script_var", "script_value");
        executionContext.setScriptVariable(scriptId, "override_var", "script_override");
        executionContext.setSessionVariable("override_var", "session_override");
        
        // Act
        Map<String, Object> allVariables = executionContext.getAllVariables(scriptId);
        
        // Assert
        assertThat(allVariables).containsEntry("global_var", "global_value");
        assertThat(allVariables).containsEntry("session_var", "session_value");
        assertThat(allVariables).containsEntry("script_var", "script_value");
        // Script variable should override session variable
        assertThat(allVariables).containsEntry("override_var", "script_override");
    }

    @Test
    @DisplayName("Should track execution metrics")
    void shouldTrackExecutionMetrics() {
        // Act
        executionContext.incrementStepsCompleted();
        executionContext.incrementStepsCompleted();
        executionContext.setTotalSteps(5);
        
        // Assert
        assertThat(executionContext.getStepsCompleted()).isEqualTo(2);
        assertThat(executionContext.getTotalSteps()).isEqualTo(5);
        assertThat(executionContext.getProgressPercentage()).isEqualTo(40);
    }

    @Test
    @DisplayName("Should calculate elapsed time")
    void shouldCalculateElapsedTime() throws InterruptedException {
        // Arrange
        long startTime = executionContext.getStartTime();
        
        // Act
        Thread.sleep(10); // Small delay for measurable elapsed time
        long elapsedTime = executionContext.getElapsedTime();
        
        // Assert
        assertThat(elapsedTime).isGreaterThan(0);
        assertThat(elapsedTime).isEqualTo(System.currentTimeMillis() - startTime);
    }

    @Test
    @DisplayName("Should handle execution parameters")
    void shouldHandleExecutionParameters() {
        // Arrange
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("param1", "value1");
        parameters.put("param2", 42);
        
        // Act
        executionContext.setExecutionParameters(parameters);
        
        // Assert
        assertThat(executionContext.getExecutionParameters()).isEqualTo(parameters);
        assertThat(executionContext.getExecutionParameter("param1"))
            .isPresent().hasValue("value1");
        assertThat(executionContext.getExecutionParameter("param2"))
            .isPresent().hasValue(42);
        assertThat(executionContext.getExecutionParameter("param3"))
            .isEmpty();
    }

    @Test
    @DisplayName("Should maintain script execution history")
    void shouldMaintainScriptExecutionHistory() {
        // Act
        executionContext.addToExecutionHistory("script1", ScriptType.STATIC_BUILTIN, true, "Success");
        executionContext.addToExecutionHistory("script2", ScriptType.CONFIGURABLE_BUILTIN, false, "Failed");
        
        // Assert
        var history = executionContext.getExecutionHistory();
        assertThat(history).hasSize(2);
        
        var firstEntry = history.get(0);
        assertThat(firstEntry.getScriptId()).isEqualTo("script1");
        assertThat(firstEntry.getScriptType()).isEqualTo(ScriptType.STATIC_BUILTIN);
        assertThat(firstEntry.isSuccess()).isTrue();
        assertThat(firstEntry.getMessage()).isEqualTo("Success");
        
        var secondEntry = history.get(1);
        assertThat(secondEntry.getScriptId()).isEqualTo("script2");
        assertThat(secondEntry.getScriptType()).isEqualTo(ScriptType.CONFIGURABLE_BUILTIN);
        assertThat(secondEntry.isSuccess()).isFalse();
        assertThat(secondEntry.getMessage()).isEqualTo("Failed");
    }

    @Test
    @DisplayName("Should handle context cleanup")
    void shouldHandleContextCleanup() {
        // Arrange
        String scriptId = "test-script";
        executionContext.setScriptVariable(scriptId, "var1", "value1");
        executionContext.setSessionVariable("session_var", "session_value");
        executionContext.setGlobalVariable("global_var", "global_value");
        
        // Act
        executionContext.cleanup();
        
        // Assert
        verify(sshConnection).close(); // Verify SSH connection is closed
        assertThat(executionContext.getScriptVariable(scriptId, "var1")).isEmpty();
        assertThat(executionContext.getSessionVariable("session_var")).isEmpty();
        // Global variables should persist after cleanup
        assertThat(executionContext.getGlobalVariable("global_var")).isPresent();
    }
}