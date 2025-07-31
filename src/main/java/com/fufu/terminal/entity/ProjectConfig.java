package com.fufu.terminal.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/**
 * 项目配置实体类
 * 用于存储项目相关的配置变量
 */
@Data
@Entity
@Table(name = "project_configs")
public class ProjectConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String name; // 项目名称
    
    @Column(columnDefinition = "TEXT")
    private String description; // 项目描述
    
    @Column(name = "project_type", length = 50)
    private String projectType; // 项目类型：mysql, redis, docker, etc.
    
    @Column(name = "config_variables", columnDefinition = "JSON")
    private String configVariables; // 配置变量，JSON格式存储
    
    @Column(name = "environment", length = 50)
    private String environment = "development"; // 环境：development, testing, production
    
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "enum('ACTIVE', 'INACTIVE') default 'ACTIVE'")
    private Status status = Status.ACTIVE;
    
    @Column(name = "created_by")
    private Long createdBy;
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum Status {
        ACTIVE, INACTIVE
    }
}