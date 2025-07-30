package com.fufu.terminal.controller.user;

import com.fufu.terminal.entity.AggregatedScript;
import com.fufu.terminal.entity.context.EnhancedScriptContext;
import com.fufu.terminal.entity.interaction.InteractionResponse;
import com.fufu.terminal.service.AggregatedScriptService;
import com.fufu.terminal.service.execution.InteractiveScriptExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 交互式脚本执行控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/user/interactive-execution")
@RequiredArgsConstructor
public class InteractiveScriptExecutionController {

    private final InteractiveScriptExecutor interactiveScriptExecutor;
    private final AggregatedScriptService aggregatedScriptService;

    /**
     * 开始执行聚合脚本
     */
    @PostMapping("/start/{aggregateScriptId}")
    public ResponseEntity<Map<String, Object>> startExecution(@PathVariable Long aggregateScriptId) {
        try {
            // 获取聚合脚本
            AggregatedScript script = aggregatedScriptService.getAggregatedScriptById(aggregateScriptId);

            // 生成会话ID
            String sessionId = UUID.randomUUID().toString();

            // 创建上下文
            EnhancedScriptContext context = new EnhancedScriptContext();

            // 异步执行脚本
            interactiveScriptExecutor.executeAggregateScript(sessionId, script, context);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sessionId", sessionId);
            response.put("scriptName", script.getName());
            response.put("message", "脚本执行已开始，请通过WebSocket监听执行进度");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error starting script execution: ", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "启动脚本执行失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 处理用户交互响应
     */
    @PostMapping("/interaction-response/{sessionId}")
    public ResponseEntity<Map<String, Object>> handleInteractionResponse(
            @PathVariable String sessionId,
            @RequestBody InteractionResponse response) {
        try {
            interactiveScriptExecutor.handleUserResponse(sessionId, response);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "交互响应已处理");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error handling interaction response: ", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "处理交互响应失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 取消脚本执行
     */
    @PostMapping("/cancel/{sessionId}")
    public ResponseEntity<Map<String, Object>> cancelExecution(@PathVariable String sessionId) {
        try {
            // 这里应该实现取消逻辑
            // interactiveScriptExecutor.cancelExecution(sessionId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "脚本执行已取消");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error cancelling script execution: ", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "取消脚本执行失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 获取执行状态
     */
    @GetMapping("/status/{sessionId}")
    public ResponseEntity<Map<String, Object>> getExecutionStatus(@PathVariable String sessionId) {
        try {
            // 这里应该查询执行状态
            // ExecutionStatus status = interactiveScriptExecutor.getExecutionStatus(sessionId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sessionId", sessionId);
            response.put("status", "RUNNING"); // 暂时返回固定值

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting execution status: ", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取执行状态失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}