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
 * 交互式脚本执行控制器 (已重构)
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
            AggregatedScript script = aggregatedScriptService.getAggregatedScriptById(aggregateScriptId);
            String sessionId = UUID.randomUUID().toString();

            // 异步执行，执行器内部会创建和管理上下文
            interactiveScriptExecutor.executeAggregateScript(sessionId, script);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sessionId", sessionId);
            response.put("message", "脚本执行已开始，请通过WebSocket监听 /topic/execution/" + sessionId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error starting script execution for aggregateScriptId {}: ", aggregateScriptId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "启动脚本执行失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 处理用户交互响应 (已重构)
     * 由前端调用，以恢复暂停的脚本执行
     */
    @PostMapping("/respond")
    public ResponseEntity<Map<String, Object>> handleInteractionResponse(@RequestBody InteractionResponse response) {
        if (response == null || response.getInteractionId() == null) {
            log.warn("Received an invalid interaction response.");
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid response"));
        }
        try {
            log.info("Handling user response for interactionId: {}", response.getInteractionId());
            interactiveScriptExecutor.handleUserResponse(response);
            return ResponseEntity.ok(Map.of("success", true, "message", "交互响应已处理"));
        } catch (Exception e) {
            log.error("Error handling interaction response for interactionId {}: ", response.getInteractionId(), e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "处理交互响应失败: " + e.getMessage()));
        }
    }
    
    // 其他端点 (cancel, status) 保持不变，待后续实现
}
