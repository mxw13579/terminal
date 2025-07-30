package com.fufu.terminal.repository;

import com.fufu.terminal.entity.ScriptInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScriptInteractionRepository extends JpaRepository<ScriptInteraction, Long> {
    
    List<ScriptInteraction> findBySessionIdOrderByResponseTime(String sessionId);
    
    List<ScriptInteraction> findByAtomicScriptIdOrderByResponseTime(Long atomicScriptId);
}