-- 插入交互式原子脚本示例数据

-- 1. 内置静态脚本：检测操作系统
INSERT INTO `atomic_scripts` (`name`, `description`, `script_content`, `script_type`, `script_type_enum`, `interaction_mode`, `input_variables`, `output_variables`, `status`, `version`, `created_by`) 
VALUES 
('detect_os', '检测操作系统类型', 'cat /etc/os-release | grep "^ID=" | cut -d"=" -f2 | tr -d \'"\'', 'bash', 'BUILT_IN_STATIC', 'SILENT', 
 '{}', 
 '{"OS_TYPE": "string"}',
 'ACTIVE', '1.0.0', 1);

-- 2. 内置参数化脚本：切换软件源
INSERT INTO `atomic_scripts` (`name`, `description`, `script_content`, `script_type`, `script_type_enum`, `interaction_mode`, `interaction_config`, `input_variables`, `output_variables`, `prerequisites`, `status`, `version`, `created_by`) 
VALUES 
('switch_apt_source', '切换APT软件源', 'sudo sed -i "s|http://archive.ubuntu.com|${mirror_url}|g" /etc/apt/sources.list && sudo apt update', 'bash', 'BUILT_IN_PARAM', 'CONDITIONAL', 
 '[{"interactionId": "source_switch", "type": "CONFIRM_RECOMMENDATION", "prompt": "当前服务器位于${SERVER_LOCATION}，建议切换到${recommended_source}软件源以提高下载速度", "options": ["是", "否"], "condition": "SERVER_LOCATION == '\''china'\''", "defaultValue": "是"}]',
 '{"mirror_url": "string", "recommended_source": "string"}', 
 '{"source_switched": "boolean"}',
 '{"SERVER_LOCATION": "string"}',
 'ACTIVE', '1.0.0', 1);

-- 3. 内置参数化脚本：Docker安装
INSERT INTO `atomic_scripts` (`name`, `description`, `script_content`, `script_type`, `script_type_enum`, `interaction_mode`, `estimated_duration`, `status`, `version`, `created_by`) 
VALUES 
('install_docker', '安装Docker', 'curl -fsSL https://get.docker.com -o get-docker.sh && sudo sh get-docker.sh && sudo usermod -aG docker $USER', 'bash', 'BUILT_IN_PARAM', 'REALTIME_OUTPUT', 
 120, 'ACTIVE', '1.0.0', 1);

-- 4. 内置参数化脚本：Docker源切换
INSERT INTO `atomic_scripts` (`name`, `description`, `script_content`, `script_type`, `script_type_enum`, `interaction_mode`, `interaction_config`, `input_variables`, `output_variables`, `prerequisites`, `status`, `version`, `created_by`) 
VALUES 
('switch_docker_mirror', '切换Docker镜像源', 'sudo mkdir -p /etc/docker && echo \'{"registry-mirrors": ["${docker_mirror_url}"]}\' | sudo tee /etc/docker/daemon.json && sudo systemctl restart docker', 'bash', 'BUILT_IN_PARAM', 'CONDITIONAL', 
 '[{"interactionId": "docker_mirror_switch", "type": "CONFIRM_RECOMMENDATION", "prompt": "当前服务器位于${SERVER_LOCATION}，建议切换到${recommended_docker_mirror}镜像源", "options": ["是", "否"], "condition": "SERVER_LOCATION == '\''china'\''", "defaultValue": "是"}]',
 '{"docker_mirror_url": "string", "recommended_docker_mirror": "string"}', 
 '{"docker_mirror_switched": "boolean"}',
 '{"SERVER_LOCATION": "string"}',
 'ACTIVE', '1.0.0', 1);

-- 5. 用户模板脚本：MySQL配置
INSERT INTO `atomic_scripts` (`name`, `description`, `script_content`, `script_type`, `script_type_enum`, `interaction_mode`, `interaction_config`, `input_variables`, `output_variables`, `status`, `version`, `created_by`) 
VALUES 
('mysql_docker_deploy', 'Docker部署MySQL', 'docker-compose up -d mysql', 'bash', 'USER_TEMPLATE', 'INPUT_REQUIRED', 
 '[{"interactionId": "mysql_config", "type": "INPUT_FORM", "prompt": "请配置MySQL参数", "inputFields": {"MYSQL_ROOT_PASSWORD": {"type": "password", "label": "Root密码", "required": true}, "MYSQL_DATABASE": {"type": "text", "label": "数据库名", "defaultValue": "app_db"}, "MYSQL_PORT": {"type": "number", "label": "端口", "defaultValue": "3306"}, "MYSQL_DATA_PATH": {"type": "text", "label": "数据存储路径", "defaultValue": "./mysql-data"}}}]',
 '{"MYSQL_ROOT_PASSWORD": "string", "MYSQL_DATABASE": "string", "MYSQL_PORT": "number", "MYSQL_DATA_PATH": "string"}', 
 '{"mysql_container_id": "string"}',
 'ACTIVE', '1.0.0', 1);

-- 插入聚合脚本示例

-- 1. 通用模板：Docker MySQL部署
INSERT INTO `aggregated_scripts` (`name`, `description`, `type`, `config_template`, `status`, `created_by`) 
VALUES 
('docker_mysql_deployment', 'Docker MySQL 一键部署', 'GENERIC_TEMPLATE', 
 '{"docker_compose_template": "version: '\''3.8'\''\nservices:\n  mysql:\n    image: mysql:8.0\n    environment:\n      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}\n      MYSQL_DATABASE: ${MYSQL_DATABASE}\n    ports:\n      - \"${MYSQL_PORT}:3306\"\n    volumes:\n      - \"${MYSQL_DATA_PATH}:/var/lib/mysql\"", "variables": {"MYSQL_ROOT_PASSWORD": {"type": "password", "required": true, "description": "MySQL root密码"}, "MYSQL_DATABASE": {"type": "string", "default": "app_db", "description": "默认数据库名"}, "MYSQL_PORT": {"type": "number", "default": 3306, "description": "MySQL端口"}, "MYSQL_DATA_PATH": {"type": "path", "default": "./mysql-data", "description": "数据存储路径"}}}', 
 'ACTIVE', 1);

-- 2. 项目特定：MySQL管理
INSERT INTO `aggregated_scripts` (`name`, `description`, `type`, `status`, `created_by`) 
VALUES 
('mysql_management', 'MySQL 数据库管理', 'PROJECT_SPECIFIC', 'ACTIVE', 1);

-- 插入聚合脚本与原子脚本的关联关系

-- Docker MySQL部署流程
INSERT INTO `aggregate_atomic_relations` (`aggregate_id`, `atomic_id`, `execution_order`, `variable_mapping`) 
VALUES 
((SELECT id FROM aggregated_scripts WHERE name = 'docker_mysql_deployment'), (SELECT id FROM atomic_scripts WHERE name = 'detect_os'), 1, '{}'),
((SELECT id FROM aggregated_scripts WHERE name = 'docker_mysql_deployment'), (SELECT id FROM atomic_scripts WHERE name = 'switch_apt_source'), 2, '{"recommended_source": "阿里云", "mirror_url": "http://mirrors.aliyun.com/ubuntu/"}'),
((SELECT id FROM aggregated_scripts WHERE name = 'docker_mysql_deployment'), (SELECT id FROM atomic_scripts WHERE name = 'install_docker'), 3, '{}'),
((SELECT id FROM aggregated_scripts WHERE name = 'docker_mysql_deployment'), (SELECT id FROM atomic_scripts WHERE name = 'switch_docker_mirror'), 4, '{"recommended_docker_mirror": "阿里云", "docker_mirror_url": "https://registry.cn-hangzhou.aliyuncs.com"}'),
((SELECT id FROM aggregated_scripts WHERE name = 'docker_mysql_deployment'), (SELECT id FROM atomic_scripts WHERE name = 'mysql_docker_deploy'), 5, '{}');

-- 插入脚本分组示例

-- 更新现有分组
UPDATE `script_groups` SET `type` = 'PROJECT_DIMENSION', `icon` = 'database', `display_order` = 1 WHERE `name` = '系统管理';
UPDATE `script_groups` SET `type` = 'PROJECT_DIMENSION', `icon` = 'rocket', `display_order` = 2 WHERE `name` = '应用部署';
UPDATE `script_groups` SET `type` = 'FUNCTION_DIMENSION', `icon` = 'monitor', `display_order` = 3 WHERE `name` = '监控巡检';

-- 新增项目维度分组
INSERT INTO `script_groups` (`name`, `description`, `type`, `icon`, `display_order`, `created_by`) 
VALUES 
('MySQL管理', 'MySQL数据库相关的部署和管理', 'PROJECT_DIMENSION', 'database', 4, 1),
('Redis管理', 'Redis缓存相关的部署和管理', 'PROJECT_DIMENSION', 'memory', 5, 1),
('Web服务管理', 'Web服务器和应用的部署管理', 'PROJECT_DIMENSION', 'globe', 6, 1);

-- 新增功能维度分组
INSERT INTO `script_groups` (`name`, `description`, `type`, `icon`, `display_order`, `created_by`) 
VALUES 
('环境初始化', '服务器环境初始化相关脚本', 'FUNCTION_DIMENSION', 'settings', 7, 1),
('安全管理', '系统安全配置和管理', 'FUNCTION_DIMENSION', 'shield', 8, 1);

-- 插入分组与聚合脚本的关联关系
INSERT INTO `group_aggregate_relations` (`group_id`, `aggregate_id`, `display_order`) 
VALUES 
((SELECT id FROM script_groups WHERE name = 'MySQL管理'), (SELECT id FROM aggregated_scripts WHERE name = 'docker_mysql_deployment'), 1),
((SELECT id FROM script_groups WHERE name = 'MySQL管理'), (SELECT id FROM aggregated_scripts WHERE name = 'mysql_management'), 2),
((SELECT id FROM script_groups WHERE name = '应用部署'), (SELECT id FROM aggregated_scripts WHERE name = 'docker_mysql_deployment'), 1);

-- 插入配置模板示例
INSERT INTO `config_templates` (`name`, `template_content`, `variable_definitions`) 
VALUES 
('mysql-docker-compose', 
 'version: ''3.8''\nservices:\n  mysql:\n    image: mysql:8.0\n    environment:\n      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}\n      MYSQL_DATABASE: ${MYSQL_DATABASE}\n    ports:\n      - "${MYSQL_PORT}:3306"\n    volumes:\n      - "${MYSQL_DATA_PATH}:/var/lib/mysql"\n    restart: unless-stopped', 
 '{"MYSQL_ROOT_PASSWORD": {"type": "password", "required": true, "description": "MySQL root密码"}, "MYSQL_DATABASE": {"type": "string", "default": "app_db", "description": "默认数据库名"}, "MYSQL_PORT": {"type": "number", "default": 3306, "description": "MySQL端口"}, "MYSQL_DATA_PATH": {"type": "path", "default": "./mysql-data", "description": "数据存储路径"}}'),

('redis-docker-compose', 
 'version: ''3.8''\nservices:\n  redis:\n    image: redis:7-alpine\n    ports:\n      - "${REDIS_PORT}:6379"\n    volumes:\n      - "${REDIS_DATA_PATH}:/data"\n    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD}\n    restart: unless-stopped', 
 '{"REDIS_PASSWORD": {"type": "password", "required": true, "description": "Redis密码"}, "REDIS_PORT": {"type": "number", "default": 6379, "description": "Redis端口"}, "REDIS_DATA_PATH": {"type": "path", "default": "./redis-data", "description": "数据存储路径"}}');