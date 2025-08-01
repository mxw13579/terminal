package com.fufu.terminal.config;

import com.fufu.terminal.handler.SshTerminalWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    
    @Value("${app.security.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String[] allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册WebSocket处理器并添加认证拦截器
        registry.addHandler(sshTerminalWebSocketHandler, "/ws/terminal")
                .setAllowedOrigins(allowedOrigins)
                .addInterceptors(webSocketAuthInterceptor);
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
        // 设置会话空闲超时时间为 30 分钟
        container.setMaxSessionIdleTimeout(30 * 60 * 1000L);
        return container;
    }


}
