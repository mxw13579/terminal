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
 * 负责为不同Linux发行版配置系统软件包管理器的镜像源
 * 基于linux-silly-tavern-docker-deploy.sh脚本的系统镜像源配置功能
 * 
 * @author Claude
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PackageManagerService {

    private final SshCommandService sshCommandService;

    /**
     * 配置系统包管理器镜像源
     * 根据操作系统类型和地理位置配置合适的镜像源
     * 
     * @param connection SSH连接
     * @param osInfo 操作系统信息
     * @param useChineseMirror 是否使用国内镜像源
     * @param progressCallback 进度回调函数
     * @return 配置结果
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
                    return PackageManagerConfigResult.builder()
                        .success(true)
                        .message("使用官方源，无需配置镜像源")
                        .configuredMirror("官方源")
                        .build();
                }

                progressCallback.accept("正在配置系统镜像源...");
                String osId = osInfo.getOsId().toLowerCase();
                
                switch (osId) {
                    case "debian":
                    case "ubuntu":
                        return configureDebianBasedMirrors(connection, osInfo, progressCallback);
                        
                    case "centos":
                    case "rhel":
                    case "fedora":
                        return configureRedHatBasedMirrors(connection, osInfo, progressCallback);
                        
                    case "arch":
                        return configureArchMirrors(connection, progressCallback);
                        
                    case "alpine":
                        return configureAlpineMirrors(connection, progressCallback);
                        
                    case "suse":
                    case "opensuse-leap":
                    case "opensuse-tumbleweed":
                        return configureSuseMirrors(connection, progressCallback);
                        
                    default:
                        progressCallback.accept(String.format("当前操作系统 %s 的系统镜像源自动配置暂不支持", osId));
                        return PackageManagerConfigResult.builder()
                            .success(false)
                            .message("不支持的操作系统")
                            .configuredMirror("未配置")
                            .build();
                }
                
            } catch (Exception e) {
                log.error("配置包管理器镜像源失败", e);
                progressCallback.accept("配置包管理器镜像源失败: " + e.getMessage());
                return PackageManagerConfigResult.builder()
                    .success(false)
                    .message("配置失败: " + e.getMessage())
                    .configuredMirror("未配置")
                    .build();
            }
        });
    }

    /**
     * 配置Debian/Ubuntu系统的镜像源
     */
    private PackageManagerConfigResult configureDebianBasedMirrors(SshConnection connection, 
                                                                 SystemDetectionService.SystemInfo osInfo,
                                                                 Consumer<String> progressCallback) throws Exception {
        
        // 检查是否已经是国内源
        CommandResult checkResult = sshCommandService.executeCommand(connection.getJschSession(), 
            "grep -q -E \"aliyun|tuna|ustc|163\" /etc/apt/sources.list");
        
        if (checkResult.exitStatus() == 0) {
            progressCallback.accept("检测到 /etc/apt/sources.list 已使用国内镜像，跳过替换");
            sshCommandService.executeCommand(connection.getJschSession(), "sudo apt-get update");
            return PackageManagerConfigResult.builder()
                .success(true)
                .message("已使用国内镜像源")
                .configuredMirror("阿里云镜像")
                .backupPath("/etc/apt/sources.list.bak")
                .build();
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
        
        return PackageManagerConfigResult.builder()
            .success(updateResult.exitStatus() == 0)
            .message("阿里云镜像源配置完成")
            .configuredMirror("阿里云镜像")
            .backupPath("/etc/apt/sources.list.bak")
            .build();
    }

    /**
     * 配置Debian镜像源
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
     * 配置RedHat系统的镜像源
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
            progressCallback.accept("检测到 /etc/yum.repos.d/ 已使用国内镜像，跳过替换");
            sshCommandService.executeCommand(connection.getJschSession(), 
                String.format("sudo %s clean all && sudo %s makecache", pkgManager, pkgManager));
            return PackageManagerConfigResult.builder()
                .success(true)
                .message("已使用国内镜像源")
                .configuredMirror("阿里云镜像")
                .build();
        }

        progressCallback.accept("备份当前 yum repo 文件...");
        sshCommandService.executeCommand(connection.getJschSession(), "sudo mkdir -p /etc/yum.repos.d/bak");
        sshCommandService.executeCommand(connection.getJschSession(), "sudo mv /etc/yum.repos.d/*.repo /etc/yum.repos.d/bak/ || true");

        String repoUrl;
        if ("fedora".equals(osId)) {
            repoUrl = "https://mirrors.aliyun.com/fedora/fedora-$(rpm -E %fedora).repo";
        } else {
            String versionId = osInfo.getOsVersionId();
            repoUrl = String.format("https://mirrors.aliyun.com/repo/Centos-%s.repo", versionId);
        }

        progressCallback.accept("下载新的 repo 文件从 " + repoUrl);
        CommandResult downloadResult = sshCommandService.executeCommand(connection.getJschSession(), 
            String.format("sudo curl -o /etc/yum.repos.d/aliyun-mirror.repo %s", repoUrl));

        progressCallback.accept("刷新软件包缓存...");
        CommandResult updateResult = sshCommandService.executeCommand(connection.getJschSession(), 
            String.format("sudo %s clean all && sudo %s makecache", pkgManager, pkgManager));

        return PackageManagerConfigResult.builder()
            .success(downloadResult.exitStatus() == 0 && updateResult.exitStatus() == 0)
            .message("阿里云镜像源配置完成")
            .configuredMirror("阿里云镜像")
            .backupPath("/etc/yum.repos.d/bak/")
            .build();
    }

    /**
     * 配置Arch Linux镜像源
     */
    private PackageManagerConfigResult configureArchMirrors(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        // 检查是否已包含清华大学镜像
        CommandResult checkResult = sshCommandService.executeCommand(connection.getJschSession(), 
            "grep -q \"tuna.tsinghua.edu.cn\" /etc/pacman.d/mirrorlist");
        
        if (checkResult.exitStatus() == 0) {
            progressCallback.accept("检测到 pacman mirrorlist 已包含清华大学镜像，跳过");
            sshCommandService.executeCommand(connection.getJschSession(), "sudo pacman -Syy --noconfirm");
            return PackageManagerConfigResult.builder()
                .success(true)
                .message("已使用国内镜像源")
                .configuredMirror("清华大学镜像")
                .build();
        }

        progressCallback.accept("备份 pacman mirrorlist...");
        sshCommandService.executeCommand(connection.getJschSession(), "sudo cp /etc/pacman.d/mirrorlist /etc/pacman.d/mirrorlist.bak");

        progressCallback.accept("将清华大学镜像源置顶...");
        sshCommandService.executeCommand(connection.getJschSession(), 
            "sudo sed -i '1s|^|Server = https://mirrors.tuna.tsinghua.edu.cn/archlinux/\\$repo/os/\\$arch\\n|' /etc/pacman.d/mirrorlist");

        CommandResult updateResult = sshCommandService.executeCommand(connection.getJschSession(), "sudo pacman -Syy --noconfirm");

        return PackageManagerConfigResult.builder()
            .success(updateResult.exitStatus() == 0)
            .message("清华大学镜像源配置完成")
            .configuredMirror("清华大学镜像")
            .backupPath("/etc/pacman.d/mirrorlist.bak")
            .build();
    }

    /**
     * 配置Alpine Linux镜像源
     */
    private PackageManagerConfigResult configureAlpineMirrors(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        // 检查是否已使用阿里云镜像
        CommandResult checkResult = sshCommandService.executeCommand(connection.getJschSession(), 
            "grep -q \"aliyun\" /etc/apk/repositories");
        
        if (checkResult.exitStatus() == 0) {
            progressCallback.accept("检测到 apk repositories 已使用国内镜像，跳过");
            sshCommandService.executeCommand(connection.getJschSession(), "sudo apk update");
            return PackageManagerConfigResult.builder()
                .success(true)
                .message("已使用国内镜像源")
                .configuredMirror("阿里云镜像")
                .build();
        }

        progressCallback.accept("备份 apk repositories...");
        sshCommandService.executeCommand(connection.getJschSession(), "sudo cp /etc/apk/repositories /etc/apk/repositories.bak");

        progressCallback.accept("替换为阿里云镜像源...");
        sshCommandService.executeCommand(connection.getJschSession(), 
            "sudo sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories");

        CommandResult updateResult = sshCommandService.executeCommand(connection.getJschSession(), "sudo apk update");

        return PackageManagerConfigResult.builder()
            .success(updateResult.exitStatus() == 0)
            .message("阿里云镜像源配置完成")
            .configuredMirror("阿里云镜像")
            .backupPath("/etc/apk/repositories.bak")
            .build();
    }

    /**
     * 配置SUSE系统镜像源（简化实现）
     */
    private PackageManagerConfigResult configureSuseMirrors(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("SUSE系统镜像源配置暂未实现");
        return PackageManagerConfigResult.builder()
            .success(false)
            .message("SUSE系统镜像源配置暂未实现")
            .configuredMirror("未配置")
            .build();
    }
    
    /**
     * 配置包管理器 - 便捷方法
     * 自动检测系统并配置适当的镜像源
     */
    public CompletableFuture<PackageManagerConfigResult> configurePackageManager(SshConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 检测系统环境
                SystemDetectionService systemDetectionService = new SystemDetectionService(sshCommandService);
                SystemDetectionService.SystemInfo osInfo = systemDetectionService.detectSystemEnvironment(connection);
                
                // 根据地理位置决定是否使用中国镜像源
                GeolocationDetectionService geoService = new GeolocationDetectionService(sshCommandService);
                GeolocationDetectionService.GeolocationInfo geoInfo = geoService.detectGeolocation(connection, (msg) -> {
                    log.info("地理位置检测: {}", msg);
                }).join();
                
                // 配置包管理器镜像源
                return configurePackageManagerMirrors(connection, osInfo, geoInfo.isUseChineseMirror(), (msg) -> {
                    log.info("包管理器配置: {}", msg);
                }).join();
                
            } catch (Exception e) {
                log.error("包管理器配置失败", e);
                return PackageManagerConfigResult.builder()
                    .success(false)
                    .message("包管理器配置失败: " + e.getMessage())
                    .configuredMirror("未配置")
                    .build();
            }
        });
    }

    /**
     * 包管理器配置结果数据类
     */
    @lombok.Data
    @lombok.Builder
    public static class PackageManagerConfigResult {
        private boolean success;
        private String message;
        private String configuredMirror;
        private String backupPath;
        @lombok.Builder.Default
        private Map<String, Object> details = new HashMap<>();
    }
}