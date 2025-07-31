package com.fufu.terminal.command;

import com.fufu.terminal.command.model.enums.SystemType;
import com.fufu.terminal.model.SshConnection;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 命令执行的上下文环境
 * 用于在责任链中的各个命令之间传递数据
 */
@Getter
@Setter
public class CommandContext {

    private final SshConnection sshConnection;
    private final WebSocketSession webSocketSession;
    private final Map<String, Object> properties = new ConcurrentHashMap<>();

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
}
