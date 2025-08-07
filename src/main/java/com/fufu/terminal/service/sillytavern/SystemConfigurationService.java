package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 系统配置服务
 * <p>
 * 负责系统级别的配置操作，包括地理位置检测、系统镜像源配置、
 * 基础环境检查等功能。基于 linux-silly-tavern-docker-deploy.sh 脚本的
 * 系统配置功能转换而来。
 * </p>
 *
 * @author Claude
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigurationService {

    private final SshCommandService sshCommandService;
    private final GeolocationDetectionService geolocationService;
    private final SystemDetectionService systemDetectionService;


    /**
     * 检测地理位置并配置系统镜像源
     *
     * @param connection SSH连接信息
     * @param progressCallback 进度回调函数
     * @return 异步返回配置结果
     */
    public CompletableFuture<SystemConfigResult> configureSystemMirrors(
            SshConnection connection,
            Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                progressCallback.accept("开始系统配置...");
                // 1. 同时检测地理位置和系统信息，因为两者都是决策所需
                GeolocationDetectionService.GeolocationInfo geoInfo =
                        geolocationService.detectGeolocation(connection, progressCallback).join();
                SystemDetectionService.SystemInfo systemInfo = systemDetectionService.detectSystemEnvironmentSync(connection);
                // 2. 【核心修改】最高优先级：处理 Debian 11 的强制修复
                if ("debian".equals(systemInfo.getOsId()) && "bullseye".equals(systemInfo.getOsVersionCodename())) {
                    progressCallback.accept("检测到 Debian 11 (bullseye)，其官方源已归档，正在强制修复...");
                    boolean configResult = fixDebian11Mirrors(connection, geoInfo.isUseChineseMirror(), progressCallback);
                    return SystemConfigResult.builder()
                            .success(configResult)
                            .message(configResult ? "Debian 11 源修复完成" : "Debian 11 源修复失败")
                            .geolocationInfo(geoInfo)
                            .systemInfo(systemInfo)
                            .skipped(false) // 明确表示没有跳过
                            .build();
                }
                // 3. 【常规逻辑】如果不是 Debian 11，再根据地理位置决定是否跳过
                if (!geoInfo.isUseChineseMirror()) {
                    progressCallback.accept("服务器不在中国大陆，且非特殊系统（如Debian 11），跳过系统镜像源配置");
                    return SystemConfigResult.builder()
                            .success(true)
                            .message("跳过系统镜像源配置（不在中国大陆）")
                            .geolocationInfo(geoInfo)
                            .systemInfo(systemInfo)
                            .skipped(true)
                            .build();
                }
                // 4. 如果不是 Debian 11，但在中国，则执行常规的镜像替换
                progressCallback.accept("检测到服务器位于中国，开始配置系统镜像源...");
                boolean configResult = configureSystemMirrorsByOs(connection, systemInfo, progressCallback);
                return SystemConfigResult.builder()
                        .success(configResult)
                        .message(configResult ? "系统镜像源配置完成" : "系统镜像源配置失败")
                        .geolocationInfo(geoInfo)
                        .systemInfo(systemInfo)
                        .skipped(false)
                        .build();
            } catch (Exception e) {
                log.error("系统配置过程中发生异常", e);
                progressCallback.accept("系统配置失败: " + e.getMessage());
                return SystemConfigResult.builder()
                        .success(false)
                        .message("系统配置失败: " + e.getMessage())
                        .errorMessage(e.getMessage())
                        .skipped(false)
                        .build();
            }
        });
    }


    // 确保 executeCommandOrThrow 方法存在于此类中

    /**
     * 为 Debian 11 (bullseye) 强制修复软件源
     * @param connection SSH连接
     * @param useChineseMirror 是否使用国内镜像
     * @param progressCallback 进度回调
     * @return 是否成功
     * @throws Exception 异常
     */
    private boolean fixDebian11Mirrors(SshConnection connection, boolean useChineseMirror, Consumer<String> progressCallback) throws Exception {
        String sourcesContent;
        if (useChineseMirror) {
            progressCallback.accept("使用阿里云归档镜像进行修复...");
            sourcesContent = """
                deb https://mirrors.aliyun.com/debian-archive/debian/ bullseye main contrib non-free
                deb https://mirrors.aliyun.com/debian-archive/debian-security/ bullseye-security main contrib non-free
                deb https://mirrors.aliyun.com/debian-archive/debian/ bullseye-updates main contrib non-free
                deb https://mirrors.aliyun.com/debian-archive/debian/ bullseye-backports main contrib non-free
                """;
        } else {
            progressCallback.accept("使用 Debian 官方归档镜像进行修复...");
            sourcesContent = """
                deb http://archive.debian.org/debian/ bullseye main contrib non-free
                deb http://archive.debian.org/debian-security/ bullseye-security main contrib non-free
                deb http://archive.debian.org/debian/ bullseye-updates main contrib non-free
                deb http://archive.debian.org/debian/ bullseye-backports main contrib non-free
                """;
        }
        // 备份并写入新源
        String fixCommand = String.format(
                "sudo cp /etc/apt/sources.list /etc/apt/sources.list.bak.$(date +%%s) && echo '%s' | sudo tee /etc/apt/sources.list > /dev/null",
                sourcesContent
        );
        executeCommandOrThrow(connection, fixCommand, "修复 Debian 11 源文件失败");

        // 写入 apt 配置，允许 insecure 源
        sshCommandService.executeCommand(
                connection.getJschSession(),
                "echo 'Acquire::AllowInsecureRepositories \"true\";' | sudo tee /etc/apt/apt.conf.d/99no-check-release > /dev/null"
        );

        progressCallback.accept("更新软件包索引 (使用归档源)...");
        CommandResult updateResult = sshCommandService.executeCommand(
                connection.getJschSession(),
                "sudo apt-get update --allow-insecure-repositories -o Acquire::Check-Valid-Until=false -o Acquire::Check-Date=false"
        );
        return updateResult.exitStatus() == 0;
    }

    
    
    private void executeCommandOrThrow(SshConnection connection, String command, String errorPrefix) throws Exception {
        CommandResult result = sshCommandService.executeCommand(connection.getJschSession(), command);
        if (result.exitStatus() != 0) {
            throw new RuntimeException(errorPrefix + ": " + result.stderr());
        }
    }


    /**
     * 根据操作系统类型配置镜像源
     *
     * @param connection SSH连接信息
     * @param systemInfo 系统信息
     * @param progressCallback 进度回调
     * @return 配置是否成功
     */
    private boolean configureSystemMirrorsByOs(SshConnection connection,
                                               SystemDetectionService.SystemInfo systemInfo,
                                               Consumer<String> progressCallback) {

        String osId = systemInfo.getOsId().toLowerCase();

        try {
            switch (osId) {
                case "debian":
                case "ubuntu":
                    return configureDebianMirrors(connection, systemInfo, progressCallback);
                case "centos":
                case "rhel":
                case "fedora":
                    return configureRedhatMirrors(connection, systemInfo, progressCallback);
                case "arch":
                    return configureArchMirrors(connection, progressCallback);
                case "alpine":
                    return configureAlpineMirrors(connection, progressCallback);
                case "suse":
                    return configureSuseMirrors(connection, progressCallback);
                default:
                    progressCallback.accept("不支持的操作系统类型: " + osId);
                    return false;
            }
        } catch (Exception e) {
            log.error("配置系统镜像源失败", e);
            progressCallback.accept("配置系统镜像源失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 配置Debian/Ubuntu系统镜像源
     *
     * @param connection SSH连接信息
     * @param systemInfo 系统信息
     * @param progressCallback 进度回调
     * @return 配置是否成功
     */
    private boolean configureDebianMirrors(SshConnection connection,
                                           SystemDetectionService.SystemInfo systemInfo,
                                           Consumer<String> progressCallback) throws Exception {
        String osId = systemInfo.getOsId();
        String codename = systemInfo.getOsVersionCodename();

        // 备份现有源文件
        progressCallback.accept("备份当前 sources.list...");
        sshCommandService.executeCommand(
                connection.getJschSession(),
                "sudo cp /etc/apt/sources.list /etc/apt/sources.list.bak.$(date +%%s)"
        );

        String sourcesContent;
        // 关键修复：如果检测到是 Debian 11，必须强制修复源
        if ("debian".equals(osId) && "bullseye".equals(codename)) {
            progressCallback.accept("检测到 Debian 11 (bullseye)，其官方源已归档，正在强制使用阿里云归档源修复...");
            sourcesContent = """
                    deb https://mirrors.aliyun.com/debian-archive/debian/ %s main contrib non-free
                    deb https://mirrors.aliyun.com/debian-archive/debian-security/ %s-security main contrib non-free
                    deb https://mirrors.aliyun.com/debian-archive/debian/ %s-updates main contrib non-free
                    deb https://mirrors.aliyun.com/debian-archive/debian/ %s-backports main contrib non-free
                    """.formatted(codename, codename, codename, codename);
        } else {
            progressCallback.accept("配置阿里云镜像源...");
            if ("debian".equals(osId)) {
                sourcesContent = generateDebianSources(codename);
            } else if ("ubuntu".equals(osId)) {
                sourcesContent = generateUbuntuSources(codename);
            } else {
                progressCallback.accept("未知的Debian系发行版: " + osId);
                return false;
            }
        }

        // 写入新的sources.list
        String writeCommand = String.format("echo '%s' | sudo tee /etc/apt/sources.list > /dev/null", sourcesContent);
        CommandResult writeResult = sshCommandService.executeCommand(connection.getJschSession(), writeCommand);
        if (writeResult.exitStatus() != 0) {
            progressCallback.accept("写入sources.list失败");
            return false;
        }

        progressCallback.accept("更新软件包索引...");
        // 为归档源添加特殊参数，对正常源无害
        CommandResult updateResult = sshCommandService.executeCommand(
                connection.getJschSession(),
                "sudo apt-get update -o Acquire::Check-Valid-Until=false -o Acquire::Check-Date=false"
        );

        return updateResult.exitStatus() == 0;
    }

    /**
     * 生成Debian系统的sources.list内容
     *
     * @param codename 版本代号
     * @return sources.list内容
     */
    private String generateDebianSources(String codename) {
        return String.format(
                "deb https://mirrors.aliyun.com/debian/ %s main contrib non-free\n" +
                "deb-src https://mirrors.aliyun.com/debian/ %s main contrib non-free\n" +
                "deb https://mirrors.aliyun.com/debian-security/ %s-security main contrib non-free\n" +
                "deb-src https://mirrors.aliyun.com/debian-security/ %s-security main contrib non-free\n" +
                "deb https://mirrors.aliyun.com/debian/ %s-updates main contrib non-free\n" +
                "deb-src https://mirrors.aliyun.com/debian/ %s-updates main contrib non-free\n" +
                "deb https://mirrors.aliyun.com/debian/ %s-backports main contrib non-free\n" +
                "deb-src https://mirrors.aliyun.com/debian/ %s-backports main contrib non-free\n",
                codename, codename, codename, codename, codename, codename, codename, codename
        );
    }

    /**
     * 生成Ubuntu系统的sources.list内容
     *
     * @param codename 版本代号
     * @return sources.list内容
     */
    private String generateUbuntuSources(String codename) {
        return String.format(
                "deb https://mirrors.aliyun.com/ubuntu/ %s main restricted universe multiverse\n" +
                "deb-src https://mirrors.aliyun.com/ubuntu/ %s main restricted universe multiverse\n" +
                "deb https://mirrors.aliyun.com/ubuntu/ %s-updates main restricted universe multiverse\n" +
                "deb-src https://mirrors.aliyun.com/ubuntu/ %s-updates main restricted universe multiverse\n" +
                "deb https://mirrors.aliyun.com/ubuntu/ %s-backports main restricted universe multiverse\n" +
                "deb-src https://mirrors.aliyun.com/ubuntu/ %s-backports main restricted universe multiverse\n" +
                "deb https://mirrors.aliyun.com/ubuntu/ %s-security main restricted universe multiverse\n" +
                "deb-src https://mirrors.aliyun.com/ubuntu/ %s-security main restricted universe multiverse\n",
                codename, codename, codename, codename, codename, codename, codename, codename
        );
    }

    /**
     * 配置RedHat系系统镜像源
     *
     * @param connection SSH连接信息
     * @param systemInfo 系统信息
     * @param progressCallback 进度回调
     * @return 配置是否成功
     */
    private boolean configureRedhatMirrors(SshConnection connection,
                                           SystemDetectionService.SystemInfo systemInfo,
                                           Consumer<String> progressCallback) throws Exception {

        String osId = systemInfo.getOsId();
        progressCallback.accept("配置 " + osId + " 系统镜像源...");

        // 根据不同发行版配置相应的镜像源
        if ("fedora".equals(osId)) {
            return configureFedoraMirrors(connection, progressCallback);
        } else if ("centos".equals(osId) || "rhel".equals(osId)) {
            return configureCentOSMirrors(connection, progressCallback);
        } else {
            progressCallback.accept("暂不支持的RedHat系发行版: " + osId);
            return false;
        }
    }

    /**
     * 配置Fedora镜像源
     *
     * @param connection SSH连接信息
     * @param progressCallback 进度回调
     * @return 配置是否成功
     */
    private boolean configureFedoraMirrors(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("备份现有仓库配置...");
        sshCommandService.executeCommand(connection.getJschSession(), "sudo mkdir -p /etc/yum.repos.d/backup");
        sshCommandService.executeCommand(connection.getJschSession(), "sudo mv /etc/yum.repos.d/*.repo /etc/yum.repos.d/backup/");

        progressCallback.accept("下载阿里云Fedora仓库配置...");
        CommandResult result = sshCommandService.executeCommand(
                connection.getJschSession(),
                "sudo wget -O /etc/yum.repos.d/fedora.repo http://mirrors.aliyun.com/repo/fedora.repo && " +
                "sudo wget -O /etc/yum.repos.d/fedora-updates.repo http://mirrors.aliyun.com/repo/fedora-updates.repo"
        );

        if (result.exitStatus() == 0) {
            progressCallback.accept("刷新软件包缓存...");
            CommandResult updateResult = sshCommandService.executeCommand(connection.getJschSession(), "sudo dnf makecache");
            return updateResult.exitStatus() == 0;
        } else {
            progressCallback.accept("下载仓库配置失败，恢复备份...");
            sshCommandService.executeCommand(connection.getJschSession(), "sudo mv /etc/yum.repos.d/backup/*.repo /etc/yum.repos.d/");
            return false;
        }
    }

    /**
     * 配置CentOS镜像源
     *
     * @param connection SSH连接信息
     * @param progressCallback 进度回调
     * @return 配置是否成功
     */
    private boolean configureCentOSMirrors(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("备份现有仓库配置...");
        sshCommandService.executeCommand(connection.getJschSession(), "sudo mkdir -p /etc/yum.repos.d/backup");
        sshCommandService.executeCommand(connection.getJschSession(), "sudo mv /etc/yum.repos.d/CentOS-*.repo /etc/yum.repos.d/backup/");

        progressCallback.accept("下载阿里云CentOS仓库配置...");
        CommandResult result = sshCommandService.executeCommand(
                connection.getJschSession(),
                "sudo wget -O /etc/yum.repos.d/CentOS-Base.repo http://mirrors.aliyun.com/repo/Centos-7.repo"
        );

        if (result.exitStatus() == 0) {
            progressCallback.accept("刷新软件包缓存...");
            CommandResult updateResult = sshCommandService.executeCommand(connection.getJschSession(), "sudo yum makecache");
            return updateResult.exitStatus() == 0;
        } else {
            progressCallback.accept("下载仓库配置失败，恢复备份...");
            sshCommandService.executeCommand(connection.getJschSession(), "sudo mv /etc/yum.repos.d/backup/*.repo /etc/yum.repos.d/");
            return false;
        }
    }

    /**
     * 配置Arch Linux镜像源
     *
     * @param connection SSH连接信息
     * @param progressCallback 进度回调
     * @return 配置是否成功
     */
    private boolean configureArchMirrors(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("配置Arch Linux中国镜像源...");

        sshCommandService.executeCommand(connection.getJschSession(), "sudo cp /etc/pacman.d/mirrorlist /etc/pacman.d/mirrorlist.bak");

        String mirrorlistContent =
                "Server = https://mirrors.aliyun.com/archlinux/$repo/os/$arch\n" +
                "Server = https://mirrors.tuna.tsinghua.edu.cn/archlinux/$repo/os/$arch\n" +
                "Server = https://mirrors.ustc.edu.cn/archlinux/$repo/os/$arch\n";

        CommandResult result = sshCommandService.executeCommand(
                connection.getJschSession(),
                String.format("sudo tee /etc/pacman.d/mirrorlist > /dev/null << 'EOF'\n%sEOF", mirrorlistContent)
        );

        if (result.exitStatus() == 0) {
            progressCallback.accept("更新软件包数据库...");
            CommandResult updateResult = sshCommandService.executeCommand(connection.getJschSession(), "sudo pacman -Sy");
            return updateResult.exitStatus() == 0;
        }
        return false;
    }

    /**
     * 配置Alpine Linux镜像源
     *
     * @param connection SSH连接信息
     * @param progressCallback 进度回调
     * @return 配置是否成功
     */
    private boolean configureAlpineMirrors(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("配置Alpine Linux中国镜像源...");

        sshCommandService.executeCommand(connection.getJschSession(), "sudo cp /etc/apk/repositories /etc/apk/repositories.bak");

        CommandResult versionResult = sshCommandService.executeCommand(
                connection.getJschSession(),
                "cat /etc/alpine-release | cut -d'.' -f1,2"
        );

        String version = versionResult.exitStatus() == 0 ? versionResult.stdout().trim() : "3.18";

        String repositoriesContent = String.format(
                "https://mirrors.aliyun.com/alpine/v%s/main\n" +
                "https://mirrors.aliyun.com/alpine/v%s/community\n",
                version, version
        );

        CommandResult result = sshCommandService.executeCommand(
                connection.getJschSession(),
                String.format("sudo tee /etc/apk/repositories > /dev/null << 'EOF'\n%sEOF", repositoriesContent)
        );

        if (result.exitStatus() == 0) {
            progressCallback.accept("更新软件包索引...");
            CommandResult updateResult = sshCommandService.executeCommand(connection.getJschSession(), "sudo apk update");
            return updateResult.exitStatus() == 0;
        }
        return false;
    }

    /**
     * 配置SUSE镜像源
     *
     * @param connection SSH连接信息
     * @param progressCallback 进度回调
     * @return 配置是否成功
     */
    private boolean configureSuseMirrors(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("配置SUSE中国镜像源（基本支持）...");

        CommandResult result = sshCommandService.executeCommand(
                connection.getJschSession(),
                "sudo zypper ar -f https://mirrors.aliyun.com/opensuse/distribution/leap/\\$releasever/repo/oss/ aliyun-oss && " +
                "sudo zypper ar -f https://mirrors.aliyun.com/opensuse/distribution/leap/\\$releasever/repo/non-oss/ aliyun-non-oss"
        );

        if (result.exitStatus() == 0) {
            progressCallback.accept("刷新软件包缓存...");
            CommandResult updateResult = sshCommandService.executeCommand(connection.getJschSession(), "sudo zypper refresh");
            return updateResult.exitStatus() == 0;
        }
        return false;
    }

    /**
     * 系统配置结果数据类
     */
    @lombok.Data
    @lombok.Builder
    public static class SystemConfigResult {
        /**
         * 配置是否成功
         */
        private boolean success;
        /**
         * 配置结果消息
         */
        private String message;
        /**
         * 是否跳过了配置
         */
        private boolean skipped;
        /**
         * 地理位置信息
         */
        private GeolocationDetectionService.GeolocationInfo geolocationInfo;
        /**
         * 系统信息
         */
        private SystemDetectionService.SystemInfo systemInfo;
        /**
         * 错误信息（如有）
         */
        private String errorMessage;

        /**
         * 获取配置是否成功
         * @return 是否成功
         */
        public boolean isSuccess() {
            return success;
        }

        /**
         * 获取配置结果消息
         * @return 结果消息
         */
        public String getMessage() {
            return message;
        }

        /**
         * 获取是否跳过了配置
         * @return 是否跳过
         */
        public boolean isSkipped() {
            return skipped;
        }

        /**
         * 获取地理位置信息
         * @return 地理位置信息
         */
        public GeolocationDetectionService.GeolocationInfo getGeolocationInfo() {
            return geolocationInfo;
        }

        /**
         * 获取系统信息
         * @return 系统信息
         */
        public SystemDetectionService.SystemInfo getSystemInfo() {
            return systemInfo;
        }
    }
}
