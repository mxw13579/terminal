package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.dto.sillytavern.SystemInfoDto;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 系统检测服务，负责检测目标主机的操作系统、Docker、资源等环境信息，
 * 并判断是否满足SillyTavern容器部署的基本要求。
 *
 * @author
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemDetectionService {

    private final SshCommandService sshCommandService;

    private static final long MIN_DISK_SPACE_MB = 500;
    private static final long MIN_MEMORY_MB = 512;

    /**
     * 校验目标主机是否满足SillyTavern部署的基本系统要求。
     *
     * @param connection SSH连接信息
     * @return 系统信息DTO，包含检测结果与建议
     */
    public SystemInfoDto validateSystemRequirements(SshConnection connection) {
        log.info("开始校验SillyTavern系统部署要求");
        SystemInfoDto systemInfo = new SystemInfoDto();
        List<String> checks = new ArrayList<>();

        try {
            SystemInfo sysEnv = detectSystemEnvironmentSync(connection);
            log.info("系统环境信息:{}", sysEnv);

            systemInfo.setOsType(sysEnv.getOsType());
            checks.add("✓ 检测到操作系统: " + sysEnv.getOsType());

            systemInfo.setDockerInstalled(sysEnv.isDockerInstalled());
            systemInfo.setDockerVersion(sysEnv.getDockerVersion());
            checks.add(sysEnv.isDockerInstalled() ? "✓ Docker可用: " + sysEnv.getDockerVersion() : "✗ 未检测到Docker");

            systemInfo.setHasRootAccess(sysEnv.isHasRootAccess());
            checks.add(sysEnv.isHasRootAccess() ? "✓ 拥有sudo权限" : "✗ 没有sudo权限");

            systemInfo.setAvailableDiskSpaceMB(sysEnv.getAvailableDiskSpaceMB());
            checks.add(formatDiskCheck(sysEnv.getAvailableDiskSpaceMB()));

            systemInfo.setTotalMemoryMB(sysEnv.getTotalMemoryMB());
            systemInfo.setAvailableMemoryMB(sysEnv.getAvailableMemoryMB());
            checks.add(formatMemoryCheck(sysEnv.getAvailableMemoryMB()));

            systemInfo.setCpuCores(sysEnv.getCpuCores());
            checks.add(sysEnv.getCpuCores() != null && sysEnv.getCpuCores() > 0
                    ? "✓ CPU核心数: " + sysEnv.getCpuCores()
                    : "? 无法获取CPU信息");

            boolean meetsRequirements = sysEnv.isHasRootAccess() &&
                    Optional.ofNullable(sysEnv.getAvailableDiskSpaceMB()).orElse(0L) >= MIN_DISK_SPACE_MB;

            systemInfo.setMeetsRequirements(meetsRequirements);
            systemInfo.setRequirementChecks(checks);

            log.info("系统要求校验完成，是否满足要求: {}", meetsRequirements);

        } catch (Exception e) {
            log.error("系统要求校验异常", e);
            checks.add("✗ 校验过程中发生异常: " + e.getMessage());
            systemInfo.setMeetsRequirements(false);
            systemInfo.setRequirementChecks(checks);
        }
        return systemInfo;
    }


    private String formatDiskCheck(Long diskSpace) {
        if (diskSpace == null) {
            return "? 无法获取磁盘空间信息";
        }
        return diskSpace >= MIN_DISK_SPACE_MB
                ? "✓ 磁盘空间充足: " + formatDiskSpace(diskSpace)
                : "✗ 磁盘空间不足: " + formatDiskSpace(diskSpace) + "，至少需要 " + formatDiskSpace(MIN_DISK_SPACE_MB);
    }
    private String formatMemoryCheck(Long availableMemory) {
        if (availableMemory == null) {
            return "? 无法获取内存信息";
        }
        return availableMemory >= MIN_MEMORY_MB
                ? "✓ 可用内存充足: " + formatDiskSpace(availableMemory) + " "
                : "✗ 可用内存不足: " + formatDiskSpace(availableMemory) + " MB，至少需要 " + MIN_MEMORY_MB + " MB";
    }


    /**
     * 检查指定端口列表在目标主机上的可用性。
     *
     * @param connection SSH连接信息
     * @param ports      需要检测的端口列表
     * @return 端口可用性映射，true表示可用
     */
    public Map<Integer, Boolean> checkPortAvailability(SshConnection connection, List<Integer> ports) {
        if (ports == null || ports.isEmpty()) return Collections.emptyMap();
        // 并行流处理，提升大批量端口检测效率
        return ports.parallelStream().collect(Collectors.toMap(
                port -> port,
                port -> {
                    try {
                        String cmd = String.format("netstat -ln | grep -q ':%d ' && echo 'used' || echo 'available'", port);
                        String result = executeCommand(connection, cmd).trim();
                        return "available".equals(result);
                    } catch (Exception e) {
                        log.warn("端口 {} 可用性检测失败: {}", port, e.getMessage());
                        return false;
                    }
                }
        ));
    }

    /**
     * 异步检测系统环境详细信息
     *
     * @param connection SSH连接信息
     * @return 异步返回系统详细信息
     */
    public CompletableFuture<SystemInfo> detectSystemEnvironment(SshConnection connection) {
        return CompletableFuture.supplyAsync(() -> detectSystemEnvironmentSync(connection));
    }

    /**
     * 同步检测系统环境详细信息，包括操作系统、发行版、资源等。
     *
     * @param connection SSH连接信息
     * @return 系统详细信息
     */
    public SystemInfo detectSystemEnvironmentSync(SshConnection connection) {
        log.info("检测系统环境详细信息");
        SystemInfo.SystemInfoBuilder builder = SystemInfo.builder();
        try {
            builder.osType(detectOsType(connection))
                    .dockerInstalled(checkDockerAvailability(connection))
                    .hasRootAccess(checkSudoAccess(connection));
            detectDistributionInfo(connection, builder);
            builder.availableDiskSpaceMB(getAvailableDiskSpace(connection))
                    .cpuCores(getCpuCoreCount(connection));
            Map<String, Long> memoryInfo = getMemoryInfo(connection);
            if (memoryInfo != null) {
                builder.totalMemoryMB(memoryInfo.get("total"))
                        .availableMemoryMB(memoryInfo.get("available"));
            }
            if (builder.build().isDockerInstalled()) {
                builder.dockerVersion(getDockerVersion(connection));
            }
            return builder.build();
        } catch (Exception e) {
            log.error("检测系统环境失败", e);
            return builder.osType("Unknown")
                    .dockerInstalled(false)
                    .hasRootAccess(false)
                    .build();
        }
    }

    /**
     * 检测Linux发行版详细信息，优先解析 /etc/os-release。
     *
     * @param connection SSH连接信息
     * @param builder    系统信息构建器
     */
    private void detectDistributionInfo(SshConnection connection, SystemInfo.SystemInfoBuilder builder) {
        try {
            String osRelease = executeCommand(connection, "cat /etc/os-release 2>/dev/null || echo ''");
            if (org.springframework.util.StringUtils.hasText(osRelease)) {
                parseOsRelease(osRelease, builder);
                return;
            }
            String lsb = executeCommand(connection, "cat /etc/lsb-release 2>/dev/null || echo ''");
            if (org.springframework.util.StringUtils.hasText(lsb)) {
                parseLsbRelease(lsb, builder);
                return;
            }
            // 优化：统一检测包管理器，减少命令执行次数
            String pkgManager = getFirstAvailableCommand(connection, List.of("apt-get", "yum", "dnf"));
            if ("apt-get".equals(pkgManager)) {
                builder.osId("ubuntu").distro("Ubuntu");
                try {
                    builder.osVersionCodename(executeCommand(connection, "lsb_release -cs 2>/dev/null").trim());
                } catch (Exception ignore) {}
                return;
            } else if ("yum".equals(pkgManager)) {
                builder.osId("centos").distro("CentOS");
                return;
            } else if ("dnf".equals(pkgManager)) {
                builder.osId("fedora").distro("Fedora");
                return;
            }
            // 兼容RedHat、SUSE、Alpine等
            String redhat = executeCommand(connection, "cat /etc/redhat-release 2>/dev/null || echo ''").trim();
            if (!redhat.isEmpty()) {
                builder.distro(redhat);
                String name = redhat.split("\\s+")[0];
                builder.osId(name.toLowerCase());
                String ver = redhat.replaceAll("^.*?([0-9]+(\\.[0-9]+)*).*$", "$1");
                builder.osVersionId(ver);
                return;
            }
            String suse = executeCommand(connection, "cat /etc/SuSE-release 2>/dev/null || echo ''").trim();
            if (!suse.isEmpty()) {
                builder.osId("suse").distro("SUSE");
                String vid = executeCommand(connection, "grep VERSION /etc/SuSE-release | head -1 | awk '{print $3}'").trim();
                builder.osVersionId(vid);
                return;
            }
            String alpine = executeCommand(connection, "cat /etc/alpine-release 2>/dev/null || echo ''").trim();
            if (!alpine.isEmpty()) {
                builder.osId("alpine")
                        .distro("Alpine Linux " + alpine)
                        .osVersionId(alpine);
                return;
            }
            String issue = executeCommand(connection, "head -1 /etc/issue 2>/dev/null || echo ''").trim();
            if (!issue.isEmpty()) {
                String name = issue.split("\\s+")[0];
                builder.osId(name.toLowerCase());
                builder.distro(issue.replaceAll("\\\\l", ""));
                return;
            }
            String unameS = executeCommand(connection, "uname -s").trim();
            builder.osId(unameS.toLowerCase())
                    .distro(unameS)
                    .osVersionId(executeCommand(connection, "uname -r").trim());
        } catch (Exception e) {
            log.warn("检测发行版信息失败: {}", e.getMessage());
        }
    }

    /**
     * 解析 /etc/os-release 文件内容，填充系统信息构建器。
     *
     * @param content 文件内容
     * @param builder 系统信息构建器
     */
    private void parseOsRelease(String content, SystemInfo.SystemInfoBuilder builder) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.startsWith("ID=")) {
                builder.osId(line.substring(3).replaceAll("\"", ""));
            } else if (line.startsWith("VERSION_ID=")) {
                builder.osVersionId(line.substring(11).replaceAll("\"", ""));
            } else if (line.startsWith("VERSION_CODENAME=")) {
                builder.osVersionCodename(line.substring(17).replaceAll("\"", ""));
            } else if (line.startsWith("PRETTY_NAME=")) {
                builder.distro(line.substring(12).replaceAll("\"", ""));
            }
        }
    }

    /**
     * 检测操作系统类型。
     *
     * @param connection SSH连接信息
     * @return 操作系统类型，如Linux、macOS等
     */
    private String detectOsType(SshConnection connection) {
        try {
            String result = executeCommand(connection, "uname -s").trim().toLowerCase();
            if (result.contains("linux")) {
                return "Linux";
            }
            if (result.contains("darwin")) {
                return "macOS";
            }
            return result.isEmpty() ? "Unknown" : result;
        } catch (Exception e) {
            log.warn("操作系统类型检测失败: {}", e.getMessage());
            return "Unknown";
        }
    }

    /**
     * 检查Docker是否可用（支持sudo和非sudo）。
     *
     * @param connection SSH连接信息
     * @return true表示Docker可用
     */
    private boolean checkDockerAvailability(SshConnection connection) {
        try {
            try {
                String result = executeCommand(connection, "docker --version");
                if (result.toLowerCase().contains("docker version")) {
                    log.debug("Docker可用（无需sudo）");
                    return true;
                }
            } catch (Exception e) {
                log.debug("Docker（无需sudo）不可用: {}", e.getMessage());
            }
            try {
                String result = executeCommand(connection, "sudo docker --version");
                if (result.toLowerCase().contains("docker version")) {
                    log.debug("Docker可用（需sudo）");
                    return true;
                }
            } catch (Exception e) {
                log.debug("Docker（需sudo）不可用: {}", e.getMessage());
            }
            try {
                String result = executeCommand(connection, "command -v docker");
                if (!result.trim().isEmpty()) {
                    String checkExec = executeCommand(connection, "[ -x \"" + result.trim() + "\" ] && echo 'yes' || echo 'no'");
                    if ("yes".equals(checkExec.trim())) {
                        log.info("检测到docker命令路径: {}", result.trim());
                        return false;
                    }
                }
            } catch (Exception e) {
                log.debug("Docker路径检测失败: {}", e.getMessage());
            }
            return false;
        } catch (Exception e) {
            log.debug("Docker可用性检测失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取Docker版本信息。
     *
     * @param connection SSH连接信息
     * @return Docker版本描述字符串
     */
    private String getDockerVersion(SshConnection connection) {
        try {
            try {
                String result = executeCommand(connection, "docker --version");
                if (result.toLowerCase().contains("docker version")) {
                    return result.trim();
                }
            } catch (Exception e) {
                log.debug("获取Docker版本（无需sudo）失败: {}", e.getMessage());
            }
            return executeCommand(connection, "sudo docker --version").trim();
        } catch (Exception e) {
            log.debug("获取Docker版本失败: {}", e.getMessage());
            return "Unknown";
        }
    }

    /**
     * 检查是否拥有sudo权限。
     *
     * @param connection SSH连接信息
     * @return true表示有sudo权限
     */
    private boolean checkSudoAccess(SshConnection connection) {
        try {
            String result = executeCommand(connection, "sudo -n true 2>&1").toLowerCase();
            return !(result.contains("password") || result.contains("sorry"));
        } catch (Exception e) {
            log.debug("sudo权限检测失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取可用磁盘空间（单位：MB）。
     *
     * @param connection SSH连接信息
     * @return 可用磁盘空间，null表示获取失败
     */
    private Long getAvailableDiskSpace(SshConnection connection) {
        final String[] commands = {
                "df -m . | tail -1 | awk '{print $4}'",
                "df -BM . | tail -1 | awk '{print $4}' | sed 's/M//'",
                "df . | tail -1 | awk '{print $4}'"
        };
        for (String cmd : commands) {
            try {
                String result = executeCommand(connection, cmd).trim();
                if (!result.isEmpty() && result.matches("\\d+")) {
                    return cmd.startsWith("df .") ? Long.parseLong(result) / 1024 : Long.parseLong(result);
                }
            } catch (Exception e) {
                log.debug("磁盘空间检测命令失败: {} - {}", cmd, e.getMessage());
            }
        }
        try {
            String debugDf = executeCommand(connection, "df -h .");
            log.info("df -h . 输出: {}", debugDf);
            String[] lines = debugDf.split("\n");
            if (lines.length >= 2) {
                String[] fields = lines[1].trim().split("\\s+");
                if (fields.length >= 4) {
                    String avail = fields[3];
                    if (avail.endsWith("G")) return (long) (Double.parseDouble(avail.replace("G", "")) * 1024);
                    if (avail.endsWith("M")) return Long.parseLong(avail.replace("M", ""));
                    if (avail.endsWith("K")) return Long.parseLong(avail.replace("K", "")) / 1024;
                }
            }
        } catch (Exception ignored) {
        }
        log.warn("无法获取磁盘空间信息");
        return null;
    }

    private void parseLsbRelease(String lsb, SystemInfo.SystemInfoBuilder builder) {
        for (String line : lsb.split("\\R")) {
            if (line.startsWith("DISTRIB_ID=")) {
                builder.osId(line.split("=", 2)[1].replaceAll("\"", "").toLowerCase());
            } else if (line.startsWith("DISTRIB_RELEASE=")) {
                builder.osVersionId(line.split("=", 2)[1].replaceAll("\"", ""));
            } else if (line.startsWith("DISTRIB_CODENAME=")) {
                builder.osVersionCodename(line.split("=", 2)[1].replaceAll("\"", ""));
            } else if (line.startsWith("DISTRIB_DESCRIPTION=")) {
                builder.distro(line.split("=", 2)[1].replaceAll("\"", ""));
            }
        }
    }

    private String getFirstAvailableCommand(SshConnection connection, List<String> commands) {
        for (String cmd : commands) {
            try {
                if (executeCommand(connection, "which " + cmd).contains(cmd)) {
                    return cmd;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 获取内存信息（单位：MB），优先使用 free -m，失败则解析 /proc/meminfo。
     *
     * @param connection SSH连接信息
     * @return Map，包含total和available，获取失败返回null
     */
    private Map<String, Long> getMemoryInfo(SshConnection connection) {
        try {
            String result = executeCommand(connection, "free -m | grep '^Mem:' | awk '{print $2 \" \" $7}'").trim();
            String[] parts = result.split("\\s+");
            if (parts.length >= 2) {
                return Map.of("total", Long.parseLong(parts[0]), "available", Long.parseLong(parts[1]));
            }
        } catch (Exception e) {
            log.warn("获取内存信息失败（free -m）: {}", e.getMessage());
        }
        try {
            String meminfo = executeCommand(connection, "cat /proc/meminfo");
            long total = 0L, available = 0L;
            for (String line : meminfo.split("\n")) {
                if (line.startsWith("MemTotal:")) total = Long.parseLong(line.replaceAll("\\D+", ""));
                if (line.startsWith("MemAvailable:")) available = Long.parseLong(line.replaceAll("\\D+", ""));
            }
            if (total > 0 && available > 0) {
                return Map.of("total", total / 1024, "available", available / 1024);
            }
        } catch (Exception e) {
            log.warn("获取内存信息失败（/proc/meminfo）: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取CPU核心数。
     *
     * @param connection SSH连接信息
     * @return 核心数，获取失败返回null
     */
    private Integer getCpuCoreCount(SshConnection connection) {
        try {
            String result = executeCommand(connection, "nproc");
            return Integer.parseInt(result.trim());
        } catch (Exception e) {
            log.warn("无法获取CPU核心数", e);
            return null;
        }
    }

    /**
     * 执行SSH命令，返回标准输出内容。
     *
     * @param connection SSH连接信息
     * @param command    要执行的命令
     * @return 命令标准输出
     * @throws Exception 命令执行异常
     */
    private String executeCommand(SshConnection connection, String command) throws Exception {
        try {
            CommandResult result = sshCommandService.executeCommand(connection.getJschSession(), command);
            if (result.exitStatus() != 0) {
                log.debug("命令非零返回码 {}: {} - {}", result.exitStatus(), command, result.stderr());
                return result.stdout();
            }
            return result.stdout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("命令执行被中断: " + command, e);
        }
    }

    /**
     * 格式化磁盘空间，超过1024MB时以GB为单位，保留两位小数，否则以MB为单位。
     *
     * @param mb 磁盘空间（MB）
     * @return 格式化后的字符串
     */
    private String formatDiskSpace(Long mb) {
        if (mb == null) {
            return "未知";
        }
        if (mb >= 1024) {
            double gb = mb / 1024.0;
            return String.format("%.2f GB", gb);
        }
        return mb + " MB";
    }

    /**
     * 检查Docker是否已安装
     *
     * @param connection SSH连接信息
     * @return 是否已安装Docker
     */
    public boolean checkDockerInstallation(SshConnection connection) {
        return checkDockerAvailability(connection);
    }

    /**
     * 检测Linux发行版类型
     *
     * @param connection SSH连接信息
     * @return Linux发行版ID
     */
    public String detectLinuxDistribution(SshConnection connection) {
        SystemInfo systemInfo = detectSystemEnvironmentSync(connection);
        return systemInfo.getOsId();
    }

    /**
     * 检查网络连接状态
     *
     * @param connection SSH连接信息
     * @return 是否有网络连接
     */
    public boolean checkInternetConnectivity(SshConnection connection) {
        try {
            CommandResult result = sshCommandService.executeCommand(
                    connection.getJschSession(),
                    "ping -c 1 -W 5 8.8.8.8 > /dev/null 2>&1"
            );
            return result.exitStatus() == 0;
        } catch (Exception e) {
            log.debug("网络连接检查失败", e);
            return false;
        }
    }

    /**
     * 检查指定端口是否可用
     *
     * @param connection SSH连接信息
     * @param port 端口号
     * @return 端口是否可用
     */
    public boolean checkPortAvailability(SshConnection connection, int port) {
        try {
            CommandResult result = sshCommandService.executeCommand(
                    connection.getJschSession(),
                    String.format("netstat -ln | grep -q ':%d ' && echo 'used' || echo 'available'", port)
            );
            return result.exitStatus() == 0 && "available".equals(result.stdout().trim());
        } catch (Exception e) {
            log.debug("端口{}可用性检查失败", port, e);
            return false;
        }
    }

    /**
     * 获取系统资源信息
     *
     * @param connection SSH连接信息
     * @return 系统资源信息映射
     */
    public Map<String, String> getSystemResources(SshConnection connection) {
        Map<String, String> resources = new HashMap<>();
        try {
            SystemInfo systemInfo = detectSystemEnvironmentSync(connection);

            resources.put("osType", systemInfo.getOsType());
            resources.put("osId", systemInfo.getOsId());
            resources.put("distro", systemInfo.getDistro());
            resources.put("version", systemInfo.getOsVersionId());
            resources.put("codename", systemInfo.getOsVersionCodename());
            resources.put("cpuCores", String.valueOf(systemInfo.getCpuCores()));
            resources.put("totalMemoryMB", String.valueOf(systemInfo.getTotalMemoryMB()));
            resources.put("availableMemoryMB", String.valueOf(systemInfo.getAvailableMemoryMB()));
            resources.put("availableDiskSpaceMB", String.valueOf(systemInfo.getAvailableDiskSpaceMB()));
            resources.put("dockerInstalled", String.valueOf(systemInfo.isDockerInstalled()));
            resources.put("dockerVersion", systemInfo.getDockerVersion());
            resources.put("hasRootAccess", String.valueOf(systemInfo.isHasRootAccess()));

        } catch (Exception e) {
            log.error("获取系统资源信息失败", e);
            resources.put("error", e.getMessage());
        }
        return resources;
    }

    /**
     * 系统信息数据对象，用于服务间传递系统检测结果。
     */
    @Data
    @Builder
    public static class SystemInfo {
        /**
         * 操作系统类型（如 Linux、macOS、Unknown）
         */
        private String osType;

        /**
         * 操作系统发行版名称（如 Ubuntu、CentOS、Alpine Linux 3.18.0 等）
         */
        private String distro;

        /**
         * 操作系统版本号（如 22.04、7.9.2009 等）
         */
        private String version;

        /**
         * 系统架构（如 x86_64、arm64 等）
         */
        private String architecture;

        /**
         * 操作系统ID（如 ubuntu、centos、alpine 等，通常来自 /etc/os-release 的 ID 字段）
         */
        private String osId;

        /**
         * 操作系统版本ID（如 22.04、7、3.18.0 等，通常来自 /etc/os-release 的 VERSION_ID 字段）
         */
        private String osVersionId;

        /**
         * 操作系统版本代号（如 jammy、bionic、focal，通常来自 /etc/os-release 的 VERSION_CODENAME 字段）
         */
        private String osVersionCodename;

        /**
         * 是否已安装Docker
         */
        private boolean dockerInstalled;

        /**
         * Docker版本描述（如 Docker version 20.10.24, build 297e128）
         */
        private String dockerVersion;

        /**
         * 是否拥有root（sudo）权限
         */
        private boolean hasRootAccess;

        /**
         * CPU核心数
         */
        private Integer cpuCores;

        /**
         * 总内存（单位：MB）
         */
        private Long totalMemoryMB;

        /**
         * 可用内存（单位：MB）
         */
        private Long availableMemoryMB;

        /**
         * 可用磁盘空间（单位：MB）
         */
        private Long availableDiskSpaceMB;

        /**
         * 环境变量（可选，key为变量名，value为变量值）
         */
        private Map<String, String> environmentVariables;

        /**
         * 已安装的软件包列表（可选）
         */
        private List<String> installedPackages;

        /**
         * 端口可用性映射（key为端口号，value为true表示可用）
         */
        private Map<Integer, Boolean> portAvailability;

        /**
         * 判断是否满足基本部署要求。
         *
         * @return true表示满足
         */
        public boolean meetsBasicRequirements() {
            return dockerInstalled && hasRootAccess &&
                    (availableDiskSpaceMB != null && availableDiskSpaceMB >= MIN_DISK_SPACE_MB) &&
                    (availableMemoryMB != null && availableMemoryMB >= MIN_MEMORY_MB);
        }

        /**
         * 获取系统摘要信息。
         *
         * @return 摘要字符串
         */
        public String getSystemSummary() {
            return String.format("%s %s (%s) - %d cores, %d MB RAM",
                    osType != null ? osType : "Unknown OS",
                    distro != null ? distro : "Unknown Distro",
                    architecture != null ? architecture : "Unknown Arch",
                    cpuCores != null ? cpuCores : 0,
                    totalMemoryMB != null ? totalMemoryMB : 0);
        }

        /**
         * 获取操作系统类型
         * @return 操作系统类型
         */
        public String getOsType() {
            return osType;
        }

        /**
         * 获取系统架构
         * @return 系统架构
         */
        public String getArchitecture() {
            return architecture;
        }
    }

}
