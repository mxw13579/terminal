package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Docker 镜像加速器配置服务。
 * <p>
 * 通过 SSH 连接，以非阻塞的方式异步配置 Docker 的国内镜像加速器，旨在提高镜像拉取速度。
 * 所有操作都遵循异步链式调用，避免了线程阻塞，提高了系统资源的利用效率。
 * </p>
 *
 * @author Claude (Refactored)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerMirrorService {

    private final SshCommandService sshCommandService;
    private final GeolocationDetectionService geolocationDetectionService;

    private static final String DOCKER_DAEMON_JSON_PATH = "/etc/docker/daemon.json";
    private static final String TEST_IMAGE = "hello-world:latest";
    private static final int DOCKER_RESTART_POLL_INTERVAL_MS = 1000;
    private static final int DOCKER_RESTART_MAX_ATTEMPTS = 10;


    /**
     * 自动检测地理位置并配置 Docker 镜像加速器。
     * <p>
     * 此方法首先异步检测服务器地理位置。如果判定位于中国大陆，则继续异步配置国内镜像加速器；
     * 否则，将跳过配置，使用 Docker 官方源。整个过程是完全非阻塞的。
     *
     * @param connection SSH 连接信息。
     * @return 一个 {@link CompletableFuture}，包含配置结果 {@link DockerMirrorConfigResult}。
     */
    public CompletableFuture<DockerMirrorConfigResult> configureMirror(SshConnection connection) {
        // 异步检测地理位置
        return geolocationDetectionService.detectGeolocation(connection, msg -> log.info("地理位置检测: {}", msg))
                // 使用 thenCompose 将检测结果传递给下一步配置，形成非阻塞链
                .thenCompose(geoInfo -> {
                    log.info("地理位置检测结果: isUseChineseMirror={}", geoInfo.isUseChineseMirror());
                    // 根据结果调用主配置方法
                    return configureDockerMirror(connection, geoInfo.isUseChineseMirror(),
                            msg -> log.info("Docker镜像配置: {}", msg));
                })
                // 统一处理链中任何环节的异常
                .exceptionally(ex -> {
                    log.error("自动配置Docker镜像失败", ex);
                    return DockerMirrorConfigResult.builder()
                            .success(false)
                            .message("自动配置失败: " + ex.getMessage())
                            .build();
                });
    }

    /**
     * 异步配置 Docker 镜像加速器。
     * <p>
     * 根据 {@code useChineseMirror} 参数决定是否配置国内镜像源。
     * 此方法构建了一个完整的非阻塞异步链，依次执行检查、查找、写入、重启和验证等步骤。
     *
     * @param connection       SSH 连接信息。
     * @param useChineseMirror 如果为 true，则配置国内镜像加速器；否则跳过。
     * @param progressCallback 用于实时反馈操作进度的回调函数。
     * @return 一个 {@link CompletableFuture}，包含配置结果 {@link DockerMirrorConfigResult}。
     */
    public CompletableFuture<DockerMirrorConfigResult> configureDockerMirror(SshConnection connection,
                                                                             boolean useChineseMirror,
                                                                             Consumer<String> progressCallback) {
        if (!useChineseMirror) {
            progressCallback.accept("跳过Docker镜像加速器配置（使用官方源）");
            return CompletableFuture.completedFuture(DockerMirrorConfigResult.builder()
                    .success(true)
                    .message("使用Docker官方源，无需配置镜像加速器")
                    .configuredMirrors("")
                    .build());
        }

        progressCallback.accept("正在配置Docker国内镜像加速器...");

        return isDockerMirrorAlreadyConfigured(connection)
                .thenCompose(isConfigured -> {
                    if (isConfigured) {
                        progressCallback.accept("检测到Docker已配置镜像加速器，跳过配置");
                        return getCurrentDockerMirrors(connection).thenApply(mirrors ->
                                DockerMirrorConfigResult.builder()
                                        .success(true)
                                        .message("Docker镜像加速器已配置")
                                        .configuredMirrors(mirrors)
                                        .build());
                    } else {
                        return findAvailableMirrors(connection, progressCallback)
                                .thenCompose(availableMirrors -> {
                                    if (availableMirrors.isEmpty()) {
                                        log.warn("所有候选Docker镜像加速器均无法访问，将使用官方源。");
                                        progressCallback.accept("警告：所有国内镜像加速器均无法访问，将跳过配置。");
                                        return CompletableFuture.completedFuture(DockerMirrorConfigResult.builder()
                                                .success(true) // 流程成功完成，但未配置
                                                .message("所有国内镜像源均不可用，已跳过加速器配置")
                                                .configuredMirrors("无 (已尝试配置但均不可用)")
                                                .build());
                                    }
                                    return writeDockerConfig(connection, availableMirrors, progressCallback)
                                            .thenCompose(v -> restartDockerService(connection, progressCallback))
                                            .thenCompose(v -> verifyDockerMirrorConfig(connection))
                                            .thenApply(configApplied -> {
                                                String configuredMirrorsString = String.join(", ", availableMirrors);
                                                return DockerMirrorConfigResult.builder()
                                                        .success(configApplied)
                                                        .message(configApplied ? "Docker镜像加速器配置成功" : "配置可能未完全生效，请检查Docker服务状态")
                                                        .configuredMirrors(configuredMirrorsString)
                                                        .configFilePath(DOCKER_DAEMON_JSON_PATH)
                                                        .build();
                                            });
                                });
                    }
                })
                .exceptionally(ex -> {
                    log.error("配置Docker镜像加速器失败", ex);
                    progressCallback.accept("配置Docker镜像加速器失败: " + ex.getMessage());
                    return DockerMirrorConfigResult.builder()
                            .success(false)
                            .message("配置失败: " + ex.getMessage())
                            .build();
                });
    }

    /**
     * 异步检查并修复可能损坏的 Docker 配置文件 (daemon.json)。
     * <p>
     * 此方法首先验证配置文件是否为有效的 JSON。如果无效或不存在，
     * 它将备份旧文件，并使用可用的国内镜像源重新生成配置文件。
     *
     * @param connection       SSH 连接信息。
     * @param progressCallback 用于实时反馈操作进度的回调函数。
     * @return 一个 {@link CompletableFuture}，其结果为布尔值，表示修复操作是否成功。
     */
    public CompletableFuture<Boolean> checkAndRepairDockerConfig(SshConnection connection, Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                progressCallback.accept("检查Docker配置文件格式...");
                String command = String.format("sudo python3 -m json.tool %s > /dev/null 2>&1", DOCKER_DAEMON_JSON_PATH);
                CommandResult checkResult = sshCommandService.executeInternal(connection.getJschSession(), command);
                if (checkResult.exitStatus() == 0) {
                    progressCallback.accept("Docker配置文件格式正确");
                    return true;
                }
                // 抛出特定异常以便在 exceptionallyCompose 中捕获和处理
                throw new RuntimeException("配置文件格式错误或不存在");
            } catch (Exception e) {
                // 将所有异常包装为 CompletionException 以在异步链中传递
                throw new CompletionException(e);
            }
        }).exceptionallyCompose(ex -> {
            // 检查是否是我们主动抛出的修复信号
            if (ex.getCause() != null && "配置文件格式错误或不存在".equals(ex.getCause().getMessage())) {
                progressCallback.accept("检测到Docker配置文件损坏或不存在，尝试修复...");
                return findAvailableMirrors(connection, progressCallback)
                        .thenCompose(availableMirrors -> {
                            if (availableMirrors.isEmpty()) {
                                log.warn("修复Docker配置失败：所有候选镜像源均不可用。");
                                progressCallback.accept("警告：所有国内镜像源均不可用，无法修复配置文件。");
                                return CompletableFuture.completedFuture(false);
                            }
                            // **【关键修复】** 将同步的备份操作包装在 runAsync 中
                            CompletableFuture<Void> backupFuture = CompletableFuture.runAsync(() -> {
                                try {
                                    String backupCommand = String.format("sudo cp %s %s.backup_$(date +%%s) 2>/dev/null || true",
                                            DOCKER_DAEMON_JSON_PATH, DOCKER_DAEMON_JSON_PATH);
                                    sshCommandService.executeInternal(connection.getJschSession(), backupCommand);
                                } catch (Exception e) {
                                    throw new CompletionException("备份旧的Docker配置文件失败", e);
                                }
                            });
                            // 在备份完成后，继续写入新配置
                            return backupFuture
                                    .thenCompose(v -> writeDockerConfig(connection, availableMirrors, progressCallback))
                                    .thenApply(v -> {
                                        progressCallback.accept("Docker配置文件修复成功");
                                        return true;
                                    });
                        });
            }
            // 如果是其他未知异常，则直接失败
            throw new CompletionException(ex);
        }).exceptionally(ex -> {
            log.error("检查和修复Docker配置失败", ex);
            progressCallback.accept("修复Docker配置时发生错误: " + ex.getMessage());
            return false;
        });
    }


    /**
     * 异步测试 Docker 镜像拉取速度。
     * <p>
     * 通过拉取一个轻量级镜像 (hello-world) 来衡量当前镜像源的性能。
     *
     * @param connection       SSH 连接信息。
     * @param progressCallback 进度回调函数。
     * @return 一个 {@link CompletableFuture}，返回 {@link DockerMirrorTestResult}。
     */
    public CompletableFuture<DockerMirrorTestResult> testDockerMirrorSpeed(SshConnection connection, Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                progressCallback.accept("测试Docker镜像拉取速度...");
                long startTime = System.currentTimeMillis();
                CommandResult pullResult = sshCommandService.executeInternal(connection.getJschSession(), "sudo docker pull " + TEST_IMAGE);
                long duration = System.currentTimeMillis() - startTime;

                boolean success = pullResult.exitStatus() == 0;
                String message = success ? String.format("镜像拉取成功，耗时: %d 毫秒", duration) : "镜像拉取失败: " + pullResult.stderr();
                progressCallback.accept(message);

                if (success) {
                    sshCommandService.executeInternal(connection.getJschSession(), "sudo docker rmi " + TEST_IMAGE + " > /dev/null 2>&1 || true");
                }

                return DockerMirrorTestResult.builder()
                        .success(success)
                        .pullTimeMs(duration)
                        .message(message)
                        .testImage(TEST_IMAGE)
                        .build();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).exceptionally(ex -> {
            log.error("测试Docker镜像速度失败", ex);
            progressCallback.accept("测试Docker镜像速度失败: " + ex.getMessage());
            return DockerMirrorTestResult.builder()
                    .success(false)
                    .pullTimeMs(-1)
                    .message("测试失败: " + ex.getMessage())
                    .testImage(TEST_IMAGE)
                    .build();
        });
    }

    /**
     * 异步获取当前 Docker 配置的镜像源列表。
     *
     * @param connection SSH 连接信息。
     * @return 一个 {@link CompletableFuture}，包含当前配置的镜像源列表的字符串表示。
     */
    public CompletableFuture<String> getCurrentDockerMirrors(SshConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String command = "sudo docker info --format '{{json .RegistryConfig.Mirrors}}'";
                CommandResult result = sshCommandService.executeInternal(connection.getJschSession(), command);
                if (result.exitStatus() == 0 && !result.stdout().trim().isEmpty()) {
                    return result.stdout().trim().replaceAll("[\\[\\]\"]", "").replace(",", ", ");
                }
                return "未配置镜像源或使用默认配置";
            } catch (Exception e) {
                log.error("获取Docker镜像源配置失败", e);
                return "获取配置失败: " + e.getMessage();
            }
        });
    }

    private CompletableFuture<Boolean> isDockerMirrorAlreadyConfigured(SshConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String command = String.format("grep -q 'registry-mirrors' %s 2>/dev/null", DOCKER_DAEMON_JSON_PATH);
                CommandResult checkResult = sshCommandService.executeInternal(connection.getJschSession(), command);
                return checkResult.exitStatus() == 0;
            } catch (Exception e) {
                log.debug("检查Docker镜像配置时出错: {}", e.getMessage());
                return false;
            }
        });
    }

    private CompletableFuture<List<String>> findAvailableMirrors(SshConnection connection, Consumer<String> progressCallback) {
        List<String> candidateMirrors = List.of(
                "https://hub-mirror.c.163.com",
                "https://mirror.baidubce.com",
                "https://registry.docker-cn.com"
        );
        List<CompletableFuture<String>> checkFutures = candidateMirrors.stream()
                .map(mirrorUrl -> checkMirrorConnectivity(connection, mirrorUrl, progressCallback))
                .toList();

        return CompletableFuture.allOf(checkFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> checkFutures.stream()
                        .map(CompletableFuture::join)
                        .filter(Objects::nonNull)
                        .toList());
    }

    private CompletableFuture<String> checkMirrorConnectivity(SshConnection connection, String mirrorUrl, Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String host = new URI(mirrorUrl).getHost();
                String command = String.format("curl -s -L --head -m 5 %s", host);
                progressCallback.accept(String.format("正在检查镜像源 %s 的可用性...", host));
                CommandResult result = sshCommandService.executeInternal(connection.getJschSession(), command);
                if (result.exitStatus() == 0 && !result.stdout().isBlank()) {
                    progressCallback.accept(String.format("镜像源 %s 可用", host));
                    return mirrorUrl;
                }
                progressCallback.accept(String.format("镜像源 %s 不可用，将被忽略", host));
                return null;
            } catch (URISyntaxException e) {
                log.error("无效的镜像源URL: {}", mirrorUrl, e);
                return null;
            } catch (Exception e) {
                log.warn("检查镜像源 {} 可用性时出错: {}", mirrorUrl, e.getMessage());
                progressCallback.accept(String.format("检查镜像源 %s 时出错，将被忽略", mirrorUrl));
                return null;
            }
        });
    }

    private CompletableFuture<Void> writeDockerConfig(SshConnection connection, List<String> mirrors, Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            try {
                progressCallback.accept("创建Docker配置目录...");
                sshCommandService.executeInternal(connection.getJschSession(), "sudo mkdir -p /etc/docker");

                String daemonJsonContent = generateDaemonJsonContent(mirrors);
                progressCallback.accept("使用可用镜像源写入Docker配置...");

                // 使用 heredoc 格式，更安全地写入多行文本
                String writeConfigCommand = String.format(
                        "sudo tee %s <<'EOF'\n%s\nEOF", DOCKER_DAEMON_JSON_PATH, daemonJsonContent);

                CommandResult writeResult = sshCommandService.executeInternal(connection.getJschSession(), writeConfigCommand);
                if (writeResult.exitStatus() != 0) {
                    throw new RuntimeException("写入Docker配置文件失败: " + writeResult.stderr());
                }
            } catch (Exception e) {
                throw new CompletionException("写入Docker配置时发生异常", e);
            }
        });
    }

    private String generateDaemonJsonContent(List<String> mirrors) {
        String mirrorListJson = mirrors.stream()
                .map(mirror -> "    \"" + mirror + "\"")
                .collect(Collectors.joining(",\n"));
        return """
               {
                 "registry-mirrors": [
               %s
                 ]
               }
               """.formatted(mirrorListJson);
    }

    private CompletableFuture<Void> restartDockerService(SshConnection connection, Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            try {
                progressCallback.accept("重新加载 systemd 配置...");
                sshCommandService.executeInternal(connection.getJschSession(), "sudo systemctl daemon-reload");

                progressCallback.accept("重启Docker服务以应用镜像加速配置...");
                CommandResult restartResult = sshCommandService.executeInternal(connection.getJschSession(), "sudo systemctl restart docker");
                if (restartResult.exitStatus() != 0) {
                    throw new RuntimeException("重启Docker服务失败: " + restartResult.stderr());
                }

                progressCallback.accept("等待Docker服务启动...");
                // 使用轮询代替固定 sleep，更健壮
                for (int i = 0; i < DOCKER_RESTART_MAX_ATTEMPTS; i++) {
                    TimeUnit.MILLISECONDS.sleep(DOCKER_RESTART_POLL_INTERVAL_MS);
                    CommandResult statusResult = sshCommandService.executeInternal(connection.getJschSession(), "sudo systemctl is-active docker");
                    if (statusResult.exitStatus() == 0 && "active".equals(statusResult.stdout().trim())) {
                        progressCallback.accept("Docker服务已成功启动。");
                        return;
                    }
                }
                throw new RuntimeException("Docker服务重启后状态异常或超时");
            } catch (Exception e) {
                throw new CompletionException("重启并验证Docker服务时发生异常", e);
            }
        });
    }

    private CompletableFuture<Boolean> verifyDockerMirrorConfig(SshConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String command = "sudo docker info --format '{{.RegistryConfig.Mirrors}}'";
                CommandResult infoResult = sshCommandService.executeInternal(connection.getJschSession(), command);
                // 检查命令是否成功执行，并且输出中是否包含我们配置的镜像之一
                return infoResult.exitStatus() == 0 && infoResult.stdout().contains("hub-mirror.c.163.com");
            } catch (Exception e) {
                log.warn("验证Docker镜像配置时出错: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * Docker 镜像配置结果的数据传输对象。
     */
    @Data
    @Builder
    public static class DockerMirrorConfigResult {
        private boolean success;
        private String message;
        private String configuredMirrors;
        private String configFilePath;
    }

    /**
     * Docker 镜像拉取速度测试结果的数据传输对象。
     */
    @Data
    @Builder
    public static class DockerMirrorTestResult {
        private boolean success;
        private long pullTimeMs;
        private String message;
        private String testImage;
    }
}
