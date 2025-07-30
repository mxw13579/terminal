package com.fufu.terminal.entity;

import com.fufu.terminal.entity.enums.AggregateScriptType;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "aggregated_scripts")
public class AggregatedScript {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private AggregateScriptType type = AggregateScriptType.GENERIC_TEMPLATE; // 聚合脚本类型
    
    @Column(name = "script_ids", columnDefinition = "JSON")
    private String scriptIds; // 存储原子脚本ID数组的JSON
    
    @Column(name = "execution_order", columnDefinition = "JSON")
    private String executionOrder; // 存储执行顺序配置
    
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "enum('ACTIVE', 'INACTIVE') default 'ACTIVE'")
    private Status status = Status.ACTIVE;
    
    @Column(name = "config_template", columnDefinition = "JSON")
    private String configTemplate; // 配置模板，用于通用模板类型
    
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