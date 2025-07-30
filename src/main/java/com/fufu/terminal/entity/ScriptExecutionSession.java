package com.fufu.terminal.entity;

import com.fufu.terminal.entity.enums.ExecutionStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

/**
 * 脚本执行会话实体
 */
@Data
@Entity
@Table(name = "script_execution_sessions")
public class ScriptExecutionSession {
    
    @Id
    @Column(length = 36)
    private String id; // UUID
    
    @Column(name = "user_id")
    private Long userId;
    
    @Column(name = "aggregate_script_id", nullable = false)
    private Long aggregateScriptId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status = ExecutionStatus.PREPARING;
    
    @Column(name = "context_data", columnDefinition = "JSON")
    private String contextData; // 上下文数据的JSON序列化
    
    @Column(name = "start_time")
    private LocalDateTime startTime;
    
    @Column(name = "end_time")
    private LocalDateTime endTime;
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}