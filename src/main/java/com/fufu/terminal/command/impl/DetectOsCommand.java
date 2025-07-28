package com.fufu.terminal.command.impl;

import com.fufu.terminal.command.Command;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.command.SshCommandUtil;
import com.fufu.terminal.command.model.OsInfo;
import com.fufu.terminal.command.model.enums.SystemType;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 检测远程服务器操作系统的命令
 * 采用多种策略，模仿 detect_os.sh 脚本的健壮性
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
     * 执行操作系统检测命令，依次尝试多种方式识别远程主机操作系统信息
     * 检测成功后将结果存入 context 属性
     * @param context 命令上下文，包含 SSH 连接等信息
     * @throws Exception 检测失败时抛出异常
     */
    @Override
    public void execute(CommandContext context) throws Exception {
        // 新建操作系统信息实体，初始值均为 unknown
        OsInfo osInfo = new OsInfo();

        // 获取系统架构信息
        osInfo.setArch(getArchitecture(context));

        // 按 shell 脚本顺序，逐步补全字段
        tryOsRelease(context, osInfo);
        tryLsbRelease(context, osInfo);
        tryLsbReleaseFile(context, osInfo);
        tryRedhatRelease(context, osInfo);
        trySuSeRelease(context, osInfo);
        tryAlpineRelease(context, osInfo);

        // Fallback: /etc/issue
        if (isUnknown(osInfo.getId())) {
            tryIssueFile(context, osInfo);
        }
        // Fallback: uname
        if (isUnknown(osInfo.getId())) {
            tryUname(context, osInfo);
        }

        // 确保所有字段都有默认值
        ensureDefaultValues(osInfo);
        osInfo.setSystemType(SystemType.fromId(osInfo.getId()));
        // 存入上下文
        context.setProperty(OS_INFO_KEY, osInfo);
    }

    /**
     * 判断字段值是否为 unknown 或空
     * @param value 字段值
     * @return 是否为 unknown
     */
    private boolean isUnknown(String value) {
        // 判断字符串是否为 null、空串或 unknown
        return value == null || value.isEmpty() || "unknown".equalsIgnoreCase(value);
    }

    /**
     * 确保所有操作系统信息字段都有默认值，避免缺失
     * @param osInfo 操作系统信息实体
     */
    private void ensureDefaultValues(OsInfo osInfo) {
        // 检查每个字段，如果为 unknown 或空则赋默认值
        if (isUnknown(osInfo.getId())) {
            osInfo.setId("unknown");
        }
        if (isUnknown(osInfo.getName())) {
            osInfo.setName("unknown");
        }
        if (isUnknown(osInfo.getPrettyName())) {
            osInfo.setPrettyName("unknown");
        }
        if (isUnknown(osInfo.getVersionId())) {
            osInfo.setVersionId("unknown");
        }
        if (isUnknown(osInfo.getVersionCodename())) {
            osInfo.setVersionCodename("unknown");
        }
        if (isUnknown(osInfo.getArch())) {
            osInfo.setArch("unknown");
        }
    }

    /**
     * 获取远程主机的架构信息（如 x86_64, arm64 等）
     * @param context 命令上下文
     * @return 架构字符串，失败时返回 "unknown"
     * @throws Exception SSH命令执行异常
     */
    private String getArchitecture(CommandContext context) throws Exception {
        // 执行 uname -m 获取系统架构
        CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), "uname -m");
        if (result.isSuccess()) {
            return result.getStdout().trim();
        } else {
            return "unknown";
        }
    }

    /**
     * 尝试通过 /etc/os-release 文件获取操作系统信息，只补全未填充字段
     * @param context 命令上下文
     * @param osInfo 操作系统信息实体
     * @throws Exception SSH命令执行异常
     */
    private void tryOsRelease(CommandContext context, OsInfo osInfo) throws Exception {
        // 读取 /etc/os-release 文件内容
        CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), "cat /etc/os-release");
        if (result.isSuccess()) {
            Map<String, String> parsed = parseKeyValue(result.getStdout());
            // 只补全未填充字段
            if (parsed.containsKey("ID") && isUnknown(osInfo.getId())) {
                osInfo.setId(parsed.get("ID").trim().toLowerCase());
            }
            if (parsed.containsKey("NAME") && isUnknown(osInfo.getName())) {
                osInfo.setName(parsed.get("NAME").trim());
            }
            if (parsed.containsKey("PRETTY_NAME") && isUnknown(osInfo.getPrettyName())) {
                osInfo.setPrettyName(parsed.get("PRETTY_NAME").trim());
            }
            if (parsed.containsKey("VERSION_ID") && isUnknown(osInfo.getVersionId())) {
                osInfo.setVersionId(parsed.get("VERSION_ID").trim());
            }
            // Ubuntu 兼容 UBUNTU_CODENAME
            if (parsed.containsKey("VERSION_CODENAME") && isUnknown(osInfo.getVersionCodename())) {
                osInfo.setVersionCodename(parsed.get("VERSION_CODENAME").trim());
            } else if ("ubuntu".equals(parsed.get("ID")) && parsed.containsKey("UBUNTU_CODENAME") && isUnknown(osInfo.getVersionCodename())) {
                osInfo.setVersionCodename(parsed.get("UBUNTU_CODENAME").trim());
            }
        }
    }

    /**
     * 尝试通过 lsb_release 命令获取操作系统信息，只补全未填充字段
     * @param context 命令上下文
     * @param osInfo 操作系统信息实体
     * @throws Exception SSH命令执行异常
     */
    private void tryLsbRelease(CommandContext context, OsInfo osInfo) throws Exception {
        // 分别执行 lsb_release 命令获取各字段
        CommandResult idResult = SshCommandUtil.executeCommand(context.getSshConnection(), "lsb_release -si 2>/dev/null");
        CommandResult descResult = SshCommandUtil.executeCommand(context.getSshConnection(), "lsb_release -sd 2>/dev/null");
        CommandResult releaseResult = SshCommandUtil.executeCommand(context.getSshConnection(), "lsb_release -sr 2>/dev/null");
        CommandResult codenameResult = SshCommandUtil.executeCommand(context.getSshConnection(), "lsb_release -sc 2>/dev/null");

        // 只补全未填充字段
        if (idResult.isSuccess() && !idResult.getStdout().trim().isEmpty() && isUnknown(osInfo.getId())) {
            osInfo.setId(idResult.getStdout().trim().toLowerCase());
            osInfo.setName(idResult.getStdout().trim());
        }
        if (descResult.isSuccess() && !descResult.getStdout().trim().isEmpty() && isUnknown(osInfo.getPrettyName())) {
            osInfo.setPrettyName(descResult.getStdout().trim());
        }
        if (releaseResult.isSuccess() && !releaseResult.getStdout().trim().isEmpty() && isUnknown(osInfo.getVersionId())) {
            osInfo.setVersionId(releaseResult.getStdout().trim());
        }
        if (codenameResult.isSuccess() && !codenameResult.getStdout().trim().isEmpty() && isUnknown(osInfo.getVersionCodename())) {
            osInfo.setVersionCodename(codenameResult.getStdout().trim());
        }
        // Ubuntu 特殊处理
        if ("ubuntu".equals(osInfo.getId()) && isUnknown(osInfo.getVersionCodename())) {
            CommandResult ubuntuCodenameResult = SshCommandUtil.executeCommand(
                    context.getSshConnection(), "grep -oP 'VERSION_CODENAME=\\K.*' /etc/os-release 2>/dev/null");
            if (ubuntuCodenameResult.isSuccess() && !ubuntuCodenameResult.getStdout().trim().isEmpty()) {
                osInfo.setVersionCodename(ubuntuCodenameResult.getStdout().trim());
            }
        }
    }

    /**
     * 尝试通过 /etc/lsb-release 文件获取操作系统信息，只补全未填充字段
     * @param context 命令上下文
     * @param osInfo 操作系统信息实体
     * @throws Exception SSH命令执行异常
     */
    private void tryLsbReleaseFile(CommandContext context, OsInfo osInfo) throws Exception {
        // 读取 /etc/lsb-release 文件内容
        CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), "cat /etc/lsb-release");
        if (result.isSuccess()) {
            Map<String, String> parsed = parseKeyValue(result.getStdout(), "=");
            // 只补全未填充字段
            if (parsed.containsKey("DISTRIB_ID") && isUnknown(osInfo.getId())) {
                osInfo.setId(parsed.get("DISTRIB_ID").trim().toLowerCase());
                osInfo.setName(parsed.get("DISTRIB_ID").trim());
            }
            if (parsed.containsKey("DISTRIB_RELEASE") && isUnknown(osInfo.getVersionId())) {
                osInfo.setVersionId(parsed.get("DISTRIB_RELEASE").trim());
            }
            if (parsed.containsKey("DISTRIB_CODENAME") && isUnknown(osInfo.getVersionCodename())) {
                osInfo.setVersionCodename(parsed.get("DISTRIB_CODENAME").trim());
            }
            if (parsed.containsKey("DISTRIB_DESCRIPTION") && isUnknown(osInfo.getPrettyName())) {
                osInfo.setPrettyName(parsed.get("DISTRIB_DESCRIPTION").trim());
            }
        }
    }

    /**
     * 尝试通过 /etc/redhat-release 文件获取 RedHat/CentOS 操作系统信息，只补全未填充字段
     * @param context 命令上下文
     * @param osInfo 操作系统信息实体
     * @throws Exception SSH命令执行异常
     */
    private void tryRedhatRelease(CommandContext context, OsInfo osInfo) throws Exception {
        // 读取 /etc/redhat-release 文件内容
        CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), "cat /etc/redhat-release");
        if (result.isSuccess()) {
            String output = result.getStdout().trim();
            // 只补全未填充字段
            if (isUnknown(osInfo.getPrettyName())) {
                osInfo.setPrettyName(output);
            }
            if (isUnknown(osInfo.getName())) {
                String name = output.split(" ")[0];
                osInfo.setName(name);
            }
            if (isUnknown(osInfo.getId())) {
                String name = osInfo.getName();
                osInfo.setId(name.toLowerCase());
            }
            if (isUnknown(osInfo.getVersionId())) {
                // 提取第一个数字串作为版本号
                String version = output.replaceAll("[^0-9.]", " ").trim().split(" ")[0];
                osInfo.setVersionId(version);
            }
            if (isUnknown(osInfo.getVersionCodename())) {
                osInfo.setVersionCodename("");
            }
        }
    }

    /**
     * 尝试通过 /etc/SuSE-release 文件获取 SUSE 操作系统信息，只补全未填充字段
     * @param context 命令上下文
     * @param osInfo 操作系统信息实体
     * @throws Exception SSH命令执行异常
     */
    private void trySuSeRelease(CommandContext context, OsInfo osInfo) throws Exception {
        // 读取 /etc/SuSE-release 文件内容
        CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), "cat /etc/SuSE-release");
        if (result.isSuccess()) {
            String output = result.getStdout();
            // 只补全未填充字段
            if (isUnknown(osInfo.getId())) {
                osInfo.setId("suse");
            }
            if (isUnknown(osInfo.getName())) {
                osInfo.setName("SUSE");
            }
            if (isUnknown(osInfo.getPrettyName())) {
                osInfo.setPrettyName("SUSE " + output.trim());
            }
            if (isUnknown(osInfo.getVersionId())) {
                // 提取 VERSION 行的第二个字段
                String version = Arrays.stream(output.split("\n"))
                        .filter(line -> line.startsWith("VERSION"))
                        .findFirst()
                        .map(line -> {
                            String[] parts = line.split("\\s+");
                            if (parts.length > 1) {
                                return parts[1].trim();
                            } else {
                                return "";
                            }
                        })
                        .orElse("");
                osInfo.setVersionId(version);
            }
            if (isUnknown(osInfo.getVersionCodename())) {
                osInfo.setVersionCodename("");
            }
        }
    }

    /**
     * 尝试通过 /etc/alpine-release 文件获取 Alpine Linux 操作系统信息，只补全未填充字段
     * @param context 命令上下文
     * @param osInfo 操作系统信息实体
     * @throws Exception SSH命令执行异常
     */
    private void tryAlpineRelease(CommandContext context, OsInfo osInfo) throws Exception {
        // 读取 /etc/alpine-release 文件内容
        CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), "cat /etc/alpine-release");
        if (result.isSuccess()) {
            String version = result.getStdout().trim();
            // 只补全未填充字段
            if (isUnknown(osInfo.getId())) {
                osInfo.setId("alpine");
            }
            if (isUnknown(osInfo.getName())) {
                osInfo.setName("Alpine Linux");
            }
            if (isUnknown(osInfo.getPrettyName())) {
                osInfo.setPrettyName("Alpine Linux " + version);
            }
            if (isUnknown(osInfo.getVersionId())) {
                osInfo.setVersionId(version);
            }
            if (isUnknown(osInfo.getVersionCodename())) {
                osInfo.setVersionCodename("");
            }
        }
    }

    /**
     * 尝试通过 /etc/issue 文件获取操作系统信息，只补全未填充字段
     * @param context 命令上下文
     * @param osInfo 操作系统信息实体
     * @throws Exception SSH命令执行异常
     */
    private void tryIssueFile(CommandContext context, OsInfo osInfo) throws Exception {
        // 读取 /etc/issue 第一行内容
        CommandResult result = SshCommandUtil.executeCommand(context.getSshConnection(), "head -1 /etc/issue | tr -d '\\\\l'");
        if (result.isSuccess()) {
            String issue = result.getStdout().trim();
            if (!issue.isEmpty()) {
                if (isUnknown(osInfo.getPrettyName())) {
                    osInfo.setPrettyName(issue);
                }
                String name = issue.split(" ")[0];
                if (isUnknown(osInfo.getName())) {
                    osInfo.setName(name);
                }
                if (isUnknown(osInfo.getId())) {
                    osInfo.setId(name.toLowerCase());
                }
            }
        }
    }

    /**
     * 尝试通过 uname 命令获取操作系统类型和版本信息，只补全未填充字段
     * @param context 命令上下文
     * @param osInfo 操作系统信息实体
     * @throws Exception SSH命令执行异常
     */
    private void tryUname(CommandContext context, OsInfo osInfo) throws Exception {
        // 执行 uname -s 获取系统类型
        CommandResult osResult = SshCommandUtil.executeCommand(context.getSshConnection(), "uname -s");
        // 执行 uname -r 获取系统版本
        CommandResult versionResult = SshCommandUtil.executeCommand(context.getSshConnection(), "uname -r");

        if (osResult.isSuccess()) {
            String osName = osResult.getStdout().trim();
            if (isUnknown(osInfo.getId())) {
                osInfo.setId(osName.toLowerCase());
            }
            if (isUnknown(osInfo.getName())) {
                osInfo.setName(osName);
            }
            if (versionResult.isSuccess() && isUnknown(osInfo.getVersionId())) {
                osInfo.setVersionId(versionResult.getStdout().trim());
            }
        }
    }

    /**
     * 解析类似 KEY=VALUE 格式的字符串为 Map
     * @param input 输入字符串
     * @param delimiter 分隔符，通常为 "="
     * @return 解析后的 Map
     */
    private Map<String, String> parseKeyValue(String input, String delimiter) {
        // 按行分割，解析每一行的 key 和 value
        return Arrays.stream(input.split("\n"))
                .filter(line -> line.contains(delimiter))
                .map(line -> line.split(delimiter, 2))
                .collect(Collectors.toMap(parts -> parts[0].trim(), parts -> parts.length > 1 ? parts[1].trim().replace("\"", "") : ""));
    }

    /**
     * 解析 KEY=VALUE 格式字符串，默认分隔符为 "="
     * @param input 输入字符串
     * @return 解析后的 Map
     */
    private Map<String, String> parseKeyValue(String input) {
        // 默认使用等号作为分隔符
        return parseKeyValue(input, "=");
    }
}
