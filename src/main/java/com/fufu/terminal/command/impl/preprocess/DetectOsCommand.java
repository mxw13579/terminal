package com.fufu.terminal.command.impl.preprocess;

import com.fufu.terminal.command.base.PreProcessCommand;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.SshCommandUtil;
import com.fufu.terminal.command.model.OsInfo;
import com.fufu.terminal.command.model.enums.SystemType;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 检测远程服务器操作系统的命令
 * 支持多种主流 Linux 发行版的识别，兼容性强
 * @author lizelin
 */
@Slf4j
public class DetectOsCommand extends PreProcessCommand {

    public static final String OS_INFO_KEY = "os_info";

    @Override
    public String getName() {
        return "Detect Operating System";
    }

    @Override
    public void execute(CommandContext context) throws Exception {
        OsInfo osInfo = new OsInfo();

        // 1. 获取架构信息
        osInfo.setArch(execAndTrim(context, "uname -m", "unknown"));

        // 2. 依次尝试各种发行版检测方式，顺序与脚本保持一致
        tryOsRelease(context, osInfo);
        tryLsbRelease(context, osInfo);
        tryLsbReleaseFile(context, osInfo);
        tryRedhatRelease(context, osInfo);
        trySuSeRelease(context, osInfo);
        tryAlpineRelease(context, osInfo);
        // 若ID仍未知，再尝试issue和uname
        if (isUnknown(osInfo.getId())) {
            tryIssueFile(context, osInfo);
        }
        if (isUnknown(osInfo.getId())) {
            tryUname(context, osInfo);
        }

        // 3. 填充默认值，并映射系统类型
        ensureDefaultValues(osInfo);
        osInfo.setSystemType(SystemType.fromId(osInfo.getId()));

        // 4. 设置到上下文属性和 SSH 连接缓存
        context.setProperty(OS_INFO_KEY, osInfo);
        context.getSshConnection().setOsInfo(osInfo);
    }

    // -------- 工具方法 --------

    private String execAndTrim(CommandContext ctx, String cmd, String defaultVal) throws Exception {
        CommandResult res = SshCommandUtil.executeCommand(ctx.getSshConnection(), cmd);
        return (res.isSuccess() && !res.getStdout().isBlank())
                ? res.getStdout().trim()
                : defaultVal;
    }

    private void setIfUnknown(Supplier<String> getter, Consumer<String> setter, String value) {
        if (isUnknown(getter.get()) && value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    private boolean isUnknown(String v) {
        return v == null || v.isBlank() || "unknown".equalsIgnoreCase(v);
    }

    private void ensureDefaultValues(OsInfo info) {
        setIfUnknown(info::getId, info::setId, "unknown");
        setIfUnknown(info::getName, info::setName, "unknown");
        setIfUnknown(info::getPrettyName, info::setPrettyName, "unknown");
        setIfUnknown(info::getVersionId, info::setVersionId, "unknown");
        setIfUnknown(info::getVersionCodename, info::setVersionCodename, "unknown");
        setIfUnknown(info::getArch, info::setArch, "unknown");
    }

    // -------- 各发行版检测 --------

    private void tryOsRelease(CommandContext ctx, OsInfo info) throws Exception {
        CommandResult r = SshCommandUtil.executeCommand(ctx.getSshConnection(), "cat /etc/os-release");
        if (!r.isSuccess()) {
            return;
        }
        Map<String, String> kv = parseKeyValue(r.getStdout());
        setIfUnknown(info::getId, info::setId,        kv.get("ID") != null ? kv.get("ID").toLowerCase() : null);
        setIfUnknown(info::getName, info::setName,    kv.get("NAME"));
        setIfUnknown(info::getPrettyName, info::setPrettyName, kv.get("PRETTY_NAME"));
        setIfUnknown(info::getVersionId, info::setVersionId,   kv.get("VERSION_ID"));
        // Ubuntu CODENAME 优先逻辑
        String codename = kv.getOrDefault("VERSION_CODENAME", kv.get("UBUNTU_CODENAME"));
        setIfUnknown(info::getVersionCodename, info::setVersionCodename, codename);
    }

    private void tryLsbRelease(CommandContext ctx, OsInfo info) throws Exception {
        String id   = execAndTrim(ctx, "lsb_release -si 2>/dev/null", "");
        String name = execAndTrim(ctx, "lsb_release -sd 2>/dev/null", "");
        String ver  = execAndTrim(ctx, "lsb_release -sr 2>/dev/null", "");
        String cod  = execAndTrim(ctx, "lsb_release -sc 2>/dev/null", "");
        setIfUnknown(info::getId, info::setId,       id.toLowerCase());
        setIfUnknown(info::getName, info::setName,   id);
        setIfUnknown(info::getPrettyName, info::setPrettyName, name);
        setIfUnknown(info::getVersionId, info::setVersionId,   ver);
        setIfUnknown(info::getVersionCodename, info::setVersionCodename, cod);
        // Ubuntu 额外从 os-release 再尝试一次
        if ("ubuntu".equals(info.getId())) {
            String uCod = execAndTrim(ctx,
                    "grep -oP 'VERSION_CODENAME=\\K.*' /etc/os-release 2>/dev/null", "");
            setIfUnknown(info::getVersionCodename, info::setVersionCodename, uCod);
        }
    }

    private void tryLsbReleaseFile(CommandContext ctx, OsInfo info) throws Exception {
        CommandResult r = SshCommandUtil.executeCommand(ctx.getSshConnection(), "cat /etc/lsb-release");
        if (!r.isSuccess()) {
            return;
        }
        Map<String, String> kv = parseKeyValue(r.getStdout(), "=");
        setIfUnknown(info::getId, info::setId,             kv.get("DISTRIB_ID").toLowerCase());
        setIfUnknown(info::getName, info::setName,         kv.get("DISTRIB_ID"));
        setIfUnknown(info::getVersionId, info::setVersionId,    kv.get("DISTRIB_RELEASE"));
        setIfUnknown(info::getVersionCodename, info::setVersionCodename, kv.get("DISTRIB_CODENAME"));
        setIfUnknown(info::getPrettyName, info::setPrettyName, kv.get("DISTRIB_DESCRIPTION"));
    }

    private void tryRedhatRelease(CommandContext ctx, OsInfo info) throws Exception {
        CommandResult r = SshCommandUtil.executeCommand(ctx.getSshConnection(), "cat /etc/redhat-release");
        if (!r.isSuccess()) {
            return;
        }
        String out = r.getStdout().trim();
        setIfUnknown(info::getPrettyName, info::setPrettyName, out);
        // 取首个单词作为名称
        String nm = out.split("\\s+")[0];
        setIfUnknown(info::getName, info::setName, nm);
        setIfUnknown(info::getId, info::setId, nm.toLowerCase());
        // 提取第一个数字串作为版本号
        String version = Arrays.stream(out.split("[^0-9.]"))
                .filter(s -> !s.isBlank())
                .findFirst().orElse("");
        setIfUnknown(info::getVersionId, info::setVersionId, version);
        setIfUnknown(info::getVersionCodename, info::setVersionCodename, "");
    }

    private void trySuSeRelease(CommandContext ctx, OsInfo info) throws Exception {
        CommandResult r = SshCommandUtil.executeCommand(ctx.getSshConnection(), "cat /etc/SuSE-release");
        if (!r.isSuccess()) {
            return;
        }
        String out = r.getStdout();
        setIfUnknown(info::getId, info::setId,          "suse");
        setIfUnknown(info::getName, info::setName,      "SUSE");
        setIfUnknown(info::getPrettyName, info::setPrettyName, "SUSE " + out.trim());
        // 提取版本号
        String ver = Arrays.stream(out.split("\\r?\\n"))
                .filter(line -> line.startsWith("VERSION"))
                .map(line -> line.split("\\s+"))
                .filter(parts -> parts.length>1)
                .map(parts -> parts[1])
                .findFirst().orElse("");
        setIfUnknown(info::getVersionId, info::setVersionId, ver);
        setIfUnknown(info::getVersionCodename, info::setVersionCodename, "");
    }

    private void tryAlpineRelease(CommandContext ctx, OsInfo info) throws Exception {
        String ver = execAndTrim(ctx, "cat /etc/alpine-release", "");
        setIfUnknown(info::getId, info::setId,          "alpine");
        setIfUnknown(info::getName, info::setName,      "Alpine Linux");
        setIfUnknown(info::getPrettyName, info::setPrettyName, "Alpine Linux " + ver);
        setIfUnknown(info::getVersionId, info::setVersionId, ver);
        setIfUnknown(info::getVersionCodename, info::setVersionCodename, "");
    }

    private void tryIssueFile(CommandContext ctx, OsInfo info) throws Exception {
        String line = execAndTrim(ctx, "head -1 /etc/issue | tr -d '\\\\l'", "");
        if (line.isBlank()) {
            return;
        }
        setIfUnknown(info::getPrettyName, info::setPrettyName, line);
        String nm = line.split("\\s+")[0];
        setIfUnknown(info::getName, info::setName, nm);
        setIfUnknown(info::getId, info::setId, nm.toLowerCase());
    }

    private void tryUname(CommandContext ctx, OsInfo info) throws Exception {
        String os  = execAndTrim(ctx, "uname -s", "");
        String ver = execAndTrim(ctx, "uname -r", "");
        setIfUnknown(info::getId, info::setId, os.toLowerCase());
        setIfUnknown(info::getName, info::setName, os);
        setIfUnknown(info::getVersionId, info::setVersionId, ver);
    }

    // -------- KV 解析 --------

    private static Map<String, String> parseKeyValue(String input) {
        return parseKeyValue(input, "=");
    }

    private static Map<String, String> parseKeyValue(String input, String delimiter) {
        return Arrays.stream(input.split("\\r?\\n"))
                .filter(line -> line.contains(delimiter))
                .map(line -> line.split(delimiter, 2))
                .collect(Collectors.toMap(
                        parts -> parts[0].trim(),
                        parts -> parts.length>1 ? parts[1].trim().replace("\"", "") : ""
                ));
    }
}
