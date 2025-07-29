package com.fufu.terminal.controller.user;

import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.model.ScriptExecutionProgress;
import com.fufu.terminal.service.AtomicScriptService;
import com.fufu.terminal.service.ProgressManagerService;
import com.fufu.terminal.service.executor.AtomicScriptExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 脚本执行控制器
 * 提供脚本执行和进度查询接口
 */
@RestController
@RequestMapping("/api/user/execution")
@RequiredArgsConstructor
public class UserScriptExecutionController {
    
    private final AtomicScriptService atomicScriptService;
    private final AtomicScriptExecutor scriptExecutor;
    private final ProgressManagerService progressManager;
    
    /**
     * 执行原子脚本
     */
    @PostMapping("/atomic-script/{scriptId}")
    public ResponseEntity<String> executeAtomicScript(@PathVariable Long scriptId) {
        try {
            AtomicScript script = atomicScriptService.getAtomicScriptById(scriptId);
            
            // 检查脚本状态
            if (!AtomicScript.Status.ACTIVE.equals(script.getStatus())) {
                return ResponseEntity.badRequest().body("脚本未激活，无法执行");
            }
            
            // 生成会话ID
            String sessionId = UUID.randomUUID().toString();
            
            // 异步执行脚本
            CompletableFuture<Object> executionFuture = scriptExecutor.executeAsync(sessionId, scriptId, script);
            
            // 设置完成后的清理任务
            executionFuture.whenComplete((result, throwable) -> {
                // 延迟清理进度数据（给客户端时间获取最终结果）
                CompletableFuture.delayedExecutor(30, java.util.concurrent.TimeUnit.SECONDS)
                        .execute(() -> progressManager.cleanupProgress(sessionId));
            });
            
            return ResponseEntity.ok(sessionId);
            
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body("脚本不存在: " + scriptId);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("执行失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询执行进度
     */
    @GetMapping("/progress/{sessionId}")
    public ResponseEntity<ScriptExecutionProgress> getExecutionProgress(@PathVariable String sessionId) {
        ScriptExecutionProgress progress = progressManager.getProgress(sessionId);
        if (progress != null) {
            return ResponseEntity.ok(progress);
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * 取消执行
     */
    @PostMapping("/cancel/{sessionId}")
    public ResponseEntity<String> cancelExecution(@PathVariable String sessionId) {
        ScriptExecutionProgress progress = progressManager.getProgress(sessionId);
        if (progress != null) {
            progressManager.setExecutionCancelled(sessionId, "用户取消执行");
            return ResponseEntity.ok("执行已取消");
        }
        return ResponseEntity.status(404).body("会话不存在");
    }
    
    /**
     * 清理进度数据
     */
    @DeleteMapping("/progress/{sessionId}")
    public ResponseEntity<String> cleanupProgress(@PathVariable String sessionId) {
        progressManager.cleanupProgress(sessionId);
        return ResponseEntity.ok("进度数据已清理");
    }
}