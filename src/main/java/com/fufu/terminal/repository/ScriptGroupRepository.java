package com.fufu.terminal.repository;

import com.fufu.terminal.entity.ScriptGroup;
import com.fufu.terminal.entity.enums.ScriptGroupType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScriptGroupRepository extends JpaRepository<ScriptGroup, Long> {
    
    List<ScriptGroup> findByStatusOrderByDisplayOrder(ScriptGroup.Status status);

    Optional<ScriptGroup> findByIdAndStatus(Long id, ScriptGroup.Status status);
    
    List<ScriptGroup> findByCreatedByOrderByDisplayOrder(Long createdBy);
    
    /**
     * Get active script groups by type with optimized fetching
     */
    List<ScriptGroup> findByStatusAndTypeOrderByDisplayOrder(ScriptGroup.Status status, ScriptGroupType type);
    
    /**
     * Optimized query to fetch ScriptGroup with all related AggregatedScripts
     * This prevents N+1 queries when accessing aggregated script relations
     */
    @Query("SELECT DISTINCT sg FROM ScriptGroup sg " +
           "LEFT JOIN FETCH sg.aggregateRelations gr " +
           "LEFT JOIN FETCH gr.aggregatedScript " +
           "WHERE sg.id = :id AND sg.status = :status")
    Optional<ScriptGroup> findByIdAndStatusWithAggregatedScripts(@Param("id") Long id, @Param("status") ScriptGroup.Status status);
    
    /**
     * Batch fetch for all active groups with their aggregated scripts
     */
    @Query("SELECT DISTINCT sg FROM ScriptGroup sg " +
           "LEFT JOIN FETCH sg.aggregateRelations gr " +
           "LEFT JOIN FETCH gr.aggregatedScript " +
           "WHERE sg.status = :status " +
           "ORDER BY sg.displayOrder")
    List<ScriptGroup> findByStatusWithAggregatedScripts(@Param("status") ScriptGroup.Status status);
    
    /**
     * Batch fetch for groups by type with their aggregated scripts
     */
    @Query("SELECT DISTINCT sg FROM ScriptGroup sg " +
           "LEFT JOIN FETCH sg.aggregateRelations gr " +
           "LEFT JOIN FETCH gr.aggregatedScript " +
           "WHERE sg.status = :status AND sg.type = :type " +
           "ORDER BY sg.displayOrder")
    List<ScriptGroup> findByStatusAndTypeWithAggregatedScripts(@Param("status") ScriptGroup.Status status, @Param("type") ScriptGroupType type);
}