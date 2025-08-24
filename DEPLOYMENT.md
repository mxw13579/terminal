# 生产环境部署配置说明

## 🚀 部署方案

### 方案一：标准反向代理部署（推荐）

**前端和后端通过同一域名访问，无需修改任何代码**

#### Nginx 配置示例：
```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    # 前端静态文件
    location / {
        root /path/to/built/frontend;
        try_files $uri $uri/ /index.html;
    }
    
    # 后端API代理
    location /api/ {
        proxy_pass http://127.0.0.1:8100/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # 支持大文件上传
        client_max_body_size 2G;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
    
    # WebSocket代理
    location /ws/ {
        proxy_pass http://127.0.0.1:8100/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

#### Docker Compose 示例：
```yaml
version: '3.8'
services:
  backend:
    build: .
    ports:
      - "8100:8100"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/conf.d/default.conf
      - ./frontend/dist:/usr/share/nginx/html
    depends_on:
      - backend
```

### 方案二：分离部署（需配置CORS）

如果前后端部署在不同域名/端口，需要配置CORS：

#### 后端 application.yml：
```yaml
spring:
  profiles:
    active: prod
    
# 生产环境CORS配置
cors:
  allowed-origins: 
    - https://your-frontend-domain.com
  allowed-methods: GET,POST,PUT,DELETE,OPTIONS
  allowed-headers: "*"
  allow-credentials: true
```

## 🔧 自动环境适配

### 当前代码的智能适配：

1. **开发环境** (`npm run dev`)：
   - 自动连接到 `localhost:8100`
   - 使用Vite代理处理API请求

2. **生产环境** (`npm run build`)：
   - 自动使用相对路径
   - 由反向代理处理路由
   - **无需修改任何代码或配置**

### 环境检测机制：
```javascript
// 自动检测环境
if (import.meta.env.PROD) {
  // 生产环境：使用相对路径，由代理处理
  apiUrl = '/api/...'
} else {
  // 开发环境：直接连接后端端口
  apiUrl = 'http://localhost:8100/api/...'
}
```

## 📁 构建与部署流程

### 1. 前端构建
```bash
cd web/ssh-treminal-ui
npm install
npm run build
# 构建产物在 dist/ 目录
```

### 2. 后端构建
```bash
# Maven构建
mvn clean package -P prod

# 或使用Docker
docker build -t terminal-app .
```

### 3. 部署验证
- ✅ 前端访问正常
- ✅ API请求正常
- ✅ WebSocket连接正常
- ✅ 文件上传/下载正常
- ✅ SillyTavern数据导出正常

## 🌍 多环境支持

### 环境变量配置：
```bash
# .env.production
VITE_API_BASE_URL=/api
VITE_WS_BASE_URL=/ws

# .env.development  
VITE_API_BASE_URL=http://localhost:8100/api
VITE_WS_BASE_URL=ws://localhost:8100/ws
```

## ✅ 部署检查清单

- [ ] 前端构建成功 (`npm run build`)
- [ ] 后端构建成功 (`mvn package`)
- [ ] Nginx配置正确
- [ ] CORS配置正确（如果需要）
- [ ] SSL证书配置（HTTPS）
- [ ] 防火墙端口开放
- [ ] 文件上传大小限制配置
- [ ] 日志轮转配置

## 🚨 重要提醒

**修复后的代码具有以下特性：**

1. **✅ 零配置部署**：生产环境无需修改代码
2. **✅ 自动环境适配**：开发/生产环境自动检测
3. **✅ 反向代理友好**：完全支持Nginx等反向代理
4. **✅ Docker友好**：支持容器化部署
5. **✅ CDN友好**：静态资源可部署到CDN

**答案：完全自动，无需手动改地址！** 🎉