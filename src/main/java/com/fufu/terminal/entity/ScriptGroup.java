package com.fufu.terminal.entity;

import com.fufu.terminal.entity.enums.ScriptGroupType;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 脚本分组实体 (已重构)
 * Organizes aggregated scripts into logical groups for display and management.
 */
@Data
@Entity
@Table(name = "script_groups", indexes = {
    @Index(name = "idx_script_group_status_display", columnList = "status, displayOrder"),
    @Index(name = "idx_script_group_type_status", columnList = "type, status"),
    @Index(name = "idx_script_group_created_by", columnList = "created_by")
})
@EntityListeners(AuditingEntityListener.class)
public class ScriptGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    private ScriptGroupType type = ScriptGroupType.PROJECT_DIMENSION;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String icon;
    
    @Column(name = "display_order")
    private Integer displayOrder = 0;
    
    @Column(name = "created_by")
    private Long createdBy;

    @OneToMany(mappedBy = "scriptGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<GroupAggregateRelation> aggregateRelations = new ArrayList<>();

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

    // Helper method to simplify adding aggregated scripts
    public void addAggregatedScript(AggregatedScript aggregatedScript, int displayOrder) {
        GroupAggregateRelation relation = new GroupAggregateRelation();
        relation.setScriptGroup(this);
        relation.setAggregatedScript(aggregatedScript);
        relation.setDisplayOrder(displayOrder);
        this.aggregateRelations.add(relation);
    }
}
