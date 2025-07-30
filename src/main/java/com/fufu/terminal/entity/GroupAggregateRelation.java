package com.fufu.terminal.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

/**
 * 分组-聚合脚本关联实体
 */
@Data
@Entity
@Table(name = "group_aggregate_relations")
public class GroupAggregateRelation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "group_id", nullable = false)
    private Long groupId;
    
    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;
    
    @Column(name = "display_order")
    private Integer displayOrder = 0;
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}