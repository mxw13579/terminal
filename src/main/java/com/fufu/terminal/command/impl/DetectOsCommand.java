package com.fufu.terminal.command.impl;

import com.fufu.terminal.command.Command;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.SshCommandUtil;
import com.fufu.terminal.command.model.OsInfo;
import com.fufu.terminal.command.model.enums.SystemType;

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
public class DetectOsCommand implements Command {

    public static final String OS_INFO_KEY = "os_info";

    /**
     * 获取命令名称
     * @return 命令名称字符串
     */
    @Override
    public String getName() {
        return "Detect Operating System";
    }

    /**
     * 执行操作系统检测主流程
     * 依次尝试多种方式识别远程主机操作系统信息，并设置到 context 属性中
     * @param context 命令上下文，包含 SSH 连接等信息
     * @throws Exception 检测失败时抛出异常
     */
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

    /**
     * 执行 SSH 命令并去除两端空白，失败返回默认值
     * @param ctx 命令上下文
     * @param cmd 要执行的命令
     * @param defaultVal 失败时返回的默认值
     * @return 命令输出的去空白结果，或默认值
     * @throws Exception SSH命令执行异常
     */
    private String execAndTrim(CommandContext ctx, String cmd, String defaultVal) throws Exception {
        CommandResult res = SshCommandUtil.executeCommand(ctx.getSshConnection(), cmd);
        return (res.isSuccess() && !res.getStdout().isBlank())
                ? res.getStdout().trim()
                : defaultVal;
    }

    /**
     * 当目标字段为 unknown 时才调用 setter 填充 value
     * @param getter 字段的getter方法引用
     * @param setter 字段的setter方法引用
     * @param value 要填充的值
     */
    private void setIfUnknown(Supplier<String> getter, Consumer<String> setter, String value) {
        if (isUnknown(getter.get()) && value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    /**
     * 判断字符串是否为 unknown 或空
     * @param v 待判断的字符串
     * @return 是否为 unknown
     */
    private boolean isUnknown(String v) {
        return v == null || v.isBlank() || "unknown".equalsIgnoreCase(v);
    }

    /**
     * 确保所有 OsInfo 字段都有默认值，避免后续空指针
     * @param info OsInfo 对象
     */
    private void ensureDefaultValues(OsInfo info) {
        setIfUnknown(info::getId, info::setId, "unknown");
        setIfUnknown(info::getName, info::setName, "unknown");
        setIfUnknown(info::getPrettyName, info::setPrettyName, "unknown");
        setIfUnknown(info::getVersionId, info::setVersionId, "unknown");
        setIfUnknown(info::getVersionCodename, info::setVersionCodename, "unknown");
        setIfUnknown(info::getArch, info::setArch, "unknown");
    }

    // -------- 各发行版检测 --------

    /**
     * 通过 /etc/os-release 文件检测操作系统信息
     * @param ctx 命令上下文
     * @param info OsInfo对象
     * @throws Exception SSH命令执行异常
     */
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

    /**
     * 通过 lsb_release 命令检测操作系统信息
     * @param ctx 命令上下文
     * @param info OsInfo对象
     * @throws Exception SSH命令执行异常
     */
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

    /**
     * 通过 /etc/lsb-release 文件检测操作系统信息
     * @param ctx 命令上下文
     * @param info OsInfo对象
     * @throws Exception SSH命令执行异常
     */
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

    /**
     * 通过 /etc/redhat-release 文件检测 RedHat/CentOS 操作系统信息
     * @param ctx 命令上下文
     * @param info OsInfo对象
     * @throws Exception SSH命令执行异常
     */
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

    /**
     * 通过 /etc/SuSE-release 文件检测 SUSE 操作系统信息
     * @param ctx 命令上下文
     * @param info OsInfo对象
     * @throws Exception SSH命令执行异常
     */
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

    /**
     * 通过 /etc/alpine-release 文件检测 Alpine Linux 操作系统信息
     * @param ctx 命令上下文
     * @param info OsInfo对象
     * @throws Exception SSH命令执行异常
     */
    private void tryAlpineRelease(CommandContext ctx, OsInfo info) throws Exception {
        String ver = execAndTrim(ctx, "cat /etc/alpine-release", "");
        setIfUnknown(info::getId, info::setId,          "alpine");
        setIfUnknown(info::getName, info::setName,      "Alpine Linux");
        setIfUnknown(info::getPrettyName, info::setPrettyName, "Alpine Linux " + ver);
        setIfUnknown(info::getVersionId, info::setVersionId, ver);
        setIfUnknown(info::getVersionCodename, info::setVersionCodename, "");
    }

    /**
     * 通过 /etc/issue 文件检测操作系统信息
     * @param ctx 命令上下文
     * @param info OsInfo对象
     * @throws Exception SSH命令执行异常
     */
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

    /**
     * 通过 uname 命令检测操作系统类型和版本
     * @param ctx 命令上下文
     * @param info OsInfo对象
     * @throws Exception SSH命令执行异常
     */
    private void tryUname(CommandContext ctx, OsInfo info) throws Exception {
        String os  = execAndTrim(ctx, "uname -s", "");
        String ver = execAndTrim(ctx, "uname -r", "");
        setIfUnknown(info::getId, info::setId, os.toLowerCase());
        setIfUnknown(info::getName, info::setName, os);
        setIfUnknown(info::getVersionId, info::setVersionId, ver);
    }

    // -------- KV 解析 --------

    /**
     * 解析 KEY=VALUE 格式字符串，默认分隔符为 "="
     * @param input 输入字符串
     * @return 解析后的 Map
     */
    private static Map<String, String> parseKeyValue(String input) {
        return parseKeyValue(input, "=");
    }

    /**
     * 解析 KEY 与 VALUE，指定分隔符
     * @param input 输入字符串
     * @param delimiter 分隔符
     * @return 解析后的 Map
     */
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
