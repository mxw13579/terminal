package com.fufu.terminal.entity;

import com.fufu.terminal.entity.enums.InteractionMode;
import com.fufu.terminal.entity.enums.ScriptType;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/**
 * 原子脚本实体类
 * 原子脚本是最小的执行单元，具有独立的功能和明确的输入输出
 */
@Data
@Entity
@Table(name = "atomic_scripts")
public class AtomicScript {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String scriptContent; // 脚本内容
    
    @Enumerated(EnumType.STRING)
    @Column(name = "script_type", length = 50)
    private ScriptType scriptType = ScriptType.USER_SIMPLE; // 脚本类型
    
    @Column(name = "input_params", columnDefinition = "JSON")
    private String inputParams; // 输入参数定义
    
    @Column(name = "output_params", columnDefinition = "JSON")
    private String outputParams; // 输出参数定义
    
    @Column(name = "dependencies", columnDefinition = "JSON")
    private String dependencies; // 依赖的其他原子脚本或系统要求
    
    @Column(name = "execution_timeout")
    private Integer executionTimeout = 300; // 执行超时时间，默认5分钟
    
    @Column(name = "retry_count")
    private Integer retryCount = 0; // 重试次数
    
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "enum('ACTIVE', 'INACTIVE', 'DRAFT') default 'DRAFT'")
    private Status status = Status.DRAFT;
    
    @Column(name = "version", length = 20)
    private String version = "1.0.0"; // 版本号
    
    @Column(name = "tags", columnDefinition = "JSON")
    private String tags; // 标签，用于分类和搜索
    
    // 交互相关字段
    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_mode")
    private InteractionMode interactionMode = InteractionMode.SILENT;
    
    @Column(name = "interaction_config", columnDefinition = "JSON")
    private String interactionConfig; // 交互配置，JSON格式存储
    
    @Column(name = "input_variables", columnDefinition = "JSON")
    private String inputVariables; // 输入变量定义
    
    @Column(name = "output_variables", columnDefinition = "JSON")
    private String outputVariables; // 输出变量定义
    
    @Column(name = "prerequisites", columnDefinition = "JSON")
    private String prerequisites; // 前置条件
    
    @Column(name = "estimated_duration")
    private Integer estimatedDuration = 0; // 预估执行时长（秒）
    
    @Column(name = "created_by")
    private Long createdBy;
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum Status {
        ACTIVE,    // 活跃，可以被使用
        INACTIVE,  // 非活跃，暂时不可用
        DRAFT      // 草稿，正在开发中
    }
    
}