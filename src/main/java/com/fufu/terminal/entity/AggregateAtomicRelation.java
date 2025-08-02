package com.fufu.terminal.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

/**
 * 聚合脚本-原子脚本关联实体 (已重构)
 * 这个类作为中间表，定义了聚合脚本中每个步骤的细节。
 */
@Data
@Entity
@Table(name = "aggregate_atomic_relations")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AggregateAtomicRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // This column is managed by the @JoinColumn in AggregatedScript
    @Column(name = "aggregate_id", nullable = false, updatable = false, insertable = false)
    private Long aggregateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atomic_id", nullable = false)
    private AtomicScript atomicScript;
    
    // Transient field for atomicId access
    @Transient
    public Long getAtomicId() {
        return atomicScript != null ? atomicScript.getId() : null;
    }
    
    public void setAtomicId(Long atomicId) {
        // This setter is for JPA query support
        // The actual relationship is managed through atomicScript field
    }

    @Column(nullable = false)
    private Integer executionOrder;

    @Type(JsonType.class)
    @Column(columnDefinition = "json")
    private String variableMapping; // Maps outputs of previous steps to inputs of this step

    @Column(length = 500)
    private String conditionExpression; // e.g., "${OS_TYPE} == 'Debian'"

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
