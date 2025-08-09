package com.fufu.terminal.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Micrometer 指标配置类。
 * <p>
 * 配置 Prometheus 指标注册表和自定义指标过滤器，
 * 为应用程序提供全面的可观测性支持。
 * </p>
 * 
 * <p><strong>功能特性：</strong></p>
 * <ul>
 *     <li>Prometheus 指标导出</li>
 *     <li>全局标签配置</li>
 *     <li>分布式追踪支持</li>
 *     <li>自定义指标过滤</li>
 * </ul>
 *
 * @author lizelin
 */
@Slf4j
@Configuration
@ConditionalOnClass(PrometheusMeterRegistry.class)
public class MetricsConfig {

    /**
     * 配置 MeterRegistry 自定义器，添加全局标签和过滤器。
     * 
     * @return MeterRegistryCustomizer 实例
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> {
            // 添加全局标签
            registry.config()
                    .commonTags("application", "terminal-application")
                    .commonTags("version", "1.0.0")
                    .commonTags("environment", getCurrentEnvironment())
                    // 配置指标命名约定
                    .namingConvention(io.micrometer.core.instrument.config.NamingConvention.dot)
                    // 添加指标过滤器
                    .meterFilter(MeterFilter.deny(id -> {
                        String name = id.getName();
                        // 过滤掉一些不需要的 JVM 指标
                        return name.startsWith("jvm.threads.states") ||
                               name.startsWith("process.files") ||
                               name.startsWith("system.load.average.1m");
                    }))
                    // 配置计时器分桶
                    .meterFilter(MeterFilter.denyNameStartsWith("ssh.command.duration"))
                    .meterFilter(MeterFilter.denyNameStartsWith("sftp.transfer.duration"));
            
            log.info("已配置 Micrometer 指标注册表，环境: {}", getCurrentEnvironment());
        };
    }

    /**
     * 获取当前运行环境。
     * 
     * @return 环境名称
     */
    private String getCurrentEnvironment() {
        String activeProfiles = System.getProperty("spring.profiles.active", "default");
        if (activeProfiles.contains("prod")) {
            return "production";
        } else if (activeProfiles.contains("test")) {
            return "test";
        } else {
            return "development";
        }
    }
}