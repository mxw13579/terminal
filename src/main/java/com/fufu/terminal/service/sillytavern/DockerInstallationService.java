package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.constants.SillyTavernConstants;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Docker 安装服务类。
 * <p>
 * 负责在远程主机上检测和安装 Docker，支持多种主流 Linux 发行版。
 * 该服务通过 SSH 执行命令，并提供了对国内镜像源的优化支持。
 * 所有操作均为异步执行，并返回 {@link CompletableFuture}。
 *
 * @author Claude (Optimized by AI Assistant)
 * @version 1.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerInstallationService {

    private final SshCommandService sshCommandService;

    /** docker-compose 包名常量 */
    private static final String DOCKER_COMPOSE_PACKAGE = "docker-compose";
    /** Docker CE 相关包名常量 */
    private static final String DOCKER_CE_PACKAGES = "docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin";
    /** 检查 Docker 版本命令 */
    private static final String DOCKER_VERSION_COMMAND = SillyTavernConstants.DOCKER_VERSION_COMMAND;

    /**
     * 异步检查目标主机上的 Docker 安装状态。
     * <p>
     * 通过执行一个组合脚本，一次性检查 Docker 是否已安装、获取版本号以及服务是否正在运行，
     * 以减少 SSH 连接开销。
     *
     * @param connection SSH 连接信息对象，包含会话详情。
     * @return 一个 {@link CompletableFuture}，其结果为 {@link DockerInstallationStatus} 对象，描述了 Docker 的当前状态。
     */
    public CompletableFuture<DockerInstallationStatus> checkDockerInstallation(final SshConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            log.debug("开始检查 Docker 安装状态...");
            String checkScript = """
                    if ! command -v docker &> /dev/null; then
                        echo "NOT_INSTALLED";
                        exit 0;
                    fi;
                    VERSION=$(docker --version);
                    if sudo systemctl is-active --quiet docker; then
                        STATUS="RUNNING";
                    else
                        STATUS="STOPPED";
                    fi;
                    echo "INSTALLED|$VERSION|$STATUS";
                    """;
            try {
                CommandResult result = executeSshCommand(connection, checkScript);
                String output = result.stdout().trim();
                if ("NOT_INSTALLED".equals(output)) {
                    return DockerInstallationStatus.builder()
                            .installed(false).version("未安装").serviceRunning(false).message("Docker 未安装")
                            .build();
                }
                String[] parts = output.split("\\|");
                if (parts.length == 3) {
                    boolean isRunning = "RUNNING".equals(parts[2]);
                    return DockerInstallationStatus.builder()
                            .installed(true)
                            .version(parts[1])
                            .serviceRunning(isRunning)
                            .message(isRunning ? "Docker 已安装且运行正常" : "Docker 已安装但服务未启动")
                            .build();
                }
                throw new IllegalStateException("无法解析 Docker 状态检查脚本的输出: " + output);
            } catch (Exception e) {
                log.error("检查 Docker 安装状态时发生异常", e);
                return DockerInstallationStatus.builder()
                        .installed(false).version("检查失败").serviceRunning(false)
                        .message("检查 Docker 状态时发生错误: " + e.getMessage())
                        .build();
            }
        });
    }


    /**
     * 异步安装 Docker，根据操作系统类型选择合适的安装方法。
     * <p>
     * 此方法会根据提供的系统信息，自动选择最适合当前系统的安装策略。
     * 安装成功后，会自动尝试启动并设置 Docker 服务为开机自启。
     *
     * @param connection       SSH 连接信息。
     * @param osInfo           通过 {@link SystemDetectionService} 获取的操作系统信息。
     * @param useChineseMirror 如果为 true，则在安装过程中优先使用国内镜像源以加快速度。
     * @param progressCallback 一个回调函数，用于接收并处理安装过程中的进度更新信息。
     * @return 一个 {@link CompletableFuture}，其结果为 {@link DockerInstallationResult} 对象，包含了安装是否成功及相关信息。
     */
    public CompletableFuture<DockerInstallationResult> installDocker(
            final SshConnection connection,
            final SystemDetectionService.SystemInfo osInfo,
            final boolean useChineseMirror,
            final Consumer<String> progressCallback) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                progressCallback.accept("开始安装 Docker...");
                String osId = osInfo.getOsId().toLowerCase();

                DockerInstallationResult result = switch (osId) {
                    case "debian", "ubuntu" ->
                            installDockerOnDebianBased(connection, osId, useChineseMirror, progressCallback);
                    case "centos", "rhel", "fedora" ->
                            installDockerOnRedHatBased(connection, osId, useChineseMirror, progressCallback);
                    case "arch" ->
                            installWithPackageManager(connection, progressCallback, "sudo pacman -S --noconfirm docker " + DOCKER_COMPOSE_PACKAGE, "Pacman 官方仓库");
                    case "alpine" ->
                            installWithPackageManager(connection, progressCallback, "sudo apk add docker " + DOCKER_COMPOSE_PACKAGE, "APK 官方仓库");
                    case "suse", "opensuse-leap", "opensuse-tumbleweed" ->
                            installWithPackageManager(connection, progressCallback, "sudo zypper install -y docker " + DOCKER_COMPOSE_PACKAGE, "Zypper 官方仓库");
                    default -> {
                        progressCallback.accept(String.format("不支持的操作系统: %s", osId));
                        yield DockerInstallationResult.builder().success(false).message("不支持的操作系统").installedVersion("未安装").build();
                    }
                };

                if (result.isSuccess()) {
                    progressCallback.accept("启动并启用 Docker 服务...");
                    startAndEnableDockerService(connection, osId, progressCallback);
                }

                return result;
            } catch (Exception e) {
                log.error("Docker 安装过程中发生异常", e);
                progressCallback.accept("Docker 安装失败: " + e.getMessage());
                return DockerInstallationResult.builder().success(false).message("安装过程中发生异常: " + e.getMessage()).build();
            }
        });
    }

    /**
     * 执行一个 SSH 命令，如果命令执行失败（退出状态码非0），则抛出运行时异常。
     *
     * @param connection  SSH 连接信息。
     * @param command     待执行的命令。
     * @param errorPrefix 异常信息的前缀。
     * @throws RuntimeException 如果命令执行失败。
     */
    private void executeCommandOrThrow(final SshConnection connection, final String command, final String errorPrefix) {
        CommandResult result = executeSshCommand(connection, command);
        if (result.exitStatus() != 0) {
            String errorMessage = String.format("%s: %s", errorPrefix, result.stderr().isBlank() ? result.stdout() : result.stderr());
            throw new RuntimeException(errorMessage);
        }
    }
    /**
     * 在 RedHat/CentOS/Fedora 等基于 RPM 的系统上安装 Docker。
     * <p>
     * 此方法采用策略模式，依次尝试多种安装方法，直到成功为止：
     * 1. Docker 官方仓库
     * 2. EPEL 仓库 (作为备选)
     * 3. Docker 官方便利脚本 (作为最终手段)
     * 这种设计提高了安装的成功率和代码的可维护性。
     */
    private DockerInstallationResult installDockerOnRedHatBased(
            final SshConnection connection, final String osName, final boolean useChineseMirror, final Consumer<String> progressCallback) {
        String pkgManager = "fedora".equals(osName) ? "dnf" : "yum";
        String repoBaseUrl = useChineseMirror ? "https://mirrors.aliyun.com/docker-ce" : "https://download.docker.com";
        String repoOsPath = "fedora".equals(osName) ? "fedora" : "centos";
        String repoUrl = String.format("%s/linux/%s/docker-ce.repo", repoBaseUrl, repoOsPath);
        List<InstallStrategy> strategies = Arrays.asList(
                new InstallStrategy(
                        "Docker 官方仓库",
                        """
                        sudo %1$s remove -y docker docker-client docker-client-latest docker-common docker-latest docker-latest-logrotate docker-logrotate docker-engine || true && \\
                        sudo %1$s install -y %1$s-utils device-mapper-persistent-data lvm2 && \\
                        sudo %1$s-config-manager --add-repo %2$s && \\
                        sudo rpm --import https://download.docker.com/linux/centos/gpg || true && \\
                        sudo %1$s makecache && \\
                        sudo %1$s install -y %3$s
                        """.formatted(pkgManager, repoUrl, DOCKER_CE_PACKAGES)
                ),
                new InstallStrategy(
                        "EPEL 仓库",
                        "sudo %s install -y epel-release && sudo %s install -y docker docker-compose".formatted(pkgManager, pkgManager)
                ),
                new InstallStrategy(
                        "Docker 官方便利脚本",
                        "curl -fsSL https://get.docker.com -o get-docker.sh && sudo sh get-docker.sh"
                )
        );
        String finalMethod = "未知";
        for (InstallStrategy strategy : strategies) {
            progressCallback.accept("正在尝试使用 [" + strategy.description() + "] 进行安装...");
            try {
                executeCommandOrThrow(connection, strategy.command(), "使用 [" + strategy.description() + "] 安装失败");
                progressCallback.accept("使用 [" + strategy.description() + "] 安装成功！");
                finalMethod = strategy.description();
                CommandResult versionResult = executeSshCommand(connection, DOCKER_VERSION_COMMAND);
                String installedVersion = versionResult.exitStatus() == 0 ? versionResult.stdout().trim() : "版本获取失败";
                return DockerInstallationResult.builder()
                        .success(true)
                        .message("Docker 安装成功")
                        .installedVersion(installedVersion)
                        .installationMethod(finalMethod)
                        .build();
            } catch (Exception e) {
                log.warn("使用安装策略 [{}] 失败: {}", strategy.description(), e.getMessage());
                progressCallback.accept("使用 [" + strategy.description() + "] 安装失败，尝试下一种方法...");
            }
        }
        throw new RuntimeException("所有 Docker 安装方法均告失败。");
    }


    /**
     * 使用特定的包管理器安装 Docker，用于 Arch, Alpine, Suse 等系统。
     */
    private DockerInstallationResult installWithPackageManager(
            final SshConnection connection, final Consumer<String> progressCallback,
            final String installCommand, final String methodDescription) {
        progressCallback.accept(String.format("在 %s 系统上，使用包管理器安装 Docker...", methodDescription));
        executeCommandOrThrow(connection, installCommand, "Docker 安装失败");
        CommandResult versionResult = executeSshCommand(connection, DOCKER_VERSION_COMMAND);
        String installedVersion = versionResult.exitStatus() == 0 ? versionResult.stdout().trim() : "版本获取失败";
        return DockerInstallationResult.builder()
                .success(versionResult.exitStatus() == 0)
                .message("Docker 安装成功")
                .installedVersion(installedVersion)
                .installationMethod(methodDescription)
                .build();
    }

    /**
     * 在 Debian/Ubuntu 等基于 APT 的系统上安装 Docker。
     * <p>
     * 将所有安装步骤合并到一个 shell 脚本中，通过一次 SSH 执行完成，
     * 显著提高了安装效率。
     */
    private DockerInstallationResult installDockerOnDebianBased(
            final SshConnection connection, final String osName, final boolean useChineseMirror, final Consumer<String> progressCallback) {
        progressCallback.accept(String.format("在 %s 系统上开始安装 Docker...", osName));
        String dockerRepoUrl = useChineseMirror ? "https://mirrors.aliyun.com/docker-ce" : "https://download.docker.com";
        String osVersionCodename = executeSshCommand(connection, "lsb_release -cs").stdout().trim();
        String installScript = """
                progress() { echo "PROGRESS: $1"; }
                progress "清理旧版本 Docker..."
                sudo apt-get remove -y docker docker-engine docker.io containerd runc || true
                
                progress "更新系统包列表并安装基础依赖..."
                sudo apt-get update
                sudo apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release
                
                progress "添加 Docker 官方 GPG 密钥和软件源..."
                sudo install -m 0755 -d /etc/apt/keyrings
                curl -fsSL "%s/linux/%s/gpg" | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
                sudo chmod a+r /etc/apt/keyrings/docker.gpg
                echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] %s/linux/%s %s stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
                
                progress "再次更新包列表并安装 Docker CE..."
                sudo apt-get update
                sudo apt-get install -y %s
                """.formatted(dockerRepoUrl, osName, dockerRepoUrl, osName, osVersionCodename, DOCKER_CE_PACKAGES);
        progressCallback.accept("正在执行一体化安装脚本...");
        executeCommandOrThrow(connection, installScript, "Docker CE 安装失败");
        CommandResult versionResult = executeSshCommand(connection, DOCKER_VERSION_COMMAND);
        String installedVersion = versionResult.exitStatus() == 0 ? versionResult.stdout().trim() : "版本获取失败";
        return DockerInstallationResult.builder()
                .success(versionResult.exitStatus() == 0)
                .message("Docker 安装成功")
                .installedVersion(installedVersion)
                .installationMethod("APT 官方仓库")
                .build();
    }

    /**
     * 安全地执行一个 SSH 命令，并将受检异常转换为非受检异常。
     * <p>
     * 此方法包装了对 sshCommandService.executeInternal 的调用，
     * 简化了错误处理，并使调用代码更清晰。
     *
     * @param connection SSH 连接信息。
     * @param command    要执行的命令。
     * @return 命令执行结果。
     * @throws RuntimeException 如果命令执行期间发生任何异常。
     */
    private CommandResult executeSshCommand(final SshConnection connection, final String command) {
        try {
            return sshCommandService.executeInternal(connection.getJschSession(), command);
        } catch (Exception e) {
            // 将受检异常转换为非受检异常，简化调用方的代码
            throw new RuntimeException("执行 SSH 命令失败: " + command, e);
        }
    }

    /**
     * 启动并设置 Docker 服务开机自启。
     * <p>
     * 针对 systemd 系统，在启动服务后会进行轮询检查，以确认服务状态，
     * 替代了原有的固定时间等待，使流程更健壮。
     */
    private void startAndEnableDockerService(
            final SshConnection connection, final String osName, final Consumer<String> progressCallback) {
        if ("alpine".equals(osName)) {
            progressCallback.accept("启动 Docker 服务 (OpenRC)...");
            executeCommandOrThrow(connection, "sudo rc-update add docker boot && sudo service docker start", "启动 Docker 服务失败");
            return;
        }
        progressCallback.accept("启动并启用 Docker 服务 (systemd)...");
        try {
            executeCommandOrThrow(connection, "sudo systemctl start docker && sudo systemctl enable docker", "启动或启用 Docker 服务失败");
            progressCallback.accept("Docker 服务启动命令已发送。");
        } catch (Exception e) {
            log.warn("首次启动 Docker 服务失败，尝试重置并重试: {}", e.getMessage());
            progressCallback.accept("首次启动失败，尝试重置并重试...");
            executeCommandOrThrow(connection, "sudo systemctl reset-failed docker && sudo systemctl daemon-reload && sudo systemctl start docker && sudo systemctl enable docker", "重试启动 Docker 服务失败");
        }
        progressCallback.accept("等待并验证 Docker 服务状态...");
        boolean serviceActive = false;
        try {
            for (int i = 0; i < 10; i++) { // 最多等待10秒
                CommandResult statusCheck = executeSshCommand(connection, "sudo systemctl is-active --quiet docker");
                if (statusCheck.exitStatus() == 0) {
                    serviceActive = true;
                    break;
                }
                TimeUnit.SECONDS.sleep(1);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断状态
            log.warn("等待 Docker 服务启动时被中断。");
        }
        if (serviceActive) {
            progressCallback.accept("Docker 服务验证成功，状态：active。");
        } else {
            log.warn("Docker 服务未能确认运行状态，可能需要手动检查。");
            progressCallback.accept("警告：Docker 服务未能确认运行状态，可能需要手动启动。");
        }
    }

    /**
     * 用于表示 Docker 安装策略的记录类。
     *
     * @param description 策略的文字描述。
     * @param command     执行该策略所需的 shell 命令。
     */
    private record InstallStrategy(String description, String command) {
    }

    /**
     * Docker 安装状态的数据传输对象。
     */
    @Data
    @Builder
    public static class DockerInstallationStatus {
        /** 是否已安装 */
        private boolean installed;
        /** Docker 版本号 */
        private String version;
        /** 服务是否正在运行 */
        private boolean serviceRunning;
        /** 描述状态的文本消息 */
        private String message;
    }

    /**
     * Docker 安装结果的数据传输对象。
     */
    @Data
    @Builder
    public static class DockerInstallationResult {
        /** 安装是否成功 */
        private boolean success;
        /** 描述结果的文本消息 */
        private String message;
        /** 成功安装后的 Docker 版本号 */
        private String installedVersion;
        /** 描述所用安装方法的文本 */
        private String installationMethod;
    }
}
