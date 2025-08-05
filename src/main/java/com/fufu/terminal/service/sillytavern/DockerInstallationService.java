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
 * Docker安装服务
 * 负责检测和安装Docker，支持多种Linux发行版
 * 基于linux-silly-tavern-docker-deploy.sh脚本的Docker安装功能
 * 
 * @author Claude
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerInstallationService {

    private final SshCommandService sshCommandService;

    /**
     * 检查Docker是否已安装
     * 
     * @param connection SSH连接
     * @return Docker安装状态信息
     */
    public CompletableFuture<DockerInstallationStatus> checkDockerInstallation(SshConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("检查Docker安装状态");
                
                // 检查docker命令是否存在
                CommandResult dockerCheck = sshCommandService.executeCommand(connection.getJschSession(), 
                    "command -v docker &> /dev/null");
                
                if (dockerCheck.exitStatus() != 0) {
                    return DockerInstallationStatus.builder()
                        .installed(false)
                        .version("未安装")
                        .message("Docker未安装")
                        .build();
                }
                
                // 获取Docker版本信息
                CommandResult versionResult = sshCommandService.executeCommand(connection.getJschSession(), 
                    "docker --version");
                
                String version = versionResult.exitStatus() == 0 ? 
                    versionResult.stdout().trim() : "版本信息获取失败";
                
                // 检查Docker服务状态
                CommandResult serviceCheck = sshCommandService.executeCommand(connection.getJschSession(), 
                    "sudo systemctl is-active docker");
                
                boolean serviceRunning = serviceCheck.exitStatus() == 0 && 
                    "active".equals(serviceCheck.stdout().trim());
                
                return DockerInstallationStatus.builder()
                    .installed(true)
                    .version(version)
                    .serviceRunning(serviceRunning)
                    .message(serviceRunning ? "Docker已安装且运行正常" : "Docker已安装但服务未启动")
                    .build();
                
            } catch (Exception e) {
                log.error("检查Docker安装状态失败", e);
                return DockerInstallationStatus.builder()
                    .installed(false)
                    .version("检查失败")
                    .message("检查Docker状态时发生错误: " + e.getMessage())
                    .build();
            }
        });
    }

    /**
     * 安装Docker
     * 根据操作系统类型选择合适的安装方法
     * 
     * @param connection SSH连接
     * @param osInfo 操作系统信息
     * @param useChineseMirror 是否使用国内镜像源
     * @param progressCallback 进度回调函数
     * @return 安装结果
     */
    public CompletableFuture<DockerInstallationResult> installDocker(SshConnection connection,
                                                                   SystemDetectionService.SystemInfo osInfo,
                                                                   boolean useChineseMirror,
                                                                   Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                progressCallback.accept("开始安装Docker...");
                
                String osId = osInfo.getOsId().toLowerCase();
                DockerInstallationResult result;
                
                switch (osId) {
                    case "debian":
                    case "ubuntu":
                        result = installDockerOnDebianBased(connection, osId, useChineseMirror, progressCallback);
                        break;
                        
                    case "centos":
                    case "rhel":
                    case "fedora":
                        result = installDockerOnRedHatBased(connection, osId, useChineseMirror, progressCallback);
                        break;
                        
                    case "arch":
                        result = installDockerOnArch(connection, progressCallback);
                        break;
                        
                    case "alpine":
                        result = installDockerOnAlpine(connection, progressCallback);
                        break;
                        
                    case "suse":
                    case "opensuse-leap":
                    case "opensuse-tumbleweed":
                        result = installDockerOnSuse(connection, progressCallback);
                        break;
                        
                    default:
                        progressCallback.accept(String.format("不支持的操作系统: %s", osId));
                        return DockerInstallationResult.builder()
                            .success(false)
                            .message("不支持的操作系统")
                            .installedVersion("未安装")
                            .build();
                }
                
                if (result.isSuccess()) {
                    progressCallback.accept("启动并启用Docker服务...");
                    startAndEnableDockerService(connection, osId, progressCallback);
                }
                
                return result;
                
            } catch (Exception e) {
                log.error("Docker安装过程中发生异常", e);
                progressCallback.accept("Docker安装失败: " + e.getMessage());
                return DockerInstallationResult.builder()
                    .success(false)
                    .message("安装过程中发生异常: " + e.getMessage())
                    .installedVersion("未安装")
                    .build();
            }
        });
    }

    /**
     * 在Debian/Ubuntu系统上安装Docker
     */
    private DockerInstallationResult installDockerOnDebianBased(SshConnection connection, 
                                                              String osName,
                                                              boolean useChineseMirror,
                                                              Consumer<String> progressCallback) throws Exception {
        
        progressCallback.accept(String.format("在 %s 系统上安装 Docker...", osName));
        
        // 确定Docker仓库URL
        String dockerRepoUrl = useChineseMirror ? 
            "https://mirrors.aliyun.com/docker-ce" : "https://download.docker.com";
        
        progressCallback.accept("使用Docker安装源: " + dockerRepoUrl);
        
        // 移除旧版本Docker
        progressCallback.accept("移除旧版本Docker...");
        sshCommandService.executeCommand(connection.getJschSession(), 
            "sudo apt-get remove -y docker docker-engine docker.io containerd runc || true");
        
        // 安装依赖
        progressCallback.accept("安装必要依赖...");
        CommandResult depsResult = sshCommandService.executeCommand(connection.getJschSession(), 
            "sudo apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release");
        
        if (depsResult.exitStatus() != 0) {
            throw new RuntimeException("安装依赖失败: " + depsResult.stderr());
        }
        
        // 添加Docker官方GPG密钥
        progressCallback.accept("添加Docker GPG密钥...");
        sshCommandService.executeCommand(connection.getJschSession(), "sudo install -m 0755 -d /etc/apt/keyrings");
        sshCommandService.executeCommand(connection.getJschSession(), 
            String.format("curl -fsSL \"%s/linux/%s/gpg\" | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg", dockerRepoUrl, osName));
        sshCommandService.executeCommand(connection.getJschSession(), "sudo chmod a+r /etc/apt/keyrings/docker.gpg");
        
        // 添加Docker仓库
        progressCallback.accept("添加Docker软件仓库...");
        String repoCommand = String.format(
            "echo \"deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] %s/linux/%s $(lsb_release -cs) stable\" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null",
            dockerRepoUrl, osName);
        sshCommandService.executeCommand(connection.getJschSession(), repoCommand);
        
        // 更新软件包索引
        progressCallback.accept("更新软件包索引...");
        CommandResult updateResult = sshCommandService.executeCommand(connection.getJschSession(), "sudo apt-get update");
        if (updateResult.exitStatus() != 0) {
            throw new RuntimeException("更新软件包索引失败: " + updateResult.stderr());
        }
        
        // 安装Docker
        progressCallback.accept("安装Docker CE...");
        CommandResult installResult = sshCommandService.executeCommand(connection.getJschSession(), 
            "sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin");
        
        if (installResult.exitStatus() != 0) {
            throw new RuntimeException("Docker安装失败: " + installResult.stderr());
        }
        
        // 验证安装
        CommandResult versionResult = sshCommandService.executeCommand(connection.getJschSession(), "docker --version");
        String installedVersion = versionResult.exitStatus() == 0 ? versionResult.stdout().trim() : "版本获取失败";
        
        return DockerInstallationResult.builder()
            .success(versionResult.exitStatus() == 0)
            .message("Docker安装成功")
            .installedVersion(installedVersion)
            .installationMethod("APT官方仓库")
            .build();
    }

    /**
     * 在RedHat系统上安装Docker
     */
    private DockerInstallationResult installDockerOnRedHatBased(SshConnection connection,
                                                              String osName,
                                                              boolean useChineseMirror,
                                                              Consumer<String> progressCallback) throws Exception {
        
        progressCallback.accept(String.format("在 %s 系统上安装 Docker...", osName));
        
        String pkgManager = "fedora".equals(osName) ? "dnf" : "yum";
        
        // 移除旧版本Docker
        progressCallback.accept("移除旧版本Docker...");
        sshCommandService.executeCommand(connection.getJschSession(), 
            String.format("sudo %s remove -y docker docker-client docker-client-latest docker-common docker-latest docker-latest-logrotate docker-logrotate docker-engine || true", pkgManager));
        
        // 安装依赖
        progressCallback.accept("安装yum-utils...");
        CommandResult depsResult = sshCommandService.executeCommand(connection.getJschSession(), 
            String.format("sudo %s install -y %s-utils", pkgManager, pkgManager));
        
        if (depsResult.exitStatus() != 0) {
            throw new RuntimeException("安装依赖失败: " + depsResult.stderr());
        }
        
        // 添加Docker仓库
        String repoUrl;
        if ("fedora".equals(osName)) {
            repoUrl = useChineseMirror ? 
                "https://mirrors.aliyun.com/docker-ce/linux/fedora/docker-ce.repo" :
                "https://download.docker.com/linux/fedora/docker-ce.repo";
        } else {
            repoUrl = useChineseMirror ? 
                "http://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo" :
                "https://download.docker.com/linux/centos/docker-ce.repo";
        }
        
        progressCallback.accept("添加Docker仓库: " + repoUrl);
        CommandResult repoResult = sshCommandService.executeCommand(connection.getJschSession(), 
            String.format("sudo %s-config-manager --add-repo %s", pkgManager, repoUrl));
        
        if (repoResult.exitStatus() != 0) {
            throw new RuntimeException("添加Docker仓库失败: " + repoResult.stderr());
        }
        
        // 安装Docker
        progressCallback.accept("安装Docker CE...");
        CommandResult installResult = sshCommandService.executeCommand(connection.getJschSession(), 
            String.format("sudo %s install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin", pkgManager));
        
        if (installResult.exitStatus() != 0) {
            throw new RuntimeException("Docker安装失败: " + installResult.stderr());
        }
        
        // 验证安装
        CommandResult versionResult = sshCommandService.executeCommand(connection.getJschSession(), "docker --version");
        String installedVersion = versionResult.exitStatus() == 0 ? versionResult.stdout().trim() : "版本获取失败";
        
        return DockerInstallationResult.builder()
            .success(versionResult.exitStatus() == 0)
            .message("Docker安装成功")
            .installedVersion(installedVersion)
            .installationMethod(pkgManager.toUpperCase() + "官方仓库")
            .build();
    }

    /**
     * 在Arch Linux上安装Docker
     */
    private DockerInstallationResult installDockerOnArch(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("在Arch Linux上安装Docker...");
        
        CommandResult installResult = sshCommandService.executeCommand(connection.getJschSession(), 
            "sudo pacman -S --noconfirm docker docker-compose");
        
        if (installResult.exitStatus() != 0) {
            throw new RuntimeException("Docker安装失败: " + installResult.stderr());
        }
        
        CommandResult versionResult = sshCommandService.executeCommand(connection.getJschSession(), "docker --version");
        String installedVersion = versionResult.exitStatus() == 0 ? versionResult.stdout().trim() : "版本获取失败";
        
        return DockerInstallationResult.builder()
            .success(versionResult.exitStatus() == 0)
            .message("Docker安装成功")
            .installedVersion(installedVersion)
            .installationMethod("Pacman官方仓库")
            .build();
    }

    /**
     * 在Alpine Linux上安装Docker
     */
    private DockerInstallationResult installDockerOnAlpine(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("在Alpine Linux上安装Docker...");
        
        CommandResult installResult = sshCommandService.executeCommand(connection.getJschSession(), 
            "sudo apk add docker docker-compose");
        
        if (installResult.exitStatus() != 0) {
            throw new RuntimeException("Docker安装失败: " + installResult.stderr());
        }
        
        CommandResult versionResult = sshCommandService.executeCommand(connection.getJschSession(), "docker --version");
        String installedVersion = versionResult.exitStatus() == 0 ? versionResult.stdout().trim() : "版本获取失败";
        
        return DockerInstallationResult.builder()
            .success(versionResult.exitStatus() == 0)
            .message("Docker安装成功")
            .installedVersion(installedVersion)
            .installationMethod("APK官方仓库")
            .build();
    }

    /**
     * 在SUSE系统上安装Docker
     */
    private DockerInstallationResult installDockerOnSuse(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("在SUSE系统上安装Docker...");
        
        CommandResult installResult = sshCommandService.executeCommand(connection.getJschSession(), 
            "sudo zypper install -y docker docker-compose");
        
        if (installResult.exitStatus() != 0) {
            throw new RuntimeException("Docker安装失败: " + installResult.stderr());
        }
        
        CommandResult versionResult = sshCommandService.executeCommand(connection.getJschSession(), "docker --version");
        String installedVersion = versionResult.exitStatus() == 0 ? versionResult.stdout().trim() : "版本获取失败";
        
        return DockerInstallationResult.builder()
            .success(versionResult.exitStatus() == 0)
            .message("Docker安装成功")
            .installedVersion(installedVersion)
            .installationMethod("Zypper官方仓库")
            .build();
    }

    /**
     * 启动并启用Docker服务
     */
    private void startAndEnableDockerService(SshConnection connection, String osName, Consumer<String> progressCallback) throws Exception {
        if ("alpine".equals(osName)) {
            // Alpine使用OpenRC
            progressCallback.accept("启动Docker服务 (OpenRC)...");
            sshCommandService.executeCommand(connection.getJschSession(), "sudo rc-update add docker boot");
            sshCommandService.executeCommand(connection.getJschSession(), "sudo service docker start");
        } else {
            // 其他发行版使用systemd
            progressCallback.accept("启动并启用Docker服务...");
            CommandResult startResult = sshCommandService.executeCommand(connection.getJschSession(), 
                "sudo systemctl start docker && sudo systemctl enable docker");
            
            if (startResult.exitStatus() != 0) {
                log.warn("Docker服务启动可能有问题: {}", startResult.stderr());
            }
        }
    }

    /**
     * Docker安装状态数据类
     */
    @lombok.Data
    @lombok.Builder
    public static class DockerInstallationStatus {
        private boolean installed;
        private String version;
        private boolean serviceRunning;
        private String message;
    }

    /**
     * Docker安装结果数据类
     */
    @lombok.Data
    @lombok.Builder
    public static class DockerInstallationResult {
        private boolean success;
        private String message;
        private String installedVersion;
        private String installationMethod;
    }
}