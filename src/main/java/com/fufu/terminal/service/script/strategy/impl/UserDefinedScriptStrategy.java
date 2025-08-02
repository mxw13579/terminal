package com.fufu.terminal.service.script.strategy.impl;

import com.fufu.terminal.service.script.ScriptExecutionResult;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.UnifiedAtomicScript;
import com.fufu.terminal.service.script.UnifiedScriptRegistry;  
import com.fufu.terminal.service.script.strategy.ScriptExecutionStrategy;
import com.fufu.terminal.service.script.strategy.ScriptSourceType;
import com.fufu.terminal.service.script.strategy.model.ScriptExecutionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 用户定义脚本执行策略
 * 处理数据库存储的用户自定义脚本，保持现有行为不变
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDefinedScriptStrategy implements ScriptExecutionStrategy {

    private final UnifiedScriptRegistry unifiedScriptRegistry;

    @Override
    public boolean canHandle(String scriptId, ScriptSourceType sourceType) {
        return ScriptSourceType.USER_DEFINED == sourceType && 
               unifiedScriptRegistry.getScript(scriptId) != null;
    }

    @Override
    public ScriptExecutionResult execute(ScriptExecutionRequest request) {
        log.info("开始执行用户定义脚本: {}", request.getScriptId());
        
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            // 获取用户定义脚本
            UnifiedAtomicScript script = unifiedScriptRegistry.getScript(request.getScriptId());
            if (script == null) {
                String errorMsg = "未找到用户定义脚本: " + request.getScriptId();
                log.error(errorMsg);
                return createFailureResult(errorMsg, startTime);
            }

            // 验证必需参数
            String validationError = validateParameters(script, request.getParameters());
            if (validationError != null) {
                log.error("参数验证失败: {}, 脚本: {}", validationError, request.getScriptId());
                return createFailureResult("参数验证失败: " + validationError, startTime);
            }

            // 设置参数到脚本执行上下文
            if (request.getParameters() != null) {
                for (Map.Entry<String, Object> entry : request.getParameters().entrySet()) {
                    request.getCommandContext().setVariable(entry.getKey(), entry.getValue());
                }
            }

            // 执行用户定义脚本
            ScriptExecutionResult result = script.execute(request.getCommandContext());
            
            if (result != null) {
                // 设置执行时间信息
                if (result.getStartTime() == null) {
                    result.setStartTime(startTime);
                }
                if (result.getEndTime() == null) {
                    result.setEndTime(LocalDateTime.now());
                }
                if (result.getDuration() == 0) {
                    result.setDuration(java.time.Duration.between(startTime, result.getEndTime()).toMillis());
                }

                log.info("用户定义脚本执行完成: {}, 成功: {}, 耗时: {}ms", 
                    request.getScriptId(), result.isSuccess(), result.getDuration());
                
                return result;
            } else {
                String errorMsg = "脚本执行返回空结果";
                log.error("用户定义脚本执行失败: {}, 错误: {}", request.getScriptId(), errorMsg);
                return createFailureResult(errorMsg, startTime);
            }
            
        } catch (Exception e) {
            String errorMsg = "脚本执行异常: " + e.getMessage();
            log.error("用户定义脚本执行异常: {}", request.getScriptId(), e);
            return createFailureResult(errorMsg, startTime);
        }
    }

    @Override
    public List<ScriptParameter> getRequiredParameters(String scriptId) {
        UnifiedAtomicScript script = unifiedScriptRegistry.getScript(scriptId);
        if (script != null) {
            ScriptParameter[] inputParams = script.getInputParameters();
            return inputParams != null ? Arrays.asList(inputParams) : List.of();
        }
        return List.of();
    }

    @Override
    public ScriptSourceType getSupportedSourceType() {
        return ScriptSourceType.USER_DEFINED;
    }

    /**
     * 验证参数
     */
    private String validateParameters(UnifiedAtomicScript script, Map<String, Object> providedParameters) {
        ScriptParameter[] requiredParameters = script.getInputParameters();
        if (requiredParameters == null || requiredParameters.length == 0) {
            return null;
        }

        Map<String, Object> params = providedParameters != null ? providedParameters : Map.of();

        for (ScriptParameter requiredParam : requiredParameters) {
            String paramName = requiredParam.getName();
            Object paramValue = params.get(paramName);

            // 检查必需参数
            if (requiredParam.isRequired() && (paramValue == null || paramValue.toString().trim().isEmpty())) {
                return "缺少必需参数: " + paramName;
            }

            // 类型验证
            if (paramValue != null) {
                String typeError = validateParameterType(requiredParam, paramValue);
                if (typeError != null) {
                    return typeError;
                }
            }
        }

        return null;
    }

    /**
     * 验证参数类型
     */
    private String validateParameterType(ScriptParameter parameter, Object value) {
        if (value == null) return null;

        try {
            switch (parameter.getType()) {
                case INTEGER:
                    if (!(value instanceof Integer)) {
                        Integer.parseInt(value.toString());
                    }
                    break;
                case BOOLEAN:
                    if (!(value instanceof Boolean)) {
                        Boolean.parseBoolean(value.toString());
                    }
                    break;
                case STRING:
                    // 字符串类型总是有效的
                    break;
                case ARRAY:
                    if (!(value instanceof List)) {
                        return "参数 " + parameter.getName() + " 应该是数组类型";
                    }
                    break;
                case OBJECT:
                    if (!(value instanceof Map)) {
                        return "参数 " + parameter.getName() + " 应该是对象类型";
                    }
                    break;
            }
        } catch (Exception e) {
            return "参数 " + parameter.getName() + " 类型不匹配，期望: " + parameter.getType().getDisplayName();
        }

        return null;
    }

    /**
     * 创建失败结果
     */
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