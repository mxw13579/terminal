package com.fufu.terminal.config;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * STOMP消息认证拦截器
 * 在STOMP消息传输过程中验证用户身份
 * @author lizelin
 */
@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        
        // 只对CONNECT命令进行认证
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            try {
                // 获取token
                String token = getTokenFromHeaders(accessor);
                if (token == null) {
                    log.warn("STOMP连接被拒绝：缺少认证token");
                    return null;
                }

                // 验证token
                StpUtil.checkLogin(token);
                
                // 将用户信息存储到会话属性中
                Object loginId = StpUtil.getLoginIdByToken(token);
                accessor.getSessionAttributes().put("userId", loginId);
                accessor.getSessionAttributes().put("token", token);
                
                log.info("STOMP认证成功，用户ID: {}", loginId);
                
            } catch (Exception e) {
                log.warn("STOMP认证失败: {}", e.getMessage());
                return null;
            }
        }
        
        return message;
    }

    /**
     * 从STOMP Headers中获取token
     */
    private String getTokenFromHeaders(StompHeaderAccessor accessor) {
        // 1. 从Authorization header获取
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        
        // 2. 从自定义token header获取
        String token = accessor.getFirstNativeHeader("token");
        if (token != null && !token.isEmpty()) {
            return token;
        }
        
        // 3. 从satoken header获取
        token = accessor.getFirstNativeHeader("satoken");
        if (token != null && !token.isEmpty()) {
            return token;
        }
        
        return null;
    }
}