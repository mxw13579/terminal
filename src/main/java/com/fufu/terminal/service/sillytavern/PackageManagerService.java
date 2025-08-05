package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 包管理器配置服务
 * <p>
 * 负责为不同Linux发行版配置系统软件包管理器的镜像源。
 * 支持Debian/Ubuntu、CentOS/RHEL/Fedora、Arch、Alpine等主流发行版。
 * 基于linux-silly-tavern-docker-deploy.sh脚本的系统镜像源配置功能。
 * </p>
 *
 * @author Claude
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PackageManagerService {

    private final SshCommandService sshCommandService;

    private static final String ALIYUN = "阿里云镜像";
    private static final String TUNA = "清华大学镜像";
    private static final String OFFICIAL = "官方源";
    private static final String NOT_CONFIGURED = "未配置";

    /**
     * 配置系统包管理器镜像源
     * <p>
     * 根据操作系统类型和地理位置配置合适的镜像源。
     * </p>
     *
     * @param connection        SSH连接
     * @param osInfo            操作系统信息
     * @param useChineseMirror  是否使用国内镜像源
     * @param progressCallback  进度回调函数
     * @return 配置结果的异步CompletableFuture
     */
    public CompletableFuture<PackageManagerConfigResult> configurePackageManagerMirrors(
            SshConnection connection,
            SystemDetectionService.SystemInfo osInfo,
            boolean useChineseMirror,
            Consumer<String> progressCallback) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!useChineseMirror) {
                    progressCallback.accept("跳过系统镜像源配置（使用官方源）");
                    return buildResult(true, "使用官方源，无需配置镜像源", OFFICIAL, null);
                }

                progressCallback.accept("正在配置系统镜像源...");
                String osId = osInfo.getOsId().toLowerCase();

                return switch (osId) {
                    case "debian", "ubuntu" ->
                            configureDebianBasedMirrors(connection, osInfo, progressCallback);
                    case "centos", "rhel", "fedora" ->
                            configureRedHatBasedMirrors(connection, osInfo, progressCallback);
                    case "arch" ->
                            configureArchMirrors(connection, progressCallback);
                    case "alpine" ->
                            configureAlpineMirrors(connection, progressCallback);
                    case "suse", "opensuse-leap", "opensuse-tumbleweed" ->
                            configureSuseMirrors(connection, progressCallback);
                    default -> {
                        String msg = String.format("当前操作系统 %s 的系统镜像源自动配置暂不支持", osId);
                        progressCallback.accept(msg);
                        yield buildResult(false, "不支持的操作系统", NOT_CONFIGURED, null);
                    }
                };

            } catch (Exception e) {
                log.error("配置包管理器镜像源失败", e);
                progressCallback.accept("配置包管理器镜像源失败: " + e.getMessage());
                return buildResult(false, "配置失败: " + e.getMessage(), NOT_CONFIGURED, null);
            }
        });
    }

    /**
     * 配置Debian/Ubuntu系统的镜像源
     *
     * @param connection       SSH连接
     * @param osInfo           操作系统信息
     * @param progressCallback 进度回调
     * @return 配置结果
     * @throws Exception 执行命令异常
     */
    private PackageManagerConfigResult configureDebianBasedMirrors(SshConnection connection,
                                                                   SystemDetectionService.SystemInfo osInfo,
                                                                   Consumer<String> progressCallback) throws Exception {
        // 检查是否已经是国内源
        CommandResult checkResult = sshCommandService.executeCommand(connection.getJschSession(),
                "grep -q -E \"aliyun|tuna|ustc|163\" /etc/apt/sources.list");

        if (checkResult.exitStatus() == 0) {
            progressCallback.accept("/etc/apt/sources.list 已使用国内镜像，跳过替换");
            sshCommandService.executeCommand(connection.getJschSession(), "sudo apt-get update");
            return buildResult(true, "已使用国内镜像源", ALIYUN, "/etc/apt/sources.list.bak");
        }

        progressCallback.accept("备份当前 sources.list...");
        sshCommandService.executeCommand(connection.getJschSession(),
                "sudo cp /etc/apt/sources.list /etc/apt/sources.list.bak");

        String osId = osInfo.getOsId().toLowerCase();
        String osVersionCodename = osInfo.getOsVersionCodename();

        if ("debian".equals(osId)) {
            configureDebianMirrors(connection, osVersionCodename, progressCallback);
        } else if ("ubuntu".equals(osId)) {
            configureUbuntuMirrors(connection, osVersionCodename, progressCallback);
        }

        progressCallback.accept("刷新软件包索引...");
        CommandResult updateResult = sshCommandService.executeCommand(connection.getJschSession(),
                "sudo apt-get update");

        return buildResult(updateResult.exitStatus() == 0, "阿里云镜像源配置完成", ALIYUN, "/etc/apt/sources.list.bak");
    }

    /**
     * 配置Debian镜像源
     *
     * @param connection       SSH连接
     * @param codename         发行版代号
     * @param progressCallback 进度回调
     * @throws Exception 执行命令异常
     */
    private void configureDebianMirrors(SshConnection connection, String codename, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("配置Debian阿里云镜像源...");
        String sourcesContent = String.format(
                "deb https://mirrors.aliyun.com/debian/ %s main contrib non-free\n" +
                        "deb-src https://mirrors.aliyun.com/debian/ %s main contrib non-free\n" +
                        "deb https://mirrors.aliyun.com/debian-security/ %s-security main contrib non-free\n" +
                        "deb-src https://mirrors.aliyun.com/debian-security/ %s-security main contrib non-free\n" +
                        "deb https://mirrors.aliyun.com/debian/ %s-updates main contrib non-free\n" +
                        "deb-src https://mirrors.aliyun.com/debian/ %s-updates main contrib non-free\n" +
                        "deb https://mirrors.aliyun.com/debian/ %s-backports main contrib non-free\n" +
                        "deb-src https://mirrors.aliyun.com/debian/ %s-backports main contrib non-free",
                codename, codename, codename, codename, codename, codename, codename, codename);

        String command = String.format("sudo tee /etc/apt/sources.list > /dev/null <<'EOF'\n%s\nEOF", sourcesContent);
        sshCommandService.executeCommand(connection.getJschSession(), command);
    }

    /**
     * 配置Ubuntu镜像源
     *
     * @param connection       SSH连接
     * @param codename         发行版代号
     * @param progressCallback 进度回调
     * @throws Exception 执行命令异常
     */
    private void configureUbuntuMirrors(SshConnection connection, String codename, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("配置Ubuntu阿里云镜像源...");
        String sourcesContent = String.format(
                "deb https://mirrors.aliyun.com/ubuntu/ %s main restricted universe multiverse\n" +
                        "deb-src https://mirrors.aliyun.com/ubuntu/ %s main restricted universe multiverse\n" +
                        "deb https://mirrors.aliyun.com/ubuntu/ %s-updates main restricted universe multiverse\n" +
                        "deb-src https://mirrors.aliyun.com/ubuntu/ %s-updates main restricted universe multiverse\n" +
                        "deb https://mirrors.aliyun.com/ubuntu/ %s-backports main restricted universe multiverse\n" +
                        "deb-src https://mirrors.aliyun.com/ubuntu/ %s-backports main restricted universe multiverse\n" +
                        "deb https://mirrors.aliyun.com/ubuntu/ %s-security main restricted universe multiverse\n" +
                        "deb-src https://mirrors.aliyun.com/ubuntu/ %s-security main restricted universe multiverse",
                codename, codename, codename, codename, codename, codename, codename, codename);

        String command = String.format("sudo tee /etc/apt/sources.list > /dev/null <<'EOF'\n%s\nEOF", sourcesContent);
        sshCommandService.executeCommand(connection.getJschSession(), command);
    }

    /**
     * 配置RedHat/CentOS/Fedora系统的镜像源
     *
     * @param connection       SSH连接
     * @param osInfo           操作系统信息
     * @param progressCallback 进度回调
     * @return 配置结果
     * @throws Exception 执行命令异常
     */
    private PackageManagerConfigResult configureRedHatBasedMirrors(SshConnection connection,
                                                                   SystemDetectionService.SystemInfo osInfo,
                                                                   Consumer<String> progressCallback) throws Exception {
        String osId = osInfo.getOsId().toLowerCase();
        String pkgManager = "fedora".equals(osId) ? "dnf" : "yum";

        // 检查是否已是国内源
        CommandResult checkResult = sshCommandService.executeCommand(connection.getJschSession(),
                "grep -q -E \"aliyun|tuna|ustc|163\" /etc/yum.repos.d/*.repo");

        if (checkResult.exitStatus() == 0) {
            progressCallback.accept("/etc/yum.repos.d/ 已使用国内镜像，跳过替换");
            sshCommandService.executeCommand(connection.getJschSession(),
                    String.format("sudo %s clean all && sudo %s makecache", pkgManager, pkgManager));
            return buildResult(true, "已使用国内镜像源", ALIYUN, null);
        }

        progressCallback.accept("备份当前 yum repo 文件...");
        sshCommandService.executeCommand(connection.getJschSession(), "sudo mkdir -p /etc/yum.repos.d/bak");
        sshCommandService.executeCommand(connection.getJschSession(), "sudo mv /etc/yum.repos.d/*.repo /etc/yum.repos.d/bak/ || true");

        String repoUrl = "fedora".equals(osId)
                ? "https://mirrors.aliyun.com/fedora/fedora-$(rpm -E %fedora).repo"
                : String.format("https://mirrors.aliyun.com/repo/Centos-%s.repo", osInfo.getOsVersionId());

        progressCallback.accept("下载新的 repo 文件从 " + repoUrl);
        CommandResult downloadResult = sshCommandService.executeCommand(connection.getJschSession(),
                String.format("sudo curl -o /etc/yum.repos.d/aliyun-mirror.repo %s", repoUrl));

        progressCallback.accept("刷新软件包缓存...");
        CommandResult updateResult = sshCommandService.executeCommand(connection.getJschSession(),
                String.format("sudo %s clean all && sudo %s makecache", pkgManager, pkgManager));

        return buildResult(downloadResult.exitStatus() == 0 && updateResult.exitStatus() == 0,
                "阿里云镜像源配置完成", ALIYUN, "/etc/yum.repos.d/bak/");
    }

    /**
     * 配置Arch Linux镜像源
     *
     * @param connection       SSH连接
     * @param progressCallback 进度回调
     * @return 配置结果
     * @throws Exception 执行命令异常
     */
    private PackageManagerConfigResult configureArchMirrors(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        // 检查是否已包含清华大学镜像
        CommandResult checkResult = sshCommandService.executeCommand(connection.getJschSession(),
                "grep -q \"tuna.tsinghua.edu.cn\" /etc/pacman.d/mirrorlist");

        if (checkResult.exitStatus() == 0) {
            progressCallback.accept("pacman mirrorlist 已包含清华大学镜像，跳过");
            sshCommandService.executeCommand(connection.getJschSession(), "sudo pacman -Syy --noconfirm");
            return buildResult(true, "已使用国内镜像源", TUNA, null);
        }

        progressCallback.accept("备份 pacman mirrorlist...");
        sshCommandService.executeCommand(connection.getJschSession(), "sudo cp /etc/pacman.d/mirrorlist /etc/pacman.d/mirrorlist.bak");

        progressCallback.accept("将清华大学镜像源置顶...");
        sshCommandService.executeCommand(connection.getJschSession(),
                "sudo sed -i '1s|^|Server = https://mirrors.tuna.tsinghua.edu.cn/archlinux/\\$repo/os/\\$arch\\n|' /etc/pacman.d/mirrorlist");

        CommandResult updateResult = sshCommandService.executeCommand(connection.getJschSession(), "sudo pacman -Syy --noconfirm");

        return buildResult(updateResult.exitStatus() == 0, "清华大学镜像源配置完成", TUNA, "/etc/pacman.d/mirrorlist.bak");
    }

    /**
     * 配置Alpine Linux镜像源
     *
     * @param connection       SSH连接
     * @param progressCallback 进度回调
     * @return 配置结果
     * @throws Exception 执行命令异常
     */
    private PackageManagerConfigResult configureAlpineMirrors(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        // 检查是否已使用阿里云镜像
        CommandResult checkResult = sshCommandService.executeCommand(connection.getJschSession(),
                "grep -q \"aliyun\" /etc/apk/repositories");

        if (checkResult.exitStatus() == 0) {
            progressCallback.accept("apk repositories 已使用国内镜像，跳过");
            sshCommandService.executeCommand(connection.getJschSession(), "sudo apk update");
            return buildResult(true, "已使用国内镜像源", ALIYUN, null);
        }

        progressCallback.accept("备份 apk repositories...");
        sshCommandService.executeCommand(connection.getJschSession(), "sudo cp /etc/apk/repositories /etc/apk/repositories.bak");

        progressCallback.accept("替换为阿里云镜像源...");
        sshCommandService.executeCommand(connection.getJschSession(),
                "sudo sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories");

        CommandResult updateResult = sshCommandService.executeCommand(connection.getJschSession(), "sudo apk update");

        return buildResult(updateResult.exitStatus() == 0, "阿里云镜像源配置完成", ALIYUN, "/etc/apk/repositories.bak");
    }

    /**
     * 配置SUSE系统镜像源（简化实现）
     *
     * @param connection       SSH连接
     * @param progressCallback 进度回调
     * @return 配置结果
     */
    private PackageManagerConfigResult configureSuseMirrors(SshConnection connection, Consumer<String> progressCallback) {
        progressCallback.accept("SUSE系统镜像源配置暂未实现");
        return buildResult(false, "SUSE系统镜像源配置暂未实现", NOT_CONFIGURED, null);
    }

    /**
     * 便捷方法：自动检测系统并配置适当的镜像源
     *
     * @param connection SSH连接
     * @return 配置结果的异步CompletableFuture
     */
    public CompletableFuture<PackageManagerConfigResult> configurePackageManager(SshConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 检测系统环境
                SystemDetectionService systemDetectionService = new SystemDetectionService(sshCommandService);
                SystemDetectionService.SystemInfo osInfo = systemDetectionService.detectSystemEnvironmentSync(connection);

                // 根据地理位置决定是否使用中国镜像源
                GeolocationDetectionService geoService = new GeolocationDetectionService(sshCommandService);
                GeolocationDetectionService.GeolocationInfo geoInfo = geoService.detectGeolocation(connection, msg -> log.info("地理位置检测: {}", msg)).join();

                // 配置包管理器镜像源
                return configurePackageManagerMirrors(connection, osInfo, geoInfo.isUseChineseMirror(), msg -> log.info("包管理器配置: {}", msg)).join();

            } catch (Exception e) {
                log.error("包管理器配置失败", e);
                return buildResult(false, "包管理器配置失败: " + e.getMessage(), NOT_CONFIGURED, null);
            }
        });
    }

    /**
     * 构建标准配置结果
     *
     * @param success         是否成功
     * @param message         消息
     * @param mirror          镜像源名称
     * @param backupPath      备份路径
     * @return 配置结果
     */
    private PackageManagerConfigResult buildResult(boolean success, String message, String mirror, String backupPath) {
        return PackageManagerConfigResult.builder()
                .success(success)
                .message(message)
                .configuredMirror(mirror)
                .backupPath(backupPath)
                .build();
    }

    /**
     * 包管理器配置结果数据类
     */
    @lombok.Data
    @lombok.Builder
    public static class PackageManagerConfigResult {
        /**
         * 是否配置成功
         */
        private boolean success;
        /**
         * 配置结果消息
         */
        private String message;
        /**
         * 已配置的镜像源名称
         */
        private String configuredMirror;
        /**
         * 备份文件路径
         */
        private String backupPath;
        /**
         * 详细信息
         */
        @lombok.Builder.Default
        private Map<String, Object> details = new HashMap<>();
    }
}
