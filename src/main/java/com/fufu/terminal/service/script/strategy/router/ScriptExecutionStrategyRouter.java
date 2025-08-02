package com.fufu.terminal.service.script.strategy.router;

import com.fufu.terminal.service.script.ScriptExecutionResult;
import com.fufu.terminal.service.script.ScriptParameter;
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

/**
 * 脚本执行策略路由器
 * 根据脚本类型选择合适的执行策略
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptExecutionStrategyRouter {

    private final ScriptTypeRegistry scriptTypeRegistry;
    private final List<ScriptExecutionStrategy> strategies;

    /**
     * 执行脚本
     * 
     * @param request 脚本执行请求
     * @return 脚本执行结果
     */
    public ScriptExecutionResult executeScript(ScriptExecutionRequest request) {
        log.info("开始路由脚本执行: {}", request.getScriptId());

        // 验证请求
        String validationError = validateRequest(request);
        if (validationError != null) {
            log.error("请求验证失败: {}", validationError);
            return createValidationErrorResult(validationError);
        }

        try {
            // 确定脚本类型
            ScriptSourceType sourceType = determineScriptSourceType(request);
            request.setSourceType(sourceType);

            // 选择执行策略
            ScriptExecutionStrategy strategy = selectStrategy(request.getScriptId(), sourceType);
            if (strategy == null) {
                String errorMsg = String.format("未找到适合的执行策略, 脚本: %s, 类型: %s", 
                    request.getScriptId(), sourceType);
                log.error(errorMsg);
                return createStrategyNotFoundResult(errorMsg);
            }

            log.info("选择执行策略: {}, 脚本: {}, 类型: {}", 
                strategy.getClass().getSimpleName(), request.getScriptId(), sourceType);

            // 执行脚本
            return strategy.execute(request);
            
        } catch (Exception e) {
            String errorMsg = "脚本执行路由异常: " + e.getMessage();
            log.error("脚本执行路由异常: {}", request.getScriptId(), e);
            return createExecutionErrorResult(errorMsg);
        }
    }

    /**
     * 获取脚本所需参数
     * 
     * @param scriptId 脚本ID
     * @return 参数列表
     */
    public List<ScriptParameter> getRequiredParameters(String scriptId) {
        if (scriptId == null || scriptId.trim().isEmpty()) {
            log.warn("脚本ID为空，返回空参数列表");
            return Collections.emptyList();
        }

        try {
            // 确定脚本类型
            ScriptSourceType sourceType = scriptTypeRegistry.determineScriptType(scriptId);
            if (sourceType == null) {
                log.warn("无法确定脚本类型: {}", scriptId);
                return Collections.emptyList();
            }

            // 选择策略
            ScriptExecutionStrategy strategy = selectStrategy(scriptId, sourceType);
            if (strategy == null) {
                log.warn("未找到适合的策略: {}, 类型: {}", scriptId, sourceType);
                return Collections.emptyList();
            }

            return strategy.getRequiredParameters(scriptId);
            
        } catch (Exception e) {
            log.error("获取脚本参数异常: {}", scriptId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 确定脚本来源类型
     */
    private ScriptSourceType determineScriptSourceType(ScriptExecutionRequest request) {
        if (request.getSourceType() != null) {
            return request.getSourceType();
        }

        ScriptSourceType sourceType = scriptTypeRegistry.determineScriptType(request.getScriptId());
        return sourceType != null ? sourceType : ScriptSourceType.USER_DEFINED;
    }

    /**
     * 选择执行策略
     */
    private ScriptExecutionStrategy selectStrategy(String scriptId, ScriptSourceType sourceType) {
        for (ScriptExecutionStrategy strategy : strategies) {
            if (strategy.canHandle(scriptId, sourceType)) {
                return strategy;
            }
        }
        return null;
    }

    /**
     * 验证请求
     */
    private String validateRequest(ScriptExecutionRequest request) {
        if (request == null) {
            return "请求不能为空";
        }

        if (request.getScriptId() == null || request.getScriptId().trim().isEmpty()) {
            return "脚本ID不能为空";
        }

        if (request.getCommandContext() == null) {
            return "命令执行上下文不能为空";
        }

        return null;
    }

    /**
     * 创建验证错误结果
     */
    private ScriptExecutionResult createValidationErrorResult(String errorMessage) {
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setSuccess(false);
        result.setErrorMessage("请求验证失败: " + errorMessage);
        result.setStartTime(LocalDateTime.now());
        result.setEndTime(LocalDateTime.now());
        result.setDuration(0);
        result.setDisplayToUser(true);
        return result;
    }

    /**
     * 创建策略未找到错误结果
     */
    private ScriptExecutionResult createStrategyNotFoundResult(String errorMessage) {
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        result.setStartTime(LocalDateTime.now());
        result.setEndTime(LocalDateTime.now());
        result.setDuration(0);
        result.setDisplayToUser(true);
        return result;
    }

    /**
     * 创建执行错误结果
     */
    private ScriptExecutionResult createExecutionErrorResult(String errorMessage) {
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        result.setStartTime(LocalDateTime.now());
        result.setEndTime(LocalDateTime.now());
        result.setDuration(0);
        result.setDisplayToUser(true);
        return result;
    }

    /**
     * 获取所有可用的执行策略
     * 
     * @return 策略列表
     */
    public List<ScriptExecutionStrategy> getAvailableStrategies() {
        return strategies;
    }

    /**
     * 获取指定脚本的执行策略
     * 
     * @param scriptId 脚本ID
     * @return 执行策略，未找到返回null
     */
    public ScriptExecutionStrategy getStrategyForScript(String scriptId) {
        ScriptSourceType sourceType = scriptTypeRegistry.determineScriptType(scriptId);
        return sourceType != null ? selectStrategy(scriptId, sourceType) : null;
    }
}