package com.fufu.terminal.repository;

import com.fufu.terminal.entity.AggregatedScript;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 聚合脚本数据访问接口，提供对AggregatedScript实体的常用数据库操作方法
 * 包括按分组、状态、创建人等条件的查询和统计等功能。
 *
 * @author lizelin
 */
@Repository
public interface AggregatedScriptRepository extends JpaRepository<AggregatedScript, Long> {

    /**
     * 根据分组ID和状态查询脚本，并按sortOrder升序排列
     * @param groupId 分组ID
     * @param status 脚本状态
     * @return 符合条件的脚本列表
     */
    List<AggregatedScript> findByGroupIdAndStatusOrderBySortOrder(Long groupId, AggregatedScript.Status status);

    /**
     * 根据状态查询所有脚本，并按sortOrder升序排列
     * @param status 脚本状态
     * @return 符合条件的脚本列表
     */
    List<AggregatedScript> findByStatusOrderBySortOrder(AggregatedScript.Status status);

    /**
     * 根据创建人ID查询其所有脚本，并按创建时间倒序排列
     * @param createdBy 创建人ID
     * @return 该用户创建的脚本列表
     */
    List<AggregatedScript> findByCreatedByOrderByCreatedAtDesc(Long createdBy);

    /**
     * 根据状态查询所有脚本
     * @param status 脚本状态
     * @return 符合条件的脚本列表
     */
    List<AggregatedScript> findByStatus(AggregatedScript.Status status);

    /**
     * 根据主键ID查找脚本
     * @param id 脚本ID
     * @return 脚本实体（Optional包装，可能为空）
     */
    Optional<AggregatedScript> findById(Long id);

    /**
     * 根据分组ID查询所有脚本
     * @param groupId 分组ID
     * @return 该分组下的所有脚本
     */
    List<AggregatedScript> findByGroupId(Long groupId);

    /**
     * 根据创建人和状态查询脚本
     * @param createdBy 创建人ID
     * @param status 脚本状态
     * @return 符合条件的脚本列表
     */
    List<AggregatedScript> findByCreatedByAndStatus(Long createdBy, AggregatedScript.Status status);

    /**
     * 统计指定状态的脚本数量
     * @param status 脚本状态
     * @return 数量
     */
    long countByStatus(AggregatedScript.Status status);

    /**
     * 删除指定分组下的所有脚本
     * @param groupId 分组ID
     */
    void deleteByGroupId(Long groupId);
}
