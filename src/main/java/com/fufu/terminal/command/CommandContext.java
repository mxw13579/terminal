package com.fufu.terminal.command;

import com.fufu.terminal.command.model.enums.SystemType;
import com.fufu.terminal.model.SshConnection;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 命令执行的上下文环境
 * 用于在责任链中的各个命令之间传递数据
 */
@Slf4j
@Getter
@Setter
public class CommandContext {

    private final SshConnection sshConnection;
    private final WebSocketSession webSocketSession;
    private final Map<String, Object> properties = new ConcurrentHashMap<>();
    
    /**
     * 脚本间变量传递存储
     */
    private Map<String, Object> scriptVariables = new ConcurrentHashMap<>();

    public CommandContext(SshConnection sshConnection, WebSocketSession webSocketSession) {
        this.sshConnection = sshConnection;
        this.webSocketSession = webSocketSession;
    }

    public void setProperty(String key, Object value) {
        this.properties.put(key, value);
    }

    public Object getProperty(String key) {
        return this.properties.get(key);
    }

    /**
     * 获取变量值，如果不存在则返回默认值
     */
    public String getVariable(String name, String defaultValue) {
        Object value = properties.get("variable_" + name);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * 获取变量值，如果不存在则返回默认值
     */
    public <T> T getVariable(String name, Class<T> clazz) {
        Object value = properties.get("variable_" + name);
        return value != null ? (T)value : null;
    }

    /**
     * 获取变量值
     */
    public String getVariable(String name) {
        Object value = properties.get("variable_" + name);
        return value != null ? value.toString() : null;
    }

    /**
     * 设置变量
     */
    public void setVariable(String name, String value) {
        properties.put("variable_" + name, value);
    }

    /**
     * 执行脚本
     */
    public CommandResult executeScript(String script) {
        try {
            if (sshConnection != null && sshConnection.isConnected()) {
                return SshCommandUtil.executeCommand(sshConnection, script);
            } else {
                return CommandResult.failure("SSH连接未建立或已断开");
            }
        } catch (Exception e) {
            return CommandResult.failure("脚本执行异常: " + e.getMessage());
        }
    }

    /**
     * 获取系统类型（模拟实现）
     */
    public SystemType getSystemType() {
        Object systemType = properties.get("systemType");
        if (systemType instanceof SystemType) {
            return (SystemType) systemType;
        }
        return SystemType.UBUNTU; // 默认值
    }

    /**
     * 设置系统类型
     */
    public void setSystemType(SystemType systemType) {
        properties.put("systemType", systemType);
    }

    /**
     * 设置脚本变量（用于脚本间传递）
     * @param name 变量名
     * @param value 变量值
     */
    public void setScriptVariable(String name, Object value) {
        scriptVariables.put(name, value);
        log.info("设置脚本变量: {} = {}", name, value);
    }

    /**
     * 获取脚本变量
     * @param name 变量名
     * @param type 变量类型
     * @return 变量值
     */
    public <T> T getScriptVariable(String name, Class<T> type) {
        Object value = scriptVariables.get(name);
        if (value == null) {
            log.warn("未找到脚本变量: {}", name);
            return null;
        }
        try {
            return type.cast(value);
        } catch (ClassCastException e) {
            log.error("脚本变量类型转换失败: {} -> {}", name, type.getSimpleName());
            return null;
        }
    }

    /**
     * 获取所有脚本变量
     * @return 变量映射表
     */
    public Map<String, Object> getAllScriptVariables() {
        return new HashMap<>(scriptVariables);
    }

    /**
     * 清除脚本变量
     */
    public void clearScriptVariables() {
        scriptVariables.clear();
        log.info("清除所有脚本变量");
    }
}
