package com.fufu.terminal.repository;

import com.fufu.terminal.entity.ScriptExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScriptExecutionRepository extends JpaRepository<ScriptExecution, Long> {
    
    List<ScriptExecution> findByScriptIdOrderByStartTimeDesc(Long scriptId);
    
    List<ScriptExecution> findByUserIdOrderByStartTimeDesc(Long userId);
    
    Optional<ScriptExecution> findBySessionId(String sessionId);
    
    List<ScriptExecution> findAllByOrderByStartTimeDesc();
    
    /**
     * 统计指定状态和时间之前的执行数量
     */
    @Query("SELECT COUNT(e) FROM ScriptExecution e WHERE e.status = :status AND e.startTime < :startTime")
    int countByStatusAndStartTimeBefore(@Param("status") ScriptExecution.Status status, 
                                       @Param("startTime") LocalDateTime startTime);
}