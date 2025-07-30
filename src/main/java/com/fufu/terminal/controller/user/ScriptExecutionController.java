package com.fufu.terminal.controller.user;

import com.fufu.terminal.entity.ExecutionLog;
import com.fufu.terminal.entity.ScriptExecution;
import com.fufu.terminal.service.ScriptEngineService;
import com.fufu.terminal.service.ScriptExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/user/script-execution")
@RequiredArgsConstructor
public class ScriptExecutionController {
    
    private final ScriptEngineService scriptEngineService;
    private final ScriptExecutionService executionService;
    
    @PostMapping("/execute/{scriptId}")
    public ResponseEntity<ScriptExecution> executeScript(@PathVariable Long scriptId) {
        String sessionId = UUID.randomUUID().toString();
        Long userId = 1L; // TODO: 从JWT token获取实际用户ID
        
        // 异步执行脚本
        CompletableFuture<ScriptExecution> future = scriptEngineService.executeScript(scriptId, userId, sessionId);
        
        // 立即返回执行记录（状态为RUNNING）
        ScriptExecution execution = executionService.getExecutionBySessionId(sessionId);
        return ResponseEntity.ok(execution);
    }
    
    @GetMapping("/logs/{executionId}")
    public ResponseEntity<List<ExecutionLog>> getExecutionLogs(@PathVariable Long executionId) {
        List<ExecutionLog> logs = executionService.getExecutionLogs(executionId);
        return ResponseEntity.ok(logs);
    }
    
    @GetMapping("/history/{scriptId}")
    public ResponseEntity<List<ScriptExecution>> getExecutionHistory(@PathVariable Long scriptId) {
        List<ScriptExecution> history = executionService.getExecutionHistory(scriptId);
        return ResponseEntity.ok(history);
    }
    
    @GetMapping("/status/{executionId}")
    public ResponseEntity<ScriptExecution> getExecutionStatus(@PathVariable Long executionId) {
        // 这里应该从数据库获取最新状态
        return ResponseEntity.ok(new ScriptExecution()); // TODO: 实现获取执行状态
    }
}