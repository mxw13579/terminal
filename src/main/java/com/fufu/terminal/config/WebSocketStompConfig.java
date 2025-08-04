package com.fufu.terminal.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP WebSocket配置
 * 配置WebSocket消息代理和STOMP端点
 * 
 * @author lizelin
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnWebApplication
@Profile("!test")
@RequiredArgsConstructor
public class WebSocketStompConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthenticationInterceptor authInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单消息代理，用于处理订阅前缀
        config.enableSimpleBroker("/queue", "/topic");
        
        // 设置应用程序目标前缀，客户端发送到以/app开头的目标
        config.setApplicationDestinationPrefixes("/app");
        
        // 设置用户目标前缀，用于点对点消息
        config.setUserDestinationPrefix("/user");
        
        log.info("STOMP message broker configured with /queue, /topic brokers and /app application prefix");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 注册认证拦截器
        registration.interceptors(authInterceptor);
        log.info("STOMP authentication interceptor registered");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册STOMP端点，客户端连接时使用
        registry.addEndpoint("/ws/terminal")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        
        // 同时注册原生WebSocket端点，用于不支持SockJS的客户端
        registry.addEndpoint("/ws/terminal-native")
                .setAllowedOriginPatterns("*");
        
        log.info("STOMP endpoints registered: /ws/terminal (with SockJS), /ws/terminal-native (native)");
    }
}