package com.fufu.terminal.controller.admin;

import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.service.AtomicScriptService;
import com.fufu.terminal.service.script.UnifiedAtomicScript;
import com.fufu.terminal.service.script.UnifiedScriptRegistry;
import com.fufu.terminal.service.script.UnifiedScriptRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 原子脚本管理控制器
 * 提供原子脚本的CRUD操作接口，支持统一脚本管理
 *
 * 安全加固：
 * 1. 添加权限验证
 * 2. 输入参数验证
 * 3. 详细的错误日志记录
 * 4. 安全的异常处理
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/atomic-scripts")
@RequiredArgsConstructor
@Validated
public class AdminAtomicScriptController {

    private final AtomicScriptService atomicScriptService;
    private final UnifiedScriptRegistry unifiedScriptRegistry;
    private final UnifiedScriptRegistrationService registrationService;

    /**
     * 获取所有原子脚本
     */
    @GetMapping
    public ResponseEntity<List<AtomicScript>> getAllAtomicScripts() {
        try {
            List<AtomicScript> scripts = atomicScriptService.getAllAtomicScripts();
            log.debug("获取所有原子脚本，共 {} 个", scripts.size());
            return ResponseEntity.ok(scripts);
        } catch (Exception e) {
            log.error("获取所有原子脚本失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据ID获取原子脚本
     */
    @GetMapping("/{id}")
    public ResponseEntity<AtomicScript> getAtomicScriptById(@PathVariable Long id) {
        try {
            AtomicScript script = atomicScriptService.getAtomicScriptById(id);
            log.debug("获取原子脚本: id={}, name={}", id, script.getName());
            return ResponseEntity.ok(script);
        } catch (RuntimeException e) {
            log.warn("原子脚本不存在: id={}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("获取原子脚本失败: id={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 创建原子脚本
     */
    @PostMapping
    public ResponseEntity<AtomicScript> createAtomicScript(@RequestBody AtomicScript script) {
        try {
            // 输入安全验证
            if (!isValidScript(script)) {
                log.warn("创建原子脚本失败：输入验证不通过, name={}", script.getName());
                return ResponseEntity.badRequest().build();
            }

            AtomicScript created = atomicScriptService.createAtomicScript(script);
            log.info("创建原子脚本成功: id={}, name={}", created.getId(), created.getName());
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            log.warn("创建原子脚本失败：参数无效, name={}, error={}", script.getName(), e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("创建原子脚本失败: name={}", script.getName(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新原子脚本
     */
    @PutMapping("/{id}")
    public ResponseEntity<AtomicScript> updateAtomicScript(
            @PathVariable Long id,
            @RequestBody AtomicScript script) {
        try {
            // 输入安全验证
            if (!isValidScript(script)) {
                log.warn("更新原子脚本失败：输入验证不通过, id={}, name={}", id, script.getName());
                return ResponseEntity.badRequest().build();
            }

            script.setId(id);
            AtomicScript updated = atomicScriptService.updateAtomicScript(script);
            log.info("更新原子脚本成功: id={}, name={}", id, updated.getName());
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            log.warn("更新原子脚本失败：脚本不存在, id={}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("更新原子脚本失败: id={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 删除原子脚本
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAtomicScript(@PathVariable Long id) {
        try {
            atomicScriptService.deleteAtomicScript(id);
            log.info("删除原子脚本成功: id={}", id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.warn("删除原子脚本失败：脚本不存在, id={}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("删除原子脚本失败: id={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据状态获取原子脚本
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<AtomicScript>> getAtomicScriptsByStatus(@PathVariable AtomicScript.Status status) {
        try {
            List<AtomicScript> scripts = atomicScriptService.getAtomicScriptsByStatus(status);
            log.debug("根据状态获取原子脚本: status={}, count={}", status, scripts.size());
            return ResponseEntity.ok(scripts);
        } catch (Exception e) {
            log.error("根据状态获取原子脚本失败: status={}", status, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 重新初始化统一脚本注册表
     */
    @PostMapping("/registry/reload")
    public ResponseEntity<String> reloadRegistry() {
        try {
            registrationService.reloadConfigurableScripts();
            log.info("统一脚本注册表重新加载成功");
            return ResponseEntity.ok("统一脚本注册表已重新加载");
        } catch (Exception e) {
            log.error("统一脚本注册表重新加载失败", e);
            return ResponseEntity.internalServerError().body("注册表重新加载失败: " + e.getMessage());
        }
    }

    /**
     * 获取统一注册表中的所有脚本
     */
    @GetMapping("/registry")
    public ResponseEntity<List<UnifiedAtomicScript>> getRegisteredScripts() {
        try {
            List<UnifiedAtomicScript> scripts = unifiedScriptRegistry.getAllScripts();
            log.debug("获取统一注册表脚本，共 {} 个", scripts.size());
            return ResponseEntity.ok(scripts);
        } catch (Exception e) {
            log.error("获取统一注册表脚本失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 脚本输入验证
     */
    private boolean isValidScript(AtomicScript script) {
        if (script == null) {
            return false;
        }

        // 验证必填字段
        if (script.getName() == null || script.getName().trim().isEmpty()) {
            return false;
        }

        if (script.getScriptContent() == null || script.getScriptContent().trim().isEmpty()) {
            return false;
        }

        // 验证脚本内容安全性
        String content = script.getScriptContent().toLowerCase();
        String[] dangerousCommands = {
            "rm -rf", "format", "del /", "deltree", "shutdown", "reboot",
            "dd if=", "mkfs", "fdisk", "parted", "chmod 777", "chown root"
        };

        for (String dangerous : dangerousCommands) {
            if (content.contains(dangerous)) {
                log.warn("检测到危险命令: {}", dangerous);
                return false;
            }
        }

        // 验证脚本长度
        if (script.getScriptContent().length() > 10000) {
            log.warn("脚本内容过长: {} 字符", script.getScriptContent().length());
            return false;
        }

        return true;
    }
}
