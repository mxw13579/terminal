-- 更新原子脚本表以支持新的交互功能
ALTER TABLE `atomic_scripts` 
ADD COLUMN `script_type_enum` ENUM('BUILT_IN_STATIC', 'BUILT_IN_PARAM', 'USER_SIMPLE', 'USER_TEMPLATE') DEFAULT 'USER_SIMPLE' AFTER `script_type`,
ADD COLUMN `interaction_mode` ENUM('SILENT', 'CONFIRMATION', 'INPUT_REQUIRED', 'CONDITIONAL', 'REALTIME_OUTPUT') DEFAULT 'SILENT' AFTER `script_type_enum`,
ADD COLUMN `interaction_config` JSON COMMENT '交互配置，JSON格式存储' AFTER `interaction_mode`,
ADD COLUMN `input_variables` JSON COMMENT '输入变量定义' AFTER `interaction_config`,
ADD COLUMN `output_variables` JSON COMMENT '输出变量定义' AFTER `input_variables`,
ADD COLUMN `prerequisites` JSON COMMENT '前置条件' AFTER `output_variables`,
ADD COLUMN `estimated_duration` INT DEFAULT 0 COMMENT '预估执行时长（秒）' AFTER `prerequisites`;

-- 更新聚合脚本表以支持新的聚合脚本类型
ALTER TABLE `aggregated_scripts`
ADD COLUMN `type` ENUM('GENERIC_TEMPLATE', 'PROJECT_SPECIFIC') DEFAULT 'GENERIC_TEMPLATE' AFTER `description`,
ADD COLUMN `config_template` JSON COMMENT '配置模板，用于通用模板类型' AFTER `execution_order`;

-- 更新脚本分组表以支持新的分组类型
ALTER TABLE `script_groups`
ADD COLUMN `type` ENUM('PROJECT_DIMENSION', 'FUNCTION_DIMENSION') DEFAULT 'PROJECT_DIMENSION' AFTER `description`,
ADD COLUMN `icon` VARCHAR(100) COMMENT '图标' AFTER `type`,
ADD COLUMN `display_order` INT DEFAULT 0 COMMENT '显示顺序' AFTER `icon`,
DROP COLUMN `init_script`,
DROP COLUMN `sort_order`;

-- 创建聚合脚本-原子脚本关联表
CREATE TABLE `aggregate_atomic_relations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `aggregate_id` bigint NOT NULL,
  `atomic_id` bigint NOT NULL,
  `execution_order` int NOT NULL,
  `variable_mapping` json COMMENT '变量映射配置',
  `condition_expression` varchar(500) COMMENT '执行条件表达式',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_aggregate_atomic_order` (`aggregate_id`, `atomic_id`, `execution_order`),
  FOREIGN KEY (`aggregate_id`) REFERENCES `aggregated_scripts`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`atomic_id`) REFERENCES `atomic_scripts`(`id`) ON DELETE CASCADE
);

-- 创建分组-聚合脚本关联表（替换原有的关联表）
DROP TABLE IF EXISTS `script_group_aggregated_scripts`;
CREATE TABLE `group_aggregate_relations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` bigint NOT NULL,
  `aggregate_id` bigint NOT NULL,
  `display_order` int DEFAULT 0,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_group_aggregate` (`group_id`, `aggregate_id`),
  FOREIGN KEY (`group_id`) REFERENCES `script_groups`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`aggregate_id`) REFERENCES `aggregated_scripts`(`id`) ON DELETE CASCADE
);

-- 创建配置模板表
CREATE TABLE `config_templates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `template_content` text NOT NULL COMMENT '模板内容，如docker-compose.yaml',
  `variable_definitions` json COMMENT '变量定义和默认值',
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

-- 创建脚本执行会话表
CREATE TABLE `script_execution_sessions` (
  `id` varchar(36) NOT NULL COMMENT 'UUID',
  `user_id` bigint,
  `aggregate_script_id` bigint NOT NULL,
  `status` ENUM('PREPARING', 'EXECUTING', 'WAITING_INPUT', 'WAITING_CONFIRM', 'COMPLETED', 'FAILED', 'CANCELLED', 'PAUSED') NOT NULL DEFAULT 'PREPARING',
  `context_data` json COMMENT '上下文数据的JSON序列化',
  `start_time` timestamp NULL,
  `end_time` timestamp NULL,
  `created_at` timestamp DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
  FOREIGN KEY (`aggregate_script_id`) REFERENCES `aggregated_scripts`(`id`)
);

-- 创建脚本交互记录表
CREATE TABLE `script_interactions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(36) NOT NULL,
  `atomic_script_id` bigint,
  `interaction_type` ENUM('CONFIRM_YES_NO', 'CONFIRM_RECOMMENDATION', 'INPUT_TEXT', 'INPUT_PASSWORD', 'INPUT_FORM', 'SELECT_OPTION', 'FILE_UPLOAD'),
  `prompt_message` text,
  `user_response` json,
  `response_time` timestamp NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`session_id`) REFERENCES `script_execution_sessions`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`atomic_script_id`) REFERENCES `atomic_scripts`(`id`)
);

-- 创建脚本执行日志表
CREATE TABLE `script_execution_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(36) NOT NULL,
  `atomic_script_id` bigint,
  `step_name` varchar(100),
  `status` ENUM('PREPARING', 'EXECUTING', 'WAITING_INPUT', 'WAITING_CONFIRM', 'COMPLETED', 'FAILED', 'SKIPPED', 'CANCELLED', 'PAUSED'),
  `message` text,
  `output` text COMMENT '命令输出',
  `execution_time` int COMMENT '执行耗时（毫秒）',
  `timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_session_timestamp` (`session_id`, `timestamp`),
  FOREIGN KEY (`session_id`) REFERENCES `script_execution_sessions`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`atomic_script_id`) REFERENCES `atomic_scripts`(`id`)
);