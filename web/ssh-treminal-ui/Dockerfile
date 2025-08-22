# Multi-stage Docker build for SSH Terminal Frontend
# Stage 1: Build environment
FROM node:20-alpine AS build

WORKDIR /app

# Copy package files for dependency caching
COPY package*.json ./

# Install dependencies (including dev dependencies for build)
RUN npm ci --silent

# Copy source code
COPY . .

# Build the application for production
RUN npm run build

# Stage 2: Production runtime with Nginx
FROM nginx:1.25-alpine

# Install curl for health checks
RUN apk add --no-cache curl

# Remove default nginx website
RUN rm -rf /usr/share/nginx/html/*

# Copy built application from build stage
COPY --from=build /app/dist /usr/share/nginx/html

# Create a simple nginx configuration inline
RUN cat > /etc/nginx/nginx.conf <<'EOF'
user nginx;
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /tmp/nginx.pid;

events {
    worker_connections 1024;
    use epoll;
    multi_accept on;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;
    
    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent" "$http_x_forwarded_for"';
    
    access_log /var/log/nginx/access.log main;
    
    sendfile on;
    tcp_nopush on;
    tcp_nodelay on;
    keepalive_timeout 65;
    types_hash_max_size 2048;
    
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_comp_level 6;
    gzip_types
        text/plain
        text/css
        text/xml
        text/javascript
        application/javascript
        application/xml+rss
        application/json
        image/svg+xml;
    
    add_header X-Frame-Options DENY always;
    add_header X-Content-Type-Options nosniff always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    
    server {
        listen 80;
        server_name localhost;
        root /usr/share/nginx/html;
        index index.html;
        
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
            expires 1y;
            add_header Cache-Control "public, immutable";
            add_header Vary Accept-Encoding;
        }
        
        location / {
            try_files $uri $uri/ /index.html;
            add_header Cache-Control "no-cache, no-store, must-revalidate";
            add_header Pragma "no-cache";
            add_header Expires "0";
        }
        
        location /health {
            access_log off;
            return 200 "healthy\n";
            add_header Content-Type text/plain;
        }
        
        location /api/ {
            proxy_pass http://backend:8100/api/;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            
            proxy_request_buffering off;
            proxy_buffering off;
            proxy_read_timeout 3600s;
            proxy_connect_timeout 60s;
            proxy_send_timeout 3600s;
            client_max_body_size 2G;
        }
        
        location /ws/ {
            proxy_pass http://backend:8100/ws/;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_read_timeout 86400;
        }
        
        error_page 500 502 503 504 /50x.html;
        location = /50x.html {
            root /usr/share/nginx/html;
        }
    }
}
EOF

# Create nginx run directories and set permissions
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chown -R nginx:nginx /var/cache/nginx && \
    chown -R nginx:nginx /var/log/nginx && \
    chown -R nginx:nginx /etc/nginx/conf.d && \
    chown nginx:nginx /tmp

# Switch to non-root user
USER nginx

# Expose port 80
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD curl -f http://localhost/health || exit 1

# Start nginx
CMD ["nginx", "-g", "daemon off;"]