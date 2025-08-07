package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Docker 安装服务类。
 * <p>
 * 负责检测和安装 Docker，支持多种 Linux 发行版。
 * 基于 linux-silly-tavern-docker-deploy.sh 脚本的 Docker 安装功能。
 * </p>
 *
 * @author Claude
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerInstallationService {

    private final SshCommandService sshCommandService;

    /**
     * docker-compose 包名常量
     */
    private static final String DOCKER_COMPOSE_PACKAGE = "docker-compose";
    /**
     * Docker CE 相关包名常量
     */
    private static final String DOCKER_CE_PACKAGES = "docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin";
    /**
     * 检查 Docker 版本命令
     */
    private static final String DOCKER_VERSION_COMMAND = "docker --version";

    /**
     * 检查目标主机是否已安装 Docker。
     *
     * @param connection SSH 连接信息
     * @return 异步返回 Docker 安装状态
     */
    public CompletableFuture<DockerInstallationStatus> checkDockerInstallation(SshConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("检查 Docker 安装状态");

                // 检查 docker 命令是否存在
                CommandResult dockerCheck = sshCommandService.executeCommand(connection.getJschSession(), "command -v docker &> /dev/null");

                if (dockerCheck.exitStatus() != 0) {
                    return DockerInstallationStatus.builder().installed(false).version("未安装").serviceRunning(false).message("Docker 未安装").build();
                }

                // 获取 Docker 版本信息
                CommandResult versionResult = sshCommandService.executeCommand(connection.getJschSession(), DOCKER_VERSION_COMMAND);
                String version = versionResult.exitStatus() == 0 ? versionResult.stdout().trim() : "版本信息获取失败";

                // 检查 Docker 服务状态
                CommandResult serviceCheck = sshCommandService.executeCommand(connection.getJschSession(), "sudo systemctl is-active docker");
                boolean serviceRunning = serviceCheck.exitStatus() == 0 && "active".equals(serviceCheck.stdout().trim());

                return DockerInstallationStatus.builder().installed(true).version(version).serviceRunning(serviceRunning).message(serviceRunning ? "Docker 已安装且运行正常" : "Docker 已安装但服务未启动").build();

            } catch (Exception e) {
                log.error("检查 Docker 安装状态失败", e);
                return DockerInstallationStatus.builder().installed(false).version("检查失败").serviceRunning(false).message("检查 Docker 状态时发生错误: " + e.getMessage()).build();
            }
        });
    }

    /**
     * 安装 Docker，根据操作系统类型选择合适的安装方法。
     *
     * @param connection       SSH 连接信息
     * @param osInfo           操作系统信息
     * @param useChineseMirror 是否使用国内镜像源
     * @param progressCallback 安装进度回调
     * @return 异步返回 Docker 安装结果
     */
    public CompletableFuture<DockerInstallationResult> installDocker(SshConnection connection, SystemDetectionService.SystemInfo osInfo, boolean useChineseMirror, Consumer<String> progressCallback) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                progressCallback.accept("开始安装 Docker...");
                String osId = osInfo.getOsId().toLowerCase();

                // 根据不同操作系统分发安装逻辑
                DockerInstallationResult result = switch (osId) {
                    case "debian", "ubuntu" ->
                            installDockerOnDebianBased(connection, osId, useChineseMirror, progressCallback);
                    case "centos", "rhel", "fedora" ->
                            installDockerOnRedHatBased(connection, osId, useChineseMirror, progressCallback);
                    case "arch" ->
                            installWithPackageManager(connection, progressCallback, "pacman", "sudo pacman -S --noconfirm docker " + DOCKER_COMPOSE_PACKAGE, "Pacman 官方仓库");
                    case "alpine" ->
                            installWithPackageManager(connection, progressCallback, "apk", "sudo apk add docker " + DOCKER_COMPOSE_PACKAGE, "APK 官方仓库");
                    case "suse", "opensuse-leap", "opensuse-tumbleweed" ->
                            installWithPackageManager(connection, progressCallback, "zypper", "sudo zypper install -y docker " + DOCKER_COMPOSE_PACKAGE, "Zypper 官方仓库");
                    default -> {
                        progressCallback.accept(String.format("不支持的操作系统: %s", osId));
                        yield DockerInstallationResult.builder().success(false).message("不支持的操作系统").installedVersion("未安装").build();
                    }
                };

                // 安装成功后启动并启用 Docker 服务
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
     * 执行一个 SSH 命令，如果失败则抛出异常。
     *
     * @param connection  SSH 连接信息
     * @param command     待执行命令
     * @param errorPrefix 错误信息前缀
     * @throws Exception 命令执行失败时抛出异常
     */
    private void executeCommandOrThrow(SshConnection connection, String command, String errorPrefix) throws Exception {
        CommandResult result = sshCommandService.executeCommand(connection.getJschSession(), command);
        if (result.exitStatus() != 0) {
            throw new RuntimeException(errorPrefix + ": " + result.stderr());
        }
    }

    /**
     * 在 RedHat/CentOS/Fedora 系统上安装 Docker。
     *
     * @param connection       SSH 连接信息
     * @param osName           操作系统名称
     * @param useChineseMirror 是否使用国内镜像
     * @param progressCallback 进度回调
     * @return 安装结果
     * @throws Exception 安装失败时抛出异常
     */
    private DockerInstallationResult installDockerOnRedHatBased(SshConnection connection, String osName, boolean useChineseMirror, Consumer<String> progressCallback) throws Exception {

        progressCallback.accept(String.format("在 %s 系统上安装 Docker...", osName));
        String pkgManager = "fedora".equals(osName) ? "dnf" : "yum";
        String repoUrl = useChineseMirror ? ("fedora".equals(osName) ? "https://mirrors.aliyun.com/docker-ce/linux/fedora/docker-ce.repo" : "http://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo") : ("fedora".equals(osName) ? "https://download.docker.com/linux/fedora/docker-ce.repo" : "https://download.docker.com/linux/centos/docker-ce.repo");
        progressCallback.accept("添加 Docker 仓库: " + repoUrl);

        // 修复安装问题：添加非交互模式和更好的错误处理
        String installCommands = String.join(" && ", String.format("sudo %s remove -y docker docker-client docker-client-latest docker-common docker-latest docker-latest-logrotate docker-logrotate docker-engine || true", pkgManager), String.format("sudo %s install -y %s-utils device-mapper-persistent-data lvm2", pkgManager, pkgManager), String.format("sudo %s-config-manager --add-repo %s", pkgManager, repoUrl),
                // 添加GPG key导入和非交互模式
                "sudo rpm --import https://download.docker.com/linux/centos/gpg || true", String.format("sudo %s makecache", pkgManager), String.format("sudo %s install -y %s", pkgManager, DOCKER_CE_PACKAGES));

        progressCallback.accept("正在执行安装脚本...");

        try {
            executeCommandOrThrow(connection, installCommands, "Docker 安装失败");
        } catch (Exception e) {
            // 如果官方源安装失败，尝试使用EPEL源
            progressCallback.accept("官方源安装失败，尝试使用EPEL源安装Docker...");
            try {
                String epelInstallCmd = String.format("sudo %s install -y epel-release && sudo %s install -y docker docker-compose", pkgManager, pkgManager);
                executeCommandOrThrow(connection, epelInstallCmd, "EPEL Docker 安装失败");
                progressCallback.accept("使用EPEL源成功安装Docker");
            } catch (Exception epelException) {
                // 最后尝试使用便利脚本
                progressCallback.accept("EPEL安装失败，尝试使用Docker便利脚本...");
                String convenienceScript = "curl -fsSL https://get.docker.com -o get-docker.sh && sudo sh get-docker.sh";
                executeCommandOrThrow(connection, convenienceScript, "Docker便利脚本安装失败");
            }
        }

        CommandResult versionResult = sshCommandService.executeCommand(connection.getJschSession(), DOCKER_VERSION_COMMAND);
        String installedVersion = versionResult.exitStatus() == 0 ? versionResult.stdout().trim() : "版本获取失败";

        return DockerInstallationResult.builder().success(versionResult.exitStatus() == 0).message("Docker 安装成功").installedVersion(installedVersion).installationMethod(pkgManager.toUpperCase() + " 官方仓库").build();
    }

    /**
     * 使用指定包管理器安装 Docker。
     *
     * @param connection        SSH 连接信息
     * @param progressCallback  进度回调
     * @param pkgManager        包管理器名称
     * @param installCommand    安装命令
     * @param methodDescription 安装方式描述
     * @return 安装结果
     * @throws Exception 安装失败时抛出异常
     */
    private DockerInstallationResult installWithPackageManager(SshConnection connection, Consumer<String> progressCallback, String pkgManager, String installCommand, String methodDescription) throws Exception {

        progressCallback.accept(String.format("在 %s 系统上安装 Docker...", methodDescription));
        executeCommandOrThrow(connection, installCommand, "Docker 安装失败");

        CommandResult versionResult = sshCommandService.executeCommand(connection.getJschSession(), DOCKER_VERSION_COMMAND);
        String installedVersion = versionResult.exitStatus() == 0 ? versionResult.stdout().trim() : "版本获取失败";

        return DockerInstallationResult.builder().success(versionResult.exitStatus() == 0).message("Docker 安装成功").installedVersion(installedVersion).installationMethod(methodDescription).build();
    }

    /**
     * 在 Debian/Ubuntu 系统上安装 Docker。
     *
     * @param connection       SSH 连接信息
     * @param osName           操作系统名称
     * @param useChineseMirror 是否使用国内镜像
     * @param progressCallback 进度回调
     * @return 安装结果
     * @throws Exception 安装失败时抛出异常
     */
    private DockerInstallationResult installDockerOnDebianBased(SshConnection connection, String osName, boolean useChineseMirror, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept(String.format("在 %s 系统上开始安装 Docker...", osName));
        // 步骤 1: 清理旧版本
        progressCallback.accept("清理旧版本 Docker...");
        executeCommandOrThrow(connection, "sudo apt-get remove -y docker docker-engine docker.io containerd runc || true", "清理旧版本失败");
        // 步骤 2: 更新包列表并安装基础依赖 (现在我们相信这一步会成功)
        progressCallback.accept("更新系统包列表并安装基础依赖...");
        executeCommandOrThrow(connection, "sudo apt-get update", "更新系统包列表失败 (apt-get update)");
        executeCommandOrThrow(connection, "sudo apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release", "安装基础依赖失败");
        // 步骤 3: 添加 Docker 官方 GPG 密钥和软件源
        progressCallback.accept("添加 Docker 官方 GPG 密钥和软件源...");
        String dockerRepoUrl = useChineseMirror ? "https://mirrors.aliyun.com/docker-ce" : "https://download.docker.com";
        String osVersionCodename = sshCommandService.executeCommand(connection.getJschSession(), "lsb_release -cs").stdout().trim();

        String setupRepoCommands = String.join(" && ", "sudo install -m 0755 -d /etc/apt/keyrings", String.format("curl -fsSL \"%s/linux/%s/gpg\" | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg", dockerRepoUrl, osName), "sudo chmod a+r /etc/apt/keyrings/docker.gpg", String.format("echo \"deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] %s/linux/%s %s stable\" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null", dockerRepoUrl, osName, osVersionCodename));
        executeCommandOrThrow(connection, setupRepoCommands, "添加 Docker 软件源失败");
        // 步骤 4: 更新源并安装 Docker CE
        progressCallback.accept("再次更新包列表并安装 Docker CE...");
        String installCommand = "sudo apt-get update && sudo apt-get install -y " + DOCKER_CE_PACKAGES;
        executeCommandOrThrow(connection, installCommand, "安装 Docker CE 失败");
        CommandResult versionResult = sshCommandService.executeCommand(connection.getJschSession(), DOCKER_VERSION_COMMAND);
        String installedVersion = versionResult.exitStatus() == 0 ? versionResult.stdout().trim() : "版本获取失败";
        return DockerInstallationResult.builder().success(versionResult.exitStatus() == 0).message("Docker 安装成功").installedVersion(installedVersion).installationMethod("APT 官方仓库").build();
    }

    /**
     * 启动并设置 Docker 服务开机自启。
     *
     * @param connection       SSH 连接信息
     * @param osName           操作系统名称
     * @param progressCallback 进度回调
     * @throws Exception 启动失败时抛出异常
     */
    private void startAndEnableDockerService(SshConnection connection, String osName, Consumer<String> progressCallback) throws Exception {

        if ("alpine".equals(osName)) {
            progressCallback.accept("启动 Docker 服务 (OpenRC)...");
            executeCommandOrThrow(connection, "sudo rc-update add docker boot && sudo service docker start", "启动 Docker 服务失败");
        } else {
            progressCallback.accept("启动并启用 Docker 服务 (systemd)...");

            // 改进的Docker服务启动逻辑，更好的错误处理
            try {
                // 首先尝试启动和启用服务
                CommandResult startResult = sshCommandService.executeCommand(connection.getJschSession(), "sudo systemctl start docker && sudo systemctl enable docker");

                if (startResult.exitStatus() != 0) {
                    progressCallback.accept("初次启动失败，尝试重置Docker服务...");
                    // 尝试重置失败的服务状态
                    sshCommandService.executeCommand(connection.getJschSession(), "sudo systemctl reset-failed docker");

                    // 重新加载systemd守护进程
                    sshCommandService.executeCommand(connection.getJschSession(), "sudo systemctl daemon-reload");

                    // 再次尝试启动
                    CommandResult retryResult = sshCommandService.executeCommand(connection.getJschSession(), "sudo systemctl start docker && sudo systemctl enable docker");

                    if (retryResult.exitStatus() != 0) {
                        log.warn("Docker 服务启动重试失败: {}", retryResult.stderr());
                        progressCallback.accept("服务启动失败，但Docker可能已安装成功，请手动启动服务");
                    } else {
                        progressCallback.accept("Docker 服务启动成功（重试后）");
                    }
                } else {
                    progressCallback.accept("Docker 服务启动成功");
                }

                // 等待服务完全启动
                Thread.sleep(3000);

                // 验证服务状态
                CommandResult statusCheck = sshCommandService.executeCommand(connection.getJschSession(), "sudo systemctl is-active docker");

                if (statusCheck.exitStatus() == 0 && "active".equals(statusCheck.stdout().trim())) {
                    progressCallback.accept("Docker 服务验证成功，状态正常");
                } else {
                    progressCallback.accept("注意：Docker 服务可能需要手动启动");
                }

            } catch (Exception e) {
                log.warn("Docker 服务启动过程中出现异常: {}", e.getMessage());
                progressCallback.accept("Docker 安装完成，但服务启动可能有问题，请检查系统日志");
                // 不抛出异常，因为安装本身可能是成功的
            }
        }
    }

    /**
     * Docker 安装状态数据类。
     */
    @Data
    @Builder
    public static class DockerInstallationStatus {
        /**
         * 是否已安装
         */
        private boolean installed;
        /**
         * Docker 版本
         */
        private String version;
        /**
         * 服务是否运行中
         */
        private boolean serviceRunning;
        /**
         * 状态消息
         */
        private String message;
    }

    /**
     * Docker 安装结果数据类。
     */
    @Data
    @Builder
    public static class DockerInstallationResult {
        /**
         * 是否安装成功
         */
        private boolean success;
        /**
         * 结果消息
         */
        private String message;
        /**
         * 已安装版本
         */
        private String installedVersion;
        /**
         * 安装方式描述
         */
        private String installationMethod;
    }
}
