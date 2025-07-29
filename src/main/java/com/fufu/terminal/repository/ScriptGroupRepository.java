package com.fufu.terminal.repository;

import com.fufu.terminal.entity.ScriptGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScriptGroupRepository extends JpaRepository<ScriptGroup, Long> {
    
    List<ScriptGroup> findByStatusOrderBySortOrder(ScriptGroup.Status status);
    
    List<ScriptGroup> findByCreatedByOrderBySortOrder(Long createdBy);
}