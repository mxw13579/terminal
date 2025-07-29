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

-- 脚本分组表
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

-- 脚本配置表
CREATE TABLE `scripts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `description` text,
  `config` json NOT NULL COMMENT '拖拽流程配置JSON',
  `group_id` bigint,
  `sort_order` int DEFAULT 0,
  `status` enum('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE',
  `created_by` bigint,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`group_id`) REFERENCES `script_groups`(`id`),
  FOREIGN KEY (`created_by`) REFERENCES `users`(`id`)
);

-- 聚合脚本表（新增）
CREATE TABLE `aggregated_scripts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `description` text,
  `group_id` bigint NOT NULL,
  `script_ids` json COMMENT '关联的脚本ID数组',
  `execution_order` json COMMENT '执行顺序配置',
  `sort_order` int DEFAULT 0,
  `status` enum('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE',
  `created_by` bigint,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`group_id`) REFERENCES `script_groups`(`id`),
  FOREIGN KEY (`created_by`) REFERENCES `users`(`id`)
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

-- 插入默认管理员用户
INSERT INTO `users` (`username`, `password`, `email`, `role`) 
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVajVG', 'admin@example.com', 'ADMIN');