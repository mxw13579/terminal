package com.fufu.terminal.service;

import com.fufu.terminal.entity.AggregatedScript;
import com.fufu.terminal.repository.AggregatedScriptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 聚合脚本服务类，封装了聚合脚本的业务操作
 * @author lizelin
 */
@Service
@RequiredArgsConstructor
public class AggregatedScriptService {

    // 聚合脚本数据仓库
    private final AggregatedScriptRepository aggregatedScriptRepository;

    /**
     * 查询所有聚合脚本
     * @return 聚合脚本列表
     */
    public List<AggregatedScript> getAllAggregatedScripts() {
        return aggregatedScriptRepository.findAll();
    }

    /**
     * 根据分组ID和状态查询聚合脚本，并按排序字段排序
     * @param groupId 分组ID
     * @param status 脚本状态
     * @return 聚合脚本列表
     */
    public List<AggregatedScript> getAggregatedScriptsByGroupAndStatus(Long groupId, AggregatedScript.Status status) {
        return aggregatedScriptRepository.findByGroupIdAndStatusOrderBySortOrder(groupId, status);
    }

    /**
     * 根据ID获取聚合脚本
     * @param id 脚本ID
     * @return 聚合脚本实体
     * @throws RuntimeException 未找到脚本时抛出异常
     */
    public AggregatedScript getAggregatedScriptById(Long id) {
        return aggregatedScriptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("未找到对应的聚合脚本"));
    }

    /**
     * 创建新的聚合脚本
     * @param aggregatedScript 聚合脚本实体
     * @return 保存后的聚合脚本
     */
    public AggregatedScript createAggregatedScript(AggregatedScript aggregatedScript) {
        return aggregatedScriptRepository.save(aggregatedScript);
    }

    /**
     * 更新聚合脚本
     * @param aggregatedScript 聚合脚本实体
     * @return 更新后的聚合脚本
     */
    public AggregatedScript updateAggregatedScript(AggregatedScript aggregatedScript) {
        return aggregatedScriptRepository.save(aggregatedScript);
    }

    /**
     * 根据ID删除聚合脚本
     * @param id 脚本ID
     */
    public void deleteAggregatedScript(Long id) {
        aggregatedScriptRepository.deleteById(id);
    }

    /**
     * 批量删除聚合脚本
     * @param ids 脚本ID列表
     */
    public void deleteAggregatedScripts(List<Long> ids) {
        ids.forEach(aggregatedScriptRepository::deleteById);
    }

    /**
     * 更新聚合脚本的排序字段
     * @param id 脚本ID
     * @param sortOrder 新的排序值
     */
    public void updateSortOrder(Long id, Integer sortOrder) {
        AggregatedScript aggregatedScript = getAggregatedScriptById(id);
        aggregatedScript.setSortOrder(sortOrder);
        aggregatedScriptRepository.save(aggregatedScript);
    }

    /**
     * 判断指定ID的聚合脚本是否存在
     * @param id 脚本ID
     * @return 存在返回true，否则false
     */
    public boolean existsById(Long id) {
        return aggregatedScriptRepository.existsById(id);
    }

    /**
     * 根据状态查询聚合脚本
     * @param status 脚本状态
     * @return 聚合脚本列表
     */
    public List<AggregatedScript> getAggregatedScriptsByStatus(AggregatedScript.Status status) {
        return aggregatedScriptRepository.findByStatus(status);
    }

    /**
     * 根据分组ID查询所有激活状态的聚合脚本，按排序字段排序
     * @param groupId 分组ID
     * @return 激活状态的聚合脚本列表
     */
    public List<AggregatedScript> getActiveAggregatedScriptsByGroup(Long groupId) {
        return getAggregatedScriptsByGroupAndStatus(groupId, AggregatedScript.Status.ACTIVE);
    }

    public List<AggregatedScript> getAggregatedScriptsByGroup(Long groupId) {
        return aggregatedScriptRepository.findByGroupId(groupId);
    }
}
