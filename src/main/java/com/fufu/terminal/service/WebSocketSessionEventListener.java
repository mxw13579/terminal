package com.fufu.terminal.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * WebSocket 会话生命周期事件监听器。
 * <p>
 * 监听 WebSocket STOMP 会话的连接、断开、订阅和取消订阅事件，
 * 并将这些事件转换为指标数据以供监控系统使用。
 * </p>
 * 
 * <p><strong>监听的事件类型：</strong></p>
 * <ul>
 *     <li>SessionConnectedEvent - 会话连接建立</li>
 *     <li>SessionDisconnectEvent - 会话连接断开</li>
 *     <li>SessionSubscribeEvent - 会话订阅目的地</li>
 *     <li>SessionUnsubscribeEvent - 会话取消订阅</li>
 * </ul>
 *
 * @author lizelin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSessionEventListener {

    private final TerminalMetrics terminalMetrics;

    /**
     * 处理 WebSocket 会话连接建立事件。
     * 
     * @param event 会话连接事件
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        String sessionId = getSessionId(event);
        log.info("WebSocket 会话已连接: {}", sessionId);
        
        // 记录会话创建指标
        terminalMetrics.recordSessionCreated(sessionId);
        terminalMetrics.recordWebSocketConnect();
        
        log.debug("已记录会话连接指标: sessionId={}", sessionId);
    }

    /**
     * 处理 WebSocket 会话断开事件。
     * 
     * @param event 会话断开事件
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = getSessionId(event);
        String disconnectReason = getDisconnectReason(event);
        
        log.info("WebSocket 会话已断开: {}, 原因: {}", sessionId, disconnectReason);
        
        // 记录会话销毁指标
        terminalMetrics.recordSessionDestroyed(sessionId);
        terminalMetrics.recordWebSocketDisconnect(disconnectReason);
        
        log.debug("已记录会话断开指标: sessionId={}, reason={}", sessionId, disconnectReason);
    }

    /**
     * 处理 WebSocket 会话订阅事件。
     * 
     * @param event 会话订阅事件
     */
    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        String sessionId = getSessionId(event);
        String destination = getDestination(event);
        
        log.debug("WebSocket 会话已订阅: sessionId={}, destination={}", sessionId, destination);
        
        // 可以根据需要添加订阅相关的指标
        // terminalMetrics.recordSubscription(destination);
    }

    /**
     * 处理 WebSocket 会话取消订阅事件。
     * 
     * @param event 会话取消订阅事件
     */
    @EventListener
    public void handleSessionUnsubscribe(SessionUnsubscribeEvent event) {
        String sessionId = getSessionId(event);
        String destination = getDestination(event);
        
        log.debug("WebSocket 会话已取消订阅: sessionId={}, destination={}", sessionId, destination);
        
        // 可以根据需要添加取消订阅相关的指标
        // terminalMetrics.recordUnsubscription(destination);
    }

    /**
     * 从事件中提取会话 ID。
     * 
     * @param event WebSocket 事件
     * @return 会话 ID
     */
    private String getSessionId(org.springframework.context.ApplicationEvent event) {
        if (event instanceof SessionConnectedEvent) {
            return ((SessionConnectedEvent) event).getMessage().getHeaders().get("simpSessionId", String.class);
        } else if (event instanceof SessionDisconnectEvent) {
            return ((SessionDisconnectEvent) event).getSessionId();
        } else if (event instanceof SessionSubscribeEvent) {
            return ((SessionSubscribeEvent) event).getMessage().getHeaders().get("simpSessionId", String.class);
        } else if (event instanceof SessionUnsubscribeEvent) {
            return ((SessionUnsubscribeEvent) event).getMessage().getHeaders().get("simpSessionId", String.class);
        }
        return "unknown";
    }

    /**
     * 从断开事件中提取断开原因。
     * 
     * @param event 会话断开事件
     * @return 断开原因
     */
    private String getDisconnectReason(SessionDisconnectEvent event) {
        org.springframework.web.socket.CloseStatus closeStatus = event.getCloseStatus();
        if (closeStatus != null) {
            switch (closeStatus.getCode()) {
                case 1000:
                    return "normal";
                case 1001:
                    return "going_away";
                case 1002:
                    return "protocol_error";
                case 1003:
                    return "unsupported_data";
                case 1006:
                    return "abnormal_closure";
                case 1011:
                    return "server_error";
                default:
                    return "code_" + closeStatus.getCode();
            }
        }
        return "unknown";
    }

    /**
     * 从订阅/取消订阅事件中提取目的地。
     * 
     * @param event 订阅相关事件
     * @return 目的地路径
     */
    private String getDestination(org.springframework.context.ApplicationEvent event) {
        if (event instanceof SessionSubscribeEvent) {
            return ((SessionSubscribeEvent) event).getMessage().getHeaders().get("simpDestination", String.class);
        } else if (event instanceof SessionUnsubscribeEvent) {
            return ((SessionUnsubscribeEvent) event).getMessage().getHeaders().get("simpDestination", String.class);
        }
        return "unknown";
    }
}