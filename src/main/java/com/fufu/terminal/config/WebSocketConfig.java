package com.fufu.terminal.config;

import com.fufu.terminal.handler.SshTerminalWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * @author lizelin
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(sshTerminalWebSocketHandler(), "/ws/terminal")
                // 允许所有来源的连接，方便本地开发，生产环境需要配置具体的来源
                .setAllowedOrigins("*");
    }
    @Bean
    public SshTerminalWebSocketHandler sshTerminalWebSocketHandler() {
        return new SshTerminalWebSocketHandler();
    }
}
