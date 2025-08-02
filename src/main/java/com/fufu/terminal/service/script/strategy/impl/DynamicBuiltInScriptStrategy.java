package com.fufu.terminal.service.script.strategy.impl;

import com.fufu.terminal.command.AtomicScriptCommand;
import com.fufu.terminal.command.CommandContext;
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
 * 动态内置脚本执行策略
 * 处理需要参数的内置脚本，如Docker安装、MySQL安装
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicBuiltInScriptStrategy implements ScriptExecutionStrategy {

    private final ScriptTypeRegistry scriptTypeRegistry;

    @Override
    public boolean canHandle(String scriptId, ScriptSourceType sourceType) {
        return ScriptSourceType.BUILT_IN_DYNAMIC == sourceType &&
               scriptTypeRegistry.isDynamicBuiltInScript(scriptId);
    }

    @Override
    public ScriptExecutionResult execute(ScriptExecutionRequest request) {
        log.info("开始执行动态内置脚本: {}", request.getScriptId());

        LocalDateTime startTime = LocalDateTime.now();

        try {
            // 获取内置脚本命令
            AtomicScriptCommand command = scriptTypeRegistry.getBuiltInScriptCommand(request.getScriptId());
            if (command == null) {
                String errorMsg = "未找到内置脚本命令: " + request.getScriptId();
                log.error(errorMsg);
                return createFailureResult(errorMsg, startTime);
            }

            // 获取脚本元数据并验证参数
            BuiltInScriptMetadata metadata = scriptTypeRegistry.getBuiltInScriptMetadata(request.getScriptId());
            if (metadata == null) {
                String errorMsg = "未找到脚本元数据: " + request.getScriptId();
                log.error(errorMsg);
                return createFailureResult(errorMsg, startTime);
            }

            // 验证必需参数
            String validationError = validateParameters(metadata.getParameters(), request.getParameters());
            if (validationError != null) {
                log.error("参数验证失败: {}, 脚本: {}", validationError, request.getScriptId());
                return createFailureResult("参数验证失败: " + validationError, startTime);
            }

            // 设置参数到命令上下文
            CommandContext context = request.getCommandContext();
            if (request.getParameters() != null) {
                for (Map.Entry<String, Object> entry : request.getParameters().entrySet()) {
                    context.setVariable(entry.getKey(), entry.getValue().toString());
                }
            }

            // 执行脚本命令
            CommandResult commandResult = command.execute(context);

            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(startTime, endTime).toMillis();

            if (commandResult.isSuccess()) {
                log.info("动态内置脚本执行成功: {}, 耗时: {}ms", request.getScriptId(), duration);

                ScriptExecutionResult result = new ScriptExecutionResult();
                result.setSuccess(true);
                result.setMessage("脚本执行成功");
                result.setStartTime(startTime);
                result.setEndTime(endTime);
                result.setDuration(duration);
                result.setDisplayOutput(commandResult.getOutput());
                result.setDisplayToUser(true);
                result.setOutputData(Map.of(
                    "scriptId", request.getScriptId(),
                    "executionTime", duration,
                    "parameters", request.getParameters() != null ? request.getParameters() : Collections.emptyMap(),
                    "output", commandResult.getOutput()
                ));

                return result;
            } else {
                String errorMsg = "脚本执行失败: " + commandResult.getErrorMessage();
                log.error("动态内置脚本执行失败: {}, 错误: {}", request.getScriptId(), errorMsg);

                ScriptExecutionResult result = createFailureResult(errorMsg, startTime);
                result.setEndTime(endTime);
                result.setDuration(duration);
                result.setDisplayOutput(commandResult.getOutput());
                result.setDisplayToUser(true);

                return result;
            }

        } catch (Exception e) {
            String errorMsg = "脚本执行异常: " + e.getMessage();
            log.error("动态内置脚本执行异常: {}", request.getScriptId(), e);
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
        return ScriptSourceType.BUILT_IN_DYNAMIC;
    }

    /**
     * 验证参数
     */
    private String validateParameters(List<ScriptParameter> requiredParameters, Map<String, Object> providedParameters) {
        if (requiredParameters == null || requiredParameters.isEmpty()) {
            return null;
        }

        Map<String, Object> params = providedParameters != null ? providedParameters : Collections.emptyMap();

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
            } else if (requiredParam.getDefaultValue() != null) {
                // 使用默认值
                params.put(paramName, requiredParam.getDefaultValue());
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
