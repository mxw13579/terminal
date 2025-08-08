package com.fufu.terminal.config;

import com.fufu.terminal.handler.SshTerminalWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * 传统 WebSocket 配置类，支持 SSH 终端 WebSocket 通信。
 * <p>
 * <strong>⚠️ 弃用警告：</strong> 此配置类已被标记为弃用。
 * 现有项目应迁移到基于 STOMP 的新架构 {@link WebSocketStompConfig}。
 * </p>
 * <p>
 * <strong>迁移指南：</strong>
 * <ul>
 *     <li>前端：替换 WebSocket 连接为 STOMP 客户端</li>
 *     <li>后端：使用 {@code @MessageMapping} 替代 {@code WebSocketHandler}</li>
 *     <li>消息路由：采用 {@code /user/queue/*} 模式替代自定义路由</li>
 *     <li>认证：集成 {@code StompAuthenticationInterceptor} 安全机制</li>
 * </ul>
 * </p>
 * <p>
 * <strong>移除计划：</strong> 本配置将在下一个主要版本中移除。
 * 当前仅在 'legacy-websocket' 或 'test' profile 下激活，用于向后兼容和测试。
 * </p>
 *
 * @author lizelin
 * @deprecated 自 v2.0 起弃用，请使用 {@link WebSocketStompConfig} 替代。
 * 计划在 v3.0 版本中移除。
 * @see WebSocketStompConfig 推荐的 STOMP 配置
 * @see StompAuthenticationInterceptor STOMP 安全认证
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@ConditionalOnWebApplication
@Profile({"legacy-websocket", "test"})
@Deprecated(since = "2.0", forRemoval = true)
@SuppressWarnings("removal") // 抑制弃用警告
public class LegacyWebSocketConfig implements WebSocketConfigurer {

    /**
     * SSH 终端 WebSocket 处理器，用于管理 WebSocket 连接和数据传输。
     */
    private final SshTerminalWebSocketHandler sshTerminalWebSocketHandler;

    /**
     * 注册 WebSocket 处理器，配置终端处理器的映射路径和跨域设置。
     * <p>
     * ⚠️ <strong>弃用说明：</strong> 此方法仅为向后兼容保留。
     * 新项目请使用 STOMP 消息映射 {@code @MessageMapping("/terminal/data")} 等。
     * </p>
     *
     * @param registry WebSocket 处理器注册器
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // ⚠️ 弃用警告：建议迁移到 STOMP 端点 /ws/stomp
        // 允许所有来源，便于开发调试。生产环境建议配置具体来源。
        registry.addHandler(sshTerminalWebSocketHandler, "/ws/terminal")
                .setAllowedOriginPatterns("*");
    }

    /**
     * 配置 WebSocket 消息大小限制。
     * <p>
     * 该 Bean 会被 Spring Boot 自动用于配置内嵌 WebSocket 服务器（如 Tomcat）。
     * <strong>⚠️ 注意：</strong> STOMP 配置已在 {@code WebSocketStompConfig} 中处理此设置。
     * </p>
     *
     * @return ServletServerContainerFactoryBean 配置消息缓冲区大小
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // 设置文本和二进制消息的最大缓冲区为 2MB
        container.setMaxTextMessageBufferSize(2 * 1024 * 1024);
        container.setMaxBinaryMessageBufferSize(2 * 1024 * 1024);
        return container;
    }
}
