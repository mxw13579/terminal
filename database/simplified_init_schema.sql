-- SSH Terminal Management System - 简化初始化数据库脚本
-- 基于个人项目简化设计原则
-- 生成时间: 2024-01-01

SET FOREIGN_KEY_CHECKS = 0;

-- 删除所有现有表
DROP TABLE IF EXISTS `execution_logs`;
DROP TABLE IF EXISTS `script_executions`;
DROP TABLE IF EXISTS `project_templates`;  
DROP TABLE IF EXISTS `project_groups`;
DROP TABLE IF EXISTS `users`;

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================
-- 核心表结构 (简化设计)
-- =====================================

-- 用户表 (简化)
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL UNIQUE COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码（BCrypt加密）',
  `role` enum('ADMIN', 'USER') DEFAULT 'USER' COMMENT '角色',
  `status` enum('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE' COMMENT '状态',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_username` (`username`),
  INDEX `idx_role_status` (`role`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 项目分组表 (简化)
CREATE TABLE `project_groups` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分组ID',
  `name` varchar(100) NOT NULL COMMENT '分组名称',
  `type` enum('DOCKER_PROJECT', 'NATIVE_PROJECT') DEFAULT 'DOCKER_PROJECT' COMMENT '项目类型',
  `description` text COMMENT '分组描述',
  `icon` varchar(100) COMMENT '图标',
  `config_template` json COMMENT '配置模板',
  `sort_order` int DEFAULT 0 COMMENT '排序顺序',
  `status` enum('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE' COMMENT '状态',
  `created_by` bigint COMMENT '创建者ID',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_type_status` (`type`, `status`),
  INDEX `idx_sort_order` (`sort_order`),
  FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目分组表';

-- 脚本执行记录表 (简化)
CREATE TABLE `script_executions` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '执行ID',
  `group_id` bigint COMMENT '所属分组ID',
  `script_name` varchar(100) NOT NULL COMMENT '脚本名称',
  `script_type` enum('BUILT_IN_STATIC', 'BUILT_IN_DYNAMIC') NOT NULL COMMENT '脚本类型',
  `parameters` json COMMENT '执行参数',
  `ssh_config` json COMMENT 'SSH连接配置(临时)',
  `status` enum('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED') DEFAULT 'PENDING' COMMENT '执行状态',
  `start_time` timestamp NULL COMMENT '开始时间',
  `end_time` timestamp NULL COMMENT '结束时间',
  `duration_ms` bigint COMMENT '执行时长(毫秒)',
  `result_data` json COMMENT '执行结果数据',
  `error_message` text COMMENT '错误信息',
  `created_by` bigint COMMENT '执行用户ID',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_group_id` (`group_id`),
  INDEX `idx_script_name` (`script_name`),
  INDEX `idx_status_created` (`status`, `created_at`),
  INDEX `idx_created_by` (`created_by`),
  FOREIGN KEY (`group_id`) REFERENCES `project_groups`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='脚本执行记录表';

-- 执行日志表 (简化)  
CREATE TABLE `execution_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `execution_id` bigint NOT NULL COMMENT '执行记录ID',
  `log_type` enum('INFO', 'WARN', 'ERROR', 'SUCCESS', 'DEBUG') DEFAULT 'INFO' COMMENT '日志类型',
  `message` text NOT NULL COMMENT '日志消息',
  `step_name` varchar(100) COMMENT '步骤名称',
  `timestamp` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '日志时间',
  PRIMARY KEY (`id`),
  INDEX `idx_execution_id` (`execution_id`),
  INDEX `idx_log_type` (`log_type`),
  INDEX `idx_timestamp` (`timestamp`),
  FOREIGN KEY (`execution_id`) REFERENCES `script_executions`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='执行日志表';

-- =====================================
-- 初始化数据
-- =====================================

-- 插入默认管理员用户
INSERT INTO `users` (`username`, `password`, `role`, `status`) VALUES 
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa', 'ADMIN', 'ACTIVE');
-- 密码是: admin123

-- 插入默认项目分组
INSERT INTO `project_groups` (`name`, `type`, `description`, `icon`, `config_template`, `sort_order`, `created_by`) VALUES 
('MySQL管理', 'DOCKER_PROJECT', 'MySQL数据库容器化部署和管理', 'database', 
 '{"mysql_root_password": {"type": "password", "description": "MySQL root密码", "required": true}, "mysql_port": {"type": "number", "description": "MySQL端口", "default": 3306}}', 
 1, 1),

('Redis管理', 'DOCKER_PROJECT', 'Redis缓存服务器容器化部署和管理', 'server', 
 '{"redis_password": {"type": "password", "description": "Redis密码"}, "redis_port": {"type": "number", "description": "Redis端口", "default": 6379}}', 
 2, 1),

('Nginx管理', 'DOCKER_PROJECT', 'Nginx Web服务器容器化部署和管理', 'globe', 
 '{"nginx_port": {"type": "number", "description": "Nginx端口", "default": 80}}', 
 3, 1),

('系统工具', 'NATIVE_PROJECT', '系统信息查看和基础工具安装', 'tools', 
 '{}', 
 4, 1);

-- =====================================
-- 索引优化
-- =====================================

-- 为经常查询的字段添加复合索引
CREATE INDEX `idx_script_executions_group_status` ON `script_executions` (`group_id`, `status`);
CREATE INDEX `idx_script_executions_user_time` ON `script_executions` (`created_by`, `created_at`);
CREATE INDEX `idx_execution_logs_execution_time` ON `execution_logs` (`execution_id`, `timestamp`);

-- =====================================
-- 数据库设置优化
-- =====================================

-- 设置字符集
ALTER DATABASE ssh_terminal CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 设置时区
SET time_zone = '+08:00';

-- =====================================
-- 清理说明
-- =====================================

/*
此SQL文件已经移除了以下复杂设计:
1. 原子脚本表 - 内置脚本改为代码管理
2. 聚合脚本表 - 简化为项目模板概念  
3. 复杂的关联表 - 减少表间依赖
4. 过度设计的字段 - 保留核心功能字段
5. 冗余的配置表 - 合并到主表中

保留的核心功能:
1. 用户管理和权限控制
2. 项目分组和模板配置
3. 脚本执行记录和状态跟踪
4. 实时日志收集和展示
5. SSH连接临时配置

内置脚本通过Java代码实现，包括:
- system-info (系统信息查看)
- docker-install (Docker安装) 
- mysql-install (MySQL安装)
- redis-install (Redis安装)
- nginx-install (Nginx安装)
*/