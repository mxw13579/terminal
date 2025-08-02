package com.fufu.terminal.controller.user;

import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.ProductionCommandContext;
import com.fufu.terminal.command.model.SshConnectionConfig;
import com.fufu.terminal.config.properties.ScriptExecutionProperties;
import com.fufu.terminal.exception.ConnectionException;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.error.ScriptExecutionErrorHandler;
import com.fufu.terminal.service.script.ScriptExecutionResult;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.model.ScriptInfo;
import com.fufu.terminal.service.script.strategy.BuiltInScriptMetadata;
import com.fufu.terminal.service.script.strategy.ScriptSourceType;
import com.fufu.terminal.service.script.strategy.model.ScriptExecutionRequest;
import com.fufu.terminal.service.script.strategy.registry.ScriptTypeRegistry;
import com.fufu.terminal.service.script.strategy.router.ScriptExecutionStrategyRouter;
import com.fufu.terminal.service.script.UnifiedScriptRegistry;
import com.fufu.terminal.service.script.UnifiedAtomicScript;
import com.fufu.terminal.service.ssh.ProductionSshConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 简化的脚本执行控制器
 * 提供基于策略模式的统一脚本执行接口
 */
@Slf4j
@RestController
@RequestMapping("/api/user/simplified-scripts")
@RequiredArgsConstructor
public class SimplifiedScriptExecutionController {

    private final ScriptExecutionStrategyRouter strategyRouter;
    private final ScriptTypeRegistry scriptTypeRegistry;
    private final UnifiedScriptRegistry unifiedScriptRegistry;
    private final ProductionSshConnectionService sshConnectionService;
    private final ScriptExecutionErrorHandler errorHandler;
    private final ScriptExecutionProperties properties;

    /**
     * 获取所有可用脚本列表
     * 包含内置脚本和用户定义脚本，带类型信息用于前端条件渲染
     */
    @GetMapping("/list")
    public ResponseEntity<List<ScriptInfo>> getAllScripts() {
        try {
            List<ScriptInfo> allScripts = new ArrayList<>();

            // 添加内置脚本
            List<BuiltInScriptMetadata> builtInScripts = scriptTypeRegistry.getAllBuiltInScriptsMetadata();
            for (BuiltInScriptMetadata metadata : builtInScripts) {
                ScriptInfo scriptInfo = convertBuiltInToScriptInfo(metadata);
                allScripts.add(scriptInfo);
            }

            // 添加用户定义脚本
            Map<String, UnifiedAtomicScript> userScripts = unifiedScriptRegistry.getAllScripts();
            for (Map.Entry<String, UnifiedAtomicScript> entry : userScripts.entrySet()) {
                String scriptId = entry.getKey();
                UnifiedAtomicScript script = entry.getValue();
                
                // 跳过内置脚本（避免重复）
                if (scriptTypeRegistry.isBuiltInScript(scriptId)) {
                    continue;
                }
                
                ScriptInfo scriptInfo = convertUserDefinedToScriptInfo(scriptId, script);
                allScripts.add(scriptInfo);
            }

            log.info("返回脚本列表，内置脚本: {}, 用户脚本: {}", 
                builtInScripts.size(), allScripts.size() - builtInScripts.size());
            
            return ResponseEntity.ok(allScripts);
            
        } catch (Exception e) {
            log.error("获取脚本列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取指定脚本的参数要求
     */
    @GetMapping("/{scriptId}/parameters")
    public ResponseEntity<List<ScriptParameter>> getScriptParameters(@PathVariable String scriptId) {
        try {
            List<ScriptParameter> parameters = strategyRouter.getRequiredParameters(scriptId);
            log.info("获取脚本参数: {}, 参数数量: {}", scriptId, parameters.size());
            return ResponseEntity.ok(parameters);
            
        } catch (Exception e) {
            log.error("获取脚本参数失败: {}", scriptId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取指定脚本的详细信息
     */
    @GetMapping("/{scriptId}/info")
    public ResponseEntity<ScriptInfo> getScriptInfo(@PathVariable String scriptId) {
        try {
            // 先尝试从内置脚本获取
            BuiltInScriptMetadata builtInMetadata = scriptTypeRegistry.getBuiltInScriptMetadata(scriptId);
            if (builtInMetadata != null) {
                ScriptInfo scriptInfo = convertBuiltInToScriptInfo(builtInMetadata);
                return ResponseEntity.ok(scriptInfo);
            }

            // 再尝试从用户定义脚本获取
            UnifiedAtomicScript userScript = unifiedScriptRegistry.getScript(scriptId);
            if (userScript != null) {
                ScriptInfo scriptInfo = convertUserDefinedToScriptInfo(scriptId, userScript);
                return ResponseEntity.ok(scriptInfo);
            }

            log.warn("脚本不存在: {}", scriptId);
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            log.error("获取脚本信息失败: {}", scriptId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 执行脚本
     */
    @PostMapping("/{scriptId}/execute")
    public ResponseEntity<ScriptExecutionResult> executeScript(
            @PathVariable String scriptId,
            @RequestBody ScriptExecutionRequestDto requestDto) {
        
        try {
            // 验证SSH连接配置
            if (requestDto.getSshConfig() == null) {
                ScriptExecutionResult errorResult = ScriptExecutionResult.failure("缺少SSH连接配置");
                return ResponseEntity.badRequest().body(errorResult);
            }

            // 创建命令上下文
            CommandContext commandContext = createCommandContext(requestDto.getSshConfig());

            // 构建执行请求
            ScriptExecutionRequest executionRequest = ScriptExecutionRequest.builder()
                .scriptId(scriptId)
                .parameters(requestDto.getParameters())
                .commandContext(commandContext)
                .timeoutMs(requestDto.getTimeoutMs())
                .async(requestDto.isAsync())
                .userId(requestDto.getUserId())
                .sessionId(requestDto.getSessionId())
                .build();

            // 执行脚本
            ScriptExecutionResult result = strategyRouter.executeScript(executionRequest);
            
            log.info("脚本执行完成: {}, 成功: {}, 耗时: {}ms", 
                scriptId, result.isSuccess(), result.getDuration());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("脚本执行异常: {}", scriptId, e);
            ScriptExecutionResult errorResult = ScriptExecutionResult.failure("脚本执行异常: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    /**
     * 转换内置脚本元数据为ScriptInfo
     */
    private ScriptInfo convertBuiltInToScriptInfo(BuiltInScriptMetadata metadata) {
        ScriptSourceType sourceType = metadata.getType().name().equals("STATIC") 
            ? ScriptSourceType.BUILT_IN_STATIC 
            : ScriptSourceType.BUILT_IN_DYNAMIC;

        ScriptInfo scriptInfo = ScriptInfo.builder()
            .id(metadata.getScriptId())
            .name(metadata.getName())
            .description(metadata.getDescription())
            .sourceType(sourceType)
            .parameters(metadata.getParameters())
            .tags(metadata.getTags())
            .version(metadata.getVersion())
            .author(metadata.getAuthor())
            .enabled(true)
            .build();

        scriptInfo.updateRequiresParameters();
        return scriptInfo;
    }

    /**
     * 转换用户定义脚本为ScriptInfo
     */
    private ScriptInfo convertUserDefinedToScriptInfo(String scriptId, UnifiedAtomicScript script) {
        List<ScriptParameter> parameters = script.getInputParameters() != null 
            ? Arrays.asList(script.getInputParameters()) 
            : List.of();

        ScriptInfo scriptInfo = ScriptInfo.builder()
            .id(scriptId)
            .name(script.getName())
            .description(script.getDescription())
            .sourceType(ScriptSourceType.USER_DEFINED)
            .parameters(parameters)
            .tags(script.getTags())
            .version("1.0.0")
            .author("User")
            .enabled(true)
            .build();

        scriptInfo.updateRequiresParameters();
        return scriptInfo;
    }

    /**
     * 创建命令执行上下文
     */
    private CommandContext createCommandContext(SshConnectionConfig sshConfig) {
        // 这里应该基于实际的CommandContext创建逻辑
        // 暂时返回一个基本的上下文，需要根据实际SSH连接配置来实现
        return new CommandContext();
    }

    /**
     * 脚本执行请求DTO
     */
    @lombok.Data
    public static class ScriptExecutionRequestDto {
        private SshConnectionConfig sshConfig;
        private Map<String, Object> parameters;
        private Long timeoutMs;
        private boolean async;
        private String userId;
        private String sessionId;
    }
}