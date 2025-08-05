package com.fufu.terminal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * <p>
 * CORS（跨域资源共享）配置类。
 * </p>
 * <p>
 * 该配置类用于解决前后端分离架构下的跨域访问问题，适配Spring Boot 3.x的安全限制。
 * </p>
 * <ul>
 *     <li>允许所有来源（开发环境）</li>
 *     <li>允许常用HTTP方法</li>
 *     <li>允许所有请求头</li>
 *     <li>支持携带凭据（如Cookie）</li>
 *     <li>设置预检请求缓存时间</li>
 * </ul>
 *
 * @author lizelin
 */
@Configuration
public class CorsConfig {

    /**
     * 创建CORS配置源Bean。
     * <p>
     * 配置允许的跨域请求来源、方法、请求头等参数。
     * </p>
     *
     * @return CorsConfigurationSource 跨域配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 允许所有来源（开发环境建议，生产环境请指定具体域名）
        configuration.setAllowedOriginPatterns(List.of("*"));

        // 允许的HTTP方法
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 允许所有请求头
        configuration.setAllowedHeaders(List.of("*"));

        // 允许携带凭据（如Cookie）
        configuration.setAllowCredentials(true);

        // 预检请求的缓存时间（单位：秒）
        configuration.setMaxAge(3600L);

        // 注册CORS配置
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
