package com.fufu.terminal.command.impl.builtin;

import com.fufu.terminal.command.AtomicScriptCommand;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.model.enums.SystemType;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.strategy.BuiltInScriptMetadata;
import com.fufu.terminal.service.script.strategy.BuiltInScriptType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Docker 安装命令
 * 内置原子脚本，支持参数配置
 */
@Slf4j
@Component("docker-install")
public class DockerInstallCommand implements AtomicScriptCommand, BuiltInScriptMetadata {

    @Override
    public CommandResult execute(CommandContext context) {
        log.info("开始执行 Docker 安装命令");

        try {
            // 获取参数
            String registryMirror = context.getVariable("registry_mirror", String.class);
            Boolean installCompose = context.getVariable("install_compose", Boolean.class);
            Boolean enableNonRootAccess = context.getVariable("enable_non_root_access", Boolean.class);

            // 新增：获取前置脚本传递的变量
            String serverLocation = context.getScriptVariable("SERVER_LOCATION", String.class);
            String osType = context.getScriptVariable("OS_TYPE", String.class);
            
            log.info("检测到服务器位置: {}, 操作系统: {}", serverLocation, osType);

            SystemType systemType = context.getSystemType();

            // 智能选择镜像源
            if (registryMirror == null || "default".equals(registryMirror)) {
                if ("China".equals(serverLocation)) {
                    registryMirror = "https://mirror.aliyun.com/docker-ce";
                    log.info("检测到中国服务器，自动使用阿里云镜像源");
                }
            }

            // 根据系统类型和参数生成安装脚本
            String installScript = generateInstallScript(systemType, registryMirror, installCompose, enableNonRootAccess);

            // 执行脚本
            CommandResult result = context.executeScript(installScript);

            if (result.isSuccess()) {
                log.info("Docker 安装成功");
                // 设置输出变量
                context.setScriptVariable("DOCKER_INSTALLED", true);
                context.setScriptVariable("DOCKER_MIRROR", registryMirror);
                return CommandResult.success("Docker 安装成功，使用镜像源: " + registryMirror);
            } else {
                log.error("Docker 安装失败: {}", result.getErrorMessage());
                return CommandResult.failure("Docker 安装失败: " + result.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("执行 Docker 安装命令异常", e);
            return CommandResult.failure("执行异常: " + e.getMessage());
        }
    }

    private String generateInstallScript(SystemType systemType, String registryMirror, Boolean installCompose, Boolean enableNonRootAccess) {
        switch (systemType) {
            case UBUNTU:
            case DEBIAN:
                return generateDebianInstallScript(registryMirror, installCompose, enableNonRootAccess);
            case CENTOS:
            case REDHAT:
                return generateRHELInstallScript(registryMirror, installCompose, enableNonRootAccess);
            default:
                throw new UnsupportedOperationException("不支持的系统类型: " + systemType);
        }
    }

    private String generateDebianInstallScript(String registryMirror, Boolean installCompose, Boolean enableNonRootAccess) {
        StringBuilder script = new StringBuilder();
        script.append("""
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
                """);

        // 配置镜像加速器
        if (registryMirror != null && !registryMirror.equals("default")) {
            script.append(String.format("""
                
                # 配置 Docker 镜像加速器
                mkdir -p /etc/docker
                cat > /etc/docker/daemon.json << EOF
                {
                  "registry-mirrors": ["%s"]
                }
                EOF
                systemctl daemon-reload
                systemctl restart docker
                """, registryMirror));
        }

        // 添加用户到 docker 组
        if (enableNonRootAccess == null || enableNonRootAccess) {
            script.append("""
                
                # 将当前用户添加到 docker 组
                if [ "$SUDO_USER" ]; then
                    usermod -aG docker $SUDO_USER
                    echo "用户 $SUDO_USER 已添加到 docker 组"
                fi
                """);
        }

        // 安装 Docker Compose
        if (installCompose == null || installCompose) {
            script.append("""
                
                # 安装 Docker Compose
                COMPOSE_VERSION=$(curl -s https://api.github.com/repos/docker/compose/releases/latest | grep tag_name | cut -d '"' -f 4)
                curl -L "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
                chmod +x /usr/local/bin/docker-compose
                echo "Docker Compose 版本: $(docker-compose --version)"
                """);
        }

        script.append("""
                
                # 验证安装
                docker --version
                
                echo "Docker 安装完成！"
                """);

        return script.toString();
    }

    private String generateRHELInstallScript(String registryMirror, Boolean installCompose, Boolean enableNonRootAccess) {
        StringBuilder script = new StringBuilder();
        script.append("""
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
                """);

        // 配置镜像加速器
        if (registryMirror != null && !registryMirror.equals("default")) {
            script.append(String.format("""
                
                # 配置 Docker 镜像加速器
                mkdir -p /etc/docker
                cat > /etc/docker/daemon.json << EOF
                {
                  "registry-mirrors": ["%s"]
                }
                EOF
                systemctl daemon-reload
                systemctl restart docker
                """, registryMirror));
        }

        // 添加用户到 docker 组
        if (enableNonRootAccess == null || enableNonRootAccess) {
            script.append("""
                
                # 将当前用户添加到 docker 组
                if [ "$SUDO_USER" ]; then
                    usermod -aG docker $SUDO_USER
                    echo "用户 $SUDO_USER 已添加到 docker 组"
                fi
                """);
        }

        // 安装 Docker Compose
        if (installCompose == null || installCompose) {
            script.append("""
                
                # 安装 Docker Compose
                COMPOSE_VERSION=$(curl -s https://api.github.com/repos/docker/compose/releases/latest | grep tag_name | cut -d '"' -f 4)
                curl -L "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
                chmod +x /usr/local/bin/docker-compose
                echo "Docker Compose 版本: $(docker-compose --version)"
                """);
        }

        script.append("""
                
                # 验证安装
                docker --version
                
                echo "Docker 安装完成！"
                """);

        return script.toString();
    }

    @Override
    public String getName() {
        return "Docker 安装";
    }

    @Override
    public String getDescription() {
        return "安装 Docker 容器运行时环境，支持镜像加速器配置和 Docker Compose 安装";
    }

    // BuiltInScriptMetadata 接口实现
    @Override
    public String getScriptId() {
        return "docker-install";
    }

    @Override
    public BuiltInScriptType getType() {
        return BuiltInScriptType.DYNAMIC;
    }

    @Override
    public List<ScriptParameter> getParameters() {
        return Arrays.asList(
            createParameter("registry_mirror", ScriptParameter.ParameterType.STRING,
                "Docker 镜像加速器地址", false, "default"),
            createParameter("install_compose", ScriptParameter.ParameterType.BOOLEAN,
                "是否安装 Docker Compose", false, true),
            createParameter("enable_non_root_access", ScriptParameter.ParameterType.BOOLEAN,
                "是否允许非 root 用户访问 Docker", false, true)
        );
    }

    private ScriptParameter createParameter(String name, ScriptParameter.ParameterType type,
                                          String description, boolean required, Object defaultValue) {
        ScriptParameter param = new ScriptParameter();
        param.setName(name);
        param.setType(type);
        param.setDescription(description);
        param.setRequired(required);
        param.setDefaultValue(defaultValue);
        return param;
    }

    @Override
    public String[] getTags() {
        return new String[]{"容器", "Docker", "安装", "基础设施"};
    }
}
