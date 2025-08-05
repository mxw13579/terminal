package com.fufu.terminal.service;

import com.fufu.terminal.model.SshConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于STOMP协议的监控服务，集成SshMonitorService并通过STOMP消息推送监控数据。
 * <p>
 * 该服务负责管理会话的监控状态，并将监控数据实时推送到前端。
 * </p>
 *
 * @author lizelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StompMonitoringService {

    private final SshMonitorService sshMonitorService;
    private final SimpMessagingTemplate messagingTemplate;

    // 记录当前处于监控状态的会话
    private final Map<String, Boolean> activeMonitoringSessions = new ConcurrentHashMap<>();

    /**
     * 启动指定会话的监控，并通过STOMP推送监控数据。
     *
     * @param sessionId  STOMP会话ID
     * @param connection SSH连接对象
     * @throws RuntimeException 启动监控过程中发生异常时抛出
     */
    public void startMonitoring(String sessionId, SshConnection connection) {
        log.info("启动STOMP监控，sessionId: {}", sessionId);

        try {
            // 标记会话为活跃监控状态
            activeMonitoringSessions.put(sessionId, true);

            // 创建会话适配器，通过STOMP推送监控数据
            StompMonitoringSessionAdapter sessionAdapter = new StompMonitoringSessionAdapter(
                    sessionId, messagingTemplate, this);

            // 使用独立SSH连接启动监控
            sshMonitorService.handleMonitorStartWithSeparateConnection(sessionAdapter, connection);

        } catch (Exception e) {
            log.error("启动STOMP监控失败，sessionId: {}，原因: {}", sessionId, e.getMessage(), e);
            activeMonitoringSessions.remove(sessionId);
            throw e;
        }
    }

    /**
     * 停止指定会话的监控。
     *
     * @param sessionId  STOMP会话ID
     * @param connection SSH连接对象
     */
    public void stopMonitoring(String sessionId, SshConnection connection) {
        log.info("停止STOMP监控，sessionId: {}", sessionId);

        try {
            // 移除活跃监控会话
            activeMonitoringSessions.remove(sessionId);

            // 停止监控
            sshMonitorService.handleMonitorStop(connection);

        } catch (Exception e) {
            log.error("停止STOMP监控失败，sessionId: {}，原因: {}", sessionId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 通过STOMP向指定会话推送监控数据。
     * 该方法由会话适配器调用。
     *
     * @param sessionId      STOMP会话ID
     * @param monitoringData 监控数据内容
     */
    public void sendMonitoringUpdate(String sessionId, Map<String, Object> monitoringData) {
        if (!activeMonitoringSessions.getOrDefault(sessionId, false)) {
            log.debug("会话{}已不处于监控状态，跳过数据推送", sessionId);
            return;
        }

        try {
            // 构建监控数据消息体
            Map<String, Object> updateMessage = Map.of(
                    "type", "monitor_update",
                    "payload", monitoringData
            );

            // 通过STOMP推送到指定用户队列，队列路径建议加斜杠分隔
            messagingTemplate.convertAndSend(
                    "/queue/monitor/data-user/" + sessionId,
                    updateMessage
            );

            log.debug("已向会话{}推送监控数据", sessionId);

        } catch (Exception e) {
            log.error("向会话{}推送监控数据失败，原因: {}", sessionId, e.getMessage(), e);
            // 若发生异常则移除监控状态，防止死循环推送
            activeMonitoringSessions.remove(sessionId);
        }
    }

    /**
     * 判断指定会话是否处于监控状态。
     *
     * @param sessionId STOMP会话ID
     * @return true表示正在监控，false表示未监控
     */
    public boolean isActivelyMonitoring(String sessionId) {
        return activeMonitoringSessions.getOrDefault(sessionId, false);
    }

    /**
     * 获取当前活跃监控会话的数量。
     *
     * @return 活跃会话数
     */
    public int getActiveMonitoringSessionCount() {
        return activeMonitoringSessions.size();
    }

    /**
     * 清理指定会话的监控状态（通常在会话断开时调用）。
     *
     * @param sessionId STOMP会话ID
     */
    public void cleanupMonitoring(String sessionId) {
        activeMonitoringSessions.remove(sessionId);
        log.debug("已清理会话{}的监控状态", sessionId);
    }
}
