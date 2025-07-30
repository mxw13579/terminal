package com.fufu.terminal.service;

import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.repository.AggregateAtomicRelationRepository;
import com.fufu.terminal.repository.AtomicScriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 原子脚本服务类
 * 提供原子脚本的CRUD操作，并与统一脚本注册表集成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtomicScriptService {
    
    private final AtomicScriptRepository atomicScriptRepository;
    private final AggregateAtomicRelationRepository aggregateAtomicRelationRepository;
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * 创建原子脚本
     */
    @Transactional
    public AtomicScript createAtomicScript(AtomicScript script) {
        script.setCreatedAt(LocalDateTime.now());
        script.setUpdatedAt(LocalDateTime.now());
        AtomicScript saved = atomicScriptRepository.save(script);
        
        // 发布脚本变更事件
        publishScriptChangeEvent();
        
        log.info("创建原子脚本: {}", saved.getName());
        return saved;
    }
    
    /**
     * 更新原子脚本
     */
    @Transactional
    public AtomicScript updateAtomicScript(AtomicScript script) {
        script.setUpdatedAt(LocalDateTime.now());
        AtomicScript saved = atomicScriptRepository.save(script);
        
        // 发布脚本变更事件
        publishScriptChangeEvent();
        
        log.info("更新原子脚本: {}", saved.getName());
        return saved;
    }
    
    /**
     * 删除原子脚本
     */
    @Transactional
    public void deleteAtomicScript(Long id) {
        AtomicScript script = getAtomicScriptById(id);
        atomicScriptRepository.deleteById(id);
        
        // 发布脚本变更事件
        publishScriptChangeEvent();
        
        log.info("删除原子脚本: {}", script.getName());
    }
    
    /**
     * 根据ID获取原子脚本
     */
    public AtomicScript getAtomicScriptById(Long id) {
        return atomicScriptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("未找到对应的原子脚本: " + id));
    }
    
    /**
     * 获取所有原子脚本
     */
    public List<AtomicScript> getAllAtomicScripts() {
        return atomicScriptRepository.findAll();
    }
    
    /**
     * 根据状态获取原子脚本
     */
    public List<AtomicScript> getAtomicScriptsByStatus(AtomicScript.Status status) {
        return atomicScriptRepository.findByStatusOrderBySortOrder(status);
    }
    
    /**
     * 根据脚本类型获取活跃的原子脚本
     */
    public List<AtomicScript> getActiveAtomicScriptsByType(String scriptType) {
        return atomicScriptRepository.findByScriptTypeAndStatusOrderBySortOrder(scriptType, AtomicScript.Status.ACTIVE);
    }
    
    /**
     * 根据标签搜索原子脚本
     */
    public List<AtomicScript> searchAtomicScriptsByTag(String tag) {
        return atomicScriptRepository.findByTagAndStatus(tag, AtomicScript.Status.ACTIVE);
    }
    
    /**
     * 根据名称模糊搜索活跃的原子脚本
     */
    public List<AtomicScript> searchAtomicScriptsByName(String name) {
        return atomicScriptRepository.findByNameContainingAndStatusOrderBySortOrder(name, AtomicScript.Status.ACTIVE);
    }
    
    /**
     * 根据创建者获取原子脚本
     */
    public List<AtomicScript> getAtomicScriptsByCreator(Long createdBy) {
        return atomicScriptRepository.findByCreatedByOrderByCreatedAtDesc(createdBy);
    }
    
    /**
     * 批量获取原子脚本
     */
    public List<AtomicScript> getAtomicScriptsByIds(List<Long> ids) {
        return atomicScriptRepository.findByIdInAndStatus(ids, AtomicScript.Status.ACTIVE);
    }
    
    /**
     * 根据聚合脚本ID获取原子脚本列表
     */
    public List<AtomicScript> getAtomicScriptsByAggregateId(Long aggregateId) {
        return aggregateAtomicRelationRepository.findByAggregateIdOrderByExecutionOrder(aggregateId)
                .stream()
                .map(relation -> getAtomicScriptById(relation.getAtomicId()))
                .collect(Collectors.toList());
    }
    
    /**
     * 发布脚本变更事件
     */
    private void publishScriptChangeEvent() {
        eventPublisher.publishEvent(new ScriptChangeEvent());
    }
    
    /**
     * 脚本变更事件
     */
    public static class ScriptChangeEvent {
        // 事件类不需要额外字段，仅作为通知使用
    }
}