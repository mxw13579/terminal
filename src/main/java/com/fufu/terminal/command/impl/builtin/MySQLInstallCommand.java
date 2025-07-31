package com.fufu.terminal.command.impl.builtin;

import com.fufu.terminal.command.AtomicScriptCommand;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.model.enums.SystemType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MySQL 安装命令
 * 内置原子脚本，需要变量传递（版本、端口等）
 */
@Slf4j
@Component("mysql-install")
public class MySQLInstallCommand implements AtomicScriptCommand {

    @Override
    public CommandResult execute(CommandContext context) {
        log.info("开始执行 MySQL 安装命令");
        
        try {
            // 获取系统类型
            SystemType systemType = context.getSystemType();
            
            // 获取配置变量
            String version = context.getVariable("mysql_version", "8.0");
            String port = context.getVariable("mysql_port", "3306");
            String rootPassword = context.getVariable("mysql_root_password", "root123456");
            
            // 根据系统类型执行不同的安装脚本
            String installScript = generateInstallScript(systemType, version, port, rootPassword);
            
            // 执行脚本
            CommandResult result = context.executeScript(installScript);
            
            if (result.isSuccess()) {
                log.info("MySQL 安装成功");
                return CommandResult.success("MySQL " + version + " 安装成功，端口: " + port);
            } else {
                log.error("MySQL 安装失败: {}", result.getErrorMessage());
                return CommandResult.failure("MySQL 安装失败: " + result.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("执行 MySQL 安装命令异常", e);
            return CommandResult.failure("执行异常: " + e.getMessage());
        }
    }

    private String generateInstallScript(SystemType systemType, String version, String port, String rootPassword) {
        switch (systemType) {
            case UBUNTU:
            case DEBIAN:
                return generateDebianInstallScript(version, port, rootPassword);
            case CENTOS:
            case REDHAT:
                return generateRHELInstallScript(version, port, rootPassword);
            default:
                throw new UnsupportedOperationException("不支持的系统类型: " + systemType);
        }
    }

    private String generateDebianInstallScript(String version, String port, String rootPassword) {
        return String.format("""
                #!/bin/bash
                set -e
                
                echo "开始安装 MySQL %s..."
                
                # 更新软件包列表
                apt-get update
                
                # 设置 MySQL root 密码
                echo "mysql-server mysql-server/root_password password %s" | debconf-set-selections
                echo "mysql-server mysql-server/root_password_again password %s" | debconf-set-selections
                
                # 安装 MySQL
                DEBIAN_FRONTEND=noninteractive apt-get install -y mysql-server
                
                # 启动 MySQL 服务
                systemctl start mysql
                systemctl enable mysql
                
                # 配置端口
                if [ "%s" != "3306" ]; then
                    sed -i 's/port.*=.*/port = %s/' /etc/mysql/mysql.conf.d/mysqld.cnf
                    systemctl restart mysql
                fi
                
                # 检查服务状态
                systemctl status mysql --no-pager
                
                echo "MySQL %s 安装完成，端口: %s"
                """, version, rootPassword, rootPassword, port, port, version, port);
    }

    private String generateRHELInstallScript(String version, String port, String rootPassword) {
        return String.format("""
                #!/bin/bash
                set -e
                
                echo "开始安装 MySQL %s..."
                
                # 安装 MySQL 仓库
                yum install -y https://dev.mysql.com/get/mysql80-community-release-el7-3.noarch.rpm
                
                # 安装 MySQL
                yum install -y mysql-community-server
                
                # 启动 MySQL 服务
                systemctl start mysqld
                systemctl enable mysqld
                
                # 获取临时密码
                TEMP_PASSWORD=$(grep 'temporary password' /var/log/mysqld.log | awk '{print $NF}')
                
                # 设置新密码
                mysql -u root -p"$TEMP_PASSWORD" --connect-expired-password -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '%s';"
                
                # 配置端口
                if [ "%s" != "3306" ]; then
                    echo "port = %s" >> /etc/my.cnf
                    systemctl restart mysqld
                fi
                
                # 检查服务状态
                systemctl status mysqld --no-pager
                
                echo "MySQL %s 安装完成，端口: %s"
                """, version, rootPassword, port, port, version, port);
    }

    @Override
    public String getName() {
        return "MySQL 安装";
    }

    @Override
    public String getDescription() {
        return "安装 MySQL 数据库服务器，支持自定义版本和端口";
    }
}
