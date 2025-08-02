package com.fufu.terminal.repository;

import com.fufu.terminal.entity.AggregateAtomicRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AggregateAtomicRelationRepository extends JpaRepository<AggregateAtomicRelation, Long> {
    
    List<AggregateAtomicRelation> findByAggregateIdOrderByExecutionOrder(Long aggregateId);
    
    @Query("SELECT r FROM AggregateAtomicRelation r WHERE r.atomicScript.id = :atomicId")
    List<AggregateAtomicRelation> findByAtomicId(@Param("atomicId") Long atomicId);
}