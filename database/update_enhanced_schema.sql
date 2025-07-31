-- 更新原子脚本表，添加变量相关字段
ALTER TABLE `atomic_scripts` 
ADD COLUMN `interaction_mode` varchar(50) DEFAULT 'SILENT' COMMENT '交互模式',
ADD COLUMN `interaction_config` json COMMENT '交互配置',
ADD COLUMN `input_variables` json COMMENT '输入变量定义',
ADD COLUMN `output_variables` json COMMENT '输出变量定义',
ADD COLUMN `prerequisites` json COMMENT '前置条件',
ADD COLUMN `estimated_duration` int DEFAULT 0 COMMENT '预估执行时长（秒）';

-- 更新聚合脚本表，添加类型字段
ALTER TABLE `aggregated_scripts`
ADD COLUMN `type` varchar(50) DEFAULT 'GENERIC_TEMPLATE' COMMENT '聚合脚本类型',
ADD COLUMN `config_template` json COMMENT '配置模板';

-- 更新脚本分组表，添加类型和图标字段
ALTER TABLE `script_groups`
ADD COLUMN `type` varchar(50) DEFAULT 'PROJECT_DIMENSION' COMMENT '分组类型',
ADD COLUMN `icon` varchar(100) COMMENT '图标',
ADD COLUMN `display_order` int DEFAULT 0 COMMENT '显示顺序';

-- 项目配置表
CREATE TABLE `project_configs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '项目名称',
  `description` text COMMENT '项目描述',
  `project_type` varchar(50) COMMENT '项目类型',
  `config_variables` json COMMENT '配置变量',
  `environment` varchar(50) DEFAULT 'development' COMMENT '环境',
  `status` enum('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE',
  `created_by` bigint,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`created_by`) REFERENCES `users`(`id`)
);

-- 脚本执行变量表
CREATE TABLE `script_execution_variables` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `execution_id` bigint NOT NULL COMMENT '执行记录ID',
  `script_id` bigint NOT NULL COMMENT '脚本ID',
  `variable_name` varchar(100) NOT NULL COMMENT '变量名',
  `variable_value` text COMMENT '变量值',
  `variable_type` varchar(50) DEFAULT 'STRING' COMMENT '变量类型',
  `is_sensitive` boolean DEFAULT false COMMENT '是否敏感数据',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_execution_id` (`execution_id`),
  KEY `idx_script_id` (`script_id`),
  FOREIGN KEY (`execution_id`) REFERENCES `script_executions`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`script_id`) REFERENCES `atomic_scripts`(`id`) ON DELETE CASCADE
);

-- 脚本执行日志表
CREATE TABLE `script_execution_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `execution_id` bigint NOT NULL COMMENT '执行记录ID',
  `step_name` varchar(200) COMMENT '步骤名称',
  `log_type` varchar(20) DEFAULT 'INFO' COMMENT '日志类型',
  `message` text NOT NULL COMMENT '日志消息',
  `step_order` int DEFAULT 0 COMMENT '步骤顺序',
  `timestamp` timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_execution_id` (`execution_id`),
  KEY `idx_timestamp` (`timestamp`),
  FOREIGN KEY (`execution_id`) REFERENCES `script_executions`(`id`) ON DELETE CASCADE
);

-- 更新脚本执行表，添加状态枚举
ALTER TABLE `script_executions`
MODIFY COLUMN `status` enum('RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED') DEFAULT 'RUNNING';

-- 插入一些示例数据

-- 插入内置原子脚本
INSERT INTO `atomic_scripts` (`name`, `description`, `script_content`, `script_type`, `input_variables`, `status`) VALUES
('MySQL 安装', '安装 MySQL 数据库服务器', 'mysql-install', 'BUILT_IN_INTERACTIVE', 
 '{"mysql_version": {"type": "string", "default": "8.0", "description": "MySQL版本"}, "mysql_port": {"type": "number", "default": "3306", "description": "MySQL端口"}, "mysql_root_password": {"type": "password", "required": true, "description": "root密码"}}', 
 'ACTIVE'),
('Redis 安装', '安装 Redis 缓存服务器', 'redis-install', 'BUILT_IN_INTERACTIVE',
 '{"redis_port": {"type": "number", "default": "6379", "description": "Redis端口"}, "redis_password": {"type": "password", "description": "Redis密码"}, "redis_max_memory": {"type": "string", "default": "256mb", "description": "最大内存"}}',
 'ACTIVE'),
('Docker 安装', '安装 Docker 容器运行时', 'docker-install', 'BUILT_IN_TEMPLATE', NULL, 'ACTIVE'),
('系统信息查看', '查看系统基本信息', 'system-info', 'BUILT_IN_TEMPLATE', NULL, 'ACTIVE');

-- 插入脚本分组
INSERT INTO `script_groups` (`name`, `description`, `type`, `status`) VALUES
('MySQL 管理', 'MySQL 数据库相关操作', 'PROJECT_DIMENSION', 'ACTIVE'),
('Redis 管理', 'Redis 缓存相关操作', 'PROJECT_DIMENSION', 'ACTIVE'),
('Docker 管理', 'Docker 容器相关操作', 'PROJECT_DIMENSION', 'ACTIVE'),
('系统监控', '系统监控和维护工具', 'FUNCTION_DIMENSION', 'ACTIVE'),
('环境初始化', '服务器环境初始化工具', 'FUNCTION_DIMENSION', 'ACTIVE');

-- 插入聚合脚本
INSERT INTO `aggregated_scripts` (`name`, `description`, `script_ids`, `execution_order`, `type`, `status`) VALUES
('MySQL 完整部署', '完整的MySQL安装和配置流程', '[1]', '[{"scriptId": 1, "order": 1}]', 'PROJECT_TEMPLATE', 'ACTIVE'),
('Redis 完整部署', '完整的Redis安装和配置流程', '[2]', '[{"scriptId": 2, "order": 1}]', 'PROJECT_TEMPLATE', 'ACTIVE'),
('Docker 环境搭建', '安装Docker并进行基础配置', '[3]', '[{"scriptId": 3, "order": 1}]', 'PROJECT_TEMPLATE', 'ACTIVE'),
('系统环境检查', '检查系统基本信息和状态', '[4]', '[{"scriptId": 4, "order": 1}]', 'GENERIC_TEMPLATE', 'ACTIVE');

-- 建立分组和聚合脚本的关联关系
INSERT INTO `script_group_aggregated_scripts` (`group_id`, `aggregated_script_id`, `sort_order`) VALUES
(1, 1, 1), -- MySQL管理 -> MySQL完整部署
(2, 2, 1), -- Redis管理 -> Redis完整部署  
(3, 3, 1), -- Docker管理 -> Docker环境搭建
(4, 4, 1), -- 系统监控 -> 系统环境检查
(5, 3, 1), -- 环境初始化 -> Docker环境搭建
(5, 4, 2); -- 环境初始化 -> 系统环境检查

-- 插入项目配置示例
INSERT INTO `project_configs` (`name`, `description`, `project_type`, `config_variables`, `environment`, `status`) VALUES
('生产环境MySQL', '生产环境MySQL数据库配置', 'mysql', 
 '{"mysql_version": "8.0", "mysql_port": 3306, "mysql_root_password": "prod_password_123", "max_connections": 1000}',
 'production', 'ACTIVE'),
('开发环境Redis', '开发环境Redis缓存配置', 'redis',
 '{"redis_port": 6379, "redis_password": "", "redis_max_memory": "128mb"}',
 'development', 'ACTIVE');