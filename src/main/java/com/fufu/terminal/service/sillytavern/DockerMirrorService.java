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
 * Docker 镜像加速器配置服务。
 * <p>
 * 负责通过 SSH 配置 Docker 的国内镜像加速器，提高镜像拉取速度。
 * 基于 linux-silly-tavern-docker-deploy.sh 脚本的加速器配置功能。
 * </p>
 *
 * @author Claude
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerMirrorService {

    private final SshCommandService sshCommandService;

    /**
     * 配置 Docker 镜像加速器。
     *
     * @param connection        SSH 连接信息
     * @param useChineseMirror  是否使用国内镜像加速器
     * @param progressCallback  进度回调函数（用于实时反馈进度消息）
     * @return 配置结果的异步任务，返回 {@link DockerMirrorConfigResult}
     */
    public CompletableFuture<DockerMirrorConfigResult> configureDockerMirror(SshConnection connection,
                                                                             boolean useChineseMirror,
                                                                             Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!useChineseMirror) {
                    progressCallback.accept("跳过Docker镜像加速器配置（使用官方源）");
                    return DockerMirrorConfigResult.builder()
                            .success(true)
                            .message("使用Docker官方源，无需配置镜像加速器")
                            .configuredMirrors("")
                            .build();
                }

                progressCallback.accept("正在配置Docker国内镜像加速器...");

                // 检查是否已经配置了镜像加速器
                if (isDockerMirrorAlreadyConfigured(connection)) {
                    progressCallback.accept("检测到Docker已配置镜像加速器，跳过配置");
                    return DockerMirrorConfigResult.builder()
                            .success(true)
                            .message("Docker镜像加速器已配置")
                            .configuredMirrors("已存在的配置")
                            .build();
                }

                // 创建Docker配置目录
                progressCallback.accept("创建Docker配置目录...");
                sshCommandService.executeCommand(connection.getJschSession(), "sudo mkdir -p /etc/docker");

                // 生成daemon.json配置文件
                String daemonJsonContent = generateDaemonJsonContent();
                progressCallback.accept("写入Docker镜像加速器配置...");

                String writeConfigCommand = String.format(
                        "sudo tee /etc/docker/daemon.json <<-'EOF'\n%s\nEOF", daemonJsonContent);

                CommandResult writeResult = sshCommandService.executeCommand(connection.getJschSession(), writeConfigCommand);

                if (writeResult.exitStatus() != 0) {
                    throw new RuntimeException("写入Docker配置文件失败: " + writeResult.stderr());
                }

                // 重启Docker服务以应用配置
                progressCallback.accept("重启Docker服务以应用镜像加速配置...");
                restartDockerService(connection);

                // 验证配置是否生效
                boolean configApplied = verifyDockerMirrorConfig(connection);

                return DockerMirrorConfigResult.builder()
                        .success(configApplied)
                        .message(configApplied ? "Docker镜像加速器配置成功" : "配置可能未完全生效，请检查Docker服务状态")
                        .configuredMirrors("网易云、百度云、Docker中国")
                        .configFilePath("/etc/docker/daemon.json")
                        .build();

            } catch (Exception e) {
                log.error("配置Docker镜像加速器失败", e);
                progressCallback.accept("配置Docker镜像加速器失败: " + e.getMessage());
                return DockerMirrorConfigResult.builder()
                        .success(false)
                        .message("配置失败: " + e.getMessage())
                        .configuredMirrors("")
                        .build();
            }
        });
    }

    /**
     * 检查 Docker 镜像加速器是否已配置。
     *
     * @param connection SSH 连接信息
     * @return 如果已配置镜像加速器则返回 true，否则返回 false
     */
    private boolean isDockerMirrorAlreadyConfigured(SshConnection connection) {
        try {
            CommandResult checkResult = sshCommandService.executeCommand(connection.getJschSession(),
                    "grep -q \"registry-mirrors\" /etc/docker/daemon.json 2>/dev/null");
            return checkResult.exitStatus() == 0;
        } catch (Exception e) {
            log.debug("检查Docker镜像配置时出错: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 生成 daemon.json 配置文件内容。
     *
     * @return 标准的 Docker 国内镜像加速器配置内容
     */
    private String generateDaemonJsonContent() {
        return "{\n" +
                "  \"registry-mirrors\": [\n" +
                "    \"https://hub-mirror.c.163.com\",\n" +
                "    \"https://mirror.baidubce.com\",\n" +
                "    \"https://registry.docker-cn.com\"\n" +
                "  ]\n" +
                "}";
    }

    /**
     * 重启 Docker 服务。
     *
     * @param connection SSH 连接信息
     * @throws Exception 如果重启失败则抛出异常
     */
    private void restartDockerService(SshConnection connection) throws Exception {
        // 重新加载systemd配置
        sshCommandService.executeCommand(connection.getJschSession(), "sudo systemctl daemon-reload");

        // 重启Docker服务
        CommandResult restartResult = sshCommandService.executeCommand(connection.getJschSession(),
                "sudo systemctl restart docker");

        if (restartResult.exitStatus() != 0) {
            throw new RuntimeException("重启Docker服务失败: " + restartResult.stderr());
        }

        // 等待Docker服务完全启动
        Thread.sleep(3000);

        // 验证Docker服务状态
        CommandResult statusResult = sshCommandService.executeCommand(connection.getJschSession(),
                "sudo systemctl is-active docker");

        if (statusResult.exitStatus() != 0 || !"active".equals(statusResult.stdout().trim())) {
            throw new RuntimeException("Docker服务重启后状态异常");
        }
    }

    /**
     * 验证 Docker 镜像加速器配置是否生效。
     *
     * @param connection SSH 连接信息
     * @return 如果配置生效返回 true，否则返回 false
     */
    private boolean verifyDockerMirrorConfig(SshConnection connection) {
        try {
            // 使用docker info命令检查镜像源配置
            CommandResult infoResult = sshCommandService.executeCommand(connection.getJschSession(),
                    "sudo docker info | grep -A 5 'Registry Mirrors'");

            return infoResult.exitStatus() == 0 &&
                    infoResult.stdout().contains("hub-mirror.c.163.com");

        } catch (Exception e) {
            log.warn("验证Docker镜像配置时出错: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查并修复 Docker 配置文件。
     * <p>
     * 如果配置文件格式有问题，尝试自动修复。
     * </p>
     *
     * @param connection       SSH 连接信息
     * @param progressCallback 进度回调函数
     * @return 异步任务，返回修复是否成功
     */
    public CompletableFuture<Boolean> checkAndRepairDockerConfig(SshConnection connection,
                                                                 Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                progressCallback.accept("检查Docker配置文件格式...");

                // 检查daemon.json是否存在且格式正确
                CommandResult checkResult = sshCommandService.executeCommand(connection.getJschSession(),
                        "sudo python3 -m json.tool /etc/docker/daemon.json > /dev/null 2>&1");

                if (checkResult.exitStatus() == 0) {
                    progressCallback.accept("Docker配置文件格式正确");
                    return true;
                }

                progressCallback.accept("检测到Docker配置文件格式错误，尝试修复...");

                // 备份原配置文件
                sshCommandService.executeCommand(connection.getJschSession(),
                        "sudo cp /etc/docker/daemon.json /etc/docker/daemon.json.backup 2>/dev/null || true");

                // 重新生成配置文件
                String daemonJsonContent = generateDaemonJsonContent();
                String writeConfigCommand = String.format(
                        "sudo tee /etc/docker/daemon.json <<-'EOF'\n%s\nEOF", daemonJsonContent);

                CommandResult writeResult = sshCommandService.executeCommand(connection.getJschSession(), writeConfigCommand);

                if (writeResult.exitStatus() != 0) {
                    progressCallback.accept("修复Docker配置文件失败");
                    return false;
                }

                progressCallback.accept("Docker配置文件修复成功");
                return true;

            } catch (Exception e) {
                log.error("检查和修复Docker配置失败", e);
                progressCallback.accept("检查Docker配置时发生错误: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * 获取当前 Docker 镜像源配置。
     *
     * @param connection SSH 连接信息
     * @return 异步任务，返回当前配置的镜像源列表（字符串形式）
     */
    public CompletableFuture<String> getCurrentDockerMirrors(SshConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                CommandResult result = sshCommandService.executeCommand(connection.getJschSession(),
                        "sudo docker info | grep -A 10 'Registry Mirrors' | tail -n +2");

                if (result.exitStatus() == 0 && !result.stdout().trim().isEmpty()) {
                    return result.stdout().trim();
                } else {
                    return "未配置镜像源或使用默认配置";
                }

            } catch (Exception e) {
                log.error("获取Docker镜像源配置失败", e);
                return "获取配置失败: " + e.getMessage();
            }
        });
    }

    /**
     * 测试 Docker 镜像拉取速度。
     * <p>
     * 拉取一个小镜像（hello-world）来测试镜像源的速度。
     * </p>
     *
     * @param connection       SSH 连接信息
     * @param progressCallback 进度回调函数
     * @return 异步任务，返回 {@link DockerMirrorTestResult}
     */
    public CompletableFuture<DockerMirrorTestResult> testDockerMirrorSpeed(SshConnection connection,
                                                                           Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                progressCallback.accept("测试Docker镜像拉取速度...");

                long startTime = System.currentTimeMillis();

                // 拉取一个很小的测试镜像（hello-world）
                CommandResult pullResult = sshCommandService.executeCommand(connection.getJschSession(),
                        "sudo docker pull hello-world:latest");

                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;

                boolean success = pullResult.exitStatus() == 0;
                String message = success ?
                        String.format("镜像拉取成功，耗时: %d 毫秒", duration) :
                        "镜像拉取失败: " + pullResult.stderr();

                progressCallback.accept(message);

                // 清理测试镜像
                if (success) {
                    sshCommandService.executeCommand(connection.getJschSession(),
                            "sudo docker rmi hello-world:latest > /dev/null 2>&1 || true");
                }

                return DockerMirrorTestResult.builder()
                        .success(success)
                        .pullTimeMs(duration)
                        .message(message)
                        .testImage("hello-world:latest")
                        .build();

            } catch (Exception e) {
                log.error("测试Docker镜像速度失败", e);
                progressCallback.accept("测试Docker镜像速度失败: " + e.getMessage());
                return DockerMirrorTestResult.builder()
                        .success(false)
                        .pullTimeMs(-1)
                        .message("测试失败: " + e.getMessage())
                        .testImage("hello-world:latest")
                        .build();
            }
        });
    }

    /**
     * Docker 镜像配置结果数据类。
     */
    @lombok.Data
    @lombok.Builder
    public static class DockerMirrorConfigResult {
        /**
         * 是否配置成功
         */
        private boolean success;
        /**
         * 配置结果消息
         */
        private String message;
        /**
         * 已配置的镜像源信息
         */
        private String configuredMirrors;
        /**
         * 配置文件路径
         */
        private String configFilePath;

        /**
         * 获取是否配置成功
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
    }

    /**
     * Docker 镜像测试结果数据类。
     */
    @lombok.Data
    @lombok.Builder
    public static class DockerMirrorTestResult {
        /**
         * 是否测试成功
         */
        private boolean success;
        /**
         * 拉取耗时（毫秒）
         */
        private long pullTimeMs;
        /**
         * 测试结果消息
         */
        private String message;
        /**
         * 测试镜像名称
         */
        private String testImage;
    }

    /**
     * 配置 Docker 镜像加速器（便捷方法）。
     * <p>
     * 自动检测地理位置并配置合适的镜像加速器。
     * </p>
     *
     * @param connection SSH 连接信息
     * @return 异步任务，返回 {@link DockerMirrorConfigResult}
     */
    public CompletableFuture<DockerMirrorConfigResult> configureMirror(SshConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 检测地理位置
                GeolocationDetectionService geoService = new GeolocationDetectionService(sshCommandService);
                GeolocationDetectionService.GeolocationInfo geoInfo = geoService.detectGeolocation(connection, (msg) -> {
                    log.info("地理位置检测: {}", msg);
                }).join();

                // 配置Docker镜像加速器
                return configureDockerMirror(connection, geoInfo.isUseChineseMirror(), (msg) -> {
                    log.info("Docker镜像配置: {}", msg);
                }).join();

            } catch (Exception e) {
                log.error("Docker镜像配置失败", e);
                return DockerMirrorConfigResult.builder()
                        .success(false)
                        .message("Docker镜像配置失败: " + e.getMessage())
                        .configuredMirrors("未配置")
                        .build();
            }
        });
    }
}
