-- SSH Terminal Management System Database Schema

-- 用户表
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL UNIQUE,
  `password` varchar(100) NOT NULL,
  `email` varchar(100),
  `role` enum('ADMIN', 'USER') DEFAULT 'USER',
  `status` enum('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

-- 原子脚本表（最小执行单元）
CREATE TABLE `atomic_scripts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `description` text,
  `script_content` text NOT NULL COMMENT '脚本内容',
  `script_type` varchar(50) COMMENT '脚本类型: bash, python, node等',
  `input_params` json COMMENT '输入参数定义',
  `output_params` json COMMENT '输出参数定义',
  `dependencies` json COMMENT '依赖的其他原子脚本或系统要求',
  `execution_timeout` int DEFAULT 300 COMMENT '执行超时时间（秒）',
  `retry_count` int DEFAULT 0 COMMENT '重试次数',
  `sort_order` int DEFAULT 0,
  `status` enum('ACTIVE', 'INACTIVE', 'DRAFT') DEFAULT 'DRAFT',
  `version` varchar(20) DEFAULT '1.0.0' COMMENT '版本号',
  `tags` json COMMENT '标签，用于分类和搜索',
  `created_by` bigint,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_name_version` (`name`, `version`),
  FOREIGN KEY (`created_by`) REFERENCES `users`(`id`)
);

-- 原子脚本表（基础脚本单元）
CREATE TABLE `scripts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `description` text,
  `config` json NOT NULL COMMENT '原子脚本配置JSON',
  `sort_order` int DEFAULT 0,
  `status` enum('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE',
  `created_by` bigint,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`created_by`) REFERENCES `users`(`id`)
);

-- 聚合脚本表（组装多个原子脚本）
CREATE TABLE `aggregated_scripts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `description` text,
  `script_ids` json COMMENT '关联的原子脚本ID数组',
  `execution_order` json COMMENT '执行顺序配置',
  `sort_order` int DEFAULT 0,
  `status` enum('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE',
  `created_by` bigint,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`created_by`) REFERENCES `users`(`id`)
);

-- 脚本分组表（用户端展示的页面分组）
CREATE TABLE `script_groups` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `description` text,
  `init_script` text COMMENT '页面初始化脚本',
  `sort_order` int DEFAULT 0,
  `status` enum('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE',
  `created_by` bigint,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`created_by`) REFERENCES `users`(`id`)
);

-- 脚本分组与聚合脚本关联表（多对多关系）
CREATE TABLE `script_group_aggregated_scripts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` bigint NOT NULL,
  `aggregated_script_id` bigint NOT NULL,
  `sort_order` int DEFAULT 0,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_group_script` (`group_id`, `aggregated_script_id`),
  FOREIGN KEY (`group_id`) REFERENCES `script_groups`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`aggregated_script_id`) REFERENCES `aggregated_scripts`(`id`) ON DELETE CASCADE
);

-- 脚本执行记录表
CREATE TABLE `script_executions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `script_id` bigint NOT NULL,
  `user_id` bigint,
  `session_id` varchar(100),
  `status` enum('RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED') DEFAULT 'RUNNING',
  `start_time` timestamp DEFAULT CURRENT_TIMESTAMP,
  `end_time` timestamp NULL,
  `error_message` text,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`script_id`) REFERENCES `scripts`(`id`),
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`)
);

-- 脚本执行详细日志表
CREATE TABLE `execution_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `execution_id` bigint NOT NULL,
  `step_name` varchar(200),
  `log_type` enum('INFO', 'SUCCESS', 'ERROR', 'WARN', 'DEBUG') DEFAULT 'INFO',
  `message` text NOT NULL,
  `timestamp` timestamp DEFAULT CURRENT_TIMESTAMP,
  `step_order` int DEFAULT 0,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`execution_id`) REFERENCES `script_executions`(`id`)
);

-- SSH服务器配置表
CREATE TABLE `ssh_servers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `host` varchar(255) NOT NULL,
  `port` int DEFAULT 22,
  `username` varchar(100),
  `auth_type` enum('PASSWORD', 'PRIVATE_KEY') DEFAULT 'PASSWORD',
  `sort_order` int DEFAULT 0,
  `status` enum('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE',
  `created_by` bigint,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`created_by`) REFERENCES `users`(`id`)
);

-- WebSocket会话管理表
CREATE TABLE `websocket_sessions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(100) NOT NULL UNIQUE,
  `user_id` bigint,
  `connection_type` enum('SSH', 'SCRIPT_EXECUTION') NOT NULL,
  `server_id` bigint,
  `script_execution_id` bigint,
  `status` enum('CONNECTED', 'DISCONNECTED') DEFAULT 'CONNECTED',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
  FOREIGN KEY (`server_id`) REFERENCES `ssh_servers`(`id`),
  FOREIGN KEY (`script_execution_id`) REFERENCES `script_executions`(`id`)
);

-- 插入默认用户
-- 管理员账号: admin / admin123
INSERT INTO `users` (`username`, `password`, `email`, `role`) 
VALUES ('admin', '$2a$12$EXRkfkdTkHjwhXeQsX5vhOxVpBnbatMWpICbFNJvpeJbFo.B5ssLO', 'admin@example.com', 'ADMIN');

-- 普通用户账号: user / user123  
INSERT INTO `users` (`username`, `password`, `email`, `role`) 
VALUES ('user', '$2a$12$8y9YQQwpY5ysX5vhOxVpBekYXNJvpeJbFo.B5ssLOwIC7sKdTkHjw', 'user@example.com', 'USER');

-- 插入示例脚本分组
INSERT INTO `script_groups` (`name`, `description`, `sort_order`, `created_by`) 
VALUES 
('系统管理', '系统相关的管理脚本', 1, 1),
('应用部署', '应用程序部署相关脚本', 2, 1),
('监控巡检', '系统监控和巡检脚本', 3, 1);

-- 插入示例原子脚本
INSERT INTO `atomic_scripts` (`name`, `description`, `script_content`, `script_type`, `input_params`, `output_params`, `tags`, `status`, `version`, `created_by`) 
VALUES 
('check_disk_space', '检查磁盘空间', 'df -h', 'bash', 
 '{}', 
 '{"disk_info": "string"}',
 '["system", "monitoring", "disk"]', 
 'ACTIVE', '1.0.0', 1),

('check_memory', '检查内存使用情况', 'free -h', 'bash', 
 '{}', 
 '{"memory_info": "string"}',
 '["system", "monitoring", "memory"]', 
 'ACTIVE', '1.0.0', 1),

('check_cpu_load', '检查CPU负载', 'uptime', 'bash', 
 '{}', 
 '{"load_info": "string"}',
 '["system", "monitoring", "cpu"]', 
 'ACTIVE', '1.0.0', 1),

('update_system', '更新系统包', 'sudo apt update && sudo apt upgrade -y', 'bash', 
 '{}', 
 '{"update_result": "string"}',
 '["system", "maintenance", "update"]', 
 'ACTIVE', '1.0.0', 1),

('restart_service', '重启服务', 'sudo systemctl restart ${service_name}', 'bash', 
 '{"service_name": "string"}', 
 '{"restart_result": "string"}',
 '["system", "service", "restart"]', 
 'ACTIVE', '1.0.0', 1);