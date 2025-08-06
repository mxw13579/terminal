package com.fufu.terminal.config;

import com.fufu.terminal.model.SshConnection;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * STOMP认证拦截器。
 * <p>
 * 该拦截器负责在STOMP连接建立和断开时，自动管理SSH连接的创建与销毁。
 * 每个STOMP会话对应一个SSH连接，便于后续Web终端操作。
 * </p>
 * <ul>
 *     <li>CONNECT命令：建立SSH连接并保存到会话映射表</li>
 *     <li>DISCONNECT命令：清理SSH连接，释放资源</li>
 * </ul>
 *
 * @author lizelin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    /**
     * SSH连接映射表。
     * <p>
     * 键为STOMP会话ID，值为对应的SSH连接对象。
     * </p>
     */
    private final Map<String, SshConnection> connections = new ConcurrentHashMap<>();

    /**
     * 拦截STOMP消息发送前的处理逻辑。
     * <p>
     * CONNECT命令时建立SSH连接，DISCONNECT命令时清理SSH连接。
     * </p>
     *
     * @param message STOMP消息
     * @param channel 消息通道
     * @return 处理后的消息
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null) {
            String sessionId = accessor.getSessionId();

            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                handleConnect(accessor, sessionId);
            } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                handleDisconnect(sessionId);
            }
        }
        return message;
    }

    /**
     * 处理STOMP CONNECT命令，建立SSH连接。
     *
     * @param accessor  STOMP头访问器
     * @param sessionId 会话ID
     */
    private void handleConnect(StompHeaderAccessor accessor, String sessionId) {
        try {
            // 从STOMP头部获取SSH连接参数
            String host = accessor.getFirstNativeHeader("host");
            String portStr = accessor.getFirstNativeHeader("port");
            String user = accessor.getFirstNativeHeader("user");
            String password = accessor.getFirstNativeHeader("password");

            // 校验参数
            if (host == null || user == null || password == null) {
                log.error("会话{}缺少必要的SSH连接参数", sessionId);
                throw new IllegalArgumentException("缺少必要的SSH连接参数");
            }

            int port = portStr != null ? Integer.parseInt(portStr) : 22;

            // 建立SSH连接
            JSch jsch = new JSch();
            Session jschSession = jsch.getSession(user, host, port);
            jschSession.setPassword(password);
            jschSession.setConfig("StrictHostKeyChecking", "no");
            jschSession.setConfig("PreferredAuthentications", "password");
            jschSession.setServerAliveInterval(30000);
            jschSession.setServerAliveCountMax(3);
            jschSession.connect(30000);

            // 创建Shell通道
            ChannelShell channel = (ChannelShell) jschSession.openChannel("shell");
            channel.setPtyType("xterm-256color");
            channel.setPtySize(80, 24, 640, 480);
            InputStream inputStream = channel.getInputStream();
            OutputStream outputStream = channel.getOutputStream();
            channel.connect(10000);

            // 保存SSH连接
            SshConnection sshConnection = new SshConnection(jsch, jschSession, channel, inputStream, outputStream);
            connections.put(sessionId, sshConnection);

            // 存入会话属性，便于后续控制器访问
            accessor.getSessionAttributes().put("sshConnection", sshConnection);
            
            // 关键修复：设置用户身份，使convertAndSendToUser能正确工作
            // 使用sessionId作为用户名，确保消息路由正确
            accessor.setUser(() -> sessionId);
            log.debug("为会话{}设置用户身份: {}", sessionId, sessionId);

            log.info("为STOMP会话{}建立SSH连接 ({}@{}:{})", sessionId, user, host, port);

        } catch (Exception e) {
            log.error("为会话{}建立SSH连接失败: {}", sessionId, e.getMessage(), e);
            // 清理部分建立的连接
            handleDisconnect(sessionId);
            // 不抛出异常，允许STOMP连接继续建立
            log.warn("会话{} SSH连接失败，STOMP连接继续，但SSH功能不可用", sessionId);
        }
    }

    /**
     * 处理STOMP DISCONNECT命令，清理SSH连接。
     *
     * @param sessionId 会话ID
     */
    private void handleDisconnect(String sessionId) {
        if (sessionId != null) {
            SshConnection connection = connections.remove(sessionId);
            if (connection != null) {
                try {
                    connection.disconnect();
                    log.info("已清理会话{}对应的SSH连接", sessionId);
                } catch (Exception e) {
                    log.error("清理会话{}的SSH连接时出错: {}", sessionId, e.getMessage());
                }
            }
        }
    }

    /**
     * 根据会话ID获取SSH连接。
     *
     * @param sessionId 会话ID
     * @return 对应的SSH连接对象，若不存在则返回null
     */
    public SshConnection getConnection(String sessionId) {
        return connections.get(sessionId);
    }

    /**
     * 获取所有活动的SSH连接映射（只读）。
     *
     * @return 当前所有活动连接的不可修改映射
     */
    public Map<String, SshConnection> getAllConnections() {
        return Collections.unmodifiableMap(connections);
    }
}
