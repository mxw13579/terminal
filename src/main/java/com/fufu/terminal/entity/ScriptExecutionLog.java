package com.fufu.terminal.entity;

import com.fufu.terminal.entity.enums.ExecutionStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 脚本执行日志实体
 */
@Data
@Entity
@Table(name = "script_execution_logs")
public class ScriptExecutionLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "execution_id")
    private Long executionId;
    
    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;
    
    @Column(name = "atomic_script_id")
    private Long atomicScriptId;
    
    @Column(name = "step_name", length = 100)
    private String stepName;
    
    @Column(name = "log_type", length = 20)
    private String logType = "INFO";
    
    @Enumerated(EnumType.STRING)
    private ExecutionStatus status;
    
    @Column(columnDefinition = "TEXT")
    private String message;
    
    @Column(columnDefinition = "TEXT")
    private String output; // 命令输出
    
    @Column(name = "step_order")
    private Integer stepOrder = 0;
    
    @Column(name = "execution_time")
    private Integer executionTime; // 执行耗时（毫秒）
    
    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
}