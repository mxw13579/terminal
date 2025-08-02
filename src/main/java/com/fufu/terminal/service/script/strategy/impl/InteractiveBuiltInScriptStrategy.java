package com.fufu.terminal.service.script.strategy.impl;

import com.fufu.terminal.command.AtomicScriptCommand;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.service.script.ScriptExecutionResult;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.strategy.BuiltInScriptMetadata;
import com.fufu.terminal.service.script.strategy.ScriptExecutionStrategy;
import com.fufu.terminal.service.script.strategy.ScriptSourceType;
import com.fufu.terminal.service.script.strategy.model.ScriptExecutionRequest;
import com.fufu.terminal.service.script.strategy.registry.ScriptTypeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 交互内置脚本执行策略
 * 处理需要用户实时交互的内置脚本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractiveBuiltInScriptStrategy implements ScriptExecutionStrategy {

    private final ScriptTypeRegistry scriptTypeRegistry;

    @Override
    public boolean canHandle(String scriptId, ScriptSourceType sourceType) {
        return ScriptSourceType.BUILT_IN_INTERACTIVE == sourceType && 
               scriptTypeRegistry.isInteractiveBuiltInScript(scriptId);
    }

    @Override
    public ScriptExecutionResult execute(ScriptExecutionRequest request) {
        log.info("开始执行交互内置脚本: {}", request.getScriptId());
        
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            // 获取内置脚本命令
            AtomicScriptCommand command = scriptTypeRegistry.getBuiltInScriptCommand(request.getScriptId());
            if (command == null) {
                String errorMsg = "未找到内置脚本命令: " + request.getScriptId();
                log.error(errorMsg);
                return createFailureResult(errorMsg, startTime);
            }

            // 执行交互脚本命令
            CommandResult commandResult = command.execute(request.getCommandContext());
            
            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(startTime, endTime).toMillis();

            if (commandResult.isSuccess()) {
                log.info("交互内置脚本执行成功: {}, 耗时: {}ms", request.getScriptId(), duration);
                
                ScriptExecutionResult result = new ScriptExecutionResult();
                result.setSuccess(true);
                result.setMessage("脚本执行成功");
                result.setStartTime(startTime);
                result.setEndTime(endTime);
                result.setDuration(duration);
                result.setDisplayOutput(commandResult.getOutput());
                result.setDisplayToUser(true);
                result.setRequiresInteraction(true); // 标记需要交互
                result.setOutputData(Map.of(
                    "scriptId", request.getScriptId(),
                    "executionTime", duration,
                    "output", commandResult.getOutput(),
                    "interactionData", commandResult.getInteractionData() // 交互数据
                ));
                
                return result;
            } else {
                String errorMsg = "脚本执行失败: " + commandResult.getErrorMessage();
                log.error("交互内置脚本执行失败: {}, 错误: {}", request.getScriptId(), errorMsg);
                
                ScriptExecutionResult result = createFailureResult(errorMsg, startTime);
                result.setEndTime(endTime);
                result.setDuration(duration);
                result.setDisplayOutput(commandResult.getOutput());
                result.setDisplayToUser(true);
                
                return result;
            }
            
        } catch (Exception e) {
            String errorMsg = "脚本执行异常: " + e.getMessage();
            log.error("交互内置脚本执行异常: {}", request.getScriptId(), e);
            return createFailureResult(errorMsg, startTime);
        }
    }

    @Override
    public List<ScriptParameter> getRequiredParameters(String scriptId) {
        BuiltInScriptMetadata metadata = scriptTypeRegistry.getBuiltInScriptMetadata(scriptId);
        return metadata != null ? metadata.getParameters() : Collections.emptyList();
    }

    @Override
    public ScriptSourceType getSupportedSourceType() {
        return ScriptSourceType.BUILT_IN_INTERACTIVE;
    }

    private ScriptExecutionResult createFailureResult(String errorMessage, LocalDateTime startTime) {
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        result.setStartTime(startTime);
        result.setEndTime(LocalDateTime.now());
        result.setDuration(java.time.Duration.between(startTime, result.getEndTime()).toMillis());
        result.setDisplayToUser(true);
        
        return result;
    }
}