package com.fufu.terminal.command.impl.environment;

import com.fufu.terminal.command.base.EnvironmentCheckCommand;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.SshCommandUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 检查Docker是否安装的命令
 * 检测远程服务器上Docker的安装状态、版本信息和运行状态
 * @author lizelin
 */
@Slf4j
public class CheckDockerCommand extends EnvironmentCheckCommand {

    public static final String DOCKER_INSTALLED_KEY = "docker_installed";
    public static final String DOCKER_VERSION_KEY = "docker_version";
    public static final String DOCKER_RUNNING_KEY = "docker_running";

    @Override
    public String getName() {
        return "Check Docker Installation";
    }

    @Override
    protected String getCheckTargetName() {
        return "Docker";
    }

    @Override
    protected boolean performCheck(CommandContext context) throws Exception {
        return checkCommandExists(context, "docker");
    }

    @Override
    protected String getVersion(CommandContext context) throws Exception {
        try {
            CommandResult result = SshCommandUtil.executeCommand(
                context.getSshConnection(),
                "docker --version 2>/dev/null"
            );

            if (result.isSuccess() && !result.getStdout().isBlank()) {
                String version = result.getStdout().trim();
                // 提取版本号部分，格式通常为: Docker version 20.10.17, build 100c701
                if (version.startsWith("Docker version ")) {
                    String versionPart = version.substring("Docker version ".length());
                    // 只取版本号，去掉build信息
                    int commaIndex = versionPart.indexOf(',');
                    if (commaIndex > 0) {
                        return versionPart.substring(0, commaIndex);
                    }
                    return versionPart;
                }
                return version;
            }
        } catch (Exception e) {
            log.debug("获取Docker版本失败: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public void execute(CommandContext context) throws Exception {
        // 先执行基础的检查逻辑
        super.execute(context);

        // 如果Docker已安装，还需要检查运行状态
        Boolean dockerInstalled = (Boolean) context.getProperty(DOCKER_INSTALLED_KEY);
        if (dockerInstalled != null && dockerInstalled) {
            boolean dockerRunning = checkDockerRunning(context);
            context.setProperty(DOCKER_RUNNING_KEY, dockerRunning);
            log.info("Docker运行状态: {}", dockerRunning ? "运行中" : "未运行");
        } else {
            context.setProperty(DOCKER_RUNNING_KEY, false);
        }
    }

    @Override
    protected void setCheckResult(CommandContext context, boolean installed, String version) {
        context.setProperty(DOCKER_INSTALLED_KEY, installed);
        context.setProperty(DOCKER_VERSION_KEY, version);
    }

    /**
     * 检查Docker服务是否正在运行
     */
    private boolean checkDockerRunning(CommandContext context) throws Exception {
        try {
            // 尝试运行docker ps检查Docker daemon是否响应
            CommandResult result = SshCommandUtil.executeCommand(
                context.getSshConnection(),
                "docker ps >/dev/null 2>&1 && echo 'running' || echo 'not_running'"
            );

            if (result.isSuccess()) {
                String output = result.getStdout().trim();
                return "running".equals(output);
            }
        } catch (Exception e) {
            log.debug("检查Docker运行状态失败: {}", e.getMessage());
        }
        return false;
    }
}
