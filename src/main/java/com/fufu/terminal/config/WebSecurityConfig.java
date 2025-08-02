package com.fufu.terminal.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web安全配置
 * 包括安全头和防护措施
 * @author lizelin
 */
@Configuration
@RequiredArgsConstructor
public class WebSecurityConfig implements WebMvcConfigurer {
    
    private final SecurityHeadersInterceptor securityHeadersInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    
    @Value("${app.security.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 添加安全头拦截器
        registry.addInterceptor(securityHeadersInterceptor)
                .addPathPatterns("/**");
        
        // 添加限流拦截器（如果启用）
        if (rateLimitEnabled) {
            registry.addInterceptor(rateLimitInterceptor)
                    .addPathPatterns("/api/**", "/ws/**");
        }
    }
}