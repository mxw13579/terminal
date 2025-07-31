package com.fufu.terminal.repository;

import com.fufu.terminal.entity.ScriptExecutionVariable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScriptExecutionVariableRepository extends JpaRepository<ScriptExecutionVariable, Long> {
    
    List<ScriptExecutionVariable> findByExecutionId(Long executionId);
    
    List<ScriptExecutionVariable> findByScriptId(Long scriptId);
    
    ScriptExecutionVariable findByExecutionIdAndVariableName(Long executionId, String variableName);
}