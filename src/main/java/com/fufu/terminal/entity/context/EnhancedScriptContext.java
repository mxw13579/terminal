package com.fufu.terminal.entity.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 增强的脚本上下文管理器 (已重构)
 * 每个执行会话拥有独立的上下文实例，支持持久化。
 */
@Data
@Slf4j
public class EnhancedScriptContext {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 关联的执行会话ID
    private String executionSessionId;

    // 存储所有可持久化的上下文变量 (运行时、用户输入、交互结果等)
    private Map<String, Object> persistentVariables = new ConcurrentHashMap<>();

    // 系统变量（只读，不参与持久化）
    private final transient Map<String, Object> systemVariables = new ConcurrentHashMap<>();

    public EnhancedScriptContext(String executionSessionId) {
        this.executionSessionId = executionSessionId;
        initializeSystemVariables();
    }

    /**
     * 获取变量值 (统一入口)
     * 优先级：持久化变量 > 系统变量
     */
    public Object getVariable(String name) {
        if (persistentVariables.containsKey(name)) {
            return persistentVariables.get(name);
        }
        return systemVariables.get(name);
    }

    /**
     * 设置变量值 (统一入口)
     */
    public void setVariable(String name, Object value) {
        if (value == null) {
            persistentVariables.remove(name);
            log.debug("Context [{}]: Removed variable: {}", executionSessionId, name);
        } else {
            persistentVariables.put(name, value);
            log.debug("Context [{}]: Set variable: {} = {}", executionSessionId, name, value);
        }
    }

    /**
     * 将上下文的核心变量序列化为JSON字符串以便存入数据库
     * @return JSON string
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this.persistentVariables);
        } catch (JsonProcessingException e) {
            log.error("Context [{}]: Failed to serialize context variables to JSON", executionSessionId, e);
            return "{}"; // 返回一个空的JSON对象作为回退
        }
    }

    /**
     * 从JSON字符串中加载并恢复上下文的核心变量
     * @param json The JSON string from the database.
     */
    public void fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return;
        }
        try {
            TypeReference<ConcurrentHashMap<String, Object>> typeRef = new TypeReference<>() {};
            this.persistentVariables = objectMapper.readValue(json, typeRef);
            log.info("Context [{}]: Successfully loaded context from JSON.", executionSessionId);
        } catch (JsonProcessingException e) {
            log.error("Context [{}]: Failed to deserialize context variables from JSON", executionSessionId, e);
        }
    }

    /**
     * 替换字符串中的变量占位符
     */
    public String resolveVariables(String template) {
        if (template == null || template.trim().isEmpty()) {
            return template;
        }
        String result = template;
        // 合并系统变量和持久化变量进行解析
        Map<String, Object> allVars = new ConcurrentHashMap<>(systemVariables);
        allVars.putAll(persistentVariables);

        for (Map.Entry<String, Object> entry : allVars.entrySet()) {
            String key = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(key, value);
        }
        return result;
    }

    /**
     * 初始化系统变量 (每次实例化时调用)
     */
    private void initializeSystemVariables() {
        systemVariables.put("OS_NAME", System.getProperty("os.name"));
        systemVariables.put("OS_VERSION", System.getProperty("os.version"));
        systemVariables.put("OS_ARCH", System.getProperty("os.arch"));
        systemVariables.put("USER_HOME", System.getProperty("user.home"));
    }
}
