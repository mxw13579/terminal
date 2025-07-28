package com.fufu.terminal.command.impl;

import com.fufu.terminal.command.Command;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.SshCommandUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 检测服务器地理位置的命令，判断是否在中国
 * 基于多个IP地理位置API服务进行检测
 * @author lizelin
 */
@Slf4j
public class DetectLocationCommand implements Command {

    public static final String LOCATION_INFO_KEY = "location_info";
    public static final String USE_CHINA_MIRROR_KEY = "use_china_mirror";

    @Override
    public String getName() {
        return "Detect Server Location";
    }

    @Override
    public void execute(CommandContext context) throws Exception {
        log.info("开始检测服务器地理位置...");
        
        String countryCode = detectCountryCode(context);
        boolean useChinaMirror = isInChina(countryCode);
        
        // 设置到上下文
        context.setProperty(LOCATION_INFO_KEY, countryCode);
        context.setProperty(USE_CHINA_MIRROR_KEY, useChinaMirror);
        
        if (useChinaMirror) {
            log.info("检测到服务器位于中国 (Code: {}), 将使用国内镜像", countryCode);
        } else {
            log.info("服务器不在中国 (Code: {}), 使用官方源", countryCode != null ? countryCode : "未知");
        }
    }

    /**
     * 检测国家代码，依次尝试多个API服务
     * @param context 命令上下文
     * @return 国家代码或null
     * @throws Exception SSH命令执行异常
     */
    private String detectCountryCode(CommandContext context) throws Exception {
        // 尝试方法1：ip-api.com
        String countryCode = tryIpApiCom(context);
        if (countryCode != null && !countryCode.isBlank()) {
            return countryCode;
        }

        // 尝试方法2：ipinfo.io/country
        countryCode = tryIpInfoCountry(context);
        if (countryCode != null && !countryCode.isBlank()) {
            return countryCode;
        }

        // 尝试方法3：ipinfo.io JSON
        countryCode = tryIpInfoJson(context);
        if (countryCode != null && !countryCode.isBlank()) {
            return countryCode;
        }

        log.warn("所有IP地理位置检测方法均失败");
        return null;
    }

    /**
     * 尝试使用ip-api.com检测国家
     * @param context 命令上下文
     * @return 国家代码或null
     * @throws Exception SSH命令执行异常
     */
    private String tryIpApiCom(CommandContext context) throws Exception {
        try {
            String cmd = "curl -s --connect-timeout 10 'http://ip-api.com/json/?fields=country' | " +
                        "sed -n 's/.*\"country\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p'";
            CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), cmd);
            if (result.isSuccess() && !result.getStdout().isBlank()) {
                return result.getStdout().trim();
            }
        } catch (Exception e) {
            log.debug("ip-api.com检测失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 尝试使用ipinfo.io/country检测国家
     * @param context 命令上下文
     * @return 国家代码或null
     * @throws Exception SSH命令执行异常
     */
    private String tryIpInfoCountry(CommandContext context) throws Exception {
        try {
            String cmd = "curl -sS --connect-timeout 10 --max-time 10 -w \"%{http_code}\" ipinfo.io/country | " +
                        "sed 's/200$//'";
            CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), cmd);
            if (result.isSuccess() && !result.getStdout().isBlank()) {
                String output = result.getStdout().trim();
                // 移除可能的HTTP状态码
                return output.replaceAll("\\d{3}$", "").trim();
            }
        } catch (Exception e) {
            log.debug("ipinfo.io/country检测失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 尝试使用ipinfo.io JSON API检测国家
     * @param context 命令上下文
     * @return 国家代码或null
     * @throws Exception SSH命令执行异常
     */
    private String tryIpInfoJson(CommandContext context) throws Exception {
        try {
            String cmd = "curl -s --connect-timeout 10 https://ipinfo.io/ | " +
                        "sed -n 's/.*\"country\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p'";
            CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), cmd);
            if (result.isSuccess() && !result.getStdout().isBlank()) {
                return result.getStdout().trim();
            }
        } catch (Exception e) {
            log.debug("ipinfo.io JSON检测失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 判断是否在中国
     * @param countryCode 国家代码
     * @return 是否在中国
     */
    private boolean isInChina(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return false;
        }
        return "China".equalsIgnoreCase(countryCode) || "CN".equalsIgnoreCase(countryCode);
    }
}