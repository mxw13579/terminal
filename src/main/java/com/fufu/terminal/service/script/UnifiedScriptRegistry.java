package com.fufu.terminal.service.script;

import com.fufu.terminal.command.Command;
import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.service.script.adapter.BuiltInScriptAdapter;
import com.fufu.terminal.service.script.adapter.ConfigurableScriptAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 统一脚本注册表
 * 管理内置脚本和可配置脚本的注册与查找
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedScriptRegistry {
    
    // 所有脚本的注册表
    private final Map<String, UnifiedAtomicScript> scriptRegistry = new ConcurrentHashMap<>();
    
    // 按类型分组的脚本
    private final Map<UnifiedAtomicScript.ScriptType, List<UnifiedAtomicScript>> scriptsByType = new ConcurrentHashMap<>();
    
    // 按标签分组的脚本
    private final Map<String, List<UnifiedAtomicScript>> scriptsByTag = new ConcurrentHashMap<>();
    
    /**
     * 注册内置脚本
     */
    public void registerBuiltInScript(Command command, String scriptId, String[] tags) {
        BuiltInScriptAdapter adapter = new BuiltInScriptAdapter(command, scriptId, tags);
        registerScript(adapter);
        log.debug("注册内置脚本: {} -> {}", scriptId, adapter.getName());
    }
    
    /**
     * 注册可配置脚本
     */
    public void registerConfigurableScript(AtomicScript atomicScript) {
        ConfigurableScriptAdapter adapter = new ConfigurableScriptAdapter(atomicScript);
        registerScript(adapter);
        log.debug("注册可配置脚本: {} -> {}", adapter.getScriptId(), adapter.getName());
    }
    
    /**
     * 注册脚本到注册表
     */
    private void registerScript(UnifiedAtomicScript script) {
        // 添加到主注册表
        scriptRegistry.put(script.getScriptId(), script);
        
        // 按类型分组
        scriptsByType.computeIfAbsent(script.getScriptType(), k -> new ArrayList<>()).add(script);
        
        // 按标签分组
        for (String tag : script.getTags()) {
            scriptsByTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(script);
        }
    }
    
    /**
     * 移除脚本
     */
    public void unregisterScript(String scriptId) {
        UnifiedAtomicScript script = scriptRegistry.remove(scriptId);
        if (script != null) {
            // 从类型分组中移除
            List<UnifiedAtomicScript> typeScripts = scriptsByType.get(script.getScriptType());
            if (typeScripts != null) {
                typeScripts.removeIf(s -> s.getScriptId().equals(scriptId));
            }
            
            // 从标签分组中移除
            for (String tag : script.getTags()) {
                List<UnifiedAtomicScript> tagScripts = scriptsByTag.get(tag);
                if (tagScripts != null) {
                    tagScripts.removeIf(s -> s.getScriptId().equals(scriptId));
                }
            }
            
            log.debug("移除脚本: {} -> {}", scriptId, script.getName());
        }
    }
    
    /**
     * 根据ID获取脚本
     */
    public UnifiedAtomicScript getScript(String scriptId) {
        return scriptRegistry.get(scriptId);
    }
    
    /**
     * 获取所有脚本
     */
    public List<UnifiedAtomicScript> getAllScripts() {
        return new ArrayList<>(scriptRegistry.values());
    }
    
    /**
     * 根据类型获取脚本
     */
    public List<UnifiedAtomicScript> getScriptsByType(UnifiedAtomicScript.ScriptType type) {
        return scriptsByType.getOrDefault(type, Collections.emptyList());
    }
    
    /**
     * 获取所有内置脚本
     */
    public List<UnifiedAtomicScript> getBuiltInScripts() {
        return getScriptsByType(UnifiedAtomicScript.ScriptType.BUILT_IN);
    }
    
    /**
     * 获取所有可配置脚本
     */
    public List<UnifiedAtomicScript> getConfigurableScripts() {
        return getScriptsByType(UnifiedAtomicScript.ScriptType.CONFIGURABLE);
    }
    
    /**
     * 根据标签获取脚本
     */
    public List<UnifiedAtomicScript> getScriptsByTag(String tag) {
        return scriptsByTag.getOrDefault(tag, Collections.emptyList());
    }
    
    /**
     * 搜索脚本（按名称或描述）
     */
    public List<UnifiedAtomicScript> searchScripts(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllScripts();
        }
        
        String lowerKeyword = keyword.toLowerCase();
        return scriptRegistry.values().stream()
                .filter(script -> 
                    script.getName().toLowerCase().contains(lowerKeyword) ||
                    script.getDescription().toLowerCase().contains(lowerKeyword) ||
                    Arrays.stream(script.getTags()).anyMatch(tag -> tag.toLowerCase().contains(lowerKeyword))
                )
                .collect(Collectors.toList());
    }
    
    /**
     * 获取所有标签
     */
    public Set<String> getAllTags() {
        return new HashSet<>(scriptsByTag.keySet());
    }
    
    /**
     * 获取脚本统计信息
     */
    public ScriptRegistryStats getStats() {
        int totalScripts = scriptRegistry.size();
        int builtInCount = getBuiltInScripts().size();
        int configurableCount = getConfigurableScripts().size();
        int totalTags = scriptsByTag.size();
        
        return new ScriptRegistryStats(totalScripts, builtInCount, configurableCount, totalTags);
    }
    
    /**
     * 清空注册表
     */
    public void clear() {
        scriptRegistry.clear();
        scriptsByType.clear();
        scriptsByTag.clear();
        log.info("脚本注册表已清空");
    }
    
    /**
     * 重新加载所有可配置脚本
     */
    public void reloadConfigurableScripts(List<AtomicScript> atomicScripts) {
        // 移除现有的可配置脚本
        List<String> configurableScriptIds = getConfigurableScripts().stream()
                .map(UnifiedAtomicScript::getScriptId)
                .collect(Collectors.toList());
        
        for (String scriptId : configurableScriptIds) {
            unregisterScript(scriptId);
        }
        
        // 重新注册可配置脚本
        for (AtomicScript atomicScript : atomicScripts) {
            if (AtomicScript.Status.ACTIVE.equals(atomicScript.getStatus())) {
                registerConfigurableScript(atomicScript);
            }
        }
        
        log.info("重新加载可配置脚本完成，共 {} 个脚本", atomicScripts.size());
    }
    
    /**
     * 脚本注册表统计信息
     */
    public static class ScriptRegistryStats {
        private final int totalScripts;
        private final int builtInScripts;
        private final int configurableScripts;
        private final int totalTags;
        
        public ScriptRegistryStats(int totalScripts, int builtInScripts, int configurableScripts, int totalTags) {
            this.totalScripts = totalScripts;
            this.builtInScripts = builtInScripts;
            this.configurableScripts = configurableScripts;
            this.totalTags = totalTags;
        }
        
        public int getTotalScripts() { return totalScripts; }
        public int getBuiltInScripts() { return builtInScripts; }
        public int getConfigurableScripts() { return configurableScripts; }
        public int getTotalTags() { return totalTags; }
        
        @Override
        public String toString() {
            return String.format("ScriptRegistryStats{总脚本=%d, 内置脚本=%d, 可配置脚本=%d, 标签数=%d}", 
                totalScripts, builtInScripts, configurableScripts, totalTags);
        }
    }
}