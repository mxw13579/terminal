package com.fufu.terminal.repository;

import com.fufu.terminal.entity.ExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, Long> {
    
    List<ExecutionLog> findByExecutionIdOrderByStepOrderAscTimestampAsc(Long executionId);
    
    List<ExecutionLog> findByExecutionIdAndLogTypeOrderByTimestampAsc(Long executionId, ExecutionLog.LogType logType);
}