package com.fufu.terminal.repository;

import com.fufu.terminal.entity.ScriptExecutionSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScriptExecutionSessionRepository extends JpaRepository<ScriptExecutionSession, String> {
    
    List<ScriptExecutionSession> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    List<ScriptExecutionSession> findByAggregateScriptIdOrderByCreatedAtDesc(Long aggregateScriptId);
}