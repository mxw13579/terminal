# SSH终端管理系统 - 简化架构设计

## 架构概述

本系统是一个基于Web的SSH终端管理平台，专注于为个人用户提供简单、直观的脚本执行和服务器管理体验。架构遵循KISS原则，避免过度设计。

### 核心设计原则

1. **简化优于复杂** - 避免过度抽象和复杂的设计模式
2. **实用优于完美** - 优先满足实际使用需求
3. **代码优于配置** - 内置脚本通过代码管理，不依赖数据库
4. **用户友好** - 降低普通用户的使用门槛

## 系统架构

### 技术栈

- **后端**: Spring Boot 3.0.2 + Java 17
- **前端**: Vue 3 + Vite + Element Plus
- **数据库**: MySQL 8.0
- **实时通信**: WebSocket + STOMP
- **SSH**: JSch 库

### 核心模块设计

```
┌─────────────────────────────────────────────────────────────┐
│                    Web前端 (Vue 3)                          │
├─────────────────────────────────────────────────────────────┤
│  用户端                     │  管理端                        │
│  - SSH终端连接              │  - 脚本分组管理                  │
│  - 项目脚本执行             │  - 用户管理                     │
│  - 实时日志查看             │  - 系统配置                     │
└─────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                控制器层 (Spring MVC)                         │
├─────────────────────────────────────────────────────────────┤
│  /api/user/*               │  /api/admin/*                  │
│  - SSH连接管理              │  - 脚本分组CRUD                 │
│  - 脚本执行控制             │  - 用户权限管理                  │
│  - 实时日志推送             │  - 系统监控                     │
└─────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    业务服务层                                │
├─────────────────────────────────────────────────────────────┤
│  SSH管理服务    │  脚本执行服务    │  项目模板服务    │  用户服务 │
│  - 连接池管理    │  - 内置脚本执行   │  - Docker项目     │  - 认证   │
│  - 会话维护     │  - 实时交互处理   │  - 配置模板      │  - 授权   │
│  - 文件传输     │  - 日志收集      │  - 一键部署      │  - 角色   │
└─────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    数据访问层                                │
├─────────────────────────────────────────────────────────────┤
│  内置脚本注册表   │  数据库实体      │  SSH连接工厂      │       │
│  (代码管理)      │  - 用户          │  - 连接复用       │       │
│  - 系统信息      │  - 项目分组      │  - 安全隔离       │       │
│  - Docker安装    │  - 执行日志      │  - 异常处理       │       │
└─────────────────────────────────────────────────────────────┘
```

## 核心概念定义

### 1. 内置脚本 (Built-in Scripts)
- **定义**: 通过Java代码实现的跨平台脚本，不存储在数据库中
- **特点**: 
  - 高兼容性：自动适配不同操作系统
  - 免维护：通过代码版本控制
  - 类型化：静态(无参数)和动态(有参数)

### 2. 项目分组 (Project Groups)
- **定义**: 按项目维度组织的脚本集合
- **类型**:
  - **Docker项目**: MySQL、Redis、Nginx等容器化项目
  - **原生项目**: 直接在系统上安装的服务

### 3. 脚本模板 (Script Templates)  
- **定义**: 预定义的项目部署模板
- **示例**: 
  - MySQL管理：安装→启动→重启→日志查看
  - Redis管理：安装→配置→监控→备份

## 数据库设计

### 简化的实体关系

```sql
-- 核心实体
users (用户)
  ├── project_groups (项目分组)
      ├── project_templates (项目模板)
      └── script_executions (执行记录)
          └── execution_logs (执行日志)
```

### 关键表结构

```sql
-- 用户表 (简化)
users {
  id, username, password, role, status
}

-- 项目分组表
project_groups {
  id, name, type, description, icon, config_template
}

-- 脚本执行记录表
script_executions {
  id, group_id, script_name, parameters, status, ssh_config
}

-- 执行日志表
execution_logs {
  id, execution_id, log_type, message, timestamp
}
```

## 内置脚本架构

### 脚本类型定义

```java
public enum BuiltInScriptType {
    STATIC,    // 静态脚本：系统信息查看
    DYNAMIC    // 动态脚本：Docker安装(需参数)
}
```

### 内置脚本注册机制

```java
@Component
public class SystemInfoScript implements BuiltInScript {
    @Override
    public String getId() { return "system-info"; }
    
    @Override
    public BuiltInScriptType getType() { return STATIC; }
    
    @Override
    public Map<String, Object> execute(ScriptContext context) {
        // 跨平台系统信息收集逻辑
    }
}
```

## 用户交互流程

### 简化的执行流程

```
用户选择项目分组 
    ↓
配置SSH连接信息
    ↓  
选择具体操作(安装/启动/重启等)
    ↓
系统自动组装内置脚本
    ↓
实时显示执行过程和结果
```

### 智能交互设计

1. **自动检测**: 地域检测→软件源推荐
2. **用户确认**: 关键操作前的确认提示
3. **实时反馈**: 执行过程的进度和状态
4. **错误恢复**: 失败时的重试和回滚选项

## 项目模板系统

### Docker项目模板

```json
{
  "name": "MySQL管理",
  "type": "DOCKER_PROJECT", 
  "operations": [
    {
      "name": "安装",
      "scripts": ["detect-os", "install-docker", "setup-mysql-container"]
    },
    {
      "name": "启动", 
      "scripts": ["start-mysql-container"]
    }
  ],
  "configTemplate": {
    "mysql_root_password": "请输入MySQL root密码",
    "mysql_port": "3306"
  }
}
```

### 原生项目模板

```json
{
  "name": "Nginx管理",
  "type": "NATIVE_PROJECT",
  "operations": [
    {
      "name": "安装",
      "scripts": ["detect-os", "update-package-manager", "install-nginx"]
    }
  ]
}
```

## WebSocket实时通信

### 通信协议

```javascript
// 订阅执行日志
/topic/execution/{executionId}/logs

// 订阅交互请求  
/topic/execution/{executionId}/interaction

// 发送用户响应
/app/execution/respond
```

### 消息格式

```json
{
  "type": "LOG|INTERACTION|STATUS",
  "timestamp": "2024-01-01T10:00:00Z",
  "content": {
    "message": "正在检测操作系统...",
    "logLevel": "INFO|WARN|ERROR"
  }
}
```

## 安全设计

### SSH连接安全
- 连接信息不持久化存储
- 会话隔离和自动清理
- 连接超时和重连机制

### 脚本执行安全
- 输入参数验证和清理
- 危险命令检测和拦截
- 执行权限控制

## 性能优化

### 连接池管理
- SSH连接复用
- 空闲连接回收
- 并发连接限制

### 前端优化  
- 组件懒加载
- 虚拟滚动(长日志)
- WebSocket断线重连

## 部署配置

### 应用配置
```yaml
ssh-terminal:
  max-connections: 50
  connection-timeout: 30s
  script-timeout: 300s
  enable-file-transfer: true
```

### 数据库配置
```yaml  
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ssh_terminal
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update
```

## 扩展点设计

### 新增内置脚本
```java
@Component
public class NewBuiltInScript implements BuiltInScript {
    // 实现接口方法
}
```

### 新增项目模板
```java
@Component  
public class CustomProjectTemplate implements ProjectTemplate {
    // 实现模板逻辑
}
```

## 开发指南

### 代码组织原则
1. **包结构清晰**: 按功能模块分包
2. **职责单一**: 每个类专注一个职责
3. **接口优先**: 面向接口编程
4. **配置外化**: 可配置项使用application.yml

### 新功能开发流程
1. 更新此架构文档
2. 设计接口和数据结构
3. 实现核心逻辑
4. 编写单元测试
5. 集成测试验证

### 注意事项
- **内置脚本**: 必须通过代码实现，禁止数据库存储
- **用户输入**: 严格验证，防止注入攻击
- **错误处理**: 提供友好的错误信息和恢复建议
- **日志记录**: 关键操作必须记录日志
- **性能监控**: 监控SSH连接数和执行时间

## 架构演进

### 短期优化 (1-2个月)
- 完善内置脚本库
- 优化用户交互体验
- 增强错误处理

### 中期扩展 (3-6个月)  
- 支持更多项目模板
- 批量服务器管理
- 脚本执行历史分析

### 长期规划 (6个月+)
- 插件系统
- 多租户支持
- 集群部署

---

## 开发者必读

**在开始编码前，请务必熟读此架构文档。所有代码提交都应该符合此架构设计原则。**

**如需修改架构，请先更新此文档并经过评审。**