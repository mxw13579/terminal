package com.fufu.terminal.repository;

import com.fufu.terminal.entity.AtomicScript;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AtomicScriptRepository extends JpaRepository<AtomicScript, Long> {
    
    /**
     * 根据状态查询原子脚本，按排序字段排序
     */
    List<AtomicScript> findByStatusOrderBySortOrder(AtomicScript.Status status);
    
    /**
     * 根据脚本类型查询活跃的原子脚本
     */
    List<AtomicScript> findByScriptTypeAndStatusOrderBySortOrder(String scriptType, AtomicScript.Status status);
    
    /**
     * 根据标签搜索原子脚本（JSON字段模糊查询）
     */
    @Query("SELECT a FROM AtomicScript a WHERE a.status = :status AND a.tags LIKE %:tag%")
    List<AtomicScript> findByTagAndStatus(@Param("tag") String tag, @Param("status") AtomicScript.Status status);
    
    /**
     * 根据名称模糊查询活跃的原子脚本
     */
    List<AtomicScript> findByNameContainingAndStatusOrderBySortOrder(String name, AtomicScript.Status status);
    
    /**
     * 根据创建者查询原子脚本
     */
    List<AtomicScript> findByCreatedByOrderByCreatedAtDesc(Long createdBy);
    
    /**
     * 根据ID列表和状态查询原子脚本
     */
    List<AtomicScript> findByIdInAndStatus(List<Long> ids, AtomicScript.Status status);
    
    /**
     * 查询所有活跃的原子脚本，用于注册
     */
    @Query("SELECT a FROM AtomicScript a WHERE a.status = 'ACTIVE' ORDER BY a.scriptType, a.sortOrder")
    List<AtomicScript> findAllActiveForRegistry();
}