package com.fufu.terminal.entity;

import com.fufu.terminal.entity.enums.ScriptGroupType;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "script_groups")
public class ScriptGroup {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private ScriptGroupType type = ScriptGroupType.PROJECT_DIMENSION; // 分组类型
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "icon", length = 100)
    private String icon; // 图标
    
    @Column(name = "display_order")
    private Integer displayOrder = 0; // 显示顺序
    
    
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