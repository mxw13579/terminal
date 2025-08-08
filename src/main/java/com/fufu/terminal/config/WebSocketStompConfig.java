package com.fufu.terminal.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

/**
 * WebSocket STOMP 配置类。
 * <p>
 * 配置 STOMP 端点、消息代理以及客户端入站通道拦截器。
 * 仅在非 test 环境下生效。
 * </p>
 * 
 * <p><strong>安全改进：</strong></p>
 * <ul>
 *     <li>根据环境配置不同的跨域策略</li>
 *     <li>生产环境限制允许的来源域名</li>
 *     <li>开发环境允许所有来源（便于开发调试）</li>
 * </ul>
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
     * 生产环境允许的来源域名列表
     */
    @Value("${terminal.security.allowed-origins:}")
    private String allowedOrigins;

    /**
     * 注册 STOMP 端点，支持 SockJS 和原生 WebSocket。
     * <p>
     * 根据Spring Profile应用不同的跨域策略：
     * <ul>
     *     <li>生产环境：仅允许配置的域名访问</li>
     *     <li>开发/测试环境：允许所有来源访问</li>
     * </ul>
     * </p>
     *
     * @param registry STOMP 端点注册器
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 确定允许的来源模式
        String[] originPatterns = determineAllowedOrigins();
        
        // 注册支持 SockJS 的端点
        registry.addEndpoint("/ws/terminal")
                .setAllowedOriginPatterns(originPatterns)
                .withSockJS();
                
        // 注册原生 WebSocket 端点
        registry.addEndpoint("/ws/terminal-native")
                .setAllowedOriginPatterns(originPatterns);
                
        log.info("已注册 STOMP 端点: /ws/terminal (SockJS), /ws/terminal-native (原生)");
        log.info("允许的来源模式: {}", Arrays.toString(originPatterns));
    }

    /**
     * 根据当前环境和配置确定允许的来源模式。
     * 
     * @return 允许的来源模式数组
     */
    private String[] determineAllowedOrigins() {
        // 检查是否为生产环境且配置了特定来源
        if (allowedOrigins != null && !allowedOrigins.trim().isEmpty()) {
            // 解析配置的来源列表
            String[] origins = allowedOrigins.split(",");
            for (int i = 0; i < origins.length; i++) {
                origins[i] = origins[i].trim();
            }
            log.info("使用配置的允许来源: {}", allowedOrigins);
            return origins;
        } else {
            // 开发环境或未配置特定来源时，允许所有来源
            log.info("使用开发模式，允许所有来源访问");
            return new String[]{"*"};
        }
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
