# SSH管理工具 - 使用说明

## 项目概述

这是一个基于Spring Boot + Vue3的SSH管理工具，支持脚本自动化执行和SSH终端连接。

## 系统架构

### 后端技术栈
- Spring Boot 3.0.2
- Spring Data JPA
- MySQL 8.0
- WebSocket (STOMP)
- JWT认证

### 前端技术栈
- Vue 3
- Element Plus
- Vue Router 4
- WebSocket客户端

## 功能特性

### 管理端功能 (`/admin`)
1. **脚本分组管理** - 创建、编辑、删除脚本分组，配置初始化脚本
2. **脚本配置管理** - 可视化配置脚本执行流程，支持JSON配置
3. **用户权限管理** - 管理用户账户和角色权限

### 用户端功能 (`/user`)
1. **首页展示** - 脚本分组卡片展示 + SSH连接入口
2. **脚本执行界面** - 左侧脚本列表，右侧实时执行日志
3. **SSH终端** - 保持原有SSH连接功能

### 核心特性
- **实时日志输出** - WebSocket推送脚本执行过程中的详细信息
- **权限分离** - 管理员配置，用户使用
- **响应式布局** - 适配不同屏幕尺寸

## 快速开始

### 1. 数据库准备

```sql
-- 创建数据库
CREATE DATABASE ssh_terminal CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 导入表结构
mysql -u root -p ssh_terminal < database/schema.sql
```

### 2. 后端启动

```bash
# 配置数据库连接
vim src/main/resources/application.properties

# 启动Spring Boot应用
mvn spring-boot:run
```

### 3. 前端启动

```bash
cd web/ssh-treminal-ui
npm install
npm run dev
```

## 使用流程

### 管理员操作流程

1. **登录管理后台**
   - 访问 `http://localhost:5174`
   - 使用账号: `admin` / `admin123`

2. **配置脚本分组**
   - 进入"脚本分组管理"
   - 创建分组（如：环境初始化、酒馆搭建）
   - 配置初始化脚本（展示环境信息）

3. **配置脚本**
   - 进入"脚本配置"
   - 为每个分组添加执行脚本
   - 配置JSON执行流程

### 用户操作流程

1. **登录用户端**
   - 使用账号: `user` / `user123`
   - 或直接访问 `http://localhost:5174/user`

2. **选择脚本分组**
   - 在首页点击对应的脚本分组卡片
   - 或点击"SSH终端连接"直接使用SSH

3. **执行脚本**
   - 左侧选择要执行的脚本
   - 点击"执行"按钮
   - 右侧实时查看执行日志

## 脚本配置格式

```json
{
  "steps": [
    {
      "type": "system_check",
      "name": "系统检测",
      "description": "检测操作系统信息"
    },
    {
      "type": "command",
      "name": "更新软件包",
      "command": "apt update && apt upgrade -y"
    },
    {
      "type": "file_operation",
      "name": "创建目录",
      "operation": "mkdir",
      "filePath": "/opt/myapp"
    },
    {
      "type": "condition",
      "name": "检查端口",
      "condition": "port_available:8080"
    }
  ]
}
```

### 支持的步骤类型

- `system_check`: 系统检测（OS、Java版本等）
- `command`: Shell命令执行
- `file_operation`: 文件操作（创建、复制、删除）
- `condition`: 条件判断

## API接口

### 管理端API
- `GET /api/admin/script-groups` - 获取脚本分组列表
- `POST /api/admin/script-groups` - 创建脚本分组
- `GET /api/admin/scripts` - 获取脚本列表
- `POST /api/admin/scripts` - 创建脚本

### 用户端API
- `GET /api/user/script-groups` - 获取激活的脚本分组
- `GET /api/user/scripts/group/{groupId}` - 获取分组下的脚本
- `POST /api/user/script-execution/execute/{scriptId}` - 执行脚本

### WebSocket端点
- `/ws/stomp` - STOMP WebSocket连接
- `/topic/execution/{executionId}` - 脚本执行日志推送
- `/topic/execution/{executionId}/status` - 执行状态推送

## 默认账户

- **管理员**: `admin` / `admin123`
- **普通用户**: `user` / `user123`

## 开发调试

### 前端开发
```bash
cd web/ssh-treminal-ui
npm run dev  # 启动开发服务器
npm run build  # 构建生产版本
```

### 后端开发
```bash
mvn spring-boot:run  # 启动应用
mvn test  # 运行测试
mvn package  # 打包应用
```

## 注意事项

1. **数据库连接** - 确保MySQL服务正常运行，数据库配置正确
2. **端口占用** - 后端默认8080端口，前端默认5173端口
3. **WebSocket连接** - 确保浏览器支持WebSocket
4. **权限验证** - 不同角色只能访问对应的功能模块

## 故障排查

### 常见问题

1. **前端启动报错**
   - 检查Node.js版本（建议16+）
   - 删除node_modules重新安装依赖

2. **后端启动失败**
   - 检查Java版本（需要17+）
   - 验证数据库连接配置

3. **WebSocket连接失败**
   - 检查后端WebSocket配置
   - 确认跨域设置正确

4. **脚本执行无日志**
   - 检查WebSocket连接状态
   - 验证脚本配置格式

## 技术支持

如有问题，请检查：
1. 控制台错误信息
2. 后端日志输出
3. 数据库连接状态
4. 网络连接情况