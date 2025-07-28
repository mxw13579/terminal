package com.fufu.terminal.command.impl;

import com.fufu.terminal.command.Command;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.SshCommandUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 检查Git是否安装的命令
 * 检测远程服务器上Git的安装状态和版本信息
 * @author lizelin
 */
@Slf4j
public class CheckGitCommand implements Command {

    public static final String GIT_INSTALLED_KEY = "git_installed";
    public static final String GIT_VERSION_KEY = "git_version";

    @Override
    public String getName() {
        return "Check Git Installation";
    }

    @Override
    public void execute(CommandContext context) throws Exception {
        log.info("开始检查Git安装状态...");
        
        boolean gitInstalled = checkGitInstalled(context);
        String gitVersion = null;
        
        if (gitInstalled) {
            gitVersion = getGitVersion(context);
            log.info("Git已安装，版本: {}", gitVersion != null ? gitVersion : "未知");
        } else {
            log.info("Git未安装");
        }
        
        // 设置到上下文
        context.setProperty(GIT_INSTALLED_KEY, gitInstalled);
        context.setProperty(GIT_VERSION_KEY, gitVersion);
    }

    /**
     * 检查Git是否已安装
     * @param context 命令上下文
     * @return 是否已安装
     * @throws Exception SSH命令执行异常
     */
    private boolean checkGitInstalled(CommandContext context) throws Exception {
        try {
            // 使用command -v检查git命令是否存在
            CommandResult result = SshCommandUtil.executeCommand(
                context.getSshConnection(), 
                "command -v git >/dev/null 2>&1 && echo 'installed' || echo 'not_installed'"
            );
            
            if (result.isSuccess()) {
                String output = result.getStdout().trim();
                return "installed".equals(output);
            }
        } catch (Exception e) {
            log.debug("检查Git安装状态失败: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 获取Git版本信息
     * @param context 命令上下文
     * @return Git版本字符串
     * @throws Exception SSH命令执行异常
     */
    private String getGitVersion(CommandContext context) throws Exception {
        try {
            CommandResult result = SshCommandUtil.executeCommand(
                context.getSshConnection(), 
                "git --version 2>/dev/null"
            );
            
            if (result.isSuccess() && !result.getStdout().isBlank()) {
                String version = result.getStdout().trim();
                // 提取版本号部分，格式通常为: git version 2.34.1
                if (version.startsWith("git version ")) {
                    return version.substring("git version ".length());
                }
                return version;
            }
        } catch (Exception e) {
            log.debug("获取Git版本失败: {}", e.getMessage());
        }
        return null;
    }
}