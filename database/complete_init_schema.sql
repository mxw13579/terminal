-- SSH Terminal Management System - Complete Database Initialization Script
-- 从零初始化数据库脚本
-- 生成时间: 2025-08-01

-- 删除已存在的表（按照外键依赖关系的逆序）
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `script_interactions`;
DROP TABLE IF EXISTS `script_execution_logs`;
DROP TABLE IF EXISTS `script_execution_sessions`;
DROP TABLE IF EXISTS `script_execution_variables`;
DROP TABLE IF EXISTS `execution_logs`;
DROP TABLE IF EXISTS `script_executions`;
DROP TABLE IF EXISTS `group_aggregate_relations`;
DROP TABLE IF EXISTS `script_group_aggregated_scripts`;
DROP TABLE IF EXISTS `aggregate_atomic_relations`;
DROP TABLE IF EXISTS `config_templates`;
DROP TABLE IF EXISTS `project_configs`;
DROP TABLE IF EXISTS `websocket_sessions`;
DROP TABLE IF EXISTS `ssh_servers`;
DROP TABLE IF EXISTS `script_groups`;
DROP TABLE IF EXISTS `aggregated_scripts`;
DROP TABLE IF EXISTS `atomic_scripts`;
DROP TABLE IF EXISTS `scripts`;
DROP TABLE IF EXISTS `users`;

SET FOREIGN_KEY_CHECKS = 1;

-- ====================================
-- 核心实体表
-- ====================================

-- 用户表
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL UNIQUE COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码（BCrypt加密）',
  `email` varchar(100) COMMENT '邮箱',
  `role` enum('ADMIN', 'USER') DEFAULT 'USER' COMMENT '角色',
  `status` enum('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE' COMMENT '状态',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_username` (`username`),
  INDEX `idx_role_status` (`role`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 原子脚本表（最小执行单元）
CREATE TABLE `atomic_scripts` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '脚本ID',
  `name` varchar(100) NOT NULL COMMENT '脚本名称',
  `description` text COMMENT '脚本描述',
  `script_content` text NOT NULL COMMENT '脚本内容',
  `script_type` varchar(50) COMMENT '脚本类型: bash, python, node等',
  `script_type_enum` enum('BUILT_IN_STATIC', 'BUILT_IN_PARAM', 'USER_SIMPLE', 'USER_TEMPLATE') DEFAULT 'USER_SIMPLE' COMMENT '脚本类型枚举',
  `interaction_mode` enum('SILENT', 'CONFIRMATION', 'INPUT_REQUIRED', 'CONDITIONAL', 'REALTIME_OUTPUT') DEFAULT 'SILENT' COMMENT '交互模式',
  `interaction_config` json COMMENT '交互配置，JSON格式存储',
  `input_variables` json COMMENT '输入变量定义',
  `output_variables` json COMMENT '输出变量定义',
  `prerequisites` json COMMENT '前置条件',
  `condition_expression` text COMMENT '条件表达式',
  `estimated_duration` int DEFAULT 0 COMMENT '预估执行时长（秒）',
  `version` varchar(20) DEFAULT '1.0.0' COMMENT '版本号',
  `sort_order` int DEFAULT 0 COMMENT '排序顺序',
  `created_by` bigint COMMENT '创建者ID',
  `status` enum('ACTIVE', 'DRAFT', 'INACTIVE') DEFAULT 'DRAFT' COMMENT '状态',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_atomic_script_status_sort` (`status`, `sort_order`),
  INDEX `idx_atomic_script_type_status` (`script_type_enum`, `status`),
  INDEX `idx_atomic_script_created_by` (`created_by`),
  INDEX `idx_name_version` (`name`, `version`),
  FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原子脚本表';

-- 聚合脚本表（组装多个原子脚本的工作流）
CREATE TABLE `aggregated_scripts` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '聚合脚本ID',
  `name` varchar(100) NOT NULL COMMENT '聚合脚本名称',
  `description` text COMMENT '脚本描述',
  `type` enum('GENERIC_TEMPLATE', 'PROJECT_TEMPLATE') DEFAULT 'GENERIC_TEMPLATE' COMMENT '聚合脚本类型',
  `config_template` json COMMENT '配置模板，用于通用模板类型',
  `sort_order` int DEFAULT 0 COMMENT '排序顺序',
  `created_by` bigint COMMENT '创建者ID',
  `status` enum('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE' COMMENT '状态',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_aggregated_script_status_sort` (`status`, `sort_order`),
  INDEX `idx_aggregated_script_created_by` (`created_by`),
  INDEX `idx_type_status` (`type`, `status`),
  FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聚合脚本表';

-- 聚合脚本-原子脚本关联表
CREATE TABLE `aggregate_atomic_relations` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `aggregate_id` bigint NOT NULL COMMENT '聚合脚本ID',
  `atomic_id` bigint NOT NULL COMMENT '原子脚本ID',
  `execution_order` int NOT NULL COMMENT '执行顺序',
  `variable_mapping` json COMMENT '变量映射配置',
  `condition_expression` varchar(500) COMMENT '执行条件表达式',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_aggregate_atomic_order` (`aggregate_id`, `atomic_id`, `execution_order`),
  INDEX `idx_aggregate_id` (`aggregate_id`),
  INDEX `idx_atomic_id` (`atomic_id`),
  FOREIGN KEY (`aggregate_id`) REFERENCES `aggregated_scripts`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`atomic_id`) REFERENCES `atomic_scripts`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聚合脚本-原子脚本关联表';

-- 脚本分组表（用户端展示的页面分组）
CREATE TABLE `script_groups` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分组ID',
  `name` varchar(100) NOT NULL COMMENT '分组名称',
  `description` text COMMENT '分组描述',
  `type` enum('PROJECT_DIMENSION', 'FUNCTION_DIMENSION') DEFAULT 'PROJECT_DIMENSION' COMMENT '分组类型',
  `icon` varchar(100) COMMENT '图标',
  `display_order` int DEFAULT 0 COMMENT '显示顺序',
  `created_by` bigint COMMENT '创建者ID',
  `status` enum('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE' COMMENT '状态',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_script_group_status_display` (`status`, `display_order`),
  INDEX `idx_script_group_type_status` (`type`, `status`),
  INDEX `idx_script_group_created_by` (`created_by`),
  FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='脚本分组表';

-- 分组-聚合脚本关联表
CREATE TABLE `group_aggregate_relations` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `group_id` bigint NOT NULL COMMENT '分组ID',
  `aggregate_id` bigint NOT NULL COMMENT '聚合脚本ID',
  `display_order` int DEFAULT 0 COMMENT '显示顺序',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_group_aggregate` (`group_id`, `aggregate_id`),
  INDEX `idx_group_id` (`group_id`),
  INDEX `idx_aggregate_id` (`aggregate_id`),
  FOREIGN KEY (`group_id`) REFERENCES `script_groups`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`aggregate_id`) REFERENCES `aggregated_scripts`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分组-聚合脚本关联表';

-- ====================================
-- 执行相关表
-- ====================================

-- 脚本执行会话表
CREATE TABLE `script_execution_sessions` (
  `id` varchar(36) NOT NULL COMMENT 'UUID会话ID',
  `user_id` bigint COMMENT '用户ID',
  `aggregate_script_id` bigint NOT NULL COMMENT '聚合脚本ID',
  `status` enum('PREPARING', 'EXECUTING', 'WAITING_INPUT', 'WAITING_CONFIRM', 'COMPLETED', 'FAILED', 'CANCELLED', 'PAUSED') NOT NULL DEFAULT 'PREPARING' COMMENT '执行状态',
  `context_data` json COMMENT '上下文数据的JSON序列化',
  `start_time` timestamp NULL COMMENT '开始时间',
  `end_time` timestamp NULL COMMENT '结束时间',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_aggregate_script_id` (`aggregate_script_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_created_at` (`created_at`),
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL,
  FOREIGN KEY (`aggregate_script_id`) REFERENCES `aggregated_scripts`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='脚本执行会话表';

-- 脚本交互记录表
CREATE TABLE `script_interactions` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '交互记录ID',
  `session_id` varchar(36) NOT NULL COMMENT '会话ID',
  `atomic_script_id` bigint COMMENT '原子脚本ID',
  `interaction_type` enum('CONFIRM_YES_NO', 'CONFIRM_RECOMMENDATION', 'INPUT_TEXT', 'INPUT_PASSWORD', 'INPUT_FORM', 'SELECT_OPTION', 'FILE_UPLOAD') COMMENT '交互类型',
  `prompt_message` text COMMENT '提示消息',
  `user_response` json COMMENT '用户响应',
  `response_time` timestamp NULL COMMENT '响应时间',
  `status` varchar(50) COMMENT '状态',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_session_id` (`session_id`),
  INDEX `idx_atomic_script_id` (`atomic_script_id`),
  INDEX `idx_interaction_type` (`interaction_type`),
  FOREIGN KEY (`session_id`) REFERENCES `script_execution_sessions`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`atomic_script_id`) REFERENCES `atomic_scripts`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='脚本交互记录表';

-- 脚本执行日志表
CREATE TABLE `script_execution_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `session_id` varchar(36) NOT NULL COMMENT '会话ID',
  `atomic_script_id` bigint COMMENT '原子脚本ID',
  `step_name` varchar(100) COMMENT '步骤名称',
  `status` enum('PREPARING', 'EXECUTING', 'WAITING_INPUT', 'WAITING_CONFIRM', 'COMPLETED', 'FAILED', 'SKIPPED', 'CANCELLED', 'PAUSED') COMMENT '执行状态',
  `message` text COMMENT '日志消息',
  `output` text COMMENT '命令输出',
  `execution_time` int COMMENT '执行耗时（毫秒）',
  `timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间戳',
  PRIMARY KEY (`id`),
  INDEX `idx_session_timestamp` (`session_id`, `timestamp`),
  INDEX `idx_atomic_script_id` (`atomic_script_id`),
  INDEX `idx_status` (`status`),
  FOREIGN KEY (`session_id`) REFERENCES `script_execution_sessions`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`atomic_script_id`) REFERENCES `atomic_scripts`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='脚本执行日志表';

-- 脚本执行变量表
CREATE TABLE `script_execution_variables` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '变量ID',
  `execution_id` bigint NOT NULL COMMENT '执行记录ID',
  `script_id` bigint NOT NULL COMMENT '脚本ID',
  `variable_name` varchar(100) NOT NULL COMMENT '变量名',
  `variable_value` text COMMENT '变量值',
  `variable_type` varchar(50) DEFAULT 'STRING' COMMENT '变量类型',
  `variable_scope` enum('GLOBAL', 'SCRIPT', 'SESSION') DEFAULT 'SCRIPT' COMMENT '变量作用域',
  `is_sensitive` boolean DEFAULT false COMMENT '是否敏感数据',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_execution_id` (`execution_id`),
  INDEX `idx_script_id` (`script_id`),
  INDEX `idx_variable_name` (`variable_name`),
  FOREIGN KEY (`script_id`) REFERENCES `atomic_scripts`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='脚本执行变量表';

-- ====================================
-- 配置和模板表
-- ====================================

-- 项目配置表
CREATE TABLE `project_configs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `name` varchar(100) NOT NULL COMMENT '项目名称',
  `description` text COMMENT '项目描述',
  `project_type` varchar(50) COMMENT '项目类型',
  `config_variables` json COMMENT '配置变量',
  `environment` varchar(50) DEFAULT 'development' COMMENT '环境',
  `status` enum('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE' COMMENT '状态',
  `created_by` bigint COMMENT '创建者ID',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_project_type` (`project_type`),
  INDEX `idx_environment` (`environment`),
  INDEX `idx_status` (`status`),
  FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目配置表';

-- 配置模板表
CREATE TABLE `config_templates` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  `name` varchar(100) NOT NULL COMMENT '模板名称',
  `template_content` text NOT NULL COMMENT '模板内容，如docker-compose.yaml',
  `variable_definitions` json COMMENT '变量定义和默认值',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置模板表';

-- ====================================
-- 旧版兼容表（保留用于数据迁移）
-- ====================================

-- 旧版脚本表（保留用于兼容性）
CREATE TABLE `scripts` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '脚本ID',
  `name` varchar(100) NOT NULL COMMENT '脚本名称',
  `description` text COMMENT '脚本描述',
  `config` json NOT NULL COMMENT '原子脚本配置JSON',
  `sort_order` int DEFAULT 0 COMMENT '排序顺序',
  `status` enum('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE' COMMENT '状态',
  `created_by` bigint COMMENT '创建者ID',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_status_sort` (`status`, `sort_order`),
  FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='旧版脚本表';

-- 旧版脚本执行记录表
CREATE TABLE `script_executions` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '执行记录ID',
  `script_id` bigint NOT NULL COMMENT '脚本ID',
  `user_id` bigint COMMENT '用户ID',
  `session_id` varchar(100) COMMENT '会话ID',
  `status` enum('RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED') DEFAULT 'RUNNING' COMMENT '执行状态',
  `start_time` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time` timestamp NULL COMMENT '结束时间',
  `error_message` text COMMENT '错误信息',
  PRIMARY KEY (`id`),
  INDEX `idx_script_id` (`script_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_status` (`status`),
  FOREIGN KEY (`script_id`) REFERENCES `scripts`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='旧版脚本执行记录表';

-- 旧版执行日志表
CREATE TABLE `execution_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `execution_id` bigint NOT NULL COMMENT '执行记录ID',
  `step_name` varchar(200) COMMENT '步骤名称',
  `log_type` enum('INFO', 'SUCCESS', 'ERROR', 'WARN', 'DEBUG') DEFAULT 'INFO' COMMENT '日志类型',
  `message` text NOT NULL COMMENT '日志消息',
  `timestamp` timestamp DEFAULT CURRENT_TIMESTAMP COMMENT '时间戳',
  `step_order` int DEFAULT 0 COMMENT '步骤顺序',
  PRIMARY KEY (`id`),
  INDEX `idx_execution_id` (`execution_id`),
  INDEX `idx_timestamp` (`timestamp`),
  FOREIGN KEY (`execution_id`) REFERENCES `script_executions`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='旧版执行日志表';

-- ====================================
-- 初始化数据
-- ====================================

-- 插入默认用户
-- 管理员账号: admin / admin123
-- 普通用户账号: user / user123
INSERT INTO `users` (`username`, `password`, `email`, `role`, `status`) VALUES 
('admin', '$2a$12$EXRkfkdTkHjwhXeQsX5vhOxVpBnbatMWpICbFNJvpeJbFo.B5ssLO', 'admin@terminal.com', 'ADMIN', 'ACTIVE'),
('user', '$2a$12$8y9YQQwpY5ysX5vhOxVpBekYXNJvpeJbFo.B5ssLOwIC7sKdTkHjw', 'user@terminal.com', 'USER', 'ACTIVE');

-- 插入示例脚本分组
INSERT INTO `script_groups` (`name`, `description`, `type`, `icon`, `display_order`, `created_by`, `status`) VALUES 
('系统管理', '系统相关的管理和维护脚本', 'FUNCTION_DIMENSION', 'el-icon-setting', 1, 1, 'ACTIVE'),
('环境部署', '各种服务和应用的部署脚本', 'FUNCTION_DIMENSION', 'el-icon-upload', 2, 1, 'ACTIVE'),
('数据库管理', '数据库相关的管理脚本', 'PROJECT_DIMENSION', 'el-icon-coin', 3, 1, 'ACTIVE'),
('Docker容器', 'Docker容器管理脚本', 'PROJECT_DIMENSION', 'el-icon-box', 4, 1, 'ACTIVE'),
('监控巡检', '系统监控和定期巡检脚本', 'FUNCTION_DIMENSION', 'el-icon-view', 5, 1, 'ACTIVE');

-- 插入示例原子脚本
INSERT INTO `atomic_scripts` (`name`, `description`, `script_content`, `script_type`, `script_type_enum`, `interaction_mode`, `input_variables`, `output_variables`, `prerequisites`, `estimated_duration`, `status`, `created_by`) VALUES 

-- 系统管理类脚本
('系统信息查看', '查看系统基本信息和资源状态', 'system-info', 'bash', 'BUILT_IN_STATIC', 'SILENT', 
 NULL, 
 '{"system_info": {"type": "string", "description": "系统基础信息"}}',
 NULL, 30, 'ACTIVE', 1),

('磁盘空间检查', '检查系统磁盘使用情况', 'df -h && echo "=== inode usage ===" && df -i', 'bash', 'USER_SIMPLE', 'SILENT', 
 NULL, 
 '{"disk_info": {"type": "string", "description": "磁盘使用信息"}}',
 NULL, 10, 'ACTIVE', 1),

('内存使用检查', '检查系统内存使用情况', 'free -h && echo "=== Memory Details ===" && cat /proc/meminfo | head -10', 'bash', 'USER_SIMPLE', 'SILENT', 
 NULL, 
 '{"memory_info": {"type": "string", "description": "内存使用信息"}}',
 NULL, 10, 'ACTIVE', 1),

('系统更新', '更新系统软件包', 'sudo apt update && sudo apt list --upgradable', 'bash', 'USER_SIMPLE', 'CONFIRMATION', 
 NULL, 
 '{"update_result": {"type": "string", "description": "更新结果"}}',
 '[{"type": "os", "value": "ubuntu"}]', 300, 'ACTIVE', 1),

-- 数据库管理类脚本
('MySQL安装', '安装和配置MySQL数据库服务器', 'mysql-install', 'bash', 'BUILT_IN_PARAM', 'INPUT_REQUIRED', 
 '{"mysql_version": {"type": "string", "default": "8.0", "description": "MySQL版本", "required": false}, "mysql_port": {"type": "number", "default": "3306", "description": "MySQL端口", "required": false}, "mysql_root_password": {"type": "password", "description": "root用户密码", "required": true}}', 
 '{"install_result": {"type": "string", "description": "安装结果"}, "mysql_status": {"type": "string", "description": "MySQL服务状态"}}',
 '[{"type": "os", "value": "ubuntu"}, {"type": "command", "value": "sudo"}]', 600, 'ACTIVE', 1),

('Redis安装', '安装和配置Redis缓存服务器', 'redis-install', 'bash', 'BUILT_IN_PARAM', 'INPUT_REQUIRED', 
 '{"redis_port": {"type": "number", "default": "6379", "description": "Redis端口", "required": false}, "redis_password": {"type": "password", "description": "Redis密码", "required": false}, "redis_max_memory": {"type": "string", "default": "256mb", "description": "最大内存限制", "required": false}}', 
 '{"install_result": {"type": "string", "description": "安装结果"}, "redis_status": {"type": "string", "description": "Redis服务状态"}}',
 '[{"type": "os", "value": "ubuntu"}, {"type": "command", "value": "sudo"}]', 300, 'ACTIVE', 1),

-- Docker管理类脚本
('Docker安装', '安装Docker容器运行时环境', 'docker-install', 'bash', 'BUILT_IN_STATIC', 'CONFIRMATION', 
 NULL, 
 '{"install_result": {"type": "string", "description": "Docker安装结果"}, "docker_version": {"type": "string", "description": "Docker版本信息"}}',
 '[{"type": "os", "value": "ubuntu"}, {"type": "command", "value": "curl"}]', 480, 'ACTIVE', 1),

('Docker Compose安装', '安装Docker Compose编排工具', 'curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose && chmod +x /usr/local/bin/docker-compose', 'bash', 'USER_SIMPLE', 'CONFIRMATION', 
 NULL, 
 '{"compose_version": {"type": "string", "description": "Docker Compose版本"}}',
 '[{"type": "command", "value": "docker"}, {"type": "command", "value": "curl"}]', 120, 'ACTIVE', 1),

-- 服务管理类脚本
('服务重启', '重启指定的系统服务', 'sudo systemctl restart ${service_name} && sudo systemctl status ${service_name}', 'bash', 'USER_TEMPLATE', 'INPUT_REQUIRED', 
 '{"service_name": {"type": "string", "description": "服务名称", "required": true}}', 
 '{"service_status": {"type": "string", "description": "服务状态信息"}}',
 '[{"type": "command", "value": "systemctl"}]', 30, 'ACTIVE', 1),

('端口检查', '检查指定端口的使用情况', 'netstat -tulpn | grep ${port} || echo "端口 ${port} 未被占用"', 'bash', 'USER_TEMPLATE', 'INPUT_REQUIRED', 
 '{"port": {"type": "number", "description": "要检查的端口号", "required": true}}', 
 '{"port_status": {"type": "string", "description": "端口使用状态"}}',
 '[{"type": "command", "value": "netstat"}]', 10, 'ACTIVE', 1);

-- 插入示例聚合脚本
INSERT INTO `aggregated_scripts` (`name`, `description`, `type`, `status`, `created_by`) VALUES 
('系统健康检查', '全面检查系统健康状态', 'GENERIC_TEMPLATE', 'ACTIVE', 1),
('MySQL完整部署', '完整的MySQL数据库部署和配置', 'PROJECT_TEMPLATE', 'ACTIVE', 1),
('Redis完整部署', '完整的Redis缓存服务部署', 'PROJECT_TEMPLATE', 'ACTIVE', 1),
('Docker环境搭建', '完整的Docker环境安装和配置', 'PROJECT_TEMPLATE', 'ACTIVE', 1),
('服务器初始化', '新服务器的基础环境初始化', 'GENERIC_TEMPLATE', 'ACTIVE', 1);

-- 建立聚合脚本与原子脚本的关联关系
INSERT INTO `aggregate_atomic_relations` (`aggregate_id`, `atomic_id`, `execution_order`) VALUES 
-- 系统健康检查
(1, 1, 1), -- 系统信息查看
(1, 2, 2), -- 磁盘空间检查
(1, 3, 3), -- 内存使用检查

-- MySQL完整部署
(2, 1, 1), -- 系统信息查看
(2, 5, 2), -- MySQL安装

-- Redis完整部署
(3, 1, 1), -- 系统信息查看
(3, 6, 2), -- Redis安装

-- Docker环境搭建
(4, 1, 1), -- 系统信息查看
(4, 7, 2), -- Docker安装
(4, 8, 3), -- Docker Compose安装

-- 服务器初始化
(5, 1, 1), -- 系统信息查看
(5, 2, 2), -- 磁盘空间检查
(5, 3, 3), -- 内存使用检查
(5, 4, 4); -- 系统更新

-- 建立分组与聚合脚本的关联关系
INSERT INTO `group_aggregate_relations` (`group_id`, `aggregate_id`, `display_order`) VALUES 
-- 系统管理
(1, 1, 1), -- 系统健康检查
(1, 5, 2), -- 服务器初始化

-- 环境部署
(2, 4, 1), -- Docker环境搭建
(2, 5, 2), -- 服务器初始化

-- 数据库管理
(3, 2, 1), -- MySQL完整部署
(3, 3, 2), -- Redis完整部署

-- Docker容器
(4, 4, 1), -- Docker环境搭建

-- 监控巡检
(5, 1, 1); -- 系统健康检查

-- 插入示例项目配置
INSERT INTO `project_configs` (`name`, `description`, `project_type`, `config_variables`, `environment`, `status`, `created_by`) VALUES 
('生产环境MySQL配置', '生产环境MySQL数据库标准配置', 'mysql', 
 '{"mysql_version": "8.0", "mysql_port": 3306, "mysql_root_password": "SecurePassword123!", "max_connections": 1000, "innodb_buffer_pool_size": "1G"}',
 'production', 'ACTIVE', 1),
 
('开发环境Redis配置', '开发环境Redis缓存标准配置', 'redis',
 '{"redis_port": 6379, "redis_password": "", "redis_max_memory": "256mb", "redis_maxmemory_policy": "allkeys-lru"}',
 'development', 'ACTIVE', 1),
 
('Docker标准配置', 'Docker容器环境标准配置', 'docker',
 '{"docker_registry": "registry.cn-hangzhou.aliyuncs.com", "docker_data_root": "/var/lib/docker", "enable_experimental": false}',
 'production', 'ACTIVE', 1);

-- 插入配置模板
INSERT INTO `config_templates` (`name`, `template_content`, `variable_definitions`) VALUES 
('Docker Compose MySQL模板', 
'version: ''3.8''
services:
  mysql:
    image: mysql:${mysql_version}
    container_name: ${container_name}
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: ${mysql_root_password}
      MYSQL_DATABASE: ${mysql_database}
      MYSQL_USER: ${mysql_user}
      MYSQL_PASSWORD: ${mysql_password}
    ports:
      - "${mysql_port}:3306"
    volumes:
      - mysql_data:/var/lib/mysql
    networks:
      - mysql_network

volumes:
  mysql_data:

networks:
  mysql_network:
    driver: bridge',
'{"mysql_version": {"type": "string", "default": "8.0", "description": "MySQL版本"}, "container_name": {"type": "string", "default": "mysql-server", "description": "容器名称"}, "mysql_root_password": {"type": "password", "required": true, "description": "root密码"}, "mysql_database": {"type": "string", "default": "appdb", "description": "默认数据库名"}, "mysql_user": {"type": "string", "default": "appuser", "description": "应用用户名"}, "mysql_password": {"type": "password", "required": true, "description": "应用用户密码"}, "mysql_port": {"type": "number", "default": 3306, "description": "映射端口"}}'),

('Nginx配置模板',
'server {
    listen ${port};
    server_name ${server_name};
    
    location / {
        proxy_pass http://${upstream_host}:${upstream_port};
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    
    access_log ${log_path}/access.log;
    error_log ${log_path}/error.log;
}',
'{"port": {"type": "number", "default": 80, "description": "监听端口"}, "server_name": {"type": "string", "required": true, "description": "服务器名称"}, "upstream_host": {"type": "string", "default": "127.0.0.1", "description": "上游主机"}, "upstream_port": {"type": "number", "default": 8080, "description": "上游端口"}, "log_path": {"type": "string", "default": "/var/log/nginx", "description": "日志路径"}}');

-- ====================================
-- 创建索引优化
-- ====================================

-- 为经常查询的字段创建组合索引
CREATE INDEX `idx_atomic_scripts_status_type` ON `atomic_scripts` (`status`, `script_type_enum`);
CREATE INDEX `idx_aggregated_scripts_type_status` ON `aggregated_scripts` (`type`, `status`);
CREATE INDEX `idx_script_groups_type_display` ON `script_groups` (`type`, `display_order`);
CREATE INDEX `idx_sessions_status_created` ON `script_execution_sessions` (`status`, `created_at`);
CREATE INDEX `idx_logs_session_timestamp` ON `script_execution_logs` (`session_id`, `timestamp` DESC);

-- ====================================
-- 数据库配置优化
-- ====================================

-- 设置字符集
ALTER DATABASE CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 提交所有更改
COMMIT;

-- 显示创建结果
SELECT 'SSH Terminal Management System Database Initialized Successfully!' as Status;
SELECT COUNT(*) as user_count FROM users;
SELECT COUNT(*) as atomic_scripts_count FROM atomic_scripts;
SELECT COUNT(*) as aggregated_scripts_count FROM aggregated_scripts;
SELECT COUNT(*) as script_groups_count FROM script_groups;
SELECT 'Database initialization completed at:' as message, NOW() as timestamp;