package com.fufu.terminal.entity.context;

import com.fufu.terminal.entity.enums.VariableScope;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 增强的脚本上下文管理器
 */
@Data
@Slf4j
@Component
public class EnhancedScriptContext {
    
    // 系统变量（只读）
    private final Map<String, Object> systemVariables = new ConcurrentHashMap<>();
    
    // 用户输入变量（初始化时设置）
    private final Map<String, Object> userInputVariables = new ConcurrentHashMap<>();
    
    // 运行时变量（脚本执行过程中产生）
    private final Map<String, Object> runtimeVariables = new ConcurrentHashMap<>();
    
    // 配置文件变量（从外部配置文件读取）
    private final Map<String, Object> configVariables = new ConcurrentHashMap<>();
    
    // 用户交互变量（通过交互获得）
    private final Map<String, Object> userInteractionVariables = new ConcurrentHashMap<>();
    
    // 待确认变量（等待用户确认的建议值）
    private final Map<String, PendingConfirmation> pendingConfirmations = new ConcurrentHashMap<>();
    
    // 变量依赖关系
    private final Map<String, List<String>> variableDependencies = new ConcurrentHashMap<>();
    
    /**
     * 按优先级获取变量值
     * 优先级：用户交互输入 > 用户确认选择 > 建议默认值 > 配置文件变量 > 运行时变量 > 系统环境变量
     */
    public Object getVariable(String name) {
        // 1. 用户交互输入变量
        if (userInteractionVariables.containsKey(name)) {
            return userInteractionVariables.get(name);
        }
        
        // 2. 用户确认选择
        PendingConfirmation pending = pendingConfirmations.get(name);
        if (pending != null && pending.isConfirmed() && pending.getUserChoice() != null) {
            return pending.getUserChoice();
        }
        
        // 3. 建议默认值
        if (pending != null && pending.getSuggestedValue() != null) {
            return pending.getSuggestedValue();
        }
        
        // 4. 配置文件变量
        if (configVariables.containsKey(name)) {
            return configVariables.get(name);
        }
        
        // 5. 运行时变量
        if (runtimeVariables.containsKey(name)) {
            return runtimeVariables.get(name);
        }
        
        // 6. 系统环境变量
        if (systemVariables.containsKey(name)) {
            return systemVariables.get(name);
        }
        
        return null;
    }
    
    /**
     * 设置变量值
     */
    public void setVariable(String name, Object value, VariableScope scope) {
        if (value == null) {
            return;
        }
        
        switch (scope) {
            case GLOBAL:
                runtimeVariables.put(name, value);
                break;
            case SESSION:
                userInputVariables.put(name, value);
                break;
            case LOCAL:
                // 局部变量暂时也存储在运行时变量中，可以根据需要扩展
                runtimeVariables.put(name, value);
                break;
        }
        
        log.debug("Set variable: {} = {} (scope: {})", name, value, scope);
    }
    
    /**
     * 设置用户交互变量
     */
    public void setUserInteractionVariable(String name, Object value) {
        userInteractionVariables.put(name, value);
        log.debug("Set user interaction variable: {} = {}", name, value);
    }
    
    /**
     * 设置待确认变量
     */
    public void setPendingConfirmation(String name, PendingConfirmation confirmation) {
        pendingConfirmations.put(name, confirmation);
        log.debug("Set pending confirmation for variable: {}", name);
    }
    
    /**
     * 确认待确认变量
     */
    public void confirmVariable(String name, Object userChoice) {
        PendingConfirmation pending = pendingConfirmations.get(name);
        if (pending != null) {
            pending.setConfirmed(true);
            pending.setUserChoice(userChoice);
            log.debug("Confirmed variable: {} = {}", name, userChoice);
        }
    }
    
    /**
     * 检查变量是否存在
     */
    public boolean hasVariable(String name) {
        return getVariable(name) != null;
    }
    
    /**
     * 获取所有变量名
     */
    public Set<String> getAllVariableNames() {
        Set<String> allNames = new HashSet<>();
        allNames.addAll(systemVariables.keySet());
        allNames.addAll(userInputVariables.keySet());
        allNames.addAll(runtimeVariables.keySet());
        allNames.addAll(configVariables.keySet());
        allNames.addAll(userInteractionVariables.keySet());
        allNames.addAll(pendingConfirmations.keySet());
        return allNames;
    }
    
    /**
     * 初始化系统变量
     */
    public void initializeSystemVariables() {
        systemVariables.put("OS_NAME", System.getProperty("os.name"));
        systemVariables.put("OS_VERSION", System.getProperty("os.version"));
        systemVariables.put("OS_ARCH", System.getProperty("os.arch"));
        systemVariables.put("JAVA_VERSION", System.getProperty("java.version"));
        systemVariables.put("USER_HOME", System.getProperty("user.home"));
        systemVariables.put("USER_NAME", System.getProperty("user.name"));
        systemVariables.put("TIMESTAMP", System.currentTimeMillis());
        
        log.info("Initialized system variables: {}", systemVariables.keySet());
    }
    
    /**
     * 清理上下文
     */
    public void clear() {
        userInputVariables.clear();
        runtimeVariables.clear();
        configVariables.clear();
        userInteractionVariables.clear();
        pendingConfirmations.clear();
        variableDependencies.clear();
        log.debug("Cleared script context");
    }
    
    /**
     * 替换字符串中的变量占位符
     */
    public String resolveVariables(String template) {
        if (template == null || template.trim().isEmpty()) {
            return template;
        }
        
        String result = template;
        // 简单的变量替换，支持 ${variable_name} 格式
        for (String varName : getAllVariableNames()) {
            Object value = getVariable(varName);
            if (value != null) {
                result = result.replace("${" + varName + "}", value.toString());
            }
        }
        
        return result;
    }
}