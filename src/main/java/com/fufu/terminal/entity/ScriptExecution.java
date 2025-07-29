package com.fufu.terminal.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "script_executions")
public class ScriptExecution {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "script_id", nullable = false)
    private Long scriptId;
    
    @Column(name = "user_id")
    private Long userId;
    
    @Column(name = "session_id", length = 100)
    private String sessionId;
    
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "enum('RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED') default 'RUNNING'")
    private Status status = Status.RUNNING;
    
    @Column(name = "start_time")
    private LocalDateTime startTime = LocalDateTime.now();
    
    @Column(name = "end_time")
    private LocalDateTime endTime;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    public enum Status {
        RUNNING, SUCCESS, FAILED, CANCELLED
    }
}