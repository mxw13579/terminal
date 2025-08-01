package com.fufu.terminal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA配置
 * 启用JPA审计功能
 * @author lizelin
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}