package com.fufu.terminal.command;

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

    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, Class<T> type) {
        Object value = this.properties.get(key);
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
}
