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
                
                // 检查是否是用户端连接（通过特殊标识）
                String clientType = accessor.getFirstNativeHeader("client-type");
                boolean isUserClient = "user".equals(clientType);
                
                if (token == null) {
                    if (isUserClient) {
                        // 用户端不需要认证，设置默认用户信息
                        accessor.getSessionAttributes().put("userId", "anonymous");
                        accessor.getSessionAttributes().put("userType", "user");
                        log.info("用户端STOMP连接（无需认证）");
                        return message;
                    } else {
                        log.warn("管理端STOMP连接被拒绝：缺少认证token");
                        return null;
                    }
                }

                // 验证token（管理端或带token的用户端）
                Object loginId = StpUtil.getLoginIdByToken(token);
                if (loginId == null) {
                    if (isUserClient) {
                        // 用户端token无效时也允许连接
                        accessor.getSessionAttributes().put("userId", "anonymous");
                        accessor.getSessionAttributes().put("userType", "user");
                        log.info("用户端STOMP连接（token无效，使用匿名模式）");
                        return message;
                    } else {
                        log.warn("管理端STOMP连接被拒绝：token无效");
                        return null;
                    }
                }
                
                // 将用户信息存储到会话属性中
                accessor.getSessionAttributes().put("userId", loginId);
                accessor.getSessionAttributes().put("token", token);
                accessor.getSessionAttributes().put("userType", isUserClient ? "user" : "admin");
                
                log.info("STOMP认证成功，用户ID: {}, 类型: {}", loginId, isUserClient ? "用户端" : "管理端");
                
            } catch (Exception e) {
                String clientType = accessor.getFirstNativeHeader("client-type");
                boolean isUserClient = "user".equals(clientType);
                
                if (isUserClient) {
                    // 用户端异常时也允许连接
                    accessor.getSessionAttributes().put("userId", "anonymous");
                    accessor.getSessionAttributes().put("userType", "user");
                    log.info("用户端STOMP连接（认证异常，使用匿名模式）: {}", e.getMessage());
                    return message;
                } else {
                    log.warn("管理端STOMP认证失败: {}", e.getMessage());
                    return null;
                }
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