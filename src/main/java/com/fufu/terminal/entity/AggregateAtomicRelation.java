package com.fufu.terminal.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

/**
 * 聚合脚本-原子脚本关联实体
 */
@Data
@Entity
@Table(name = "aggregate_atomic_relations")
public class AggregateAtomicRelation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;
    
    @Column(name = "atomic_id", nullable = false)
    private Long atomicId;
    
    @Column(name = "execution_order", nullable = false)
    private Integer executionOrder;
    
    @Column(name = "variable_mapping", columnDefinition = "JSON")
    private String variableMapping; // 变量映射配置
    
    @Column(name = "condition_expression", length = 500)
    private String conditionExpression; // 执行条件表达式
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}