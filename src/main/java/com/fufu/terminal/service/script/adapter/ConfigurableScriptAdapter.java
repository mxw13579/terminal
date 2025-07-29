package com.fufu.terminal.service.script.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.SshCommandUtil;
import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.service.script.ScriptExecutionResult;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.UnifiedAtomicScript;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 可配置脚本适配器
 * 将AtomicScript实体适配到统一的原子脚本接口
 */
@Slf4j
public class ConfigurableScriptAdapter implements UnifiedAtomicScript {
    
    private final AtomicScript atomicScript;
    private final ObjectMapper objectMapper;
    private ScriptParameter[] inputParameters;
    private ScriptParameter[] outputParameters;
    
    public ConfigurableScriptAdapter(AtomicScript atomicScript) {
        this.atomicScript = atomicScript;
        this.objectMapper = new ObjectMapper();
        this.inputParameters = parseParameters(atomicScript.getInputParams());
        this.outputParameters = parseParameters(atomicScript.getOutputParams());
    }
    
    @Override
    public String getScriptId() {
        return "configurable_" + atomicScript.getId();
    }
    
    @Override
    public String getName() {
        return atomicScript.getName();
    }
    
    @Override
    public String getDescription() {
        return atomicScript.getDescription();
    }
    
    @Override
    public ScriptType getScriptType() {
        return ScriptType.CONFIGURABLE;
    }
    
    @Override
    public String[] getTags() {
        if (atomicScript.getTags() != null) {
            try {
                List<String> tagList = objectMapper.readValue(atomicScript.getTags(), 
                    new TypeReference<List<String>>() {});
                return tagList.toArray(new String[0]);
            } catch (Exception e) {
                log.warn("解析脚本标签失败: {}", e.getMessage());
            }
        }
        return new String[]{atomicScript.getScriptType()};
    }
    
    @Override
    public boolean shouldExecute(CommandContext context) {
        // 可配置脚本默认都可以执行，除非明确设置为INACTIVE
        return AtomicScript.Status.ACTIVE.equals(atomicScript.getStatus());
    }
    
    @Override
    public ScriptExecutionResult execute(CommandContext context) throws Exception {
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            log.info("开始执行可配置脚本: {}", getName());
            
            // 替换脚本中的变量
            String processedScript = processScriptContent(context);
            
            // 根据脚本类型执行
            String output = executeScript(context, processedScript);
            
            // 收集输出数据
            Map<String, Object> outputData = extractOutputData(output, context);
            
            // 生成前台显示内容
            String displayOutput = generateDisplayOutput(output);
            
            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(startTime, endTime).toMillis();
            
            ScriptExecutionResult result = ScriptExecutionResult.success(
                "脚本执行成功: " + getName(), 
                outputData, 
                displayOutput
            );
            result.setStartTime(startTime);
            result.setDuration(duration);
            
            log.info("可配置脚本执行成功: {}, 耗时: {}ms", getName(), duration);
            return result;
            
        } catch (Exception e) {
            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(startTime, endTime).toMillis();
            
            String errorMsg = "脚本执行失败: " + e.getMessage();
            ScriptExecutionResult result = ScriptExecutionResult.failure(errorMsg, errorMsg);
            result.setStartTime(startTime);
            result.setDuration(duration);
            
            log.error("可配置脚本执行失败: {}, 耗时: {}ms, 错误: {}", getName(), duration, e.getMessage());
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
     * 解析参数定义JSON
     */
    private ScriptParameter[] parseParameters(String parametersJson) {
        if (parametersJson == null || parametersJson.trim().isEmpty()) {
            return new ScriptParameter[0];
        }
        
        try {
            Map<String, Object> paramsMap = objectMapper.readValue(parametersJson, Map.class);
            return paramsMap.entrySet().stream()
                .map(entry -> {
                    String name = entry.getKey();
                    Object value = entry.getValue();
                    
                    if (value instanceof Map) {
                        Map<String, Object> paramDef = (Map<String, Object>) value;
                        return new ScriptParameter(
                            name,
                            parseParameterType((String) paramDef.get("type")),
                            (String) paramDef.getOrDefault("description", ""),
                            (Boolean) paramDef.getOrDefault("required", false),
                            paramDef.get("defaultValue"),
                            (String) paramDef.get("validationRule")
                        );
                    } else {
                        // 简单格式：{"param1": "string", "param2": "integer"}
                        return new ScriptParameter(
                            name,
                            parseParameterType(value.toString()),
                            "",
                            false,
                            null,
                            null
                        );
                    }
                })
                .toArray(ScriptParameter[]::new);
        } catch (Exception e) {
            log.warn("解析参数定义失败: {}", e.getMessage());
            return new ScriptParameter[0];
        }
    }
    
    /**
     * 解析参数类型
     */
    private ScriptParameter.ParameterType parseParameterType(String typeStr) {
        if (typeStr == null) return ScriptParameter.ParameterType.STRING;
        
        return switch (typeStr.toLowerCase()) {
            case "integer", "int", "number" -> ScriptParameter.ParameterType.INTEGER;
            case "boolean", "bool" -> ScriptParameter.ParameterType.BOOLEAN;
            case "array", "list" -> ScriptParameter.ParameterType.ARRAY;
            case "object", "map" -> ScriptParameter.ParameterType.OBJECT;
            default -> ScriptParameter.ParameterType.STRING;
        };
    }
    
    /**
     * 处理脚本内容，替换变量
     */
    private String processScriptContent(CommandContext context) {
        String script = atomicScript.getScriptContent();
        
        // 替换上下文变量 ${variable_name}
        Pattern pattern = Pattern.compile("\\$\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(script);
        
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = context.getProperty(varName);
            String replacement = value != null ? value.toString() : matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * 执行脚本
     */
    private String executeScript(CommandContext context, String script) throws Exception {
        String scriptType = atomicScript.getScriptType();
        Integer timeout = atomicScript.getExecutionTimeout();
        
        if ("bash".equalsIgnoreCase(scriptType)) {
            return executeBashScript(context, script, timeout);
        } else if ("python".equalsIgnoreCase(scriptType)) {
            return executePythonScript(context, script, timeout);
        } else {
            // 默认使用SSH直接执行
            CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), script);
            if (!result.isSuccess()) {
                throw new Exception("脚本执行失败: " + result.getStderr());
            }
            return result.getStdout();
        }
    }
    
    /**
     * 执行Bash脚本
     */
    private String executeBashScript(CommandContext context, String script, Integer timeout) throws Exception {
        CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), "bash -c '" + script + "'");
        if (!result.isSuccess()) {
            throw new Exception("Bash脚本执行失败: " + result.getStderr());
        }
        return result.getStdout();
    }
    
    /**
     * 执行Python脚本
     */
    private String executePythonScript(CommandContext context, String script, Integer timeout) throws Exception {
        CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), "python3 -c \"" + script + "\"");
        if (!result.isSuccess()) {
            throw new Exception("Python脚本执行失败: " + result.getStderr());
        }
        return result.getStdout();
    }
    
    /**
     * 从输出中提取数据
     */
    private Map<String, Object> extractOutputData(String output, CommandContext context) {
        Map<String, Object> outputData = new HashMap<>();
        outputData.put("script_output", output);
        outputData.put("script_name", getName());
        outputData.put("script_type", atomicScript.getScriptType());
        
        // 尝试解析JSON格式的输出
        if (output.trim().startsWith("{") && output.trim().endsWith("}")) {
            try {
                Map<String, Object> jsonOutput = objectMapper.readValue(output, Map.class);
                outputData.putAll(jsonOutput);
            } catch (Exception e) {
                log.debug("输出不是有效的JSON格式，作为普通文本处理");
            }
        }
        
        return outputData;
    }
    
    /**
     * 生成前台显示内容
     */
    private String generateDisplayOutput(String output) {
        StringBuilder display = new StringBuilder();
        display.append("🔧 ").append(getName()).append(" 执行完成\n");
        
        // 如果输出内容不为空，显示关键信息
        if (output != null && !output.trim().isEmpty()) {
            String[] lines = output.split("\n");
            
            // 显示前几行或者重要信息
            int displayLines = Math.min(3, lines.length);
            for (int i = 0; i < displayLines; i++) {
                if (lines[i].trim().length() > 0) {
                    display.append("📄 ").append(lines[i].trim()).append("\n");
                }
            }
            
            if (lines.length > displayLines) {
                display.append("... (还有 ").append(lines.length - displayLines).append(" 行输出)\n");
            }
        }
        
        return display.toString();
    }
}