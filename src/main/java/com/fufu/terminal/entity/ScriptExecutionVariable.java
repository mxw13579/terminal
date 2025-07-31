package com.fufu.terminal.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

/**
 * 脚本执行变量实体类
 * 用于存储脚本执行时的变量值
 */
@Data
@Entity
@Table(name = "script_execution_variables")
public class ScriptExecutionVariable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "execution_id", nullable = false)
    private Long executionId; // 执行记录ID
    
    @Column(name = "script_id", nullable = false)
    private Long scriptId; // 脚本ID
    
    @Column(name = "variable_name", nullable = false, length = 100)
    private String variableName; // 变量名
    
    @Column(name = "variable_value", columnDefinition = "TEXT")
    private String variableValue; // 变量值
    
    @Column(name = "variable_type", length = 50)
    private String variableType = "STRING"; // 变量类型：STRING, NUMBER, BOOLEAN, JSON
    
    @Column(name = "is_sensitive")
    private Boolean isSensitive = false; // 是否敏感数据（密码等）
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}