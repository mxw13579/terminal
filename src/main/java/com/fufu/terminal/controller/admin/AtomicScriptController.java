package com.fufu.terminal.controller.admin;

import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.service.AtomicScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 原子脚本管理API
 */
@RestController
@RequestMapping("/api/admin/atomic-scripts")
@RequiredArgsConstructor
public class AtomicScriptController {

    private final AtomicScriptService atomicScriptService;

    /**
     * 创建一个新的原子脚本
     */
    @PostMapping
    public ResponseEntity<AtomicScript> createAtomicScript(@RequestBody AtomicScript script) {
        AtomicScript createdScript = atomicScriptService.createAtomicScript(script);
        return ResponseEntity.ok(createdScript);
    }

    /**
     * 获取所有原子脚本
     */
    @GetMapping
    public ResponseEntity<List<AtomicScript>> getAllAtomicScripts() {
        List<AtomicScript> scripts = atomicScriptService.getAllAtomicScripts();
        return ResponseEntity.ok(scripts);
    }

    /**
     * 根据ID获取单个原子脚本
     */
    @GetMapping("/{id}")
    public ResponseEntity<AtomicScript> getAtomicScriptById(@PathVariable Long id) {
        AtomicScript script = atomicScriptService.getAtomicScriptById(id);
        return ResponseEntity.ok(script);
    }

    /**
     * 更新一个已有的原子脚本
     */
    @PutMapping("/{id}")
    public ResponseEntity<AtomicScript> updateAtomicScript(@PathVariable Long id, @RequestBody AtomicScript script) {
        // 确保ID被正确设置，以执行更新操作
        script.setId(id);
        AtomicScript updatedScript = atomicScriptService.updateAtomicScript(script);
        return ResponseEntity.ok(updatedScript);
    }

    /**
     * 根据ID删除一个原子脚本
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAtomicScript(@PathVariable Long id) {
        atomicScriptService.deleteAtomicScript(id);
        return ResponseEntity.noContent().build();
    }
}
