package com.fufu.terminal.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/**
 * 配置模板实体
 */
@Data
@Entity
@Table(name = "config_templates")
public class ConfigTemplate {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(name = "template_content", nullable = false, columnDefinition = "TEXT")
    private String templateContent; // 模板内容，如docker-compose.yaml
    
    @Column(name = "variable_definitions", columnDefinition = "JSON")
    private String variableDefinitions; // 变量定义和默认值
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}