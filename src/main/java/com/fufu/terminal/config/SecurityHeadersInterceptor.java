package com.fufu.terminal.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 安全头拦截器
 * 添加各种安全相关的HTTP头
 * @author lizelin
 */
@Slf4j
@Component
public class SecurityHeadersInterceptor implements HandlerInterceptor {
    
    @Value("${app.security.csp.policy:default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:}")
    private String cspPolicy;
    
    @Value("${app.security.hsts.enabled:true}")
    private boolean hstsEnabled;
    
    @Value("${app.security.hsts.max-age:31536000}")
    private long hstsMaxAge;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            // Content Security Policy
            response.setHeader("Content-Security-Policy", cspPolicy);
            
            // XSS保护
            response.setHeader("X-XSS-Protection", "1; mode=block");
            
            // 防止MIME类型嗅探
            response.setHeader("X-Content-Type-Options", "nosniff");
            
            // 防止点击劫持
            response.setHeader("X-Frame-Options", "DENY");
            
            // 引用者策略
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            
            // 权限策略
            response.setHeader("Permissions-Policy", 
                "camera=(), microphone=(), geolocation=(), payment=()");
            
            // HSTS（仅HTTPS）
            if (hstsEnabled && request.isSecure()) {
                response.setHeader("Strict-Transport-Security", 
                    String.format("max-age=%d; includeSubDomains; preload", hstsMaxAge));
            }
            
            // 清除服务器信息
            response.setHeader("Server", "");
            
            // 缓存控制（敏感数据）
            if (request.getRequestURI().contains("/api/")) {
                response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Expires", "0");
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error setting security headers", e);
            return true; // 不阻止请求继续处理
        }
    }
}