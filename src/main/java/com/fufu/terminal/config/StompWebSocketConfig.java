package com.fufu.terminal.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * Enhanced STOMP WebSocket Configuration
 * 
 * Provides comprehensive WebSocket STOMP configuration with heartbeat,
 * authentication, session management, and message routing for real-time
 * script execution progress and user interaction.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class StompWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    
    @Value("${app.websocket.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String[] allowedOrigins;
    
    @Value("${app.websocket.heartbeat.client:10000}")
    private long clientHeartbeat;
    
    @Value("${app.websocket.heartbeat.server:10000}")
    private long serverHeartbeat;
    
    @Value("${app.websocket.message.max-size:64000}")
    private int maxMessageSize;
    
    @Value("${app.websocket.buffer.send-size:512000}")
    private int sendBufferSize;
    
    @Value("${app.websocket.timeout.send:20000}")
    private long sendTimeout;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple message broker for sending messages to clients
        config.enableSimpleBroker("/topic", "/queue")
              .setHeartbeatValue(new long[]{serverHeartbeat, clientHeartbeat})
              .setTaskScheduler(heartbeatTaskScheduler());
        
        // Set application destination prefix for messages bound for @MessageMapping
        config.setApplicationDestinationPrefixes("/app");
        
        // Configure user destination prefix for personalized messages
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register STOMP endpoint with enhanced configuration
        registry.addEndpoint("/ws/stomp")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS()
                .setSessionCookieNeeded(false)
                .setHeartbeatTime(25000) // SockJS heartbeat
                .setDisconnectDelay(5000) // Delay before closing connection
                .setHttpMessageCacheSize(1000) // Cache size for HTTP streaming
                .setStreamBytesLimit(128 * 1024); // 128KB limit for streaming
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(maxMessageSize) // Max message size
                   .setSendBufferSizeLimit(sendBufferSize) // Send buffer size
                   .setSendTimeLimit(sendTimeout) // Send timeout
                   .setTimeToFirstMessage(30000); // Time to first message
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Add enhanced STOMP authentication interceptor
        registration.interceptors(stompAuthChannelInterceptor)
                   .taskExecutor()
                   .corePoolSize(4)
                   .maxPoolSize(8)
                   .keepAliveSeconds(60);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // Configure outbound channel for better performance
        registration.taskExecutor()
                   .corePoolSize(4)
                   .maxPoolSize(8)
                   .keepAliveSeconds(60);
    }
    
    /**
     * Task scheduler for WebSocket heartbeat
     */
    @Bean
    public TaskScheduler heartbeatTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("websocket-heartbeat-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }
}