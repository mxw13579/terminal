package com.fufu.terminal.controller.user;

import com.fufu.terminal.entity.ScriptGroup;
import com.fufu.terminal.entity.ExecutionLog;
import com.fufu.terminal.service.ScriptGroupService;
import com.fufu.terminal.service.script.strategy.router.ScriptExecutionStrategyRouter;
import com.fufu.terminal.service.script.strategy.model.ScriptExecutionRequest;
import com.fufu.terminal.service.script.ScriptExecutionResult;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.strategy.ScriptSourceType;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.ProductionCommandContext;
import com.fufu.terminal.command.model.SshConnectionConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 统一脚本控制器
 * 合并原有的多个脚本执行控制器功能
 */
@Slf4j
@RestController
@RequestMapping("/api/user/scripts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UnifiedScriptController {
    
    private final ScriptGroupService scriptGroupService;
    private final ScriptExecutionStrategyRouter strategyRouter;
    
    /**
     * 获取脚本分组列表
     */
    @GetMapping("/groups")
    public ResponseEntity<List<ScriptGroup>> getScriptGroups() {
        try {
            List<ScriptGroup> groups = scriptGroupService.findAllActiveGroups();
            return ResponseEntity.ok(groups);
        } catch (Exception e) {
            log.error("获取脚本分组失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取分组内的脚本列表
     */
    @GetMapping("/groups/{groupId}/scripts")
    public ResponseEntity<List<Object>> getGroupScripts(@PathVariable Long groupId) {
        try {
            // 获取分组内的聚合脚本列表
            List<Object> scripts = scriptGroupService.getGroupScripts(groupId);
            return ResponseEntity.ok(scripts);
        } catch (Exception e) {
            log.error("获取分组脚本失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 统一脚本执行接口
     * 支持所有4种脚本类型：BUILT_IN_STATIC, BUILT_IN_DYNAMIC, BUILT_IN_INTERACTIVE, USER_DEFINED
     */
    @PostMapping("/execute/{scriptId}")
    public ResponseEntity<ScriptExecutionResult> executeScript(
            @PathVariable String scriptId,
            @RequestBody ScriptExecutionRequestDto requestDto) {
        
        log.info("开始执行脚本: {}, 类型: {}", scriptId, requestDto.getScriptType());
        
        try {
            // 创建命令上下文
            CommandContext context = createCommandContext(requestDto);
            
            // 创建脚本执行请求
            ScriptExecutionRequest request = ScriptExecutionRequest.builder()
                .scriptId(scriptId)
                .sourceType(requestDto.getScriptType())
                .parameters(requestDto.getParameters())
                .commandContext(context)
                .async(requestDto.isAsync())
                .userId(requestDto.getUserId())
                .sessionId(requestDto.getSessionId())
                .build();
                
            // 使用策略路由器执行脚本
            ScriptExecutionResult result = strategyRouter.executeScript(request);
            
            log.info("脚本执行完成: {}, 成功: {}", scriptId, result.isSuccess());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("脚本执行失败: {}", scriptId, e);
            
            ScriptExecutionResult errorResult = new ScriptExecutionResult();
            errorResult.setSuccess(false);
            errorResult.setErrorMessage("脚本执行异常: " + e.getMessage());
            
            return ResponseEntity.ok(errorResult);
        }
    }
    
    /**
     * 获取脚本参数要求
     */
    @GetMapping("/parameters/{scriptId}")
    public ResponseEntity<List<ScriptParameter>> getScriptParameters(@PathVariable String scriptId) {
        try {
            List<ScriptParameter> parameters = strategyRouter.getRequiredParameters(scriptId);
            return ResponseEntity.ok(parameters);
        } catch (Exception e) {
            log.error("获取脚本参数失败: {}", scriptId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取执行日志
     */
    @GetMapping("/execution/{executionId}/logs")
    public ResponseEntity<List<ExecutionLog>> getExecutionLogs(@PathVariable String executionId) {
        try {
            // 获取执行日志的逻辑
            List<ExecutionLog> logs = getLogsFromService(executionId);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("获取执行日志失败: {}", executionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 提交交互响应
     */
    @PostMapping("/interaction/respond")
    public ResponseEntity<Void> submitInteractionResponse(@RequestBody InteractionResponseDto responseDto) {
        try {
            log.info("收到交互响应: requestId={}, response={}", 
                responseDto.getRequestId(), responseDto.getResponse());
            
            // 处理交互响应的逻辑
            handleInteractionResponse(responseDto);
            
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("处理交互响应失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // 辅助方法
    private CommandContext createCommandContext(ScriptExecutionRequestDto requestDto) {
        SshConnectionConfig sshConfig = requestDto.getSshConfig();
        return new ProductionCommandContext(sshConfig);
    }
    
    private List<ExecutionLog> getLogsFromService(String executionId) {
        // 实现获取日志的逻辑
        return List.of(); // 占位符
    }
    
    private void handleInteractionResponse(InteractionResponseDto responseDto) {
        // 实现交互响应处理逻辑
    }
    
    // DTO 类定义
    @Data
    public static class ScriptExecutionRequestDto {
        private ScriptSourceType scriptType;
        private Map<String, Object> parameters;
        private SshConnectionConfig sshConfig;
        private boolean async = false;
        private String userId;
        private String sessionId;
    }
    
    @Data
    public static class InteractionResponseDto {
        private String requestId;
        private Object response;
        private String timestamp;
    }
}