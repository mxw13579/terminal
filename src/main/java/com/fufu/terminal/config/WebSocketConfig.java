package com.fufu.terminal.config;

import com.fufu.terminal.handler.SshTerminalWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * @author lizelin
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final SshTerminalWebSocketHandler sshTerminalWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 直接使用注入的handler实例，它已经包含了所有必要的依赖
        registry.addHandler(sshTerminalWebSocketHandler, "/ws/terminal")
                // 允许所有来源的连接，方便本地开发，生产环境需要配置具体的来源
                .setAllowedOrigins("*");
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
