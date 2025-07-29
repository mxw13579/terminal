package com.fufu.terminal.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "execution_logs")
public class ExecutionLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "execution_id", nullable = false)
    private Long executionId;
    
    @Column(name = "step_name", length = 200)
    private String stepName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "log_type", columnDefinition = "enum('INFO', 'SUCCESS', 'ERROR', 'WARN', 'DEBUG') default 'INFO'")
    private LogType logType = LogType.INFO;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;
    
    private LocalDateTime timestamp = LocalDateTime.now();
    
    @Column(name = "step_order")
    private Integer stepOrder = 0;
    
    public enum LogType {
        INFO, SUCCESS, ERROR, WARN, DEBUG
    }
}