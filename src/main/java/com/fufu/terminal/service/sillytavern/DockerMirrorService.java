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
    private final GeolocationDetectionService geolocationDetectionService;


    /**
     * 异步配置 Docker 镜像加速器。
     * <p>
     * 根据 {@code useChineseMirror} 参数决定是否配置国内镜像源。
     * 如果选择不配置，将直接返回成功。
     * 如果已存在镜像配置，则会跳过，避免重复操作。
     *
     * @param connection        SSH 连接信息
     * @param useChineseMirror  如果为 true，则配置国内镜像加速器；否则跳过。
     * @param progressCallback  用于实时反馈操作进度的回调函数。
     * @return 一个 CompletableFuture，包含配置结果 {@link DockerMirrorConfigResult}。
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

                if (isDockerMirrorAlreadyConfigured(connection)) {
                    progressCallback.accept("检测到Docker已配置镜像加速器，跳过配置");
                    String currentMirrors = getCurrentDockerMirrors(connection).join();
                    return DockerMirrorConfigResult.builder()
                            .success(true)
                            .message("Docker镜像加速器已配置")
                            .configuredMirrors(currentMirrors)
                            .build();
                }

                // 创建并写入 Docker 配置文件
                writeDockerConfig(connection, progressCallback);

                // 重启 Docker 服务以应用配置
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
     * 使用 JDK 17 文本块以提高可读性。
     *
     * @return 标准的 Docker 国内镜像加速器配置内容的字符串。
     */
    private String generateDaemonJsonContent() {
        return """
               {
                 "registry-mirrors": [
                   "https://hub-mirror.c.163.com",
                   "https://mirror.baidubce.com",
                   "https://registry.docker-cn.com"
                 ]
               }
               """;
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
     * 使用 'docker info --format' 命令精确获取镜像源信息，比 grep 更健壮。
     *
     * @param connection SSH 连接信息
     * @return 如果配置的镜像源已生效，返回 true，否则返回 false。
     */
    private boolean verifyDockerMirrorConfig(SshConnection connection) {
        try {
            // 使用 docker info --format 精确获取镜像配置，避免因版本更新导致输出格式变化
            String command = "sudo docker info --format '{{.RegistryConfig.Mirrors}}'";
            CommandResult infoResult = sshCommandService.executeCommand(connection.getJschSession(), command);

            // 检查命令是否成功执行，并且输出中是否包含我们配置的镜像之一
            return infoResult.exitStatus() == 0 && infoResult.stdout().contains("hub-mirror.c.163.com");

        } catch (Exception e) {
            log.warn("验证Docker镜像配置时出错: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 异步检查并修复可能损坏的 Docker 配置文件 (daemon.json)。
     * <p>
     * 此方法首先会验证 {@code /etc/docker/daemon.json} 是否为有效的 JSON 格式。
     * 如果文件不存在或格式不正确，它将备份旧文件（如果存在），
     * 然后使用标准镜像源配置重新生成该文件。
     *
     * @param connection       SSH 连接信息。
     * @param progressCallback 用于实时反馈操作进度的回调函数。
     * @return 一个 CompletableFuture，其结果为布尔值，表示修复操作是否成功。
     */
    public CompletableFuture<Boolean> checkAndRepairDockerConfig(SshConnection connection,
                                                                 Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                progressCallback.accept("检查Docker配置文件格式...");

                // 使用 python 检查 json 格式是一种常见且有效的技巧
                CommandResult checkResult = sshCommandService.executeCommand(connection.getJschSession(),
                        "sudo python3 -m json.tool /etc/docker/daemon.json > /dev/null 2>&1");

                if (checkResult.exitStatus() == 0) {
                    progressCallback.accept("Docker配置文件格式正确");
                    return true;
                }

                progressCallback.accept("检测到Docker配置文件格式错误或文件不存在，尝试修复...");

                // 备份可能已损坏的配置文件
                sshCommandService.executeCommand(connection.getJschSession(),
                        "sudo cp /etc/docker/daemon.json /etc/docker/daemon.json.backup_$(date +%s) 2>/dev/null || true");

                // 重新生成并写入配置文件
                writeDockerConfig(connection, progressCallback);

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
     * 异步获取当前 Docker 配置的镜像源列表。
     *
     * @param connection SSH 连接信息。
     * @return 一个 CompletableFuture，包含当前配置的镜像源列表的字符串表示。
     *         如果无法获取或未配置，则返回相应的提示信息。
     */
    public CompletableFuture<String> getCurrentDockerMirrors(SshConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 使用 --format 精确提取镜像列表，比 grep 更稳定
                String command = "sudo docker info --format '{{json .RegistryConfig.Mirrors}}'";
                CommandResult result = sshCommandService.executeCommand(connection.getJschSession(), command);

                if (result.exitStatus() == 0 && !result.stdout().trim().isEmpty()) {
                    // 对输出进行清理，去除可能存在的方括号和引号
                    return result.stdout().trim().replaceAll("[\\[\\]\"]", "").replace(",", ", ");
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
    public CompletableFuture<DockerMirrorTestResult> testDockerMirrorSpeed(
            SshConnection connection,
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
     * 自动配置 Docker 镜像加速器。
     * <p>
     * 此便捷方法会自动检测服务器的地理位置。如果判定位于中国大陆，
     * 则会自动配置国内镜像加速器，否则将使用 Docker 官方源。
     *
     * @param connection SSH 连接信息。
     * @return 一个 CompletableFuture，包含配置结果 {@link DockerMirrorConfigResult}。
     */
    public CompletableFuture<DockerMirrorConfigResult> configureMirror(SshConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 使用注入的 service 进行地理位置检测
                GeolocationDetectionService.GeolocationInfo geoInfo = geolocationDetectionService.detectGeolocation(connection,
                        msg -> log.info("地理位置检测: {}", msg)).join();

                // 根据检测结果调用主配置方法
                return configureDockerMirror(connection, geoInfo.isUseChineseMirror(),
                        msg -> log.info("Docker镜像配置: {}", msg)).join();

            } catch (Exception e) {
                log.error("自动配置Docker镜像失败", e);
                return DockerMirrorConfigResult.builder()
                        .success(false)
                        .message("自动配置Docker镜像失败: " + e.getMessage())
                        .build();
            }
        });
    }

    /**
     * 将 Docker 配置写入远程服务器的 /etc/docker/daemon.json 文件。
     *
     * @param connection       SSH 连接信息。
     * @param progressCallback 用于进度反馈的回调。
     * @throws RuntimeException 如果写入文件失败。
     */
    private void writeDockerConfig(SshConnection connection, Consumer<String> progressCallback) throws InterruptedException {
        // 创建Docker配置目录
        progressCallback.accept("创建Docker配置目录...");
        sshCommandService.executeCommand(connection.getJschSession(), "sudo mkdir -p /etc/docker");

        // 生成daemon.json配置文件
        String daemonJsonContent = generateDaemonJsonContent();
        progressCallback.accept("写入Docker镜像加速器配置...");

        // 使用 tee 和 here document 安全地写入多行文本
        String writeConfigCommand = String.format(
                "echo '%s' | sudo tee /etc/docker/daemon.json", daemonJsonContent);

        CommandResult writeResult = sshCommandService.executeCommand(connection.getJschSession(), writeConfigCommand);

        if (writeResult.exitStatus() != 0) {
            throw new RuntimeException("写入Docker配置文件失败: " + writeResult.stderr());
        }
    }
}
