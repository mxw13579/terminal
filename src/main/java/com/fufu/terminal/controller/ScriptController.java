package com.fufu.terminal.controller;

import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.engine.ScriptEngine;
import com.fufu.terminal.command.model.CommandInfo;
import com.fufu.terminal.command.model.ScriptRequest;
import com.fufu.terminal.command.registry.CommandRegistry;
import com.fufu.terminal.command.util.SshConnectionUtil;
import com.fufu.terminal.model.SshConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 脚本管理控制器
 * 提供命令管理和脚本执行的REST API
 * @author lizelin
 */
@RestController
@RequestMapping("/api/script")
@Slf4j
@CrossOrigin(origins = "*")
public class ScriptController {
    
    @Autowired
    private CommandRegistry commandRegistry;
    
    @Autowired
    private ScriptEngine scriptEngine;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    /**
     * 获取所有可用命令
     */
    @GetMapping("/commands")
    public ResponseEntity<Map<String, Object>> getCommands() {
        try {
            Map<String, CommandInfo> allCommands = commandRegistry.getAllCommands();
            Map<String, List<CommandInfo>> categories = commandRegistry.getCommandsByCategory();
            
            Map<String, Object> result = new HashMap<>();
            result.put("commands", allCommands);
            result.put("categories", categories);
            result.put("total", allCommands.size());
            
            log.info("返回 {} 个命令，{} 个分类", allCommands.size(), categories.size());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("获取命令列表失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "获取命令列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取指定分类的命令
     */
    @GetMapping("/commands/category/{category}")
    public ResponseEntity<List<CommandInfo>> getCommandsByCategory(@PathVariable String category) {
        try {
            List<CommandInfo> commands = commandRegistry.getCommandsByCategory(category);
            return ResponseEntity.ok(commands);
        } catch (Exception e) {
            log.error("获取分类命令失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 验证脚本的有效性
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateScript(@RequestBody List<String> commandIds) {
        try {
            boolean isValid = scriptEngine.validateScript(commandIds);
            List<String> optimized = scriptEngine.optimizeScript(commandIds);
            
            Map<String, Object> result = new HashMap<>();
            result.put("valid", isValid);
            result.put("original", commandIds);
            result.put("optimized", optimized);
            result.put("addedDependencies", optimized.size() - commandIds.size());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("验证脚本失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "验证脚本失败: " + e.getMessage()));
        }
    }
    
    /**
     * 测试SSH连接
     */
    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody ScriptRequest request) {
        try {
            boolean connected = SshConnectionUtil.testConnection(request.getSshConfig());
            
            Map<String, Object> result = new HashMap<>();
            result.put("connected", connected);
            result.put("message", connected ? "连接成功" : "连接失败");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("测试SSH连接失败", e);
            return ResponseEntity.ok(Map.of(
                "connected", false,
                "message", "连接失败: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 执行脚本
     */
    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeScript(@RequestBody ScriptRequest request) {
        try {
            // 验证请求
            if (!request.isValid()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "请求参数无效"));
            }
            
            // 优化脚本（如果需要）
            List<String> commandIds = request.isAutoOptimize() ? 
                    scriptEngine.optimizeScript(request.getCommandIds()) : 
                    request.getCommandIds();
            
            log.info("开始执行脚本，命令数: {}", commandIds.size());
            
            // 异步执行脚本
            CompletableFuture.runAsync(() -> {
                SshConnection sshConnection = null;
                try {
                    // 创建SSH连接
                    sshConnection = SshConnectionUtil.createConnection(request.getSshConfig());
                    CommandContext context = new CommandContext(sshConnection, null);
                    
                    // 执行脚本
                    scriptEngine.executeScript(commandIds, context, progress -> {
                        try {
                            // 通过WebSocket发送进度更新
                            messagingTemplate.convertAndSend("/topic/script-progress", progress);
                        } catch (Exception e) {
                            log.error("发送进度更新失败", e);
                        }
                    });
                    
                } catch (Exception e) {
                    log.error("脚本执行异常", e);
                    // 发送错误消息
                    try {
                        messagingTemplate.convertAndSend("/topic/script-error", 
                                Map.of("error", e.getMessage()));
                    } catch (Exception ex) {
                        log.error("发送错误消息失败", ex);
                    }
                } finally {
                    // 关闭SSH连接
                    SshConnectionUtil.closeConnection(sshConnection);
                }
            });
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "脚本开始执行");
            result.put("commandCount", commandIds.size());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("启动脚本执行失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "启动脚本执行失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取系统状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("commandCount", commandRegistry.getAllCommands().size());
        status.put("categories", commandRegistry.getAllCategories());
        status.put("serverTime", System.currentTimeMillis());
        
        return ResponseEntity.ok(status);
    }
}