package com.fufu.terminal.repository;

import com.fufu.terminal.entity.AggregatedScript;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AggregatedScriptRepository extends JpaRepository<AggregatedScript, Long> {
    
    List<AggregatedScript> findByStatusOrderBySortOrder(AggregatedScript.Status status);
    
    List<AggregatedScript> findByIdInAndStatus(List<Long> ids, AggregatedScript.Status status);
    
    List<AggregatedScript> findByCreatedByOrderByCreatedAtDesc(Long createdBy);
}
