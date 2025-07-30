package com.fufu.terminal.repository;

import com.fufu.terminal.entity.AggregateAtomicRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AggregateAtomicRelationRepository extends JpaRepository<AggregateAtomicRelation, Long> {
    
    List<AggregateAtomicRelation> findByAggregateIdOrderByExecutionOrder(Long aggregateId);
    
    List<AggregateAtomicRelation> findByAtomicId(Long atomicId);
}