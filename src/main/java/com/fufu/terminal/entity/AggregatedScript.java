package com.fufu.terminal.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fufu.terminal.entity.enums.AggregateScriptType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 聚合脚本实体 (已重构)
 * 代表一个可执行的工作流，由多个原子脚本按特定顺序和条件组成。
 */
@Data
@Entity
@Table(name = "aggregated_scripts", indexes = {
    @Index(name = "idx_aggregated_script_status_sort", columnList = "status, sort_order"),
    @Index(name = "idx_aggregated_script_created_by", columnList = "created_by")
})
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AggregatedScript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private AggregateScriptType type = AggregateScriptType.GENERIC_TEMPLATE;
    
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    
    @Column(name = "created_by")
    private Long createdBy;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "aggregate_id") // This assumes a foreign key in the relation table
    @OrderBy("executionOrder ASC")
    private List<AggregateAtomicRelation> atomicScriptRelations = new ArrayList<>();

    @Type(JsonType.class)
    @Column(columnDefinition = "json")
    private String configTemplate; // For GENERIC_TEMPLATE type

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Status {
        ACTIVE, INACTIVE
    }

    // Helper method to simplify adding steps
    public void addAtomicScript(AtomicScript atomicScript, int order, String condition, String variableMapping) {
        AggregateAtomicRelation relation = new AggregateAtomicRelation();
        relation.setAggregateId(this.getId());
        relation.setAtomicScript(atomicScript);
        relation.setExecutionOrder(order);
        relation.setConditionExpression(condition);
        relation.setVariableMapping(variableMapping);
        this.atomicScriptRelations.add(relation);
    }
}
