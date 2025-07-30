package com.fufu.terminal.repository;

import com.fufu.terminal.entity.ScriptExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScriptExecutionLogRepository extends JpaRepository<ScriptExecutionLog, Long> {
    
    List<ScriptExecutionLog> findBySessionIdOrderByTimestamp(String sessionId);
    
    List<ScriptExecutionLog> findByAtomicScriptIdOrderByTimestamp(Long atomicScriptId);
}