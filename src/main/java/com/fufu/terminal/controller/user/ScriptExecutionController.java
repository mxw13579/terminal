package com.fufu.terminal.controller.user;

import com.fufu.terminal.entity.AggregatedScript;
import com.fufu.terminal.entity.ScriptExecution;
import com.fufu.terminal.service.AggregatedScriptService;
import com.fufu.terminal.service.ScriptExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/user/script-execution")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ScriptExecutionController {
    
    private final ScriptExecutionService scriptExecutionService;
    private final AggregatedScriptService aggregatedScriptService;

    /**
     * 执行聚合脚本
     */
    @PostMapping("/execute/{aggregatedScriptId}")
    public ResponseEntity<ScriptExecution> executeAggregatedScript(
            @PathVariable Long aggregatedScriptId,
            @RequestBody(required = false) String connectionConfig) {
        
        try {
            log.info("开始执行聚合脚本: {}", aggregatedScriptId);
            
            // 获取聚合脚本信息
            AggregatedScript aggregatedScript;
            try {
                aggregatedScript = aggregatedScriptService.getAggregatedScriptById(aggregatedScriptId);
            } catch (RuntimeException e) {
                log.warn("未找到聚合脚本: {}", aggregatedScriptId);
                return ResponseEntity.notFound().build();
            }
            
            // 启动执行
            ScriptExecution execution = scriptExecutionService.executeAggregatedScript(
                aggregatedScript, connectionConfig);
            
            return ResponseEntity.ok(execution);
            
        } catch (Exception e) {
            log.error("执行聚合脚本失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取执行历史
     */
    @GetMapping("/history")
    public ResponseEntity<List<ScriptExecution>> getExecutionHistory() {
        try {
            List<ScriptExecution> executions = scriptExecutionService.getExecutionHistory();
            return ResponseEntity.ok(executions);
        } catch (Exception e) {
            log.error("获取执行历史失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取执行详情
     */
    @GetMapping("/{executionId}")
    public ResponseEntity<ScriptExecution> getExecutionDetails(@PathVariable Long executionId) {
        try {
            ScriptExecution execution = scriptExecutionService.getExecutionById(executionId);
            if (execution == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(execution);
        } catch (Exception e) {
            log.error("获取执行详情失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 取消执行
     */
    @PostMapping("/{executionId}/cancel")
    public ResponseEntity<Void> cancelExecution(@PathVariable Long executionId) {
        try {
            scriptExecutionService.cancelExecution(executionId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("取消执行失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}