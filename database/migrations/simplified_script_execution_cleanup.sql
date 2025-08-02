-- Simplified Script Execution System - Database Cleanup Migration
-- 移除内置脚本的数据库记录，保持代码和数据分离

-- 创建备份表以防回滚需要
CREATE TABLE IF NOT EXISTS atomic_scripts_builtin_backup AS
SELECT * FROM atomic_scripts WHERE 1=0;

-- 记录要删除的内置脚本
INSERT INTO atomic_scripts_builtin_backup
SELECT * FROM atomic_scripts 
WHERE script_name IN (
    'system-info',
    'docker-install', 
    'mysql-install',
    'redis-install'
) OR script_id IN (
    'system-info',
    'docker-install',
    'mysql-install', 
    'redis-install'
);

-- 记录删除操作的日志
INSERT INTO migration_log (migration_name, operation, affected_records, created_at)
SELECT 
    'simplified_script_execution_cleanup' as migration_name,
    'builtin_scripts_backup' as operation,
    COUNT(*) as affected_records,
    NOW() as created_at
FROM atomic_scripts_builtin_backup;

-- 删除聚合脚本中包含内置脚本的关联关系
DELETE FROM aggregate_atomic_relation 
WHERE atomic_script_id IN (
    SELECT id FROM atomic_scripts 
    WHERE script_name IN ('system-info', 'docker-install', 'mysql-install', 'redis-install')
    OR script_id IN ('system-info', 'docker-install', 'mysql-install', 'redis-install')
);

-- 记录关联关系删除日志
INSERT INTO migration_log (migration_name, operation, affected_records, created_at)
VALUES (
    'simplified_script_execution_cleanup',
    'aggregate_relations_deleted',
    ROW_COUNT(),
    NOW()
);

-- 删除脚本组中包含内置脚本的关联关系
DELETE FROM script_group_scripts 
WHERE script_id IN (
    SELECT id FROM atomic_scripts 
    WHERE script_name IN ('system-info', 'docker-install', 'mysql-install', 'redis-install')
    OR script_id IN ('system-info', 'docker-install', 'mysql-install', 'redis-install')
);

-- 记录脚本组关联删除日志
INSERT INTO migration_log (migration_name, operation, affected_records, created_at)
VALUES (
    'simplified_script_execution_cleanup',
    'script_group_relations_deleted', 
    ROW_COUNT(),
    NOW()
);

-- 删除内置脚本的执行日志（可选，如果要保留历史记录可以注释掉）
-- DELETE FROM script_execution WHERE script_id IN (
--     SELECT id FROM atomic_scripts 
--     WHERE script_name IN ('system-info', 'docker-install', 'mysql-install', 'redis-install')
--     OR script_id IN ('system-info', 'docker-install', 'mysql-install', 'redis-install')
-- );

-- 最后删除内置脚本记录
DELETE FROM atomic_scripts 
WHERE script_name IN ('system-info', 'docker-install', 'mysql-install', 'redis-install')
OR script_id IN ('system-info', 'docker-install', 'mysql-install', 'redis-install');

-- 记录主删除操作日志
INSERT INTO migration_log (migration_name, operation, affected_records, created_at)
VALUES (
    'simplified_script_execution_cleanup',
    'builtin_scripts_deleted',
    ROW_COUNT(), 
    NOW()
);

-- 验证删除结果
SELECT 
    'Cleanup Verification' as operation,
    COUNT(*) as remaining_builtin_scripts
FROM atomic_scripts 
WHERE script_name IN ('system-info', 'docker-install', 'mysql-install', 'redis-install')
OR script_id IN ('system-info', 'docker-install', 'mysql-install', 'redis-install');

-- 显示清理统计
SELECT 
    migration_name,
    operation,
    affected_records,
    created_at
FROM migration_log 
WHERE migration_name = 'simplified_script_execution_cleanup'
ORDER BY created_at DESC;

-- 提交事务
COMMIT;