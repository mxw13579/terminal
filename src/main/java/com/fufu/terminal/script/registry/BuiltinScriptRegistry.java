package com.fufu.terminal.script.registry;

import com.fufu.terminal.entity.enums.ScriptType;
import com.fufu.terminal.script.ExecutableScript;
import com.fufu.terminal.script.builtin.static_scripts.ServerLocationDetectionScript;
import com.fufu.terminal.script.builtin.static_scripts.SystemInfoCollectionScript;
import com.fufu.terminal.script.builtin.configurable.DockerInstallationScript;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enhanced Built-in Script Registry
 * Manages all built-in scripts (Static, Configurable, and Interactive) in code
 * Aligns with simplified architecture principle: built-in scripts are code-managed, not database-stored
 */
@Component
@Slf4j
public class BuiltinScriptRegistry {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    private final Map<String, ExecutableScript> staticScripts = new ConcurrentHashMap<>();
    private final Map<String, ExecutableScript> configurableScripts = new ConcurrentHashMap<>();
    private final Map<String, ExecutableScript> interactiveScripts = new ConcurrentHashMap<>();
    private final Map<String, ExecutableScript> allScripts = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initializeBuiltinScripts() {
        log.info("Initializing built-in script registry...");
        
        // Register static scripts (no parameters, immediate execution)
        registerScript(applicationContext.getBean(ServerLocationDetectionScript.class));
        registerScript(applicationContext.getBean(SystemInfoCollectionScript.class));
        // registerScript(new NetworkConnectivityTestScript());
        // registerScript(new SecurityStatusCheckScript());
        
        // Register configurable scripts (parameters with intelligent decisions)
        registerScript(applicationContext.getBean(DockerInstallationScript.class));
        // registerScript(new MirrorConfigurationScript());
        // registerScript(new NodeInstallationScript());
        // registerScript(new DevelopmentEnvironmentSetupScript());
        
        // Register interactive scripts (real-time user interaction)
        // registerScript(new CustomSoftwareInstallationScript());
        // registerScript(new SystemConfigurationWizardScript());
        // registerScript(new DatabaseSetupWizardScript());
        
        log.info("Registered {} built-in scripts", allScripts.size());
    }
    
    /**
     * Register a built-in script with type validation
     */
    public void registerScript(ExecutableScript script) {
        if (!script.getType().isBuiltIn()) {
            throw new IllegalArgumentException("Only built-in script types can be registered: " + script.getType());
        }
        
        allScripts.put(script.getId(), script);
        
        switch (script.getType()) {
            case STATIC_BUILTIN:
                staticScripts.put(script.getId(), script);
                break;
            case CONFIGURABLE_BUILTIN:
                configurableScripts.put(script.getId(), script);
                break;
            case INTERACTIVE_BUILTIN:
                interactiveScripts.put(script.getId(), script);
                break;
            default:
                log.warn("Unexpected script type for built-in script: {}", script.getType());
        }
        
        log.debug("Registered built-in script: {} ({}) - {}", 
                script.getId(), script.getType(), script.getName());
    }
    
    /**
     * Get script by ID
     */
    public Optional<ExecutableScript> getScript(String scriptId) {
        return Optional.ofNullable(allScripts.get(scriptId));
    }
    
    /**
     * Get all scripts of a specific type
     */
    public List<ExecutableScript> getScriptsByType(ScriptType type) {
        if (!type.isBuiltIn()) {
            return Collections.emptyList();
        }
        
        return allScripts.values().stream()
            .filter(script -> script.getType() == type)
            .sorted(Comparator.comparing(ExecutableScript::getName))
            .collect(Collectors.toList());
    }
    
    /**
     * Get scripts by category
     */
    public List<ExecutableScript> getScriptsByCategory(String category) {
        return allScripts.values().stream()
            .filter(script -> category.equals(script.getCategory()))
            .sorted(Comparator.comparing(ExecutableScript::getName))
            .collect(Collectors.toList());
    }
    
    /**
     * Get all built-in scripts
     */
    public List<ExecutableScript> getAllScripts() {
        return allScripts.values().stream()
            .sorted(Comparator.comparing(ExecutableScript::getName))
            .collect(Collectors.toList());
    }
    
    /**
     * Search scripts by name or description
     */
    public List<ExecutableScript> searchScripts(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllScripts();
        }
        
        String lowercaseQuery = query.toLowerCase();
        return allScripts.values().stream()
            .filter(script -> 
                script.getName().toLowerCase().contains(lowercaseQuery) ||
                script.getDescription().toLowerCase().contains(lowercaseQuery) ||
                script.getCategory().toLowerCase().contains(lowercaseQuery))
            .sorted(Comparator.comparing(ExecutableScript::getName))
            .collect(Collectors.toList());
    }
    
    /**
     * Get scripts by tags
     */
    public List<ExecutableScript> getScriptsByTags(Set<String> tags) {
        return allScripts.values().stream()
            .filter(script -> !Collections.disjoint(script.getTags(), tags))
            .sorted(Comparator.comparing(ExecutableScript::getName))
            .collect(Collectors.toList());
    }
    
    /**
     * Check if script exists
     */
    public boolean hasScript(String scriptId) {
        return allScripts.containsKey(scriptId);
    }
    
    /**
     * Get script count by type
     */
    public Map<ScriptType, Integer> getScriptCountByType() {
        Map<ScriptType, Integer> counts = new HashMap<>();
        counts.put(ScriptType.STATIC_BUILTIN, staticScripts.size());
        counts.put(ScriptType.CONFIGURABLE_BUILTIN, configurableScripts.size());
        counts.put(ScriptType.INTERACTIVE_BUILTIN, interactiveScripts.size());
        return counts;
    }
    
    /**
     * Get all categories
     */
    public Set<String> getAllCategories() {
        return allScripts.values().stream()
            .map(ExecutableScript::getCategory)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }
    
    /**
     * Get registry statistics
     */
    public RegistryStats getStats() {
        return RegistryStats.builder()
            .totalScripts(allScripts.size())
            .staticScripts(staticScripts.size())
            .configurableScripts(configurableScripts.size())
            .interactiveScripts(interactiveScripts.size())
            .categories(getAllCategories())
            .build();
    }
    
    /**
     * Registry statistics data class
     */
    @lombok.Data
    @lombok.Builder
    public static class RegistryStats {
        private int totalScripts;
        private int staticScripts;
        private int configurableScripts;
        private int interactiveScripts;
        private Set<String> categories;
    }
}