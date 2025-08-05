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
 * 此配置用于向后兼容，在 STOMP 迁移期间可通过 profile 'legacy-websocket' 或 'test' 激活。
 * 推荐使用 {@link WebSocketStompConfig} 进行新的基于 STOMP 的实现。
 * </p>
 *
 * @author lizelin
 * @deprecated 建议使用 WebSocketStompConfig 替代。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@ConditionalOnWebApplication
@Profile({"legacy-websocket", "test"})
@Deprecated
public class LegacyWebSocketConfig implements WebSocketConfigurer {

    /**
     * SSH 终端 WebSocket 处理器，用于管理 WebSocket 连接和数据传输。
     */
    private final SshTerminalWebSocketHandler sshTerminalWebSocketHandler;

    /**
     * 注册 WebSocket 处理器，配置终端处理器的映射路径和跨域设置。
     *
     * @param registry WebSocket 处理器注册器
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 允许所有来源，便于开发调试。生产环境建议配置具体来源。
        registry.addHandler(sshTerminalWebSocketHandler, "/ws/terminal")
                .setAllowedOriginPatterns("*");
    }

    /**
     * 配置 WebSocket 消息大小限制。
     * <p>
     * 该 Bean 会被 Spring Boot 自动用于配置内嵌 WebSocket 服务器（如 Tomcat）。
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
