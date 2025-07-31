package com.fufu.terminal.command.impl.builtin;

import com.fufu.terminal.command.AtomicScriptCommand;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Redis 安装命令
 * 内置原子脚本，需要变量传递（端口、密码等）
 */
@Slf4j
@Component("redis-install")
public class RedisInstallCommand implements AtomicScriptCommand {

    @Override
    public CommandResult execute(CommandContext context) {
        log.info("开始执行 Redis 安装命令");
        
        try {
            // 获取配置变量
            String port = context.getVariable("redis_port", "6379");
            String password = context.getVariable("redis_password", "");
            String maxMemory = context.getVariable("redis_max_memory", "256mb");
            
            // 生成安装脚本
            String installScript = generateInstallScript(port, password, maxMemory);
            
            // 执行脚本
            CommandResult result = context.executeScript(installScript);
            
            if (result.isSuccess()) {
                log.info("Redis 安装成功");
                return CommandResult.success("Redis 安装成功，端口: " + port + 
                    (password.isEmpty() ? "" : "，已设置密码"));
            } else {
                log.error("Redis 安装失败: {}", result.getErrorMessage());
                return CommandResult.failure("Redis 安装失败: " + result.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("执行 Redis 安装命令异常", e);
            return CommandResult.failure("执行异常: " + e.getMessage());
        }
    }

    private String generateInstallScript(String port, String password, String maxMemory) {
        String passwordConfig = password.isEmpty() ? "" : String.format("requirepass %s", password);
        
        return String.format("""
                #!/bin/bash
                set -e
                
                echo "开始安装 Redis..."
                
                # 检查系统类型并安装 Redis
                if command -v apt-get >/dev/null 2>&1; then
                    apt-get update
                    apt-get install -y redis-server
                elif command -v yum >/dev/null 2>&1; then
                    yum install -y epel-release
                    yum install -y redis
                else
                    echo "不支持的系统类型"
                    exit 1
                fi
                
                # 备份原配置文件
                cp /etc/redis/redis.conf /etc/redis/redis.conf.backup 2>/dev/null || cp /etc/redis.conf /etc/redis.conf.backup 2>/dev/null || true
                
                # 创建自定义配置
                cat > /etc/redis/redis.conf << 'EOF' || cat > /etc/redis.conf << 'EOF'
                bind 127.0.0.1 ::1
                port %s
                timeout 300
                tcp-keepalive 300
                loglevel notice
                logfile /var/log/redis/redis-server.log
                save 900 1
                save 300 10
                save 60 10000
                maxmemory %s
                maxmemory-policy allkeys-lru
                %s
                appendonly yes
                appendfilename "appendonly.aof"
                appendfsync everysec
                databases 16
                tcp-backlog 511
                EOF
                
                # 创建日志目录
                mkdir -p /var/log/redis
                chown redis:redis /var/log/redis 2>/dev/null || true
                
                # 启动 Redis 服务
                systemctl start redis-server 2>/dev/null || systemctl start redis
                systemctl enable redis-server 2>/dev/null || systemctl enable redis
                
                # 等待服务启动
                sleep 2
                
                # 测试连接
                if [ "%s" = "" ]; then
                    redis-cli -p %s ping
                else
                    redis-cli -p %s -a "%s" ping
                fi
                
                echo "Redis 安装完成！"
                echo "端口: %s"
                echo "最大内存: %s"
                if [ "%s" != "" ]; then
                    echo "已设置密码保护"
                fi
                """, port, maxMemory, passwordConfig, password, port, port, password, port, maxMemory, password);
    }

    @Override
    public String getName() {
        return "Redis 安装";
    }

    @Override
    public String getDescription() {
        return "安装 Redis 缓存服务器，支持自定义端口、密码和内存限制";
    }
}