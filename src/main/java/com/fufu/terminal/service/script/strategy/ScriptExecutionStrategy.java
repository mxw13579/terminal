package com.fufu.terminal.service.script.strategy;

import com.fufu.terminal.service.script.ScriptExecutionResult;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.strategy.model.ScriptExecutionRequest;

import java.util.List;

/**
 * 脚本执行策略接口
 * 使用策略模式处理不同类型的脚本执行逻辑
 */
public interface ScriptExecutionStrategy {

    /**
     * 判断是否能处理指定的脚本类型和执行模式
     * 
     * @param scriptId 脚本ID
     * @param sourceType 脚本来源类型
     * @return 是否能处理
     */
    boolean canHandle(String scriptId, ScriptSourceType sourceType);

    /**
     * 执行脚本
     * 
     * @param request 脚本执行请求
     * @return 脚本执行结果
     */
    ScriptExecutionResult execute(ScriptExecutionRequest request);

    /**
     * 获取脚本所需的参数列表
     * 
     * @param scriptId 脚本ID
     * @return 参数列表，静态脚本返回空列表
     */
    List<ScriptParameter> getRequiredParameters(String scriptId);

    /**
     * 获取策略支持的脚本来源类型
     * 
     * @return 支持的脚本来源类型
     */
    ScriptSourceType getSupportedSourceType();
}