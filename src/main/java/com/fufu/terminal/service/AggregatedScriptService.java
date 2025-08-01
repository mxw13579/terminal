package com.fufu.terminal.service;

import com.fufu.terminal.controller.admin.dto.AggregatedScriptCreateRequest;
import com.fufu.terminal.entity.AggregateAtomicRelation;
import com.fufu.terminal.entity.AggregatedScript;
import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.repository.AggregatedScriptRepository;
import com.fufu.terminal.repository.AtomicScriptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 聚合脚本服务类 (已重构)
 * 封装了聚合脚本的业务操作，包括从构建器请求创建脚本。
 */
@Service
@RequiredArgsConstructor
public class AggregatedScriptService {

    private final AggregatedScriptRepository aggregatedScriptRepository;
    private final AtomicScriptRepository atomicScriptRepository; // Added dependency

    @Transactional
    public AggregatedScript createAggregatedScript(AggregatedScriptCreateRequest request) {
        AggregatedScript newScript = new AggregatedScript();
        newScript.setName(request.getName());
        newScript.setDescription(request.getDescription());
        newScript.setType(request.getType());
        newScript.setStatus(AggregatedScript.Status.ACTIVE);

        if (request.getSteps() != null) {
            for (AggregatedScriptCreateRequest.StepDto stepDto : request.getSteps()) {
                AtomicScript atomicScript = atomicScriptRepository.findById(stepDto.getAtomicScriptId())
                        .orElseThrow(() -> new RuntimeException("AtomicScript not found with id: " + stepDto.getAtomicScriptId()));

                AggregateAtomicRelation relation = new AggregateAtomicRelation();
                relation.setAtomicScript(atomicScript);
                relation.setExecutionOrder(stepDto.getExecutionOrder());
                relation.setConditionExpression(stepDto.getConditionExpression());
                relation.setVariableMapping(stepDto.getVariableMapping());
                
                // The back-reference is handled by the @JoinColumn in AggregatedScript
                newScript.getAtomicScriptRelations().add(relation);
            }
        }

        return aggregatedScriptRepository.save(newScript);
    }

    /**
     * Get aggregated script by ID with optimized fetching to prevent N+1 queries
     */
    public AggregatedScript getAggregatedScriptById(Long id) {
        return aggregatedScriptRepository.findByIdWithAtomicScripts(id)
                .orElseThrow(() -> new RuntimeException("AggregatedScript not found with id: " + id));
    }
    
    /**
     * Get all active aggregated scripts with optimized fetching
     */
    public List<AggregatedScript> getActiveAggregatedScripts() {
        return aggregatedScriptRepository.findByStatusWithAtomicScripts(AggregatedScript.Status.ACTIVE);
    }
    
    /**
     * Get aggregated scripts by IDs with optimized fetching
     */
    public List<AggregatedScript> getAggregatedScriptsByIds(List<Long> ids) {
        return aggregatedScriptRepository.findByIdInAndStatusWithAtomicScripts(ids, AggregatedScript.Status.ACTIVE);
    }

    public List<AggregatedScript> getAllAggregatedScripts() {
        return aggregatedScriptRepository.findAll();
    }

    @Transactional
    public void deleteAggregatedScript(Long id) {
        aggregatedScriptRepository.deleteById(id);
    }
    
    // Other existing methods can be kept or refactored as needed
}
