package com.fufu.terminal.config;

import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.security.TokenVault;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 *     <li>CONNECT命令：使用授权令牌建立SSH连接并保存到会话映射表</li>
 *     <li>DISCONNECT命令：清理SSH连接，释放资源</li>
 * </ul>
 * 
 * <p><strong>安全改进：</strong></p>
 * <ul>
 *     <li>使用Authorization头传递令牌，替代明文密码传输</li>
 *     <li>支持环境配置的严格主机密钥检查</li>
 *     <li>敏感信息日志过滤</li>
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
     * 令牌保险库，用于安全的凭据检索
     */
    private final TokenVault tokenVault;

    /**
     * SSH严格主机密钥检查配置
     * 生产环境应设置为true，开发环境可设置为false
     */
    @Value("${terminal.security.strict-host-checking:true}")
    private boolean strictHostKeyChecking;

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
     * 处理STOMP CONNECT命令，使用令牌建立SSH连接。
     * 
     * <p><strong>安全改进：</strong></p>
     * <ul>
     *     <li>使用Authorization头获取令牌，不再使用明文密码头</li>
     *     <li>从令牌保险库安全检索凭据</li>
     *     <li>根据环境配置应用严格主机密钥检查</li>
     * </ul>
     *
     * @param accessor  STOMP头访问器
     * @param sessionId 会话ID
     */
    private void handleConnect(StompHeaderAccessor accessor, String sessionId) {
        try {
            // 从Authorization头获取令牌（替代明文密码）
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("会话{}缺少有效的Authorization头", sessionId);
                throw new IllegalArgumentException("缺少有效的授权令牌");
            }
            
            String token = authHeader.substring(7); // 移除 "Bearer " 前缀
            log.debug("收到令牌认证请求，会话: {}, 令牌: {}...", sessionId, token.substring(0, 8));
            
            // 从令牌保险库检索凭据
            TokenVault.VaultEntry credentials = tokenVault.retrieveAndRemove(token);
            if (credentials == null) {
                log.warn("会话{}的令牌无效或已过期", sessionId);
                throw new IllegalArgumentException("令牌无效或已过期");
            }
            
            // 提取凭据信息
            String host = credentials.getHost();
            String portStr = credentials.getPort();
            String user = credentials.getUser();
            String password = credentials.getPassword();
            
            int port = Integer.parseInt(portStr);
            
            log.info("开始为会话{}建立SSH连接到 {}@{}:{}", sessionId, user, host, port);

            // 建立SSH连接
            JSch jsch = new JSch();
            Session jschSession = jsch.getSession(user, host, port);
            jschSession.setPassword(password);
            
            // 根据环境配置设置严格主机密钥检查
            String hostKeyChecking = strictHostKeyChecking ? "yes" : "no";
            jschSession.setConfig("StrictHostKeyChecking", hostKeyChecking);
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

            log.info("为STOMP会话{}建立SSH连接成功 ({}@{}:{})，严格主机检查: {}", 
                    sessionId, user, host, port, hostKeyChecking);

        } catch (IllegalArgumentException e) {
            log.warn("会话{}认证失败: {}", sessionId, e.getMessage());
            // 清理部分建立的连接
            handleDisconnect(sessionId);
            // 认证失败时抛出异常，阻止STOMP连接建立
            throw new IllegalStateException("认证失败: " + e.getMessage(), e);
            
        } catch (Exception e) {
            log.error("为会话{}建立SSH连接失败: {}", sessionId, e.getMessage(), e);
            // 清理部分建立的连接
            handleDisconnect(sessionId);
            // SSH连接失败时抛出异常，阻止STOMP连接建立
            throw new IllegalStateException("SSH连接失败: " + e.getMessage(), e);
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
