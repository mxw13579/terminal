package com.fufu.terminal.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

/**
 * 分组-聚合脚本关联实体 (已重构)
 * Defines the relationship and ordering of aggregated scripts within a group.
 */
@Data
@Entity
@Table(name = "group_aggregate_relations")
public class GroupAggregateRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private ScriptGroup scriptGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aggregate_id", nullable = false)
    private AggregatedScript aggregatedScript;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
