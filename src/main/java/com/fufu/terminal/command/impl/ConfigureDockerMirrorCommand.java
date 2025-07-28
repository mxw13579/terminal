package com.fufu.terminal.command.impl;

import com.fufu.terminal.command.Command;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.SshCommandUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 配置Docker镜像加速器的命令
 * 根据地理位置和Docker安装状态配置Docker国内镜像加速器
 * @author lizelin
 */
@Slf4j
public class ConfigureDockerMirrorCommand implements Command {

    public static final String DOCKER_MIRROR_CONFIGURED_KEY = "docker_mirror_configured";

    @Override
    public String getName() {
        return "Configure Docker Mirror";
    }

    @Override
    public void execute(CommandContext context) throws Exception {
        log.info("开始配置Docker镜像加速器...");
        
        // 检查是否需要配置中国镜像
        Boolean useChinaMirror = (Boolean) context.getProperty(DetectLocationCommand.USE_CHINA_MIRROR_KEY);
        if (useChinaMirror == null || !useChinaMirror) {
            log.info("跳过Docker镜像加速器配置（不在中国大陆）");
            context.setProperty(DOCKER_MIRROR_CONFIGURED_KEY, false);
            return;
        }

        // 检查Docker是否安装
        Boolean dockerInstalled = (Boolean) context.getProperty(CheckDockerCommand.DOCKER_INSTALLED_KEY);
        if (dockerInstalled == null || !dockerInstalled) {
            log.info("跳过Docker镜像加速器配置（Docker未安装）");
            context.setProperty(DOCKER_MIRROR_CONFIGURED_KEY, false);
            return;
        }

        boolean configured = false;
        try {
            configured = configureDockerMirror(context);
        } catch (Exception e) {
            log.error("配置Docker镜像加速器时发生错误", e);
            configured = false;
        }

        context.setProperty(DOCKER_MIRROR_CONFIGURED_KEY, configured);
        log.info("Docker镜像加速器配置完成，结果: {}", configured ? "成功" : "失败");
    }

    /**
     * 配置Docker镜像加速器
     * @param context 命令上下文
     * @return 是否配置成功
     * @throws Exception SSH命令执行异常
     */
    private boolean configureDockerMirror(CommandContext context) throws Exception {
        log.info("配置Docker国内镜像加速器...");

        // 创建Docker配置目录
        CommandResult mkdirResult = SshCommandUtil.executeCommand(
            context.getSshConnection(),
            "sudo mkdir -p /etc/docker"
        );
        if (!mkdirResult.isSuccess()) {
            log.error("创建/etc/docker目录失败");
            return false;
        }

        // 配置daemon.json内容
        String daemonConfig = """
            {
              "registry-mirrors": [
                "https://hub-mirror.c.163.com",
                "https://mirror.baidubce.com",
                "https://registry.docker-cn.com"
              ]
            }""";

        // 写入daemon.json配置文件
        CommandResult configResult = SshCommandUtil.executeCommand(
            context.getSshConnection(),
            String.format("sudo tee /etc/docker/daemon.json <<-'EOF'%n%s%nEOF", daemonConfig)
        );

        if (!configResult.isSuccess()) {
            log.error("写入Docker daemon.json配置失败");
            return false;
        }

        log.info("Docker镜像加速器配置已写入");

        // 重新加载systemd配置
        CommandResult reloadResult = SshCommandUtil.executeCommand(
            context.getSshConnection(),
            "sudo systemctl daemon-reload"
        );
        if (!reloadResult.isSuccess()) {
            log.warn("重新加载systemd配置失败，但配置文件已写入");
        }

        // 重启Docker服务
        log.info("重启Docker服务以应用镜像加速配置...");
        CommandResult restartResult = SshCommandUtil.executeCommand(
            context.getSshConnection(),
            "sudo systemctl restart docker"
        );

        if (!restartResult.isSuccess()) {
            log.error("重启Docker服务失败: {}", restartResult.getStderr());
            return false;
        }

        // 验证Docker服务是否正常启动
        CommandResult statusResult = SshCommandUtil.executeCommand(
            context.getSshConnection(),
            "sudo systemctl is-active docker"
        );

        if (statusResult.isSuccess() && "active".equals(statusResult.getStdout().trim())) {
            log.info("Docker服务已成功重启并正在运行");
            return true;
        } else {
            log.error("Docker服务重启后状态异常");
            return false;
        }
    }
}