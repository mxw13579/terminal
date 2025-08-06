package com.fufu.terminal.service.sillytavern;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fufu.terminal.dto.sillytavern.VersionInfoDto;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Docker 版本管理服务，负责查询 SillyTavern 镜像的最新版本、执行升级和清理操作。
 * <p>
 * 该服务提供了对 Docker 容器进行版本管理的系列功能，包括：
 * <ul>
 *     <li>从 GitHub API 获取可用的发行版本。</li>
 *     <li>查询指定容器的当前版本和最新版本信息。</li>
 *     <li>以异步方式安全地升级容器到指定版本。</li>
 *     <li>清理不再使用的旧镜像。</li>
 * </ul>
 * </p>
 * <p>
 * <b>线程安全</b>:
 * 通过为每个容器维护一个独立的 {@link ReentrantLock}，确保了对同一容器的升级操作是互斥的，
 * 防止了并发场景下的重复升级和状态冲突。
 * </p>
 *
 * @author lizelin
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerVersionService {

    /** SSH命令服务 */
    private final SshCommandService sshCommandService;
    /** RestTemplate用于远程API调用 */
    private final RestTemplate restTemplate;
    /** Jackson对象映射器 */
    private final ObjectMapper objectMapper;

    /** 并发控制：每个容器独立锁，防止重复升级 */
    private final Map<String, ReentrantLock> upgradeLocks = new ConcurrentHashMap<>();

    private static final String SILLYTAVERN_IMAGE_REPO = "ghcr.io/sillytavern/sillytavern";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/sillytavern/sillytavern/releases";
    private static final int MAX_VERSION_COUNT = 3;
    private static final String DEFAULT_VERSION = "latest";
    private static final int CONTAINER_START_RETRIES = 15; // 容器启动检查重试次数
    private static final long CONTAINER_START_INTERVAL_MS = 2000; // 容器启动检查间隔（毫秒）


    /**
     * 用于映射 GitHub Release API 响应的内部 record。
     * 使用 @JsonProperty 确保与 JSON 字段名精确匹配。
     */
    private record GitHubRelease(
            @JsonProperty("tag_name") String tagName,
            boolean prerelease,
            boolean draft
    ) {}

    /**
     * 获取指定容器的升级锁。
     *
     * @param containerName 容器名称
     * @return 对应的 ReentrantLock
     */
    private ReentrantLock getUpgradeLock(String containerName) {
        // 每个容器独立锁，确保升级操作线程安全
        return upgradeLocks.computeIfAbsent(containerName, k -> new ReentrantLock());
    }

    /**
     * 获取容器当前版本及可用最新版本信息。
     *
     * @param connection    SSH 连接
     * @param containerName 容器名称
     * @return 版本信息 DTO
     */
    public VersionInfoDto getVersionInfo(SshConnection connection, String containerName) {
        log.debug("获取版本信息，容器: {}", containerName);

        VersionInfoDto versionInfo = new VersionInfoDto();
        versionInfo.setContainerName(containerName);
        versionInfo.setLastChecked(LocalDateTime.now());

        try {
            String currentVersion = getCurrentContainerVersion(connection, containerName);
            versionInfo.setCurrentVersion(currentVersion);

            List<String> availableVersions = getAvailableVersions();
            versionInfo.setAvailableVersions(availableVersions);

            if (!availableVersions.isEmpty()) {
                versionInfo.setLatestVersion(availableVersions.get(0));
                versionInfo.setHasUpdate(!currentVersion.equals(availableVersions.get(0)));
            }
        } catch (Exception e) {
            log.error("获取版本信息失败: {}", e.getMessage(), e);
            versionInfo.setError("获取版本信息失败: " + e.getMessage());
        }

        return versionInfo;
    }

    /**
     * 获取容器当前使用的镜像版本。
     *
     * @param connection    SSH 连接
     * @param containerName 容器名称
     * @return 镜像版本号
     */
    private String getCurrentContainerVersion(SshConnection connection, String containerName) {
        try {
            String result = executeCommand(connection,
                    String.format("sudo docker inspect %s --format='{{.Config.Image}}'", containerName));
            if (result.contains(":")) {
                String[] parts = result.trim().split(":");
                return parts[parts.length - 1];
            }
            return DEFAULT_VERSION;
        } catch (Exception e) {
            log.warn("无法获取容器版本信息: {}", e.getMessage());
            return "unknown";
        }
    }

    /**
     * 从 GitHub Releases 获取最新的正式版本列表。
     * <p>
     * 此方法会调用 GitHub API，筛选出非草稿、非预发布的前 {@value #MAX_VERSION_COUNT} 个版本。
     * 结果会被缓存，以减少对外部 API 的调用频率。
     * 如果 API 调用失败，将返回一个包含 "latest", "staging", "release" 的默认列表。
     * </p>
     *
     * @return 可用版本号的列表 (例如 ["1.11.0", "1.10.3"])。
     */
    @Cacheable(value = "sillytavern-versions", unless = "#result == null || #result.isEmpty()")
    public List<String> getAvailableVersions() {
        log.debug("从 GitHub API 获取版本信息...");
        try {
            // 获取API响应并让Jackson自动映射到对象数组
            String response = restTemplate.getForObject(GITHUB_API_URL + "?per_page=10", String.class);
            GitHubRelease[] releases = objectMapper.readValue(response, GitHubRelease[].class);

            // 使用Stream API进行过滤、映射和收集
            List<String> versions = Arrays.stream(releases)
                    .filter(r -> r != null && !r.prerelease() && !r.draft() && r.tagName() != null)
                    .map(GitHubRelease::tagName)
                    .map(tagName -> tagName.startsWith("v") ? tagName.substring(1) : tagName) // 移除 'v' 前缀
                    .limit(MAX_VERSION_COUNT)
                    .collect(Collectors.toList());

            if (versions.isEmpty()) {
                log.warn("未能从 GitHub API 获取到任何有效的正式版本，将使用默认版本。");
                versions.add(DEFAULT_VERSION);
            }

            log.info("成功获取到 {} 个可用版本", versions.size());
            return versions;
        } catch (Exception e) {
            log.error("获取可用版本失败，将返回默认列表: {}", e.getMessage(), e);
            // API调用失败时的回退机制
            return List.of(DEFAULT_VERSION, "staging", "release");
        }
    }

    /**
     * 异步升级指定容器到目标版本，并清理旧镜像。
     * <p>
     * 此操作是线程安全的，通过锁机制防止对同一容器的并发升级。
     * 升级流程包括：停止当前容器、拉取新镜像、使用原配置重建容器、启动新容器、清理旧镜像。
     * 进度会通过 {@code progressCallback} 回调函数实时反馈。
     * </p>
     *
     * @param connection       SSH 连接信息
     * @param containerName    要升级的容器名称
     * @param targetVersion    目标版本号
     * @param progressCallback 用于接收进度更新的消费者回调
     * @return 一个代表异步升级任务的 {@link CompletableFuture}
     * @throws RuntimeException 如果升级操作正在进行或升级过程中发生错误
     */
    public CompletableFuture<Void> upgradeToVersion(SshConnection connection, String containerName,
                                                    String targetVersion, Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            ReentrantLock lock = getUpgradeLock(containerName);
            if (!lock.tryLock()) {
                String errorMsg = "容器 " + containerName + " 正在进行升级操作，请稍后再试";
                log.warn(errorMsg);
                progressCallback.accept(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
            try {
                log.info("开始升级容器 {} 到版本 {}", containerName, targetVersion);
                String targetImage = SILLYTAVERN_IMAGE_REPO + ":" + targetVersion;

                progressCallback.accept("检查容器状态...");
                if (!checkContainerExists(connection, containerName)) {
                    throw new RuntimeException("容器 " + containerName + " 不存在");
                }

                String currentImage = getCurrentImage(connection, containerName);

                progressCallback.accept("停止容器...");
                executeCommand(connection, String.format("sudo docker stop %s", containerName));

                progressCallback.accept("拉取新版本镜像: " + targetVersion);
                executeCommand(connection, String.format("sudo docker pull %s", targetImage));

                progressCallback.accept("更新容器镜像...");
                executeCommand(connection, String.format("sudo docker rm %s", containerName));

                String createCommand = buildCreateCommand(connection, containerName, targetImage);
                executeCommand(connection, createCommand);

                progressCallback.accept("启动更新后的容器...");
                executeCommand(connection, String.format("sudo docker start %s", containerName));

                // 使用轮询等待容器启动，而不是固定时间的休眠
                progressCallback.accept("等待容器启动...");
                waitForContainerToStart(connection, containerName);

                progressCallback.accept("清理旧镜像...");
                if (currentImage != null && !currentImage.equals(targetImage)) {
                    cleanupOldImage(connection, currentImage);
                }

                progressCallback.accept("版本升级完成: " + targetVersion);
                log.info("容器 {} 成功升级到版本 {}", containerName, targetVersion);

            } catch (Exception e) {
                log.error("版本升级失败，容器: {} - {}", containerName, e.getMessage(), e);
                progressCallback.accept("升级失败: " + e.getMessage());
                // 将异常重新抛出，以便CompletableFuture可以捕获它
                throw new RuntimeException("版本升级失败: " + e.getMessage(), e);
            } finally {
                lock.unlock();
                log.debug("容器 {} 的升级锁已释放", containerName);
            }
        });
    }


    /**
     * 等待指定的 Docker 容器进入 'running' 状态。
     * <p>
     * 此方法通过定期执行 `docker inspect` 命令来轮询容器状态。
     * 如果容器在预设的重试次数内成功启动，方法将正常返回。否则，将抛出异常。
     * </p>
     *
     * @param connection    SSH 连接
     * @param containerName 容器名称
     * @throws Exception 如果在规定时间内容器未能启动，或命令执行被中断
     */
    private void waitForContainerToStart(SshConnection connection, String containerName) throws Exception {
        log.debug("等待容器 {} 启动...", containerName);
        for (int i = 0; i < CONTAINER_START_RETRIES; i++) {
            try {
                String status = executeCommand(connection,
                        String.format("sudo docker inspect %s --format='{{.State.Running}}'", containerName));
                if ("true".equalsIgnoreCase(status.trim())) {
                    log.info("容器 {} 已成功启动", containerName);
                    return; // 成功启动，退出轮询
                }
            } catch (RuntimeException e) {
                // inspect 命令可能会在容器刚创建时短暂失败，记录警告并继续重试
                log.warn("检查容器状态失败 (尝试 {}/{})，将重试: {}", i + 1, CONTAINER_START_RETRIES, e.getMessage());
            }
            Thread.sleep(CONTAINER_START_INTERVAL_MS);
        }
        throw new RuntimeException("容器 " + containerName + " 在规定时间内未能启动");
    }

    /**
     * 清理未使用的 Docker 镜像。
     *
     * @param connection SSH 连接
     * @throws Exception 清理失败时抛出
     */
    public void cleanupUnusedImages(SshConnection connection) throws Exception {
        log.info("开始清理未使用的 Docker 镜像...");
        executeCommand(connection, "sudo docker image prune -f");

        String result = executeCommand(connection,
                "sudo docker images ghcr.io/sillytavern/sillytavern --format '{{.Repository}}:{{.Tag}} {{.ID}}'");
        String[] lines = result.split("\n");
        List<String> imagesToRemove = new ArrayList<>();

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.trim().split(" ");
            if (parts.length >= 2) {
                String imageTag = parts[0];
                String imageId = parts[1];
                if (!isImageInUse(connection, imageId)) {
                    imagesToRemove.add(imageTag);
                }
            }
        }

        for (String image : imagesToRemove) {
            try {
                executeCommand(connection, String.format("sudo docker rmi %s", image));
                log.info("已删除未使用的镜像: {}", image);
            } catch (Exception e) {
                log.warn("删除镜像失败 {}: {}", image, e.getMessage());
            }
        }
        log.info("镜像清理完成，删除了 {} 个未使用的镜像", imagesToRemove.size());
    }

    /**
     * 检查容器是否存在。
     *
     * @param connection    SSH 连接
     * @param containerName 容器名称
     * @return 存在返回 true，否则 false
     */
    private boolean checkContainerExists(SshConnection connection, String containerName) {
        try {
            String result = executeCommand(connection,
                    String.format("sudo docker ps -a --filter name=%s --format '{{.ID}}'", containerName));
            return !result.trim().isEmpty();
        } catch (Exception e) {
            log.warn("检查容器是否存在时出错: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取容器当前使用的镜像（带标签）。
     *
     * @param connection    SSH 连接
     * @param containerName 容器名称
     * @return 镜像名:标签
     */
    private String getCurrentImage(SshConnection connection, String containerName) {
        try {
            return executeCommand(connection,
                    String.format("sudo docker inspect %s --format='{{.Config.Image}}'", containerName)).trim();
        } catch (Exception e) {
            log.warn("获取容器镜像信息失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建容器创建命令，尽量复用原有端口和卷挂载配置。
     *
     * @param connection    SSH 连接
     * @param containerName 容器名称
     * @param image         镜像名:标签
     * @return docker create 命令字符串
     */
    private String buildCreateCommand(SshConnection connection, String containerName, String image) {
        try {
            String portInfo = executeCommand(connection,
                    String.format("sudo docker inspect %s --format='{{range $p, $conf := .NetworkSettings.Ports}} -p {{(index $conf 0).HostPort}}:{{$p}} {{end}}'", containerName));
            String volumeInfo = executeCommand(connection,
                    String.format("sudo docker inspect %s --format='{{range .Mounts}} -v {{.Source}}:{{.Destination}} {{end}}'", containerName));
            return String.format("sudo docker create --name %s %s %s %s",
                    containerName, portInfo.trim(), volumeInfo.trim(), image);
        } catch (Exception e) {
            log.warn("获取容器配置失败，使用默认配置: {}", e.getMessage());
            return String.format("sudo docker create --name %s -p 8000:8000 -v /opt/sillytavern/data:/app/data %s",
                    containerName, image);
        }
    }

    /**
     * 清理指定的旧镜像。
     *
     * @param connection    SSH 连接
     * @param imageToRemove 镜像名:标签
     */
    private void cleanupOldImage(SshConnection connection, String imageToRemove) {
        try {
            executeCommand(connection, String.format("sudo docker rmi %s", imageToRemove));
            log.info("已清理旧镜像: {}", imageToRemove);
        } catch (Exception e) {
            log.warn("清理旧镜像失败 {}: {}", imageToRemove, e.getMessage());
        }
    }

    /**
     * 检查镜像是否被任何容器使用。
     *
     * @param connection SSH 连接
     * @param imageId    镜像 ID
     * @return 被使用返回 true，否则 false
     */
    private boolean isImageInUse(SshConnection connection, String imageId) {
        try {
            String result = executeCommand(connection,
                    String.format("sudo docker ps -a --filter ancestor=%s --format '{{.ID}}'", imageId));
            return !result.trim().isEmpty();
        } catch (Exception e) {
            log.warn("检查镜像是否被使用时出错: {}", e.getMessage());
            return true; // 出错时保守处理，认为镜像在使用中
        }
    }

    /**
     * 执行 SSH 命令并返回标准输出。
     *
     * @param connection SSH 连接
     * @param command    命令字符串
     * @return 命令标准输出
     * @throws Exception 执行失败时抛出
     */
    private String executeCommand(SshConnection connection, String command) throws Exception {
        try {
            CommandResult result = sshCommandService.executeCommand(connection.getJschSession(), command);
            if (result.exitStatus() != 0) {
                String errorMsg = "命令执行失败，退出码 " + result.exitStatus() +
                        ": " + result.stderr();
                log.debug("命令执行失败: {} - {}", command, errorMsg);
                throw new RuntimeException(errorMsg);
            }
            return result.stdout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("命令执行被中断: " + command, e);
        }
    }
}
