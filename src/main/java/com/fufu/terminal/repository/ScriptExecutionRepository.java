package com.fufu.terminal.repository;

import com.fufu.terminal.entity.ScriptExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScriptExecutionRepository extends JpaRepository<ScriptExecution, Long> {
    
    List<ScriptExecution> findByScriptIdOrderByStartTimeDesc(Long scriptId);
    
    List<ScriptExecution> findByUserIdOrderByStartTimeDesc(Long userId);
    
    Optional<ScriptExecution> findBySessionId(String sessionId);
}