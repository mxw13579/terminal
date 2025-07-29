package com.fufu.terminal.service.script.adapter;

import com.fufu.terminal.command.Command;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.service.script.ScriptExecutionResult;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.UnifiedAtomicScript;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 内置脚本适配器
 * 将现有的Command接口适配到统一的原子脚本接口
 */
@Slf4j
public class BuiltInScriptAdapter implements UnifiedAtomicScript {
    
    private final Command command;
    private final String scriptId;
    private final String[] tags;
    private final ScriptParameter[] inputParameters;
    private final ScriptParameter[] outputParameters;
    
    public BuiltInScriptAdapter(Command command, String scriptId, String[] tags) {
        this.command = command;
        this.scriptId = scriptId;
        this.tags = tags != null ? tags : new String[0];
        this.inputParameters = extractInputParameters(command);
        this.outputParameters = extractOutputParameters(command);
    }
    
    @Override
    public String getScriptId() {
        return scriptId;
    }
    
    @Override
    public String getName() {
        return command.getName();
    }
    
    @Override
    public String getDescription() {
        // 从类注释或者类名生成描述
        return generateDescription(command);
    }
    
    @Override
    public ScriptType getScriptType() {
        return ScriptType.BUILT_IN;
    }
    
    @Override
    public String[] getTags() {
        return tags;
    }
    
    @Override
    public boolean shouldExecute(CommandContext context) {
        return command.shouldExecute(context);
    }
    
    @Override
    public ScriptExecutionResult execute(CommandContext context) throws Exception {
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            log.info("开始执行内置脚本: {}", getName());
            
            // 执行内置命令
            command.execute(context);
            
            // 收集输出数据
            Map<String, Object> outputData = collectOutputData(context);
            
            // 生成前台显示内容
            String displayOutput = generateDisplayOutput(context, outputData);
            
            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(startTime, endTime).toMillis();
            
            ScriptExecutionResult result = ScriptExecutionResult.success(
                "脚本执行成功: " + getName(), 
                outputData, 
                displayOutput
            );
            result.setStartTime(startTime);
            result.setDuration(duration);
            
            log.info("内置脚本执行成功: {}, 耗时: {}ms", getName(), duration);
            return result;
            
        } catch (Exception e) {
            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(startTime, endTime).toMillis();
            
            String errorMsg = "脚本执行失败: " + e.getMessage();
            ScriptExecutionResult result = ScriptExecutionResult.failure(errorMsg, errorMsg);
            result.setStartTime(startTime);
            result.setDuration(duration);
            
            log.error("内置脚本执行失败: {}, 耗时: {}ms, 错误: {}", getName(), duration, e.getMessage());
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
     * 从Command生成描述信息
     */
    private String generateDescription(Command command) {
        String className = command.getClass().getSimpleName();
        
        // 根据不同的命令类型生成描述
        if (className.contains("Detect")) {
            return "检测系统信息: " + command.getName();
        } else if (className.contains("Check")) {
            return "检查环境依赖: " + command.getName();
        } else if (className.contains("Configure")) {
            return "配置系统设置: " + command.getName();
        } else {
            return "执行系统命令: " + command.getName();
        }
    }
    
    /**
     * 提取输入参数（基于命令类型推断）
     */
    private ScriptParameter[] extractInputParameters(Command command) {
        String className = command.getClass().getSimpleName();
        
        if (className.contains("Configure")) {
            // 配置类命令通常需要配置参数
            return new ScriptParameter[]{
                new ScriptParameter("config_enabled", ScriptParameter.ParameterType.BOOLEAN, 
                    "是否启用配置", false, true, null)
            };
        } else if (className.contains("Check")) {
            // 检查类命令可能需要检查参数
            return new ScriptParameter[]{
                new ScriptParameter("strict_check", ScriptParameter.ParameterType.BOOLEAN, 
                    "是否严格检查", false, false, null)
            };
        }
        
        return new ScriptParameter[0];
    }
    
    /**
     * 提取输出参数（基于命令类型推断）
     */
    private ScriptParameter[] extractOutputParameters(Command command) {
        String className = command.getClass().getSimpleName();
        
        if (className.contains("Detect")) {
            return new ScriptParameter[]{
                new ScriptParameter("detected_info", ScriptParameter.ParameterType.OBJECT, 
                    "检测到的系统信息", false, null, null)
            };
        } else if (className.contains("Check")) {
            return new ScriptParameter[]{
                new ScriptParameter("check_result", ScriptParameter.ParameterType.BOOLEAN, 
                    "检查结果", false, null, null),
                new ScriptParameter("check_message", ScriptParameter.ParameterType.STRING, 
                    "检查消息", false, null, null)
            };
        }
        
        return new ScriptParameter[]{
            new ScriptParameter("execution_result", ScriptParameter.ParameterType.STRING, 
                "执行结果", false, null, null)
        };
    }
    
    /**
     * 从上下文中收集输出数据
     */
    private Map<String, Object> collectOutputData(CommandContext context) {
        Map<String, Object> outputData = new HashMap<>();
        
        // 收集常见的输出数据
        Object osInfo = context.getProperty("os_info");
        if (osInfo != null) {
            outputData.put("os_info", osInfo);
        }
        
        Object locationInfo = context.getProperty("location_info");
        if (locationInfo != null) {
            outputData.put("location_info", locationInfo);
        }
        
        // 收集所有上下文属性
        if (context.getProperties() != null) {
            outputData.putAll(context.getProperties());
        }
        
        return outputData;
    }
    
    /**
     * 生成前台显示内容
     */
    private String generateDisplayOutput(CommandContext context, Map<String, Object> outputData) {
        StringBuilder display = new StringBuilder();
        display.append("✅ ").append(getName()).append(" 执行完成\n");
        
        // 根据命令类型生成不同的显示内容
        String className = command.getClass().getSimpleName();
        
        if (className.contains("DetectOs")) {
            Object osInfo = outputData.get("os_info");
            if (osInfo != null) {
                display.append("📋 检测到系统信息: ").append(osInfo.toString()).append("\n");
            }
        } else if (className.contains("DetectLocation")) {
            Object locationInfo = outputData.get("location_info");
            if (locationInfo != null) {
                display.append("🌍 检测到地理位置: ").append(locationInfo.toString()).append("\n");
            }
        } else if (className.contains("Check")) {
            display.append("🔍 环境检查完成\n");
        } else if (className.contains("Configure")) {
            display.append("⚙️ 系统配置完成\n");
        }
        
        return display.toString();
    }
}