package com.fufu.terminal.command.impl.preprocess;

import com.fufu.terminal.command.base.PreProcessCommand;
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
public class DetectLocationCommand extends PreProcessCommand {

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
     */
    private String detectCountryCode(CommandContext context) throws Exception {

        //ipinfo.io（先 /country，再 JSON）
        String countryCode = tryIpInfo(context);
        log.info("尝试使用 ipinfo.io 检测，结果: {}", countryCode != null ? countryCode : "未知");
        if (hasCode(countryCode)) {
            return countryCode;
        }

        //ip-api.com
        countryCode = tryIpApiCom(context);
        log.info("尝试使用 ip-api.com 检测，结果: {}", countryCode != null ? countryCode : "未知");
        if (hasCode(countryCode)) {
            return countryCode;
        }

        //inet-ip.info/json
        countryCode = tryInetIpInfo(context);
        log.info("尝试使用 inet-ip.info 检测，结果: {}", countryCode != null ? countryCode : "未知");
        if (hasCode(countryCode)) {
            return countryCode;
        }

        log.warn("所有 IP 地理位置检测方法均失败");
        return null;
    }

    private boolean hasCode(String code) {
        return code != null && !code.isBlank();
    }

    private String tryIpApiCom(CommandContext context) throws Exception {
        try {
            String cmd = "curl -s --connect-timeout 10 'http://ip-api.com/json/?fields=country' | " +
                        "sed -n 's/.*\"country\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p'";
            CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), cmd);
            log.info("ip-api.com JSON检测结果: {}", result.getStdout());
            if (result.isSuccess() && !result.getStdout().isBlank()) {
                return result.getStdout().trim();
            }
        } catch (Exception e) {
            log.debug("ip-api.com检测失败: {}", e.getMessage());
        }
        return null;
    }

    private String tryIpInfo(CommandContext context) throws Exception {
        try {
            // 先调用 /country 简单接口
            String cmd1 = "curl -sS --connect-timeout 10 --max-time 10 -w \"%{http_code}\" ipinfo.io/country | sed 's/200$//'";
            CommandResult r1 = SshCommandUtil.executeCommand(context.getSshConnection(), cmd1);
            log.info("ipinfo.io /country 检测结果: {}", r1.getStdout());
            String out1 = r1.isSuccess() ? r1.getStdout().trim().replaceAll("\\d{3}$", "") : "";
            if (!out1.isBlank()) {
                return out1;
            }
            // 再调用 JSON 接口解析 country 字段
            String cmd2 = "curl -s --connect-timeout 10 https://ipinfo.io/ | " +
                    "sed -n 's/.*\"country\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p'";
            CommandResult r2 = SshCommandUtil.executeCommand(context.getSshConnection(), cmd2);
            log.info("ipinfo.io JSON检测结果: {}", r2.getStdout());
            if (r2.isSuccess() && !r2.getStdout().isBlank()) {
                return r2.getStdout().trim();
            }
        } catch (Exception e) {
            log.debug("ipinfo.io 检测失败: {}", e.getMessage());
        }
        return null;
    }


    private boolean isInChina(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return false;
        }
        return "China".equalsIgnoreCase(countryCode) || "CN".equalsIgnoreCase(countryCode);
    }

    private String tryInetIpInfo(CommandContext context) throws Exception {
        try {
            String cmd = "curl -s --connect-timeout 10 inet-ip.info/json | " +
                    "sed -n 's/.*\"IsoCode\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p'";
            CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), cmd);
            log.info("inet-ip.info JSON检测结果: {}", result.getStdout());
            if (result.isSuccess() && !result.getStdout().isBlank()) {
                return result.getStdout().trim();
            }
        } catch (Exception e) {
            log.debug("inet-ip.info 检测失败: {}", e.getMessage());
        }
        return null;
    }
}
