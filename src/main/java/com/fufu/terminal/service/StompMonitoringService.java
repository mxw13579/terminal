package com.fufu.terminal.service;

import com.fufu.terminal.config.StompDestinationConfig;
import com.fufu.terminal.model.SshConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 STOMP 协议的监控服务，集成 SshMonitorService 并通过 STOMP 消息推送监控数据。
 * <p>
 * 该服务负责管理会话的监控状态，并将监控数据实时推送到前端。
 * </p>
 * @author lizelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StompMonitoringService {

    private final SshMonitorService sshMonitorService;
    private final SimpMessagingTemplate messagingTemplate;

    /** 活跃监控会话ID 集合 */
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    /**
     * 启动指定会话的监控，并通过 STOMP 推送监控数据。
     *
     * @param sessionId  STOMP 会话 ID
     * @param connection SSH 连接对象
     */
    public void startMonitoring(String sessionId, SshConnection connection) {
        log.info("启动 STOMP 监控，sessionId={}", sessionId);
        activeSessions.add(sessionId);

        try {
            var adapter = new StompMonitoringSessionAdapter(sessionId, messagingTemplate, this);
            sshMonitorService.handleMonitorStartWithSeparateConnection(adapter, connection);
        } catch (Exception e) {
            log.error("启动 STOMP 监控失败，sessionId={}，原因={}", sessionId, e.getMessage(), e);
            activeSessions.remove(sessionId);
            throw e;
        }
    }

    /**
     * 停止指定会话的监控。
     *
     * @param sessionId  STOMP 会话 ID
     * @param connection SSH 连接对象
     */
    public void stopMonitoring(String sessionId, SshConnection connection) {
        log.info("停止 STOMP 监控，sessionId={}", sessionId);
        activeSessions.remove(sessionId);

        try {
            sshMonitorService.handleMonitorStop(connection);
        } catch (Exception e) {
            log.error("停止 STOMP 监控失败，sessionId={}，原因={}", sessionId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 由会话适配器调用，通过 STOMP 向指定会话推送监控数据。
     *
     * @param sessionId      STOMP 会话 ID
     * @param monitoringData 监控数据内容
     */
    public void sendMonitoringUpdate(String sessionId, Map<String, Object> monitoringData) {
        if (!activeSessions.contains(sessionId)) {
            log.debug("会话 {} 已不处于监控状态，跳过数据推送", sessionId);
            return;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "type", "monitor_update",
                    "payload", monitoringData
            );

            messagingTemplate.convertAndSendToUser(
                    sessionId,
                    StompDestinationConfig.USER_MONITOR,
                    payload
            );
            log.debug("已向会话 {} 推送监控数据", sessionId);
        } catch (Exception e) {
            log.error("向会话 {} 推送监控数据失败，原因={}", sessionId, e.getMessage(), e);
            // 出错时移除会话，防止重复推送
            activeSessions.remove(sessionId);
        }
    }

    /**
     * 判断指定会话是否处于监控状态。
     *
     * @param sessionId STOMP 会话 ID
     * @return true 表示正在监控；false 表示未监控
     */
    public boolean isActivelyMonitoring(String sessionId) {
        return activeSessions.contains(sessionId);
    }

    /**
     * 获取当前活跃监控会话的数量。
     *
     * @return 活跃会话数
     */
    public int getActiveMonitoringSessionCount() {
        return activeSessions.size();
    }

    /**
     * 清理指定会话的监控状态（通常在会话断开时调用）。
     *
     * @param sessionId STOMP 会话 ID
     */
    public void cleanupMonitoring(String sessionId) {
        if (activeSessions.remove(sessionId)) {
            log.debug("已清理会话 {} 的监控状态", sessionId);
        }
    }
}
