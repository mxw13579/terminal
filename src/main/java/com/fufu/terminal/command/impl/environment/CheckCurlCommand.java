package com.fufu.terminal.command.impl.environment;

import com.fufu.terminal.command.base.EnvironmentCheckCommand;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.SshCommandUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 检查Curl是否安装的命令
 * 检测远程服务器上Curl的安装状态和版本信息
 * @author lizelin
 */
@Slf4j
public class CheckCurlCommand extends EnvironmentCheckCommand {

    public static final String CURL_INSTALLED_KEY = "curl_installed";
    public static final String CURL_VERSION_KEY = "curl_version";

    @Override
    public String getName() {
        return "Check Curl Installation";
    }

    @Override
    protected String getCheckTargetName() {
        return "Curl";
    }

    @Override
    protected boolean performCheck(CommandContext context) throws Exception {
        return checkCommandExists(context, "curl");
    }

    @Override
    protected String getVersion(CommandContext context) throws Exception {
        try {
            CommandResult result = SshCommandUtil.executeCommand(
                context.getSshConnection(),
                "curl --version 2>/dev/null | head -1"
            );

            if (result.isSuccess() && !result.getStdout().isBlank()) {
                String version = result.getStdout().trim();
                // 提取版本号部分，格式通常为: curl 7.68.0 (x86_64-pc-linux-gnu)
                if (version.startsWith("curl ")) {
                    String versionPart = version.substring("curl ".length());
                    // 只取版本号，去掉平台信息
                    int spaceIndex = versionPart.indexOf(' ');
                    if (spaceIndex > 0) {
                        return versionPart.substring(0, spaceIndex);
                    }
                    return versionPart;
                }
                return version;
            }
        } catch (Exception e) {
            log.debug("获取Curl版本失败: {}", e.getMessage());
        }
        return null;
    }

    @Override
    protected void setCheckResult(CommandContext context, boolean installed, String version) {
        context.setProperty(CURL_INSTALLED_KEY, installed);
        context.setProperty(CURL_VERSION_KEY, version);
    }
}
