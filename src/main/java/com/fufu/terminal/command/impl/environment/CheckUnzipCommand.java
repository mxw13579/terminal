package com.fufu.terminal.command.impl.environment;

import com.fufu.terminal.command.base.EnvironmentCheckCommand;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.SshCommandUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 检查Unzip是否安装的命令
 * 检测远程服务器上Unzip的安装状态和版本信息
 * @author lizelin
 */
@Slf4j
public class CheckUnzipCommand extends EnvironmentCheckCommand {

    public static final String UNZIP_INSTALLED_KEY = "unzip_installed";
    public static final String UNZIP_VERSION_KEY = "unzip_version";

    @Override
    public String getName() {
        return "Check Unzip Installation";
    }

    @Override
    protected String getCheckTargetName() {
        return "Unzip";
    }

    @Override
    protected boolean performCheck(CommandContext context) throws Exception {
        return checkCommandExists(context, "unzip");
    }

    @Override
    protected String getVersion(CommandContext context) throws Exception {
        try {
            CommandResult result = SshCommandUtil.executeCommand(
                context.getSshConnection(),
                "unzip -v 2>/dev/null | head -1"
            );

            if (result.isSuccess() && !result.getStdout().isBlank()) {
                String version = result.getStdout().trim();
                // 提取版本号部分，格式通常为: UnZip 6.00 of 20 April 2009, by Debian
                if (version.toLowerCase().contains("unzip")) {
                    // 查找版本号模式
                    String[] parts = version.split("\\s+");
                    for (int i = 0; i < parts.length - 1; i++) {
                        if (parts[i].toLowerCase().contains("unzip")) {
                            String nextPart = parts[i + 1];
                            // 检查是否为版本号格式
                            if (nextPart.matches("\\d+\\.\\d+.*")) {
                                return nextPart;
                            }
                        }
                    }
                }
                return version;
            }
        } catch (Exception e) {
            log.debug("获取Unzip版本失败: {}", e.getMessage());
        }
        return null;
    }

    @Override
    protected void setCheckResult(CommandContext context, boolean installed, String version) {
        context.setProperty(UNZIP_INSTALLED_KEY, installed);
        context.setProperty(UNZIP_VERSION_KEY, version);
    }
}
