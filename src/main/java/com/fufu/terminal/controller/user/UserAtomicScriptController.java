package com.fufu.terminal.controller.user;

import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.service.AtomicScriptService;
import com.fufu.terminal.service.script.UnifiedAtomicScript;
import com.fufu.terminal.service.script.UnifiedScriptRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户原子脚本控制器
 * 提供用户查询原子脚本的接口，同时支持可配置脚本和内置脚本
 */
@RestController
@RequestMapping("/api/user/atomic-scripts")
@RequiredArgsConstructor
public class UserAtomicScriptController {
    
    private final AtomicScriptService atomicScriptService;
    private final UnifiedScriptRegistry unifiedScriptRegistry;
    
    /**
     * 获取所有活跃的原子脚本（仅可配置脚本）
     */
    @GetMapping
    public ResponseEntity<List<AtomicScript>> getActiveAtomicScripts() {
        List<AtomicScript> scripts = atomicScriptService.getAtomicScriptsByStatus(AtomicScript.Status.ACTIVE);
        return ResponseEntity.ok(scripts);
    }
    
    /**
     * 获取所有统一脚本（包括内置脚本和可配置脚本）
     */
    @GetMapping("/unified")
    public ResponseEntity<List<UnifiedAtomicScript>> getAllUnifiedScripts() {
        List<UnifiedAtomicScript> scripts = unifiedScriptRegistry.getAllScripts();
        return ResponseEntity.ok(scripts);
    }
    
    /**
     * 根据类型获取统一脚本
     */
    @GetMapping("/unified/type/{scriptType}")
    public ResponseEntity<List<UnifiedAtomicScript>> getUnifiedScriptsByType(@PathVariable UnifiedAtomicScript.ScriptType scriptType) {
        List<UnifiedAtomicScript> scripts = unifiedScriptRegistry.getScriptsByType(scriptType);
        return ResponseEntity.ok(scripts);
    }
    
    /**
     * 获取所有内置脚本
     */
    @GetMapping("/built-in")
    public ResponseEntity<List<UnifiedAtomicScript>> getBuiltInScripts() {
        List<UnifiedAtomicScript> scripts = unifiedScriptRegistry.getBuiltInScripts();
        return ResponseEntity.ok(scripts);
    }
    
    /**
     * 获取所有可配置脚本
     */
    @GetMapping("/configurable")
    public ResponseEntity<List<UnifiedAtomicScript>> getConfigurableScripts() {
        List<UnifiedAtomicScript> scripts = unifiedScriptRegistry.getConfigurableScripts();
        return ResponseEntity.ok(scripts);
    }
    
    /**
     * 根据ID获取原子脚本（仅活跃状态）
     */
    @GetMapping("/{id}")
    public ResponseEntity<AtomicScript> getAtomicScriptById(@PathVariable Long id) {
        try {
            AtomicScript script = atomicScriptService.getAtomicScriptById(id);
            if (AtomicScript.Status.ACTIVE.equals(script.getStatus())) {
                return ResponseEntity.ok(script);
            }
            return ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 根据脚本类型获取活跃的原子脚本
     */
    @GetMapping("/type/{scriptType}")
    public ResponseEntity<List<AtomicScript>> getActiveAtomicScriptsByType(@PathVariable String scriptType) {
        List<AtomicScript> scripts = atomicScriptService.getActiveAtomicScriptsByType(scriptType);
        return ResponseEntity.ok(scripts);
    }
    
    /**
     * 根据标签搜索原子脚本
     */
    @GetMapping("/search/tag")
    public ResponseEntity<List<AtomicScript>> searchByTag(@RequestParam String tag) {
        List<AtomicScript> scripts = atomicScriptService.searchAtomicScriptsByTag(tag);
        return ResponseEntity.ok(scripts);
    }
    
    /**
     * 根据名称搜索原子脚本
     */
    @GetMapping("/search/name")
    public ResponseEntity<List<AtomicScript>> searchByName(@RequestParam String name) {
        List<AtomicScript> scripts = atomicScriptService.searchAtomicScriptsByName(name);
        return ResponseEntity.ok(scripts);
    }
    
    /**
     * 根据统一脚本ID获取脚本
     */
    @GetMapping("/unified/{scriptId}")
    public ResponseEntity<UnifiedAtomicScript> getUnifiedScript(@PathVariable String scriptId) {
        UnifiedAtomicScript script = unifiedScriptRegistry.getScript(scriptId);
        if (script != null) {
            return ResponseEntity.ok(script);
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * 根据标签搜索统一脚本
     */
    @GetMapping("/unified/search/tag")
    public ResponseEntity<List<UnifiedAtomicScript>> searchUnifiedByTag(@RequestParam String tag) {
        List<UnifiedAtomicScript> scripts = unifiedScriptRegistry.getScriptsByTag(tag);
        return ResponseEntity.ok(scripts);
    }
    
    /**
     * 根据关键词搜索统一脚本
     */
    @GetMapping("/unified/search")
    public ResponseEntity<List<UnifiedAtomicScript>> searchUnifiedScripts(@RequestParam String keyword) {
        List<UnifiedAtomicScript> scripts = unifiedScriptRegistry.searchScripts(keyword);
        return ResponseEntity.ok(scripts);
    }
    
    /**
     * 获取所有标签
     */
    @GetMapping("/tags")
    public ResponseEntity<List<String>> getAllTags() {
        List<String> tags = unifiedScriptRegistry.getAllTags().stream()
                .sorted()
                .collect(Collectors.toList());
        return ResponseEntity.ok(tags);
    }
    
    /**
     * 获取脚本注册表统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<UnifiedScriptRegistry.ScriptRegistryStats> getRegistryStats() {
        UnifiedScriptRegistry.ScriptRegistryStats stats = unifiedScriptRegistry.getStats();
        return ResponseEntity.ok(stats);
    }
}