package com.fufu.terminal.service.refactored;

import com.fufu.terminal.controller.dto.*;
import com.fufu.terminal.entity.enums.ScriptType;
import com.fufu.terminal.script.ExecutableScript;
import com.fufu.terminal.script.registry.BuiltinScriptRegistry;
import com.fufu.terminal.script.model.ScriptResult;
import com.fufu.terminal.command.model.SshConnectionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RefactoredScriptExecutionService
 * Tests the consolidated service layer with 4-type script support
 */
@ExtendWith(MockitoExtension.class)
class RefactoredScriptExecutionServiceTest {

    @Mock
    private BuiltinScriptRegistry builtinRegistry;

    @Mock
    private ExecutableScript staticScript;

    @Mock
    private ExecutableScript configurableScript;

    @Mock
    private ExecutableScript interactiveScript;

    private RefactoredScriptExecutionService executionService;

    @BeforeEach
    void setUp() {
        executionService = new RefactoredScriptExecutionService(builtinRegistry);
        
        // Setup script mocks
        setupScriptMock(staticScript, "static-script", "Static Script", ScriptType.STATIC_BUILTIN);
        setupScriptMock(configurableScript, "configurable-script", "Configurable Script", ScriptType.CONFIGURABLE_BUILTIN);
        setupScriptMock(interactiveScript, "interactive-script", "Interactive Script", ScriptType.INTERACTIVE_BUILTIN);
    }

    private void setupScriptMock(ExecutableScript script, String id, String name, ScriptType type) {
        when(script.getId()).thenReturn(id);
        when(script.getName()).thenReturn(name);
        when(script.getDescription()).thenReturn("Test script description");
        when(script.getCategory()).thenReturn("Test");
        when(script.getType()).thenReturn(type);
        when(script.getVersion()).thenReturn("1.0.0");
        when(script.getParameters()).thenReturn(List.of());
        when(script.getRequiredVariables()).thenReturn(Set.of());
        when(script.getOutputVariables()).thenReturn(Set.of());
        when(script.getTags()).thenReturn(Set.of());
        when(script.getEstimatedExecutionTime()).thenReturn(Optional.empty());
        when(script.requiresElevatedPrivileges()).thenReturn(false);
        when(script.getSupportedOperatingSystems()).thenReturn(Set.of());
    }

    @Test
    @DisplayName("Should execute static built-in script successfully")
    void shouldExecuteStaticBuiltInScriptSuccessfully() {
        // Arrange
        String scriptId = "static-script";
        String userId = "test-user";
        ExecutionRequest request = createExecutionRequest();
        
        when(builtinRegistry.getScript(scriptId)).thenReturn(Optional.of(staticScript));
        when(staticScript.executeAsync(any())).thenReturn(CompletableFuture.completedFuture(ScriptResult.success("Execution completed")));
        
        // Act
        String sessionId = executionService.executeScript(scriptId, request, userId);
        
        // Assert
        assertThat(sessionId).isNotNull().isNotBlank();
        
        // Wait a bit for async execution to start
        await().atMost(java.time.Duration.ofSeconds(2)).untilAsserted(() -> {
            ExecutionProgress progress = executionService.getProgress(sessionId, userId);
            assertThat(progress).isNotNull();
            assertThat(progress.getSessionId()).isEqualTo(sessionId);
        });
    }

    @Test
    @DisplayName("Should execute configurable built-in script with intelligent decisions")
    void shouldExecuteConfigurableBuiltInScriptWithIntelligentDecisions() {
        // Arrange
        String scriptId = "configurable-script";
        String userId = "test-user";
        ExecutionRequest request = createExecutionRequest();
        request.setParameters(Map.of("region", "china", "mirror_type", "docker"));
        
        when(builtinRegistry.getScript(scriptId)).thenReturn(Optional.of(configurableScript));
        when(configurableScript.executeAsync(any())).thenReturn(CompletableFuture.completedFuture(ScriptResult.success("Configured with China mirrors")));
        
        // Act
        String sessionId = executionService.executeScript(scriptId, request, userId);
        
        // Assert
        assertThat(sessionId).isNotNull();
        
        await().atMost(java.time.Duration.ofSeconds(2)).untilAsserted(() -> {
            ExecutionProgress progress = executionService.getProgress(sessionId, userId);
            assertThat(progress).isNotNull();
            assertThat(progress.getCurrentStep()).contains("script");
        });
    }

    @Test
    @DisplayName("Should execute interactive built-in script with user interaction support")
    void shouldExecuteInteractiveBuiltInScriptWithUserInteractionSupport() {
        // Arrange
        String scriptId = "interactive-script";
        String userId = "test-user";
        ExecutionRequest request = createExecutionRequest();
        
        when(builtinRegistry.getScript(scriptId)).thenReturn(Optional.of(interactiveScript));
        when(interactiveScript.supportsInteraction()).thenReturn(true);
        when(interactiveScript.executeAsync(any())).thenReturn(CompletableFuture.completedFuture(ScriptResult.success("Interactive execution completed")));
        
        // Act
        String sessionId = executionService.executeScript(scriptId, request, userId);
        
        // Assert
        assertThat(sessionId).isNotNull();
        
        await().atMost(java.time.Duration.ofSeconds(2)).untilAsserted(() -> {
            ExecutionProgress progress = executionService.getProgress(sessionId, userId);
            assertThat(progress).isNotNull();
        });
    }

    @Test
    @DisplayName("Should handle script not found")
    void shouldHandleScriptNotFound() {
        // Arrange
        String scriptId = "non-existent-script";
        String userId = "test-user";
        ExecutionRequest request = createExecutionRequest();
        
        when(builtinRegistry.getScript(scriptId)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThatThrownBy(() -> executionService.executeScript(scriptId, request, userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Script not found");
    }

    @Test
    @DisplayName("Should validate script execution request")
    void shouldValidateScriptExecutionRequest() {
        // Arrange
        String scriptId = "static-script";
        String userId = "test-user";
        ExecutionRequest request = createExecutionRequest();
        
        when(builtinRegistry.getScript(scriptId)).thenReturn(Optional.of(staticScript));
        
        // Act
        ValidationResponse validation = executionService.validateScriptExecution(scriptId, request, userId);
        
        // Assert
        assertThat(validation.isValid()).isTrue();
        assertThat(validation.getMessage()).isEqualTo("Validation passed");
    }

    @Test
    @DisplayName("Should reject invalid SSH configuration")
    void shouldRejectInvalidSshConfiguration() {
        // Arrange
        String scriptId = "static-script";
        String userId = "test-user";
        ExecutionRequest request = new ExecutionRequest();
        request.setSshConfig(null); // Invalid SSH config
        
        when(builtinRegistry.getScript(scriptId)).thenReturn(Optional.of(staticScript));
        
        // Act
        ValidationResponse validation = executionService.validateScriptExecution(scriptId, request, userId);
        
        // Assert
        assertThat(validation.isValid()).isFalse();
        assertThat(validation.getMessage()).contains("Invalid SSH configuration");
        assertThat(validation.getSuggestions()).isNotEmpty();
    }

    @Test
    @DisplayName("Should get scripts by type")
    void shouldGetScriptsByType() {
        // Arrange
        String userId = "test-user";
        when(builtinRegistry.getScriptsByType(ScriptType.STATIC_BUILTIN))
            .thenReturn(List.of(staticScript));
        
        // Act
        List<ScriptInfo> scripts = executionService.getScriptsByType(ScriptType.STATIC_BUILTIN, null, userId);
        
        // Assert
        assertThat(scripts).hasSize(1);
        assertThat(scripts.get(0).getId()).isEqualTo("static-script");
        assertThat(scripts.get(0).getType()).isEqualTo(ScriptType.STATIC_BUILTIN);
    }

    @Test
    @DisplayName("Should get scripts by category")
    void shouldGetScriptsByCategory() {
        // Arrange
        String userId = "test-user";
        String category = "System";
        when(builtinRegistry.getScriptsByCategory(category))
            .thenReturn(List.of(staticScript, configurableScript));
        
        // Act
        List<ScriptInfo> scripts = executionService.getScriptsByType(ScriptType.STATIC_BUILTIN, category, userId);
        
        // Assert
        assertThat(scripts).hasSize(2);
        verify(builtinRegistry).getScriptsByCategory(category);
    }

    @Test
    @DisplayName("Should search scripts")
    void shouldSearchScripts() {
        // Arrange
        String userId = "test-user";
        String searchTerm = "static";
        when(builtinRegistry.searchScripts(searchTerm))
            .thenReturn(List.of(staticScript));
        
        // Act
        List<ScriptInfo> scripts = executionService.getAllScripts(null, searchTerm, userId);
        
        // Assert
        assertThat(scripts).hasSize(1);
        assertThat(scripts.get(0).getId()).isEqualTo("static-script");
        verify(builtinRegistry).searchScripts(searchTerm);
    }

    @Test
    @DisplayName("Should get script details")
    void shouldGetScriptDetails() {
        // Arrange
        String scriptId = "static-script";
        String userId = "test-user";
        when(builtinRegistry.getScript(scriptId)).thenReturn(Optional.of(staticScript));
        
        // Act
        ScriptInfo scriptInfo = executionService.getScriptDetails(scriptId, userId);
        
        // Assert
        assertThat(scriptInfo).isNotNull();
        assertThat(scriptInfo.getId()).isEqualTo(scriptId);
        assertThat(scriptInfo.getName()).isEqualTo("Static Script");
        assertThat(scriptInfo.getType()).isEqualTo(ScriptType.STATIC_BUILTIN);
    }

    @Test
    @DisplayName("Should cancel script execution")
    void shouldCancelScriptExecution() {
        // Arrange
        String scriptId = "static-script";
        String userId = "test-user";
        ExecutionRequest request = createExecutionRequest();
        
        when(builtinRegistry.getScript(scriptId)).thenReturn(Optional.of(staticScript));
        when(staticScript.executeAsync(any())).thenReturn(new CompletableFuture<>()); // Never completing future
        
        String sessionId = executionService.executeScript(scriptId, request, userId);
        
        // Act
        executionService.cancelExecution(sessionId, userId);
        
        // Assert
        ExecutionProgress progress = executionService.getProgress(sessionId, userId);
        assertThat(progress.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Should handle user interaction")
    void shouldHandleUserInteraction() {
        // Arrange
        String sessionId = "test-session";
        String userId = "test-user";
        InteractionRequest interactionRequest = new InteractionRequest();
        interactionRequest.setType("USER_INPUT");
        interactionRequest.setResponse("user response");
        
        // Act
        InteractionResponse response = executionService.handleUserInteraction(sessionId, interactionRequest, userId);
        
        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    @DisplayName("Should track active execution count")
    void shouldTrackActiveExecutionCount() {
        // Arrange
        String scriptId = "static-script";
        String userId = "test-user";
        ExecutionRequest request = createExecutionRequest();
        
        when(builtinRegistry.getScript(scriptId)).thenReturn(Optional.of(staticScript));
        when(staticScript.executeAsync(any())).thenReturn(new CompletableFuture<>()); // Never completing future
        
        int initialCount = executionService.getActiveExecutionCount();
        
        // Act
        executionService.executeScript(scriptId, request, userId);
        
        // Assert
        assertThat(executionService.getActiveExecutionCount()).isEqualTo(initialCount + 1);
    }

    @Test
    @DisplayName("Should handle execution failure gracefully")
    void shouldHandleExecutionFailureGracefully() {
        // Arrange
        String scriptId = "static-script";
        String userId = "test-user";
        ExecutionRequest request = createExecutionRequest();
        
        when(builtinRegistry.getScript(scriptId)).thenReturn(Optional.of(staticScript));
        CompletableFuture<ScriptResult> failedFuture = CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("Script execution failed");
        });
        when(staticScript.executeAsync(any())).thenReturn(failedFuture);
        
        // Act
        String sessionId = executionService.executeScript(scriptId, request, userId);
        
        // Assert
        await().atMost(java.time.Duration.ofSeconds(3)).untilAsserted(() -> {
            ExecutionProgress progress = executionService.getProgress(sessionId, userId);
            assertThat(progress.getStatus()).isEqualTo("FAILED");
            assertThat(progress.getErrorMessage()).contains("Script execution failed");
        });
    }

    private ExecutionRequest createExecutionRequest() {
        ExecutionRequest request = new ExecutionRequest();
        SshConnectionConfig sshConfig = new SshConnectionConfig();
        sshConfig.setHost("test-host");
        sshConfig.setUsername("test-user");
        sshConfig.setPassword("test-password");
        sshConfig.setPort(22);
        request.setSshConfig(sshConfig);
        return request;
    }

    // Helper method for async assertions
    private org.awaitility.core.ConditionFactory await() {
        return org.awaitility.Awaitility.await();
    }
}