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
            
            // 检查是否是用户端连接（通过URL参数或Header识别）
            boolean isUserClient = isUserClient(request);
            
            if (token == null) {
                if (isUserClient) {
                    // 用户端不需要认证，设置默认用户信息
                    attributes.put("userId", "anonymous");
                    attributes.put("userType", "user");
                    log.info("用户端WebSocket连接（无需认证）");
                    return true;
                } else {
                    log.warn("管理端WebSocket连接被拒绝：缺少认证token");
                    return false;
                }
            }

            // 验证token（管理端或带token的用户端）
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId == null) {
                if (isUserClient) {
                    // 用户端token无效时也允许连接
                    attributes.put("userId", "anonymous");
                    attributes.put("userType", "user");
                    log.info("用户端WebSocket连接（token无效，使用匿名模式）");
                    return true;
                } else {
                    log.warn("管理端WebSocket连接被拒绝：token无效");
                    return false;
                }
            }
            
            // 将用户信息存储到WebSocket会话属性中
            attributes.put("userId", loginId);
            attributes.put("token", token);
            attributes.put("userType", isUserClient ? "user" : "admin");
            
            log.info("WebSocket认证成功，用户ID: {}, 类型: {}", loginId, isUserClient ? "用户端" : "管理端");
            return true;
            
        } catch (Exception e) {
            // 检查是否是用户端连接
            boolean isUserClient = isUserClient(request);
            
            if (isUserClient) {
                // 用户端异常时也允许连接
                attributes.put("userId", "anonymous");
                attributes.put("userType", "user");
                log.info("用户端WebSocket连接（认证异常，使用匿名模式）: {}", e.getMessage());
                return true;
            } else {
                log.warn("管理端WebSocket认证失败: {}", e.getMessage());
                return false;
            }
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
    
    /**
     * 判断是否是用户端连接
     * 通过URL参数或Header判断
     */
    private boolean isUserClient(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            
            // 1. 通过URL参数判断
            String clientType = servletRequest.getServletRequest().getParameter("client-type");
            if ("user".equals(clientType)) {
                return true;
            }
            
            // 2. 通过Header判断
            String clientTypeHeader = servletRequest.getHeaders().getFirst("client-type");
            if ("user".equals(clientTypeHeader)) {
                return true;
            }
            
            // 3. 通过URI路径判断（如果有特定的用户端WebSocket路径）
            String path = request.getURI().getPath();
            if (path != null && path.contains("/user/")) {
                return true;
            }
        }
        
        return false;
    }
}