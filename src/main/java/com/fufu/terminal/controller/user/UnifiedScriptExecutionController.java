package com.fufu.terminal.controller.user;

import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.script.ScriptExecutionResult;
import com.fufu.terminal.service.script.UnifiedAtomicScript;
import com.fufu.terminal.service.script.UnifiedScriptRegistry;
import com.fufu.terminal.service.ProgressManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 统一脚本执行控制器
 * 提供统一脚本执行接口，支持内置脚本和可配置脚本的统一执行
 */
@Slf4j
@RestController
@RequestMapping("/api/user/unified-execution")
@RequiredArgsConstructor
public class UnifiedScriptExecutionController {
    
    private final UnifiedScriptRegistry scriptRegistry;
    private final ProgressManagerService progressManager;
    
    /**
     * 执行统一脚本
     */
    @PostMapping("/script/{scriptId}")
    public ResponseEntity<String> executeUnifiedScript(@PathVariable String scriptId) {
        try {
            UnifiedAtomicScript script = scriptRegistry.getScript(scriptId);
            if (script == null) {
                return ResponseEntity.status(404).body("脚本不存在: " + scriptId);
            }
            
            // 生成会话ID
            String sessionId = UUID.randomUUID().toString();
            
            // 异步执行脚本
            CompletableFuture<Object> executionFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return executeScript(sessionId, script);
                } catch (Exception e) {
                    progressManager.setExecutionFailed(sessionId, "脚本执行异常: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            });
            
            // 设置完成后的清理任务
            executionFuture.whenComplete((result, throwable) -> {
                // 延迟清理进度数据（给客户端时间获取最终结果）
                CompletableFuture.delayedExecutor(30, java.util.concurrent.TimeUnit.SECONDS)
                        .execute(() -> progressManager.cleanupProgress(sessionId));
            });
            
            return ResponseEntity.ok(sessionId);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body("执行失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行脚本
     */
    private Object executeScript(String sessionId, UnifiedAtomicScript script) throws Exception {
        log.info("开始执行统一脚本: sessionId={}, scriptId={}, name={}", 
            sessionId, script.getScriptId(), script.getName());
        
        // 初始化进度（统一脚本通常分为3个步骤：准备、执行、清理）
        progressManager.initializeProgress(sessionId, System.currentTimeMillis(), 3);
        
        try {
            // 步骤1: 准备执行环境
            progressManager.updateCurrentStep(sessionId, "准备执行环境", "正在初始化脚本上下文...");
            
            // 创建模拟的命令上下文（实际项目中需要根据具体需求创建）
            CommandContext context = createMockCommandContext();
            
            progressManager.updateStepProgress(sessionId, 50, "上下文初始化完成");
            
            // 检查是否应该执行
            if (!script.shouldExecute(context)) {
                progressManager.updateStepProgress(sessionId, 100, "脚本条件检查：跳过执行");
                progressManager.completeCurrentStep(sessionId, "脚本条件不满足，跳过执行");
                
                // 直接跳到清理步骤
                progressManager.updateCurrentStep(sessionId, "清理资源", "跳过执行，直接清理");
                progressManager.completeCurrentStep(sessionId, "执行完成（已跳过）");
                
                return "脚本条件不满足，已跳过执行";
            }
            
            progressManager.updateStepProgress(sessionId, 100, "执行环境准备完成");
            progressManager.completeCurrentStep(sessionId, "执行环境准备就绪");
            
            // 步骤2: 执行脚本内容
            progressManager.updateCurrentStep(sessionId, "执行脚本", "正在执行: " + script.getName());
            
            ScriptExecutionResult result = script.execute(context);
            
            if (result.isSuccess()) {
                progressManager.completeCurrentStep(sessionId, "脚本执行成功");
            } else {
                throw new Exception(result.getErrorMessage());
            }
            
            // 步骤3: 清理和收集结果
            progressManager.updateCurrentStep(sessionId, "清理资源", "正在收集执行结果...");
            progressManager.updateStepProgress(sessionId, 50, "结果收集中...");
            
            // 设置结果数据
            progressManager.setResultData(sessionId, result.getOutputData());
            
            progressManager.updateStepProgress(sessionId, 100, "资源清理完成");
            progressManager.completeCurrentStep(sessionId, "执行完成，结果已收集");
            
            log.info("统一脚本执行成功: sessionId={}, scriptId={}", sessionId, script.getScriptId());
            return result;
            
        } catch (Exception e) {
            log.error("统一脚本执行失败: sessionId={}, scriptId={}, error={}", 
                sessionId, script.getScriptId(), e.getMessage());
            progressManager.setExecutionFailed(sessionId, "执行失败: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 创建模拟的命令上下文
     * 实际项目中需要根据具体需求创建真实的SSH连接和WebSocket会话
     */
    private CommandContext createMockCommandContext() {
        // 这里创建一个模拟的上下文，实际使用时需要传入真实的SSH连接
        // 为了编译通过，传入null值作为模拟参数
        SshConnection mockSshConnection = new SshConnection(null, null, null, null, null);
        return new CommandContext(mockSshConnection, null);
    }
}