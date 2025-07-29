package com.fufu.terminal.entity;

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
    
    @Column(name = "group_id", nullable = false)
    private Long groupId;
    
    @Column(name = "script_ids", columnDefinition = "JSON")
    private String scriptIds; // 存储脚本ID数组的JSON
    
    @Column(name = "execution_order", columnDefinition = "JSON")
    private String executionOrder; // 存储执行顺序配置
    
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    
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