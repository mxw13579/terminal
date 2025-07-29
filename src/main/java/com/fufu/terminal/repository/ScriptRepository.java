package com.fufu.terminal.repository;

import com.fufu.terminal.entity.Script;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScriptRepository extends JpaRepository<Script, Long> {
    
    List<Script> findByGroupIdAndStatusOrderBySortOrder(Long groupId, Script.Status status);
    
    List<Script> findByStatusOrderBySortOrder(Script.Status status);
    
    List<Script> findByCreatedByOrderByCreatedAtDesc(Long createdBy);
}