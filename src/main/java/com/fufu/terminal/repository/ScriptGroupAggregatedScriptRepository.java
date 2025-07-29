package com.fufu.terminal.repository;

import com.fufu.terminal.entity.ScriptGroupAggregatedScript;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScriptGroupAggregatedScriptRepository extends JpaRepository<ScriptGroupAggregatedScript, Long> {
    
    List<ScriptGroupAggregatedScript> findByGroupIdOrderBySortOrder(Long groupId);
    
    @Query("SELECT sgas FROM ScriptGroupAggregatedScript sgas " +
           "JOIN FETCH sgas.aggregatedScript aggScript " +
           "WHERE sgas.groupId = :groupId " +
           "AND aggScript.status = 'ACTIVE' " +
           "ORDER BY sgas.sortOrder")
    List<ScriptGroupAggregatedScript> findActiveAggregatedScriptsByGroupId(@Param("groupId") Long groupId);
}