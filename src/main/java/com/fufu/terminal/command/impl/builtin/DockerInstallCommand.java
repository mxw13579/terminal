package com.fufu.terminal.command.impl.builtin;

import com.fufu.terminal.command.AtomicScriptCommand;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.model.enums.SystemType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Docker 安装命令
 * 内置原子脚本，不需要变量传递
 */
@Slf4j
@Component("docker-install")
public class DockerInstallCommand implements AtomicScriptCommand {

    @Override
    public CommandResult execute(CommandContext context) {
        log.info("开始执行 Docker 安装命令");

        try {
            SystemType systemType = context.getSystemType();

            // 根据系统类型生成安装脚本
            String installScript = generateInstallScript(systemType);

            // 执行脚本
            CommandResult result = context.executeScript(installScript);

            if (result.isSuccess()) {
                log.info("Docker 安装成功");
                return CommandResult.success("Docker 安装成功");
            } else {
                log.error("Docker 安装失败: {}", result.getErrorMessage());
                return CommandResult.failure("Docker 安装失败: " + result.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("执行 Docker 安装命令异常", e);
            return CommandResult.failure("执行异常: " + e.getMessage());
        }
    }

    private String generateInstallScript(SystemType systemType) {
        switch (systemType) {
            case UBUNTU:
            case DEBIAN:
                return generateDebianInstallScript();
            case CENTOS:
            case REDHAT:
                return generateRHELInstallScript();
            default:
                throw new UnsupportedOperationException("不支持的系统类型: " + systemType);
        }
    }

    private String generateDebianInstallScript() {
        return """
                #!/bin/bash
                set -e
                
                echo "开始安装 Docker..."
                
                # 更新软件包索引
                apt-get update
                
                # 安装依赖包
                apt-get install -y \\
                    apt-transport-https \\
                    ca-certificates \\
                    curl \\
                    gnupg \\
                    lsb-release
                
                # 添加 Docker 官方 GPG 密钥
                curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
                
                # 设置稳定版仓库
                echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
                
                # 更新软件包索引
                apt-get update
                
                # 安装 Docker
                apt-get install -y docker-ce docker-ce-cli containerd.io
                
                # 启动 Docker 服务
                systemctl start docker
                systemctl enable docker
                
                # 将当前用户添加到 docker 组
                if [ "$SUDO_USER" ]; then
                    usermod -aG docker $SUDO_USER
                fi
                
                # 验证安装
                docker --version
                
                echo "Docker 安装完成！"
                """;
    }

    private String generateRHELInstallScript() {
        return """
                #!/bin/bash
                set -e
                
                echo "开始安装 Docker..."
                
                # 卸载旧版本
                yum remove -y docker docker-client docker-client-latest docker-common docker-latest docker-latest-logrotate docker-logrotate docker-engine
                
                # 安装依赖包
                yum install -y yum-utils
                
                # 设置仓库
                yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
                
                # 安装 Docker
                yum install -y docker-ce docker-ce-cli containerd.io
                
                # 启动 Docker 服务
                systemctl start docker
                systemctl enable docker
                
                # 将当前用户添加到 docker 组
                if [ "$SUDO_USER" ]; then
                    usermod -aG docker $SUDO_USER
                fi
                
                # 验证安装
                docker --version
                
                echo "Docker 安装完成！"
                """;
    }

    @Override
    public String getName() {
        return "Docker 安装";
    }

    @Override
    public String getDescription() {
        return "安装 Docker 容器运行时环境";
    }
}
