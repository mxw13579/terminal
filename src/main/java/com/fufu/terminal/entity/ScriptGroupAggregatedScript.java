package com.fufu.terminal.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "script_group_aggregated_scripts")
public class ScriptGroupAggregatedScript {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "group_id", nullable = false)
    private Long groupId;
    
    @Column(name = "aggregated_script_id", nullable = false)
    private Long aggregatedScriptId;
    
    @Column(name = "sort_order", columnDefinition = "int default 0")
    private Integer sortOrder = 0;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    // 关联对象
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", insertable = false, updatable = false)
    private ScriptGroup scriptGroup;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aggregated_script_id", insertable = false, updatable = false)
    private AggregatedScript aggregatedScript;
}