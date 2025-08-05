package com.fufu.terminal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * <p>HTTP客户端与JSON序列化配置类。</p>
 * <p>
 * 本配置类为 SillyTavern 服务提供 RestTemplate 和 ObjectMapper Bean，
 * 统一管理 HTTP 客户端超时参数及 JSON 序列化行为，保证服务调用和数据处理的标准化。
 * </p>
 *
 * @author
 */
@Slf4j
@Configuration
@EnableCaching
public class HttpClientConfig {

    /**
     * 构建 RestTemplate Bean，用于 HTTP API 调用。
     * 配置连接超时和读取超时，提升调用健壮性。
     *
     * @param builder RestTemplateBuilder，由Spring自动注入
     * @return 配置好的 RestTemplate 实例
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 构建 ObjectMapper Bean，用于 JSON 序列化与反序列化。
     * 注册 JavaTimeModule 以支持 JDK 8+ 时间类型，并禁用时间戳输出，采用 ISO-8601 格式。
     *
     * @return 配置好的 ObjectMapper 实例
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 注册Java 8时间模块，支持LocalDateTime等类型
        mapper.registerModule(new JavaTimeModule());
        // 禁用时间戳输出，统一为ISO-8601格式
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
