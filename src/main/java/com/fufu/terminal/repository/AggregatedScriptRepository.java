package com.fufu.terminal.repository;

import com.fufu.terminal.entity.AggregatedScript;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AggregatedScriptRepository extends JpaRepository<AggregatedScript, Long> {
    
    List<AggregatedScript> findByStatusOrderBySortOrder(AggregatedScript.Status status);
    
    List<AggregatedScript> findByIdInAndStatus(List<Long> ids, AggregatedScript.Status status);
    
    List<AggregatedScript> findByCreatedByOrderByCreatedAtDesc(Long createdBy);
    
    /**
     * Optimized query to fetch AggregatedScript with all related AtomicScripts
     * This prevents N+1 queries when accessing atomic script relations
     */
    @Query("SELECT DISTINCT a FROM AggregatedScript a " +
           "LEFT JOIN FETCH a.atomicScriptRelations r " +
           "LEFT JOIN FETCH r.atomicScript " +
           "WHERE a.id = :id")
    Optional<AggregatedScript> findByIdWithAtomicScripts(@Param("id") Long id);
    
    /**
     * Optimized query to fetch all active AggregatedScripts with their AtomicScripts
     * Used for bulk operations to prevent N+1 queries
     */
    @Query("SELECT DISTINCT a FROM AggregatedScript a " +
           "LEFT JOIN FETCH a.atomicScriptRelations r " +
           "LEFT JOIN FETCH r.atomicScript " +
           "WHERE a.status = :status " +
           "ORDER BY a.id")
    List<AggregatedScript> findByStatusWithAtomicScripts(@Param("status") AggregatedScript.Status status);
    
    /**
     * Batch fetch for multiple IDs with atomic scripts
     */
    @Query("SELECT DISTINCT a FROM AggregatedScript a " +
           "LEFT JOIN FETCH a.atomicScriptRelations r " +
           "LEFT JOIN FETCH r.atomicScript " +
           "WHERE a.id IN :ids AND a.status = :status")
    List<AggregatedScript> findByIdInAndStatusWithAtomicScripts(@Param("ids") List<Long> ids, @Param("status") AggregatedScript.Status status);
}
