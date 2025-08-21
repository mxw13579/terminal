# SSH Terminal Web Application - 生产部署指南

## 项目概述

这是一个基于Spring Boot + Vue 3的高级SSH终端Web应用，具有实时终端操作、文件传输、系统监控和SillyTavern Docker管理等企业级功能。

### 技术栈
- **后端**: Spring Boot 3.0.2 + Java 17 + WebSocket/STOMP + JSch
- **前端**: Vue 3 + TypeScript + Vite + Pinia + xterm.js
- **通信**: WebSocket (STOMP) + HTTP Streaming
- **监控**: Actuator + Prometheus + Micrometer

## 系统要求

### 服务器最低配置
- **CPU**: 2核心以上
- **内存**: 4GB RAM以上（推荐8GB）
- **存储**: 20GB可用空间
- **操作系统**: Ubuntu 18.04+ / CentOS 7+ / RHEL 7+
- **Java**: OpenJDK 17或更高版本
- **Node.js**: 18.0+（构建时需要）
- **Nginx**: 1.18+（推荐）

### 网络要求
- **端口**: 8100（后端API），80/443（Nginx），5174（开发环境前端）
- **防火墙**: 允许WebSocket连接（ws/wss协议）
- **带宽**: 支持大文件传输（最大2GB单文件）

## 后端部署

### 1. 环境准备

#### 安装Java 17
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-17-jdk

# CentOS/RHEL
sudo yum install java-17-openjdk java-17-openjdk-devel

# 验证安装
java -version
javac -version
```

#### 安装Maven（可选，如果使用源码构建）
```bash
# Ubuntu/Debian
sudo apt install maven

# CentOS/RHEL
sudo yum install maven

# 验证安装
mvn -version
```

### 2. 应用构建与打包

#### 从源码构建
```bash
# 克隆或上传源码
cd /opt/terminal-app

# 清理并打包
mvn clean package -DskipTests

# 构建后的JAR文件位于
ls target/terminal-*.jar
```

### 3. 配置文件设置

#### 生产环境配置（application-production.yml）
```yaml
# 创建 /opt/terminal-app/config/application-production.yml
spring:
  profiles:
    active: production
  datasource:
    url: jdbc:mysql://your-db-host:3306/ssh_terminal?useUnicode=true&characterEncoding=utf8&useSSL=true&serverTimezone=GMT%2B8
    username: ${DB_USERNAME:your_db_user}
    password: ${DB_PASSWORD:your_db_password}

server:
  port: 8100

terminal:
  security:
    strict-host-checking: true
    allowed-origins: ${ALLOWED_ORIGINS:https://your-domain.com,https://www.your-domain.com}
  rate-limit:
    enabled: true
    connections-per-minute: 5

logging:
  level:
    root: INFO
    com.fufu.terminal: INFO
  file:
    name: /var/log/terminal-app/application.log

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
```

### 4. 系统服务配置

#### 创建服务用户
```bash
sudo useradd -r -s /bin/false terminal-app
sudo mkdir -p /opt/terminal-app
sudo mkdir -p /var/log/terminal-app
sudo chown terminal-app:terminal-app /var/log/terminal-app
```

#### 创建Systemd服务文件
```bash
sudo nano /etc/systemd/system/terminal-app.service
```

```ini
[Unit]
Description=SSH Terminal Web Application
After=network.target mysql.service

[Service]
Type=simple
User=terminal-app
Group=terminal-app
WorkingDirectory=/opt/terminal-app
ExecStart=/usr/bin/java -jar \
    -Xms512m -Xmx2g \
    -Dspring.profiles.active=production \
    -Dspring.config.location=classpath:/application.yml,/opt/terminal-app/config/application-production.yml \
    /opt/terminal-app/terminal-0.0.1-SNAPSHOT.jar

# 环境变量
Environment=JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
Environment=DB_USERNAME=your_db_user
Environment=DB_PASSWORD=your_secure_password
Environment=ALLOWED_ORIGINS=https://your-domain.com

# 重启策略
Restart=always
RestartSec=10

# 安全设置
NoNewPrivileges=yes
PrivateTmp=yes
ProtectSystem=strict
ReadWritePaths=/opt/terminal-app /var/log/terminal-app /tmp

# 资源限制
LimitNOFILE=65536
LimitNPROC=4096

[Install]
WantedBy=multi-user.target
```

#### 启动服务
```bash
# 重新加载systemd配置
sudo systemctl daemon-reload

# 启用服务（开机自启）
sudo systemctl enable terminal-app

# 启动服务
sudo systemctl start terminal-app

# 查看服务状态
sudo systemctl status terminal-app

# 查看日志
sudo journalctl -u terminal-app -f
```

### 5. 日志轮转配置
```bash
sudo nano /etc/logrotate.d/terminal-app
```

```
/var/log/terminal-app/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 0644 terminal-app terminal-app
    postrotate
        systemctl reload terminal-app > /dev/null 2>&1 || true
    endscript
}
```

## 前端部署

### 1. 构建准备

#### 安装Node.js
```bash
# 使用NodeSource仓库（推荐）
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# 或使用NVM
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash
source ~/.bashrc
nvm install 18
nvm use 18

# 验证安装
node -v
npm -v
```

### 2. 构建前端应用

```bash
cd web/ssh-treminal-ui

# 安装依赖
npm install

# 生产环境构建
npm run build

# 构建完成后，dist目录包含所有静态文件
ls dist/
```

### 3. 静态文件部署

#### 方案1：复制到Web服务器目录
```bash
# 创建网站目录
sudo mkdir -p /var/www/terminal-app
sudo chown -R www-data:www-data /var/www/terminal-app

# 复制构建文件
sudo cp -r web/ssh-treminal-ui/dist/* /var/www/terminal-app/
sudo chown -R www-data:www-data /var/www/terminal-app
```

#### 方案2：使用Docker容器（可选）
```dockerfile
# Dockerfile.frontend
FROM nginx:alpine

COPY web/ssh-treminal-ui/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

```bash
# 构建和运行
docker build -f Dockerfile.frontend -t terminal-frontend .
docker run -d -p 80:80 --name terminal-frontend terminal-frontend
```

## Nginx配置

### 1. 安装Nginx

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install nginx

# CentOS/RHEL
sudo yum install nginx

# 启用服务
sudo systemctl enable nginx
sudo systemctl start nginx
```

### 2. 基础HTTP配置

创建配置文件 `/etc/nginx/sites-available/terminal-app`：

```nginx
# /etc/nginx/sites-available/terminal-app

# 上游后端服务
upstream backend {
    server localhost:8100 max_fails=3 fail_timeout=30s;
    # 如果有多个后端实例，可以添加负载均衡
    # server localhost:8101 max_fails=3 fail_timeout=30s;
}

# HTTP服务器配置
server {
    listen 80;
    server_name your-domain.com www.your-domain.com;

    # 访问日志
    access_log /var/log/nginx/terminal-app.access.log;
    error_log /var/log/nginx/terminal-app.error.log;

    # 客户端上传限制（与应用配置匹配）
    client_max_body_size 2048M;
    client_body_timeout 300s;
    client_header_timeout 60s;

    # 前端静态文件
    location / {
        root /var/www/terminal-app;
        index index.html;
        try_files $uri $uri/ /index.html;
        
        # 缓存控制
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
            expires 1y;
            add_header Cache-Control "public, immutable";
        }
        
        # HTML文件不缓存
        location ~* \.html$ {
            expires -1;
            add_header Cache-Control "no-cache, no-store, must-revalidate";
        }
    }

    # API代理到后端
    location /api/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 大文件上传支持
        proxy_request_buffering off;
        proxy_buffering off;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_connect_timeout 60s;

        # HTTP/1.1支持
        proxy_http_version 1.1;
        proxy_set_header Connection "";
    }

    # 监控端点代理
    location /actuator/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 限制访问（可选）
        # allow 192.168.1.0/24;
        # deny all;
    }

    # WebSocket代理
    location /ws/ {
        proxy_pass http://backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket特定配置
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_connect_timeout 60s;
        
        # 禁用缓冲
        proxy_buffering off;
        proxy_cache off;
    }

    # 安全头部
    add_header X-Frame-Options DENY;
    add_header X-Content-Type-Options nosniff;
    add_header X-XSS-Protection "1; mode=block";
    add_header Referrer-Policy strict-origin-when-cross-origin;

    # 隐藏Nginx版本
    server_tokens off;
}
```

### 3. HTTPS/SSL配置（生产推荐）

#### 使用Let's Encrypt（免费SSL证书）

```bash
# 安装Certbot
sudo apt install certbot python3-certbot-nginx

# 获取SSL证书
sudo certbot --nginx -d your-domain.com -d www.your-domain.com

# 自动续期
sudo crontab -e
# 添加：0 12 * * * /usr/bin/certbot renew --quiet
```

#### 手动SSL配置
```nginx
# HTTPS服务器配置
server {
    listen 443 ssl http2;
    server_name your-domain.com www.your-domain.com;

    # SSL证书配置
    ssl_certificate /path/to/your/certificate.crt;
    ssl_certificate_key /path/to/your/private.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES256-GCM-SHA512:DHE-RSA-AES256-GCM-SHA512:ECDHE-RSA-AES256-GCM-SHA384:DHE-RSA-AES256-GCM-SHA384;
    ssl_prefer_server_ciphers off;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;

    # HSTS（HTTP严格传输安全）
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    # ... 其他配置与HTTP版本相同 ...
}

# HTTP到HTTPS重定向
server {
    listen 80;
    server_name your-domain.com www.your-domain.com;
    return 301 https://$server_name$request_uri;
}
```

### 4. 启用配置

```bash
# 创建软链接启用站点
sudo ln -s /etc/nginx/sites-available/terminal-app /etc/nginx/sites-enabled/

# 删除默认配置（可选）
sudo rm /etc/nginx/sites-enabled/default

# 测试配置
sudo nginx -t

# 重新加载配置
sudo systemctl reload nginx
```

### 5. 高级Nginx配置

#### 负载均衡配置
```nginx
upstream backend {
    least_conn;  # 负载均衡算法
    server localhost:8100 weight=3 max_fails=3 fail_timeout=30s;
    server localhost:8101 weight=2 max_fails=3 fail_timeout=30s;
    server localhost:8102 backup;  # 备份服务器
}
```

#### 缓存配置
```nginx
# 在http块中添加
proxy_cache_path /var/cache/nginx/terminal levels=1:2 keys_zone=terminal_cache:10m max_size=1g inactive=60m use_temp_path=off;

# 在location块中添加
location /api/static/ {
    proxy_cache terminal_cache;
    proxy_cache_valid 200 1h;
    proxy_cache_use_stale error timeout invalid_header updating http_500 http_502 http_503 http_504;
    proxy_pass http://backend;
}
```

#### 限流配置
```nginx
# 在http块中添加
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;
limit_req_zone $binary_remote_addr zone=ws_limit:10m rate=5r/s;

# 在location块中添加
location /api/ {
    limit_req zone=api_limit burst=20 nodelay;
    # ... 其他配置
}

location /ws/ {
    limit_req zone=ws_limit burst=10 nodelay;
    # ... 其他配置
}
```

## 数据库设置

### MySQL配置示例

```sql
-- 创建数据库
CREATE DATABASE ssh_terminal CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户并授权
CREATE USER 'terminal_user'@'localhost' IDENTIFIED BY 'secure_password_here';
GRANT ALL PRIVILEGES ON ssh_terminal.* TO 'terminal_user'@'localhost';
FLUSH PRIVILEGES;

-- 远程连接（如果需要）
CREATE USER 'terminal_user'@'%' IDENTIFIED BY 'secure_password_here';
GRANT ALL PRIVILEGES ON ssh_terminal.* TO 'terminal_user'@'%';
FLUSH PRIVILEGES;
```

### 数据库优化配置

```ini
# /etc/mysql/mysql.conf.d/mysqld.cnf 优化配置
[mysqld]
# 连接设置
max_connections = 200
max_connect_errors = 1000

# 缓冲池设置
innodb_buffer_pool_size = 1G
innodb_log_file_size = 256M

# 字符集设置
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci

# 性能优化
query_cache_size = 128M
tmp_table_size = 128M
max_heap_table_size = 128M
```

## 环境变量配置

### 系统环境变量

```bash
# /etc/environment 或 ~/.bashrc
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export SPRING_PROFILES_ACTIVE=production
export DB_USERNAME=terminal_user
export DB_PASSWORD=secure_password_here
export ALLOWED_ORIGINS=https://your-domain.com,https://www.your-domain.com
```

### 应用专用环境文件

```bash
# /opt/terminal-app/.env
DB_HOST=localhost
DB_PORT=3306
DB_NAME=ssh_terminal
DB_USERNAME=terminal_user
DB_PASSWORD=secure_password_here
JWT_SECRET=your-jwt-secret-key-here
ALLOWED_ORIGINS=https://your-domain.com
LOG_LEVEL=INFO
```

## 安全配置

### 1. 防火墙设置

#### UFW（Ubuntu）
```bash
# 启用UFW
sudo ufw enable

# 允许必要端口
sudo ufw allow ssh
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8100/tcp  # 仅限内网访问时

# 限制内网访问（可选）
sudo ufw allow from 192.168.1.0/24 to any port 8100

# 查看状态
sudo ufw status
```

#### Firewalld（CentOS/RHEL）
```bash
# 启用防火墙
sudo systemctl enable firewalld
sudo systemctl start firewalld

# 允许服务
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --permanent --add-port=8100/tcp

# 重新加载
sudo firewall-cmd --reload
```

### 2. SSL/TLS最佳实践

#### SSL配置测试
```bash
# 使用SSL Labs测试（在线）
# https://www.ssllabs.com/ssltest/

# 本地测试
openssl s_client -connect your-domain.com:443 -servername your-domain.com
```

#### 证书自动续期监控
```bash
# 创建续期检查脚本
sudo nano /usr/local/bin/check-ssl-renewal.sh
```

```bash
#!/bin/bash
DOMAIN="your-domain.com"
EXPIRY_DATE=$(openssl s_client -connect $DOMAIN:443 -servername $DOMAIN 2>/dev/null | openssl x509 -noout -enddate | cut -d= -f2)
EXPIRY_TIMESTAMP=$(date -d "$EXPIRY_DATE" +%s)
CURRENT_TIMESTAMP=$(date +%s)
DAYS_LEFT=$(( (EXPIRY_TIMESTAMP - CURRENT_TIMESTAMP) / 86400 ))

if [ $DAYS_LEFT -lt 30 ]; then
    echo "SSL certificate for $DOMAIN expires in $DAYS_LEFT days. Please renew!"
    # 可以发送邮件或其他通知
fi
```

### 3. 应用安全加固

#### JVM安全参数
```bash
# 在systemd服务文件中添加
-Djava.security.egd=file:/dev/./urandom
-Dfile.encoding=UTF-8
-Duser.timezone=Asia/Shanghai
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/terminal-app/heapdump.hprof
```

#### 文件权限设置
```bash
# 设置正确的文件权限
sudo chmod 600 /opt/terminal-app/config/application-production.yml
sudo chmod 755 /opt/terminal-app/terminal-*.jar
sudo chown -R terminal-app:terminal-app /opt/terminal-app
```

## 监控与日志

### 1. 应用监控

#### Prometheus配置
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'terminal-app'
    static_configs:
      - targets: ['localhost:8100']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
```

#### Grafana Dashboard
```json
{
  "dashboard": {
    "title": "SSH Terminal Application",
    "panels": [
      {
        "title": "JVM Memory Usage",
        "type": "graph",
        "targets": [
          {
            "expr": "jvm_memory_used_bytes{job=\"terminal-app\"}"
          }
        ]
      },
      {
        "title": "WebSocket Connections",
        "type": "stat",
        "targets": [
          {
            "expr": "terminal_websocket_connections_total"
          }
        ]
      }
    ]
  }
}
```

### 2. 日志管理

#### ELK Stack配置（可选）

```yaml
# filebeat.yml
filebeat.inputs:
- type: log
  enabled: true
  paths:
    - /var/log/terminal-app/*.log
  fields:
    service: terminal-app
  fields_under_root: true
  json.keys_under_root: true

output.elasticsearch:
  hosts: ["localhost:9200"]
  index: "terminal-app-%{+yyyy.MM.dd}"
```

#### 简单日志监控脚本
```bash
#!/bin/bash
# /usr/local/bin/monitor-terminal-app.sh

LOG_FILE="/var/log/terminal-app/application.log"
ERROR_PATTERN="ERROR|FATAL|Exception"
ALERT_EMAIL="admin@your-domain.com"

tail -n 100 $LOG_FILE | grep -E "$ERROR_PATTERN" | while read line; do
    echo "Terminal App Error: $line" | mail -s "Terminal App Alert" $ALERT_EMAIL
done
```

## 故障排除

### 常见问题及解决方案

#### 1. 应用启动失败

**检查Java版本**
```bash
java -version
# 确保是Java 17+
```

**检查端口占用**
```bash
sudo netstat -tlnp | grep :8100
sudo lsof -i :8100
```

**查看详细日志**
```bash
sudo journalctl -u terminal-app -n 100
tail -f /var/log/terminal-app/application.log
```

#### 2. WebSocket连接失败

**检查Nginx配置**
```bash
sudo nginx -t
sudo systemctl reload nginx
```

**检查防火墙规则**
```bash
sudo ufw status
sudo firewall-cmd --list-all
```

#### 3. 大文件上传失败

**检查Nginx配置**
```nginx
client_max_body_size 2048M;
proxy_request_buffering off;
```

**检查应用配置**
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 2147483648
      max-request-size: 2147483648
```

#### 4. 数据库连接问题

**测试数据库连接**
```bash
mysql -h localhost -u terminal_user -p ssh_terminal
```

**检查数据库服务状态**
```bash
sudo systemctl status mysql
sudo systemctl status mariadb
```

### 性能调优建议

#### 1. JVM调优
```bash
# 堆内存设置（根据服务器配置调整）
-Xms1g -Xmx4g

# GC调优
-XX:+UseG1GC
-XX:G1HeapRegionSize=16m
-XX:+UseStringDeduplication

# 监控参数
-XX:+PrintGCDetails
-XX:+PrintGCTimeStamps
-Xloggc:/var/log/terminal-app/gc.log
```

#### 2. Nginx调优
```nginx
# 工作进程数
worker_processes auto;

# 连接数限制
worker_connections 1024;

# 文件句柄限制
worker_rlimit_nofile 2048;

# 缓冲区大小
client_body_buffer_size 128k;
client_header_buffer_size 1k;
large_client_header_buffers 4 4k;
```

#### 3. 系统级调优
```bash
# /etc/security/limits.conf
terminal-app soft nofile 65536
terminal-app hard nofile 65536
terminal-app soft nproc 32768
terminal-app hard nproc 32768

# /etc/sysctl.conf
net.core.somaxconn = 65535
net.ipv4.tcp_max_syn_backlog = 65535
vm.swappiness = 1
```

## 部署自动化脚本

### 完整部署脚本

```bash
#!/bin/bash
# deploy.sh - 自动化部署脚本

set -e

# 配置变量
APP_NAME="terminal-app"
APP_USER="terminal-app"
APP_DIR="/opt/$APP_NAME"
JAR_FILE="terminal-0.0.1-SNAPSHOT.jar"
SERVICE_NAME="terminal-app"

echo "🚀 开始部署 SSH Terminal Web Application..."

# 1. 停止现有服务
echo "📛 停止现有服务..."
sudo systemctl stop $SERVICE_NAME || true

# 2. 备份现有版本
if [ -f "$APP_DIR/$JAR_FILE" ]; then
    echo "💾 备份现有版本..."
    sudo cp "$APP_DIR/$JAR_FILE" "$APP_DIR/$JAR_FILE.backup.$(date +%Y%m%d%H%M%S)"
fi

# 3. 构建应用
echo "🔨 构建应用..."
mvn clean package -DskipTests

# 4. 部署新版本
echo "📦 部署新版本..."
sudo cp "target/$JAR_FILE" "$APP_DIR/"
sudo chown $APP_USER:$APP_USER "$APP_DIR/$JAR_FILE"

# 5. 构建前端
echo "🎨 构建前端..."
cd web/ssh-treminal-ui
npm install
npm run build
sudo cp -r dist/* /var/www/terminal-app/
sudo chown -R www-data:www-data /var/www/terminal-app/
cd ../..

# 6. 启动服务
echo "🔄 启动服务..."
sudo systemctl start $SERVICE_NAME

# 7. 验证部署
echo "✅ 验证部署..."
sleep 10
if sudo systemctl is-active --quiet $SERVICE_NAME; then
    echo "✅ 服务启动成功！"
    echo "🌐 应用地址: http://your-domain.com"
    echo "📊 监控地址: http://your-domain.com/actuator/health"
else
    echo "❌ 服务启动失败，请检查日志："
    sudo journalctl -u $SERVICE_NAME -n 20
    exit 1
fi

echo "🎉 部署完成！"
```

### 健康检查脚本

```bash
#!/bin/bash
# health-check.sh

APP_URL="http://localhost:8100"
HEALTH_ENDPOINT="$APP_URL/actuator/health"

echo "🔍 执行健康检查..."

# 检查应用健康状态
HEALTH_STATUS=$(curl -s "$HEALTH_ENDPOINT" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")

if [ "$HEALTH_STATUS" = "UP" ]; then
    echo "✅ 应用健康状态: UP"
else
    echo "❌ 应用健康状态: $HEALTH_STATUS"
    exit 1
fi

# 检查WebSocket连接
WS_TEST=$(curl -s -I "$APP_URL/ws/info" | head -n 1 | grep "200 OK" || echo "FAIL")
if [ "$WS_TEST" != "FAIL" ]; then
    echo "✅ WebSocket端点正常"
else
    echo "❌ WebSocket端点异常"
    exit 1
fi

echo "🎉 所有健康检查通过！"
```

## 总结

本部署指南涵盖了SSH Terminal Web Application的完整生产部署流程，包括：

1. **后端部署**: Spring Boot应用的构建、配置和服务化
2. **前端部署**: Vue.js应用的构建和静态文件部署
3. **Nginx配置**: 反向代理、负载均衡、SSL/TLS配置
4. **安全配置**: 防火墙、SSL证书、应用安全加固
5. **监控日志**: 应用监控、日志管理、性能调优
6. **故障排除**: 常见问题诊断和解决方案
7. **自动化**: 部署脚本和健康检查

遵循本指南可以确保应用在生产环境中稳定、安全、高性能地运行。建议在实际部署前在测试环境中验证所有配置。