package com.fufu.terminal.service.script.strategy.impl;

import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.service.AtomicScriptService;
import com.fufu.terminal.service.script.ScriptExecutionResult;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.strategy.ScriptExecutionStrategy;
import com.fufu.terminal.service.script.strategy.ScriptSourceType;
import com.fufu.terminal.service.script.strategy.model.ScriptExecutionRequest;
import com.fufu.terminal.command.CommandResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 用户定义脚本执行策略
 * 处理管理员在管理端配置的用户自定义脚本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDefinedScriptStrategy implements ScriptExecutionStrategy {

    private final AtomicScriptService atomicScriptService;
    
    // 参数替换模式：${参数名}
    private static final Pattern PARAMETER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    @Override
    public boolean canHandle(String scriptId, ScriptSourceType sourceType) {
        return ScriptSourceType.USER_DEFINED == sourceType;
    }

    @Override
    public ScriptExecutionResult execute(ScriptExecutionRequest request) {
        log.info("开始执行用户定义脚本: {}", request.getScriptId());
        
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            // 从数据库加载用户脚本
            AtomicScript userScript = atomicScriptService.getById(Long.parseLong(request.getScriptId()));
            if (userScript == null) {
                String errorMsg = "未找到用户脚本: " + request.getScriptId();
                log.error(errorMsg);
                return createFailureResult(errorMsg, startTime);
            }
            
            // 处理脚本参数替换
            String processedScript = processScriptParameters(
                userScript.getScriptContent(), 
                request.getParameters(),
                request.getCommandContext().getAllScriptVariables()
            );
            
            log.info("用户脚本参数处理完成，准备执行");
            log.debug("处理后的脚本内容: {}", processedScript);
            
            // 执行处理后的脚本
            CommandResult commandResult = request.getCommandContext().executeScript(processedScript);
            
            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(startTime, endTime).toMillis();
            
            if (commandResult.isSuccess()) {
                log.info("用户脚本执行成功: {}, 耗时: {}ms", request.getScriptId(), duration);
                
                ScriptExecutionResult result = new ScriptExecutionResult();
                result.setSuccess(true);
                result.setMessage("用户脚本执行成功");
                result.setStartTime(startTime);
                result.setEndTime(endTime);
                result.setDuration(duration);
                result.setDisplayOutput(commandResult.getOutput());
                result.setDisplayToUser(true);
                result.setOutputData(Map.of(
                    "scriptId", request.getScriptId(),
                    "scriptName", userScript.getName(),
                    "executionTime", duration,
                    "output", commandResult.getOutput()
                ));
                
                return result;
            } else {
                String errorMsg = "用户脚本执行失败: " + commandResult.getErrorMessage();
                log.error("用户脚本执行失败: {}, 错误: {}", request.getScriptId(), errorMsg);
                
                ScriptExecutionResult result = createFailureResult(errorMsg, startTime);
                result.setEndTime(endTime);
                result.setDuration(duration);
                result.setDisplayOutput(commandResult.getOutput());
                result.setDisplayToUser(true);
                
                return result;
            }
            
        } catch (Exception e) {
            String errorMsg = "用户脚本执行异常: " + e.getMessage();
            log.error("用户脚本执行异常: {}", request.getScriptId(), e);
            return createFailureResult(errorMsg, startTime);
        }
    }

    @Override
    public List<ScriptParameter> getRequiredParameters(String scriptId) {
        try {
            AtomicScript userScript = atomicScriptService.getById(Long.parseLong(scriptId));
            if (userScript != null && userScript.getInputVariables() != null) {
                // 解析输入变量JSON为ScriptParameter列表
                return parseInputVariables(userScript.getInputVariables());
            }
        } catch (Exception e) {
            log.error("获取用户脚本参数失败: {}", scriptId, e);
        }
        return List.of();
    }

    @Override
    public ScriptSourceType getSupportedSourceType() {
        return ScriptSourceType.USER_DEFINED;
    }
    
    /**
     * 处理脚本参数替换
     * 支持两种参数：
     * 1. 用户输入参数：${mysql_port}
     * 2. 脚本变量：${SERVER_LOCATION}
     */
    private String processScriptParameters(String scriptContent, 
                                         Map<String, Object> parameters,
                                         Map<String, Object> scriptVariables) {
        String result = scriptContent;
        
        Matcher matcher = PARAMETER_PATTERN.matcher(scriptContent);
        while (matcher.find()) {
            String paramName = matcher.group(1);
            String placeholder = "${" + paramName + "}";
            
            Object value = null;
            
            // 优先从用户参数中查找
            if (parameters != null && parameters.containsKey(paramName)) {
                value = parameters.get(paramName);
            }
            // 其次从脚本变量中查找
            else if (scriptVariables != null && scriptVariables.containsKey(paramName)) {
                value = scriptVariables.get(paramName);
            }
            
            if (value != null) {
                result = result.replace(placeholder, String.valueOf(value));
                log.debug("替换参数: {} -> {}", placeholder, value);
            } else {
                log.warn("未找到参数值: {}", paramName);
            }
        }
        
        return result;
    }
    
    /**
     * 解析输入变量JSON为ScriptParameter列表
     */
    private List<ScriptParameter> parseInputVariables(String inputVariablesJson) {
        // 实现JSON解析逻辑
        // 这里简化实现，实际应该使用JSON库解析
        return List.of(); // 占位符
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