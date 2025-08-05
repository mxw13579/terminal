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
 * WebSocket STOMP 配置类。
 * <p>
 * 配置 STOMP 端点、消息代理以及客户端入站通道拦截器。
 * 仅在非 test 环境下生效。
 * </p>
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

    /**
     * 注册 STOMP 端点，支持 SockJS 和原生 WebSocket。
     *
     * @param registry STOMP 端点注册器
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册支持 SockJS 的端点
        registry.addEndpoint("/ws/terminal")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        // 注册原生 WebSocket 端点
        registry.addEndpoint("/ws/terminal-native")
                .setAllowedOriginPatterns("*");
        log.info("已注册 STOMP 端点: /ws/terminal (SockJS), /ws/terminal-native (原生)");
    }

    /**
     * 配置消息代理，设置应用前缀和用户前缀。
     *
     * @param config 消息代理注册器
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单消息代理，处理 /queue 和 /topic 前缀的消息
        config.enableSimpleBroker("/queue", "/topic");
        // 设置应用消息前缀，客户端发送消息需以 /app 开头
        config.setApplicationDestinationPrefixes("/app");
        // 设置用户点对点消息前缀
        config.setUserDestinationPrefix("/user");
        log.info("消息代理已配置：/queue, /topic, 应用前缀 /app, 用户前缀 /user");
    }

    /**
     * 配置客户端入站通道，添加认证拦截器。
     *
     * @param registration 通道注册器
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 添加认证拦截器
        registration.interceptors(authInterceptor);
        log.info("已注册 STOMP 认证拦截器");
    }
}
