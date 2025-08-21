# SSH Terminal Application - 无数据库Docker部署

## 🎯 重要说明

**此应用完全不需要数据库！**

经过代码分析发现：
- 应用使用 `ConcurrentHashMap` 在内存中管理所有状态
- 没有任何JPA/Hibernate依赖
- SSH连接、会话管理、文件上传进度都存储在内存中
- 应用架构设计为完全无状态的内存型服务

## 🚀 快速部署

### 基本部署
```bash
# 启动前端和后端（无数据库）
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 生产部署（带监控）
```bash
# 包含Prometheus监控和Nginx代理
docker-compose --profile monitoring --profile proxy up -d
```

## 📊 服务访问

| 服务 | URL | 说明 |
|------|-----|------|
| 🌐 前端应用 | http://localhost | SSH Terminal Web界面 |
| 🔧 后端API | http://localhost:8100 | Spring Boot REST API |
| 📈 Prometheus | http://localhost:9090 | 监控指标 |
| 📊 Grafana | http://localhost:3000 | 监控面板 |
| 🔀 Nginx代理 | http://localhost:8080 | 负载均衡器 |

## 💾 数据存储说明

### 内存存储 (运行时)
- SSH连接会话
- 用户认证token  
- 文件上传进度
- WebSocket连接状态

### 持久化存储 (Docker Volumes)
- `terminal_uploads/`: 上传的文件
- `logs/`: 应用日志
- `temp/`: 临时文件

### 🔄 重启行为
- 重启后所有内存状态清空（SSH连接、会话等）
- 已上传的文件和日志保持不变
- 无需数据库迁移或备份

## ⚡ 性能优化

应用使用轻量级内存架构：
- 启动时间快（无数据库连接等待）
- 内存占用小（256MB-512MB足够）  
- 横向扩展简单（无状态设计）

## 🛠️ 环境变量

```env
# 应用配置
SPRING_PROFILES_ACTIVE=docker
SERVER_PORT=8100

# 安全配置  
ALLOWED_ORIGINS=http://localhost,https://yourdomain.com

# 文件传输限制
MAX_FILE_SIZE=2147483648
MAX_TOTAL_SIZE=2147483648
THROTTLE_BYTES_PER_SECOND=10485760

# JVM内存设置（轻量级）
JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC
```

## 🔧 开发模式

```bash
# 方式1：完全容器化
docker-compose up -d

# 方式2：混合开发（推荐）
# 后端本地运行
mvn spring-boot:run

# 前端本地开发
cd web/ssh-treminal-ui
npm run dev
```

## 📝 架构特点

### ✅ 优点
- **部署简单**：无需数据库配置
- **启动快速**：无数据库连接延迟
- **资源占用少**：内存型架构
- **水平扩展**：完全无状态
- **维护简单**：无数据一致性问题

### ⚠️ 注意事项
- **重启丢失状态**：SSH连接需要重新建立
- **不适合集群**：多实例间状态不共享
- **内存限制**：大量并发连接需要足够内存

## 🔍 故障排查

```bash
# 检查容器状态
docker-compose ps

# 查看后端日志
docker-compose logs backend

# 查看前端日志  
docker-compose logs frontend

# 检查健康状态
curl http://localhost:8100/actuator/health

# 重启服务
docker-compose restart backend
```

## 📦 部署文件说明

- `docker-compose.yml`: 主要部署配置（无数据库）
- `Dockerfile`: 后端Spring Boot应用构建
- `web/ssh-treminal-ui/Dockerfile`: 前端Vue应用构建
- `web/ssh-treminal-ui/nginx.conf`: Nginx配置（包含API代理）

---
**总结**：这是一个现代化的内存型SSH终端应用，设计简洁，部署便捷，无需任何数据库依赖！