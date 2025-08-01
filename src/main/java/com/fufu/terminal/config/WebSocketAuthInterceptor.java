package com.fufu.terminal.config;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket握手认证拦截器
 * 在WebSocket连接建立前验证用户身份
 * @author lizelin
 */
@Slf4j
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        try {
            // 获取token参数
            String token = getTokenFromRequest(request);
            if (token == null) {
                log.warn("WebSocket连接被拒绝：缺少认证token");
                return false;
            }

            // 验证token
            StpUtil.checkLogin(token);
            
            // 将用户信息存储到WebSocket会话属性中
            Object loginId = StpUtil.getLoginIdByToken(token);
            attributes.put("userId", loginId);
            attributes.put("token", token);
            
            log.info("WebSocket认证成功，用户ID: {}", loginId);
            return true;
            
        } catch (Exception e) {
            log.warn("WebSocket认证失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手完成后的处理
        if (exception != null) {
            log.error("WebSocket握手异常", exception);
        }
    }

    /**
     * 从请求中获取token
     * 支持多种方式：URL参数、Header、Cookie
     */
    private String getTokenFromRequest(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            
            // 1. 从URL参数中获取
            String token = servletRequest.getServletRequest().getParameter("token");
            if (token != null && !token.isEmpty()) {
                return token;
            }
            
            // 2. 从Header中获取
            token = servletRequest.getHeaders().getFirst("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                return token.substring(7);
            }
            
            // 3. 从Cookie中获取（Sa-Token默认的token名称）
            token = servletRequest.getHeaders().getFirst("satoken");
            if (token != null && !token.isEmpty()) {
                return token;
            }
        }
        
        return null;
    }
}