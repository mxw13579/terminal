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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统检测服务，负责检测目标主机的操作系统、Docker、资源等环境信息，
 * 并判断是否满足SillyTavern容器部署的基本要求。
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
        List<String> warnings = new ArrayList<>();

        try {
            // 检测操作系统类型
            systemInfo.setOsType(detectOsType(connection));
            checks.add("✓ 检测到操作系统: " + systemInfo.getOsType());

            // 检查Docker可用性
            if (checkDockerAvailability(connection)) {
                systemInfo.setDockerInstalled(true);
                systemInfo.setDockerVersion(getDockerVersion(connection));
                checks.add("✓ Docker可用: " + systemInfo.getDockerVersion());
            } else {
                systemInfo.setDockerInstalled(false);
                checks.add("✗ 未检测到Docker");
                warnings.add("Docker需要安装。请运行以下命令安装Docker:");
                warnings.add("curl -fsSL https://get.docker.com -o get-docker.sh");
                warnings.add("sudo sh get-docker.sh");
                warnings.add("或访问 https://docs.docker.com/engine/install/ 查看安装指南");
            }

            // 检查sudo权限
            if (checkSudoAccess(connection)) {
                systemInfo.setHasRootAccess(true);
                checks.add("✓ 拥有sudo权限");
            } else {
                systemInfo.setHasRootAccess(false);
                checks.add("✗ 没有sudo权限");
            }

            // 检查磁盘空间
            Long diskSpace = getAvailableDiskSpace(connection);
            systemInfo.setAvailableDiskSpaceMB(diskSpace);
            if (diskSpace != null && diskSpace >= MIN_DISK_SPACE_MB) {
                checks.add("✓ 磁盘空间充足: " + formatDiskSpace(diskSpace));
            } else {
                checks.add("✗ 磁盘空间不足: " + formatDiskSpace(diskSpace) + "，至少需要 " + formatDiskSpace(MIN_DISK_SPACE_MB) );
            }

            // 检查内存
            Map<String, Long> memoryInfo = getMemoryInfo(connection);
            if (memoryInfo != null) {
                systemInfo.setTotalMemoryMB(memoryInfo.get("total"));
                systemInfo.setAvailableMemoryMB(memoryInfo.get("available"));
                if (memoryInfo.get("available") >= MIN_MEMORY_MB) {
                    checks.add("✓ 可用内存充足: " + formatDiskSpace(memoryInfo.get("available")) + " MB");
                } else {
                    checks.add("✗ 可用内存不足: " + formatDiskSpace(memoryInfo.get("available")) + " MB，至少需要 " + MIN_MEMORY_MB + " MB");
                }
            } else {
                checks.add("? 无法获取内存信息");
            }

            // 检查CPU核心数
            Integer cpuCores = getCpuCoreCount(connection);
            systemInfo.setCpuCores(cpuCores);
            if (cpuCores != null && cpuCores > 0) {
                checks.add("✓ CPU核心数: " + cpuCores);
            } else {
                checks.add("? 无法获取CPU信息");
            }

            // 判断是否满足要求
            boolean meetsRequirements = systemInfo.getDockerInstalled() &&
                    systemInfo.getHasRootAccess() &&
                    (diskSpace != null && diskSpace >= MIN_DISK_SPACE_MB);

            systemInfo.setMeetsRequirements(meetsRequirements);
            systemInfo.setRequirementChecks(checks);
            systemInfo.setWarnings(warnings);

            log.info("系统要求校验完成，是否满足要求: {}", meetsRequirements);

        } catch (Exception e) {
            log.error("系统要求校验异常", e);
            checks.add("✗ 校验过程中发生异常: " + e.getMessage());
            systemInfo.setMeetsRequirements(false);
            systemInfo.setRequirementChecks(checks);
        }

        return systemInfo;
    }

    /**
     * 检查指定端口列表在目标主机上的可用性。
     *
     * @param connection SSH连接信息
     * @param ports      需要检测的端口列表
     * @return 端口可用性映射，true表示可用
     */
    public Map<Integer, Boolean> checkPortAvailability(SshConnection connection, List<Integer> ports) {
        if (ports == null || ports.isEmpty()) {
            return Collections.emptyMap(); // 更安全的空Map
        }
        Map<Integer, Boolean> availability = new HashMap<>();
        for (Integer port : ports) {
            try {
                String cmd = String.format("netstat -ln | grep -q ':%d ' && echo 'used' || echo 'available'", port);
                String result = executeCommand(connection, cmd).trim();
                availability.put(port, "available".equals(result));
            } catch (Exception e) {
                log.warn("端口 {} 可用性检测失败: {}", port, e.getMessage());
                availability.put(port, false);
            }
        }
        return availability;
    }


    /**
     * 检测系统环境详细信息，包括操作系统、发行版、资源等。
     *
     * @param connection SSH连接信息
     * @return 系统详细信息
     */
    public SystemInfo detectSystemEnvironment(SshConnection connection) {
        log.info("检测系统环境详细信息");
        try {
            SystemInfo.SystemInfoBuilder builder = SystemInfo.builder();
            builder.osType(detectOsType(connection));
            builder.dockerInstalled(checkDockerAvailability(connection));
            builder.hasRootAccess(checkSudoAccess(connection));
            detectDistributionInfo(connection, builder);
            builder.availableDiskSpaceMB(getAvailableDiskSpace(connection));
            builder.cpuCores(getCpuCoreCount(connection));
            Map<String, Long> memoryInfo = getMemoryInfo(connection);
            if (memoryInfo != null) {
                builder.totalMemoryMB(memoryInfo.get("total"));
                builder.availableMemoryMB(memoryInfo.get("available"));
            }
            if (builder.build().isDockerInstalled()) {
                builder.dockerVersion(getDockerVersion(connection));
            }
            return builder.build();
        } catch (Exception e) {
            log.error("检测系统环境失败", e);
            return SystemInfo.builder()
                    .osType("Unknown")
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
            // 1. /etc/os-release（保留原实现）
            String osRelease = executeCommand(connection, "cat /etc/os-release 2>/dev/null || echo ''");
            if (!osRelease.isBlank()) {
                parseOsRelease(osRelease, builder);
                return;
            }

            // 2. /etc/lsb-release
            String lsb = executeCommand(connection, "cat /etc/lsb-release 2>/dev/null || echo ''");
            if (!lsb.isBlank()) {
                // 载入环境变量
                for (String line : lsb.split("\\R")) {
                    if (line.startsWith("DISTRIB_ID=")) {
                        builder.osId(line.split("=",2)[1].replaceAll("\"","").toLowerCase());
                    } else if (line.startsWith("DISTRIB_RELEASE=")) {
                        builder.osVersionId(line.split("=",2)[1].replaceAll("\"",""));
                    } else if (line.startsWith("DISTRIB_CODENAME=")) {
                        builder.osVersionCodename(line.split("=",2)[1].replaceAll("\"",""));
                    } else if (line.startsWith("DISTRIB_DESCRIPTION=")) {
                        builder.distro(line.split("=",2)[1].replaceAll("\"",""));
                    }
                }
                return;
            }

            // 3. apt/yum/dnf 保留原实现——Ubuntu/CentOS/Fedora
            if (executeCommand(connection, "which apt-get").contains("apt-get")) {
                builder.osId("ubuntu").distro("Ubuntu");
                // 取代lsb_release 做fallback
                try { builder.osVersionCodename(
                        executeCommand(connection, "lsb_release -cs 2>/dev/null").trim());
                } catch (Exception ignore) {}
                return;
            } else if (executeCommand(connection, "which yum").contains("yum")) {
                builder.osId("centos").distro("CentOS");
                return;
            } else if (executeCommand(connection, "which dnf").contains("dnf")) {
                builder.osId("fedora").distro("Fedora");
                return;
            }

            // 4. /etc/redhat-release
            String redhat = executeCommand(connection, "cat /etc/redhat-release 2>/dev/null || echo ''").trim();
            if (!redhat.isEmpty()) {
                builder.distro(redhat);
                String name = redhat.split("\\s+")[0];
                builder.osId(name.toLowerCase());
                // version 数字
                String ver = redhat.replaceAll("^.*?([0-9]+(\\.[0-9]+)*).*$","$1");
                builder.osVersionId(ver);
                return;
            }

            // 5. /etc/SuSE-release
            String suse = executeCommand(connection, "cat /etc/SuSE-release 2>/dev/null || echo ''").trim();
            if (!suse.isEmpty()) {
                builder.osId("suse").distro("SUSE");
                // grep VERSION
                String vid = executeCommand(connection,
                        "grep VERSION /etc/SuSE-release | head -1 | awk '{print $3}'").trim();
                builder.osVersionId(vid);
                return;
            }

            // 6. /etc/alpine-release
            String alpine = executeCommand(connection, "cat /etc/alpine-release 2>/dev/null || echo ''").trim();
            if (!alpine.isEmpty()) {
                builder.osId("alpine")
                        .distro("Alpine Linux " + alpine)
                        .osVersionId(alpine);
                return;
            }

            // 7. /etc/issue 兜底
            String issue = executeCommand(connection, "head -1 /etc/issue 2>/dev/null || echo ''").trim();
            if (!issue.isEmpty()) {
                String name = issue.split("\\s+")[0];
                builder.osId(name.toLowerCase());
                builder.distro(issue.replaceAll("\\\\l",""));
                return;
            }

            // 8. uname 终极兜底
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
            // 先尝试直接执行docker命令
            try {
                String result = executeCommand(connection, "docker --version");
                if (result.toLowerCase().contains("docker version")) {
                    log.debug("Docker可用（无需sudo）");
                    return true;
                }
            } catch (Exception e) {
                log.debug("Docker（无需sudo）不可用: {}", e.getMessage());
            }
            // 再尝试sudo
            try {
                String result = executeCommand(connection, "sudo docker --version");
                if (result.toLowerCase().contains("docker version")) {
                    log.debug("Docker可用（需sudo）");
                    return true;
                }
            } catch (Exception e) {
                log.debug("Docker（需sudo）不可用: {}", e.getMessage());
            }
            // 检查docker命令是否存在且为可执行文件
            try {
                String result = executeCommand(connection, "command -v docker");
                if (!result.trim().isEmpty()) {
                    // 检查是否为可执行文件
                    String checkExec = executeCommand(connection, "[ -x \"" + result.trim() + "\" ] && echo 'yes' || echo 'no'");
                    if ("yes".equals(checkExec.trim())) {
                        log.info("检测到docker命令路径: {}", result.trim());
                        return false; // 存在但不可用
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
                    if (cmd.startsWith("df .")) {
                        return Long.parseLong(result) / 1024;
                    }
                    return Long.parseLong(result);
                }
            } catch (Exception e) {
                log.debug("磁盘空间检测命令失败: {} - {}", cmd, e.getMessage());
            }
        }
        // 尝试解析 df -h . 的输出
        try {
            String debugDf = executeCommand(connection, "df -h .");
            log.info("df -h . 输出: {}", debugDf);
            String[] lines = debugDf.split("\n");
            if (lines.length >= 2) {
                String[] fields = lines[1].trim().split("\\s+");
                if (fields.length >= 4) {
                    String avail = fields[3];
                    // 解析单位
                    if (avail.endsWith("G")) {
                        double gb = Double.parseDouble(avail.replace("G", ""));
                        return (long) (gb * 1024);
                    } else if (avail.endsWith("M")) {
                        return Long.parseLong(avail.replace("M", ""));
                    } else if (avail.endsWith("K")) {
                        return Long.parseLong(avail.replace("K", "")) / 1024;
                    }
                }
            }
        } catch (Exception ignored) {}
        log.warn("无法获取磁盘空间信息");
        return null;
    }


    /**
     * 获取内存信息（单位：MB），优先使用 free -m，失败则解析 /proc/meminfo。
     *
     * @param connection SSH连接信息
     * @return Map，包含total和available，获取失败返回null
     */
    private Map<String, Long> getMemoryInfo(SshConnection connection) {
        // 优先尝试 free -m
        try {
            String result = executeCommand(connection, "free -m | grep '^Mem:' | awk '{print $2 \" \" $7}'").trim();
            String[] parts = result.split("\\s+");
            if (parts.length >= 2) {
                return Map.of(
                        "total", Long.parseLong(parts[0]),
                        "available", Long.parseLong(parts[1])
                );
            }
        } catch (Exception e) {
            log.warn("获取内存信息失败（free -m）: {}", e.getMessage());
        }
        // 备用方案，解析 /proc/meminfo
        try {
            String meminfo = executeCommand(connection, "cat /proc/meminfo");
            long total = 0L, available = 0L;
            for (String line : meminfo.split("\n")) {
                if (line.startsWith("MemTotal:")) {
                    total = Long.parseLong(line.replaceAll("\\D+", ""));
                }
                if (line.startsWith("MemAvailable:")) {
                    available = Long.parseLong(line.replaceAll("\\D+", ""));
                }
            }
            if (total > 0 && available > 0) {
                // /proc/meminfo 单位为KB，需转为MB
                return Map.of(
                        "total", total / 1024,
                        "available", available / 1024
                );
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
     * 系统信息数据对象，用于服务间传递系统检测结果。
     */
    @Data
    @Builder
    public static class SystemInfo {
        private String osType;
        private String distro;
        private String version;
        private String architecture;
        private String osId;
        private String osVersionId;
        private String osVersionCodename;
        private boolean dockerInstalled;
        private String dockerVersion;
        private boolean hasRootAccess;
        private Integer cpuCores;
        private Long totalMemoryMB;
        private Long availableMemoryMB;
        private Long availableDiskSpaceMB;
        private Map<String, String> environmentVariables;
        private List<String> installedPackages;
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
    }
}
