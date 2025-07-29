package com.fufu.terminal.command.impl.environment;

import com.fufu.terminal.command.base.EnvironmentCheckCommand;
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
public class CheckGitCommand extends EnvironmentCheckCommand {

    public static final String GIT_INSTALLED_KEY = "git_installed";
    public static final String GIT_VERSION_KEY = "git_version";

    @Override
    public String getName() {
        return "Check Git Installation";
    }

    @Override
    protected String getCheckTargetName() {
        return "Git";
    }

    @Override
    protected boolean performCheck(CommandContext context) throws Exception {
        return checkCommandExists(context, "git");
    }

    @Override
    protected String getVersion(CommandContext context) throws Exception {
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

    @Override
    protected void setCheckResult(CommandContext context, boolean installed, String version) {
        context.setProperty(GIT_INSTALLED_KEY, installed);
        context.setProperty(GIT_VERSION_KEY, version);
    }
}
