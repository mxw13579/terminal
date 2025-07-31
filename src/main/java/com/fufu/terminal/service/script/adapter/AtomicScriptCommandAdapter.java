package com.fufu.terminal.service.script.adapter;

import com.fufu.terminal.command.AtomicScriptCommand;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.service.script.ScriptExecutionResult;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.UnifiedAtomicScript;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 原子脚本命令适配器
 * 将AtomicScriptCommand接口适配到统一的原子脚本接口
 */
@Slf4j
public class AtomicScriptCommandAdapter implements UnifiedAtomicScript {
    
    private final AtomicScriptCommand atomicCommand;
    private final String scriptId;
    private final String[] tags;
    private final ScriptParameter[] inputParameters;
    private final ScriptParameter[] outputParameters;
    
    public AtomicScriptCommandAdapter(AtomicScriptCommand atomicCommand, String scriptId, String[] tags) {
        this.atomicCommand = atomicCommand;
        this.scriptId = scriptId;
        this.tags = tags != null ? tags : new String[0];
        this.inputParameters = extractInputParameters(atomicCommand);
        this.outputParameters = extractOutputParameters(atomicCommand);
    }
    
    @Override
    public String getScriptId() {
        return scriptId;
    }
    
    @Override
    public String getName() {
        return atomicCommand.getName();
    }
    
    @Override
    public String getDescription() {
        return atomicCommand.getDescription();
    }
    
    @Override
    public ScriptType getScriptType() {
        // 所有原子脚本命令都是内置类型
        return ScriptType.BUILT_IN;
    }
    
    @Override
    public String[] getTags() {
        return tags;
    }
    
    @Override
    public boolean shouldExecute(CommandContext context) {
        return true; // 原子脚本命令默认总是执行
    }
    
    @Override
    public ScriptExecutionResult execute(CommandContext context) throws Exception {
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            log.info("开始执行原子脚本命令: {}", getName());
            
            // 执行原子脚本命令
            CommandResult commandResult = atomicCommand.execute(context);
            
            // 收集输出数据
            Map<String, Object> outputData = collectOutputData(context, commandResult);
            
            // 生成前台显示内容
            String displayOutput = generateDisplayOutput(commandResult);
            
            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(startTime, endTime).toMillis();
            
            ScriptExecutionResult result;
            if (commandResult.isSuccess()) {
                result = ScriptExecutionResult.success(
                    commandResult.getOutput(), 
                    outputData, 
                    displayOutput
                );
            } else {
                result = ScriptExecutionResult.failure(
                    commandResult.getErrorMessage(), 
                    displayOutput
                );
            }
            
            result.setStartTime(startTime);
            result.setDuration(duration);
            
            log.info("原子脚本命令执行完成: {}, 成功: {}, 耗时: {}ms", 
                getName(), commandResult.isSuccess(), duration);
            return result;
            
        } catch (Exception e) {
            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(startTime, endTime).toMillis();
            
            String errorMsg = "脚本执行异常: " + e.getMessage();
            ScriptExecutionResult result = ScriptExecutionResult.failure(errorMsg, errorMsg);
            result.setStartTime(startTime);
            result.setDuration(duration);
            
            log.error("原子脚本命令执行异常: {}, 耗时: {}ms, 错误: {}", getName(), duration, e.getMessage());
            throw e;
        }
    }
    
    @Override
    public ScriptParameter[] getInputParameters() {
        return inputParameters;
    }
    
    @Override
    public ScriptParameter[] getOutputParameters() {
        return outputParameters;
    }
    
    /**
     * 提取输入参数（基于命令类型推断）
     */
    private ScriptParameter[] extractInputParameters(AtomicScriptCommand command) {
        String className = command.getClass().getSimpleName();
        
        if (className.contains("MySQL")) {
            // MySQL安装需要版本和配置参数
            return new ScriptParameter[]{
                new ScriptParameter("mysql_version", ScriptParameter.ParameterType.STRING, 
                    "MySQL版本", false, "8.0", null),
                new ScriptParameter("root_password", ScriptParameter.ParameterType.STRING, 
                    "root密码", true, null, null),
                new ScriptParameter("enable_remote", ScriptParameter.ParameterType.BOOLEAN, 
                    "启用远程访问", false, false, null)
            };
        } else if (className.contains("Redis")) {
            // Redis安装需要版本和配置参数
            return new ScriptParameter[]{
                new ScriptParameter("redis_version", ScriptParameter.ParameterType.STRING, 
                    "Redis版本", false, "7.0", null),
                new ScriptParameter("bind_address", ScriptParameter.ParameterType.STRING, 
                    "绑定地址", false, "127.0.0.1", null),
                new ScriptParameter("port", ScriptParameter.ParameterType.INTEGER, 
                    "端口号", false, 6379, null)
            };
        }
        
        return new ScriptParameter[0];
    }
    
    /**
     * 提取输出参数（基于命令类型推断）
     */
    private ScriptParameter[] extractOutputParameters(AtomicScriptCommand command) {
        return new ScriptParameter[]{
            new ScriptParameter("execution_result", ScriptParameter.ParameterType.STRING, 
                "执行结果", false, null, null),
            new ScriptParameter("success", ScriptParameter.ParameterType.BOOLEAN, 
                "是否成功", false, null, null)
        };
    }
    
    /**
     * 从上下文和命令结果中收集输出数据
     */
    private Map<String, Object> collectOutputData(CommandContext context, CommandResult commandResult) {
        Map<String, Object> outputData = new HashMap<>();
        
        // 基本结果信息
        outputData.put("success", commandResult.isSuccess());
        outputData.put("output", commandResult.getOutput());
        outputData.put("execution_result", commandResult.getOutput());
        
        if (!commandResult.isSuccess()) {
            outputData.put("error_message", commandResult.getErrorMessage());
        }
        
        // 收集上下文属性
        if (context.getProperties() != null) {
            outputData.putAll(context.getProperties());
        }
        
        return outputData;
    }
    
    /**
     * 生成前台显示内容
     */
    private String generateDisplayOutput(CommandResult commandResult) {
        StringBuilder display = new StringBuilder();
        
        if (commandResult.isSuccess()) {
            display.append("✅ ").append(getName()).append(" 执行成功\n");
            display.append("📋 ").append(commandResult.getOutput()).append("\n");
        } else {
            display.append("❌ ").append(getName()).append(" 执行失败\n");
            display.append("⚠️ ").append(commandResult.getErrorMessage()).append("\n");
        }
        
        return display.toString();
    }
}