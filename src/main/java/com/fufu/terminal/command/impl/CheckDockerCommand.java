package com.fufu.terminal.command.impl;

import com.fufu.terminal.command.Command;
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
public class CheckDockerCommand implements Command {

    public static final String DOCKER_INSTALLED_KEY = "docker_installed";
    public static final String DOCKER_VERSION_KEY = "docker_version";
    public static final String DOCKER_RUNNING_KEY = "docker_running";

    @Override
    public String getName() {
        return "Check Docker Installation";
    }

    @Override
    public void execute(CommandContext context) throws Exception {
        log.info("开始检查Docker安装状态...");
        
        boolean dockerInstalled = checkDockerInstalled(context);
        String dockerVersion = null;
        boolean dockerRunning = false;
        
        if (dockerInstalled) {
            dockerVersion = getDockerVersion(context);
            dockerRunning = checkDockerRunning(context);
            log.info("Docker已安装，版本: {}，运行状态: {}", 
                dockerVersion != null ? dockerVersion : "未知", 
                dockerRunning ? "运行中" : "未运行");
        } else {
            log.info("Docker未安装");
        }
        
        // 设置到上下文
        context.setProperty(DOCKER_INSTALLED_KEY, dockerInstalled);
        context.setProperty(DOCKER_VERSION_KEY, dockerVersion);
        context.setProperty(DOCKER_RUNNING_KEY, dockerRunning);
    }

    /**
     * 检查Docker是否已安装
     * @param context 命令上下文
     * @return 是否已安装
     * @throws Exception SSH命令执行异常
     */
    private boolean checkDockerInstalled(CommandContext context) throws Exception {
        try {
            // 使用command -v检查docker命令是否存在
            CommandResult result = SshCommandUtil.executeCommand(
                context.getSshConnection(), 
                "command -v docker >/dev/null 2>&1 && echo 'installed' || echo 'not_installed'"
            );
            
            if (result.isSuccess()) {
                String output = result.getStdout().trim();
                return "installed".equals(output);
            }
        } catch (Exception e) {
            log.debug("检查Docker安装状态失败: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 获取Docker版本信息
     * @param context 命令上下文
     * @return Docker版本字符串
     * @throws Exception SSH命令执行异常
     */
    private String getDockerVersion(CommandContext context) throws Exception {
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

    /**
     * 检查Docker服务是否正在运行
     * @param context 命令上下文
     * @return 是否正在运行
     * @throws Exception SSH命令执行异常
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