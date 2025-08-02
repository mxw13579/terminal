package com.fufu.terminal.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fufu.terminal.entity.enums.InteractionMode;
import com.fufu.terminal.entity.enums.ScriptType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 原子脚本实体类 (已重构)
 * 最小的可执行单元，定义了脚本内容、参数、类型和交互行为。
 */
@Data
@Entity
@Table(name = "atomic_scripts", indexes = {
    @Index(name = "idx_atomic_script_status_sort", columnList = "status, sort_order"),
    @Index(name = "idx_atomic_script_type_status", columnList = "script_type_enum, status"),
    @Index(name = "idx_atomic_script_created_by", columnList = "created_by")
})
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AtomicScript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String scriptContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "script_type_enum")
    private ScriptType scriptType;

    @Enumerated(EnumType.STRING)
    private InteractionMode interactionMode = InteractionMode.SILENT;

    @Type(JsonType.class)
    @Column(columnDefinition = "json")
    private String interactionConfig; // e.g., { "prompt": "...", "options": ["yes", "no"] }

    @Type(JsonType.class)
    @Column(columnDefinition = "json")
    private String inputVariables; // Defines expected input variables, e.g., { "port": "number", "username": "string" }

    @Type(JsonType.class)
    @Column(columnDefinition = "json")
    private String outputVariables; // Defines variables this script will output, e.g., { "detected_version": "string" }

    @Type(JsonType.class)
    @Column(columnDefinition = "json")
    private String prerequisites; // e.g., [{"type": "command", "value": "docker"}, {"type": "os", "value": "debian"}]

    @Column(columnDefinition = "TEXT")
    private String conditionExpression;

    @Column(name = "estimated_duration")
    private Integer estimatedDuration = 0; // In seconds
    
    @Column(name = "version", length = 20)
    private String version = "1.0.0";
    
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    
    @Column(name = "created_by")
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    private Status status = Status.DRAFT;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String getConditionExpression() {
        return conditionExpression;
    }

    // Simplified status enum
    public enum Status {
        ACTIVE,  // Ready to be used
        DRAFT,   // Under development
        INACTIVE // Deprecated or disabled
    }
}
