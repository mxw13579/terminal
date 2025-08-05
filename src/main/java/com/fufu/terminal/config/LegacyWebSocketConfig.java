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
 * 传统WebSocket配置类
 * 为SSH终端应用提供WebSocket配置支持
 * 
 * 此配置保留用于STOMP迁移过程中的向后兼容性
 * 可以通过设置profile为'legacy-websocket'或移除WebSocketStompConfig类来激活
 * 
 * @author lizelin
 * @deprecated 请使用WebSocketStompConfig进行新的基于STOMP的实现
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@ConditionalOnWebApplication
@Profile({"legacy-websocket", "test"})  // Enable for legacy profile or tests
public class LegacyWebSocketConfig implements WebSocketConfigurer {
    
    /**
     * SSH终端WebSocket处理器
     * 用于处理WebSocket连接和SSH终端数据传输
     */
    private final SshTerminalWebSocketHandler sshTerminalWebSocketHandler;

    /**
     * 注册WebSocket处理器
     * 配置WebSocket终端处理器的映射路径和跨域设置
     * 
     * @param registry WebSocket处理器注册器
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 直接使用注入的handler实例，它已经包含了所有必要的依赖
        registry.addHandler(sshTerminalWebSocketHandler, "/ws/terminal")
                // 允许所有来源的连接，方便本地开发，生产环境需要配置具体的来源
                .setAllowedOriginPatterns("*");
    }

    /**
     * 配置 WebSocket 消息大小限制
     * 这个 Bean 会自动被 Spring Boot 用来配置内嵌的 WebSocket 服务器（如 Tomcat）
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // 设置文本消息的最大缓冲区大小为 2MB
        container.setMaxTextMessageBufferSize(2 * 1024 * 1024);
        // 设置二进制消息的最大缓冲区大小为 2MB
        container.setMaxBinaryMessageBufferSize(2 * 1024 * 1024);
        return container;
    }


}
