package com.fufu.terminal.script.registry;

import com.fufu.terminal.entity.enums.ScriptType;
import com.fufu.terminal.script.ExecutableScript;
import com.fufu.terminal.script.builtin.static_scripts.ServerLocationDetectionScript;
import com.fufu.terminal.script.builtin.static_scripts.SystemInfoCollectionScript;
import com.fufu.terminal.script.builtin.configurable.DockerInstallationScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BuiltinScriptRegistry
 * Tests the enhanced built-in script registry with 4-type classification support
 */
@ExtendWith(MockitoExtension.class)
class BuiltinScriptRegistryTest {

    @Mock
    private ApplicationContext applicationContext;
    
    @Mock
    private ServerLocationDetectionScript locationScript;
    
    @Mock
    private SystemInfoCollectionScript systemInfoScript;
    
    @Mock
    private DockerInstallationScript dockerScript;

    private BuiltinScriptRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BuiltinScriptRegistry();
        
        // Set up the mocked application context
        when(applicationContext.getBean(ServerLocationDetectionScript.class)).thenReturn(locationScript);
        when(applicationContext.getBean(SystemInfoCollectionScript.class)).thenReturn(systemInfoScript);
        when(applicationContext.getBean(DockerInstallationScript.class)).thenReturn(dockerScript);
        
        // Set up script mocks
        setupScriptMock(locationScript, "location-detection", "Server Location Detection", 
                       "Detects server geographic location", "System", ScriptType.STATIC_BUILTIN);
        setupScriptMock(systemInfoScript, "system-info", "System Information", 
                       "Collects system information", "System", ScriptType.STATIC_BUILTIN);
        setupScriptMock(dockerScript, "docker-install", "Docker Installation", 
                       "Installs Docker with geographic-aware mirror selection", "Development", ScriptType.CONFIGURABLE_BUILTIN);
        
        // Use reflection to set the applicationContext field
        try {
            var field = BuiltinScriptRegistry.class.getDeclaredField("applicationContext");
            field.setAccessible(true);
            field.set(registry, applicationContext);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set applicationContext", e);
        }
    }

    private void setupScriptMock(ExecutableScript script, String id, String name, String description, 
                                String category, ScriptType type) {
        when(script.getId()).thenReturn(id);
        when(script.getName()).thenReturn(name);
        when(script.getDescription()).thenReturn(description);
        when(script.getCategory()).thenReturn(category);
        when(script.getType()).thenReturn(type);
        when(script.getTags()).thenReturn(Set.of());
    }

    @Test
    @DisplayName("Should initialize and register all built-in scripts")
    void shouldInitializeAndRegisterAllBuiltinScripts() {
        // Act
        registry.initializeBuiltinScripts();
        
        // Assert
        assertThat(registry.getAllScripts()).hasSize(3);
        assertThat(registry.hasScript("location-detection")).isTrue();
        assertThat(registry.hasScript("system-info")).isTrue();
        assertThat(registry.hasScript("docker-install")).isTrue();
    }

    @Test
    @DisplayName("Should register script with correct type classification")
    void shouldRegisterScriptWithCorrectTypeClassification() {
        // Act
        registry.initializeBuiltinScripts();
        
        // Assert
        List<ExecutableScript> staticScripts = registry.getScriptsByType(ScriptType.STATIC_BUILTIN);
        List<ExecutableScript> configurableScripts = registry.getScriptsByType(ScriptType.CONFIGURABLE_BUILTIN);
        
        assertThat(staticScripts).hasSize(2);
        assertThat(configurableScripts).hasSize(1);
        
        assertThat(staticScripts).extracting(ExecutableScript::getId)
            .containsExactlyInAnyOrder("location-detection", "system-info");
        assertThat(configurableScripts).extracting(ExecutableScript::getId)
            .containsExactly("docker-install");
    }

    @Test
    @DisplayName("Should get script by ID")
    void shouldGetScriptById() {
        // Arrange
        registry.initializeBuiltinScripts();
        
        // Act
        Optional<ExecutableScript> script = registry.getScript("system-info");
        
        // Assert
        assertThat(script).isPresent();
        assertThat(script.get().getId()).isEqualTo("system-info");
        assertThat(script.get().getName()).isEqualTo("System Information");
    }

    @Test
    @DisplayName("Should return empty when script not found")
    void shouldReturnEmptyWhenScriptNotFound() {
        // Arrange
        registry.initializeBuiltinScripts();
        
        // Act
        Optional<ExecutableScript> script = registry.getScript("non-existent");
        
        // Assert
        assertThat(script).isEmpty();
    }

    @Test
    @DisplayName("Should get scripts by category")
    void shouldGetScriptsByCategory() {
        // Arrange
        registry.initializeBuiltinScripts();
        
        // Act
        List<ExecutableScript> systemScripts = registry.getScriptsByCategory("System");
        List<ExecutableScript> devScripts = registry.getScriptsByCategory("Development");
        
        // Assert
        assertThat(systemScripts).hasSize(2);
        assertThat(devScripts).hasSize(1);
        
        assertThat(systemScripts).extracting(ExecutableScript::getId)
            .containsExactlyInAnyOrder("location-detection", "system-info");
        assertThat(devScripts).extracting(ExecutableScript::getId)
            .containsExactly("docker-install");
    }

    @Test
    @DisplayName("Should search scripts by name and description")
    void shouldSearchScriptsByNameAndDescription() {
        // Arrange
        registry.initializeBuiltinScripts();
        
        // Act
        List<ExecutableScript> dockerResults = registry.searchScripts("docker");
        List<ExecutableScript> systemResults = registry.searchScripts("system");
        List<ExecutableScript> installResults = registry.searchScripts("install");
        
        // Assert
        assertThat(dockerResults).hasSize(1);
        assertThat(dockerResults.get(0).getId()).isEqualTo("docker-install");
        
        assertThat(systemResults).hasSize(1);
        assertThat(systemResults.get(0).getId()).isEqualTo("system-info");
        
        assertThat(installResults).hasSize(1);
        assertThat(installResults.get(0).getId()).isEqualTo("docker-install");
    }

    @Test
    @DisplayName("Should return all scripts when search query is empty")
    void shouldReturnAllScriptsWhenSearchQueryIsEmpty() {
        // Arrange
        registry.initializeBuiltinScripts();
        
        // Act
        List<ExecutableScript> results = registry.searchScripts("");
        
        // Assert
        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("Should get scripts by tags")
    void shouldGetScriptsByTags() {
        // Arrange
        when(systemInfoScript.getTags()).thenReturn(Set.of("system", "monitoring"));
        when(dockerScript.getTags()).thenReturn(Set.of("docker", "installation"));
        
        registry.initializeBuiltinScripts();
        
        // Act
        List<ExecutableScript> systemTagged = registry.getScriptsByTags(Set.of("system"));
        List<ExecutableScript> dockerTagged = registry.getScriptsByTags(Set.of("docker"));
        
        // Assert
        assertThat(systemTagged).hasSize(1);
        assertThat(systemTagged.get(0).getId()).isEqualTo("system-info");
        
        assertThat(dockerTagged).hasSize(1);
        assertThat(dockerTagged.get(0).getId()).isEqualTo("docker-install");
    }

    @Test
    @DisplayName("Should reject non-built-in script types")
    void shouldRejectNonBuiltInScriptTypes() {
        // Arrange
        ExecutableScript userScript = mock(ExecutableScript.class);
        when(userScript.getType()).thenReturn(ScriptType.USER_SCRIPT);
        
        // Act & Assert
        assertThatThrownBy(() -> registry.registerScript(userScript))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Only built-in script types can be registered");
    }

    @Test
    @DisplayName("Should get script count by type")
    void shouldGetScriptCountByType() {
        // Arrange
        registry.initializeBuiltinScripts();
        
        // Act
        Map<ScriptType, Integer> counts = registry.getScriptCountByType();
        
        // Assert
        assertThat(counts).containsEntry(ScriptType.STATIC_BUILTIN, 2);
        assertThat(counts).containsEntry(ScriptType.CONFIGURABLE_BUILTIN, 1);
        assertThat(counts).containsEntry(ScriptType.INTERACTIVE_BUILTIN, 0);
    }

    @Test
    @DisplayName("Should get all categories")
    void shouldGetAllCategories() {
        // Arrange
        registry.initializeBuiltinScripts();
        
        // Act
        Set<String> categories = registry.getAllCategories();
        
        // Assert
        assertThat(categories).containsExactlyInAnyOrder("System", "Development");
    }

    @Test
    @DisplayName("Should get registry statistics")
    void shouldGetRegistryStatistics() {
        // Arrange
        registry.initializeBuiltinScripts();
        
        // Act
        BuiltinScriptRegistry.RegistryStats stats = registry.getStats();
        
        // Assert
        assertThat(stats.getTotalScripts()).isEqualTo(3);
        assertThat(stats.getStaticScripts()).isEqualTo(2);
        assertThat(stats.getConfigurableScripts()).isEqualTo(1);
        assertThat(stats.getInteractiveScripts()).isEqualTo(0);
        assertThat(stats.getCategories()).containsExactlyInAnyOrder("System", "Development");
    }

    @Test
    @DisplayName("Should return empty list for non-built-in script type query")
    void shouldReturnEmptyListForNonBuiltInScriptTypeQuery() {
        // Arrange
        registry.initializeBuiltinScripts();
        
        // Act
        List<ExecutableScript> userScripts = registry.getScriptsByType(ScriptType.USER_SCRIPT);
        
        // Assert
        assertThat(userScripts).isEmpty();
    }

    @Test
    @DisplayName("Should check script existence correctly")
    void shouldCheckScriptExistenceCorrectly() {
        // Arrange
        registry.initializeBuiltinScripts();
        
        // Act & Assert
        assertThat(registry.hasScript("system-info")).isTrue();
        assertThat(registry.hasScript("docker-install")).isTrue();
        assertThat(registry.hasScript("non-existent")).isFalse();
    }

    @Test
    @DisplayName("Should return scripts sorted by name")
    void shouldReturnScriptsSortedByName() {
        // Arrange
        registry.initializeBuiltinScripts();
        
        // Act
        List<ExecutableScript> allScripts = registry.getAllScripts();
        
        // Assert
        assertThat(allScripts).extracting(ExecutableScript::getName)
            .isSorted();
    }
}