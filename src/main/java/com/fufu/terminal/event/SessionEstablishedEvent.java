package com.fufu.terminal.event;

import org.springframework.context.ApplicationEvent;

/**
 * STOMP会话建立事件
 * 当SSH连接通过STOMP认证拦截器成功建立时发布此事件
 * 
 * @author lizelin
 */
public class SessionEstablishedEvent extends ApplicationEvent {

    private final String sessionId;
    private final String message;

    /**
     * 构造会话建立事件
     * 
     * @param source 事件源对象
     * @param sessionId STOMP会话ID
     * @param message 会话建立消息
     */
    public SessionEstablishedEvent(Object source, String sessionId, String message) {
        super(source);
        this.sessionId = sessionId;
        this.message = message;
    }

    /**
     * 获取STOMP会话ID
     * 
     * @return 会话ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取会话建立消息
     * 
     * @return 消息内容
     */
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "SessionEstablishedEvent{" +
                "sessionId='" + sessionId + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}