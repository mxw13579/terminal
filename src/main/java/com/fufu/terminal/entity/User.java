package com.fufu.terminal.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String username;
    
    @Column(nullable = false, length = 100)
    private String password;
    
    @Column(length = 100)
    private String email;
    
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "enum('ADMIN', 'USER') default 'USER'")
    private Role role = Role.USER;
    
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "enum('ACTIVE', 'INACTIVE') default 'ACTIVE'")
    private Status status = Status.ACTIVE;
    
    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum Role {
        ADMIN, USER
    }
    
    public enum Status {
        ACTIVE, INACTIVE
    }
}