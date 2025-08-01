package com.fufu.terminal.repository;

import com.fufu.terminal.entity.ScriptGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import java.util.Optional;

@Repository
public interface ScriptGroupRepository extends JpaRepository<ScriptGroup, Long> {
    
    List<ScriptGroup> findByStatusOrderByDisplayOrder(ScriptGroup.Status status);

    Optional<ScriptGroup> findByIdAndStatus(Long id, ScriptGroup.Status status);
    
    List<ScriptGroup> findByCreatedByOrderByDisplayOrder(Long createdBy);
}