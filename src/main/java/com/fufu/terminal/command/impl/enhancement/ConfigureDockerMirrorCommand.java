package com.fufu.terminal.command.impl.enhancement;

import com.fufu.terminal.command.base.EnhancementCommand;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.impl.environment.CheckDockerCommand;
import lombok.extern.slf4j.Slf4j;

/**
 * 配置Docker镜像加速器的命令
 * 根据地理位置和Docker安装状态配置Docker国内镜像加速器
 * @author lizelin
 */
@Slf4j
public class ConfigureDockerMirrorCommand extends EnhancementCommand {

    public static final String DOCKER_MIRROR_CONFIGURED_KEY = "docker_mirror_configured";

    @Override
    public String getName() {
        return "Configure Docker Mirror";
    }

    @Override
    protected String getEnhancementTargetName() {
        return "Docker镜像加速器";
    }

    @Override
    protected boolean shouldEnhance(CommandContext context) {
        // 需要中国镜像且Docker已安装
        if (!needsChinaMirror(context)) {
            log.info("跳过Docker镜像加速器配置（不在中国大陆）");
            return false;
        }

        Boolean dockerInstalled = (Boolean) context.getProperty(CheckDockerCommand.DOCKER_INSTALLED_KEY);
        if (dockerInstalled == null || !dockerInstalled) {
            log.info("跳过Docker镜像加速器配置（Docker未安装）");
            return false;
        }

        return true;
    }

    @Override
    protected boolean performEnhancement(CommandContext context) throws Exception {
        return configureDockerMirror(context);
    }

    @Override
    protected void setEnhancementResult(CommandContext context, boolean success) {
        context.setProperty(DOCKER_MIRROR_CONFIGURED_KEY, success);
    }

    /**
     * 配置Docker镜像加速器
     */
    private boolean configureDockerMirror(CommandContext context) throws Exception {
        log.info("配置Docker国内镜像加速器...");

        // 创建Docker配置目录
        if (!createDockerConfigDir(context)) {
            return false;
        }

        // 写入daemon.json配置
        if (!writeDaemonConfig(context)) {
            return false;
        }

        // 重启Docker服务
        if (!restartDockerService(context)) {
            return false;
        }

        log.info("Docker镜像加速器配置已完成");
        return true;
    }

    /**
     * 创建Docker配置目录
     */
    private boolean createDockerConfigDir(CommandContext context) throws Exception {
        CommandResult result = executeCommand(context, "sudo mkdir -p /etc/docker");
        if (!result.isSuccess()) {
            log.error("创建/etc/docker目录失败");
            return false;
        }
        return true;
    }

    /**
     * 写入Docker daemon.json配置
     */
    private boolean writeDaemonConfig(CommandContext context) throws Exception {
        String daemonConfig = generateDaemonConfig();

        CommandResult result = executeCommand(context,
            String.format("sudo tee /etc/docker/daemon.json <<-'EOF'%n%s%nEOF", daemonConfig));

        if (!result.isSuccess()) {
            log.error("写入Docker daemon.json配置失败");
            return false;
        }

        log.info("Docker镜像加速器配置已写入");
        return true;
    }

    /**
     * 重启Docker服务
     */
    private boolean restartDockerService(CommandContext context) throws Exception {
        // 重新加载systemd配置
        CommandResult reloadResult = executeCommand(context, "sudo systemctl daemon-reload");
        if (!reloadResult.isSuccess()) {
            log.warn("重新加载systemd配置失败，但配置文件已写入");
        }

        // 重启Docker服务
        log.info("重启Docker服务以应用镜像加速配置...");
        CommandResult restartResult = executeCommand(context, "sudo systemctl restart docker");
        if (!restartResult.isSuccess()) {
            log.error("重启Docker服务失败: {}", restartResult.getStderr());
            return false;
        }

        // 验证Docker服务状态
        return verifyDockerService(context);
    }

    /**
     * 验证Docker服务状态
     */
    private boolean verifyDockerService(CommandContext context) throws Exception {
        CommandResult statusResult = executeCommand(context, "sudo systemctl is-active docker");

        if (statusResult.isSuccess() && "active".equals(statusResult.getStdout().trim())) {
            log.info("Docker服务已成功重启并正在运行");
            return true;
        } else {
            log.error("Docker服务重启后状态异常");
            return false;
        }
    }

    /**
     * 生成Docker daemon.json配置内容
     */
    private String generateDaemonConfig() {
        return """
            {
              "registry-mirrors": [
                "https://hub-mirror.c.163.com",
                "https://mirror.baidubce.com",
                "https://registry.docker-cn.com"
              ]
            }""";
    }
}
