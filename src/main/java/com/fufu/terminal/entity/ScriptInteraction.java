package com.fufu.terminal.entity;

import com.fufu.terminal.entity.enums.InteractionType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 脚本交互记录实体
 */
@Data
@Entity
@Table(name = "script_interactions")
public class ScriptInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Column(name = "atomic_script_id")
    private Long atomicScriptId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type")
    private InteractionType interactionType;

    @Column(name = "prompt_message", columnDefinition = "TEXT")
    private String promptMessage;

    @Column(name = "user_response", columnDefinition = "JSON")
    private String userResponse;

    @Column(name = "response_time")
    private LocalDateTime responseTime;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
