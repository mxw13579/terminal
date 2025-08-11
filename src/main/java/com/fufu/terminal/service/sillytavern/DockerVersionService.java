package com.fufu.terminal.service.sillytavern;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.dto.sillytavern.VersionInfoDto;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Docker 版本管理服务，负责查询 SillyTavern 镜像的最新版本、执行升级和清理操作。
 * <p>
 * 该服务提供了对 Docker 容器进行版本管理的系列功能，包括：
 * <ul>
 *     <li>从 GitHub API 获取可用的发行版本。</li>
 *     <li>查询指定容器的当前版本和最新版本信息。</li>
 *     <li>以异步方式安全地升级容器到指定版本。</li>
 *     <li>高效清理不再使用的旧镜像。</li>
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
    /** 版本缓存服务 */
    private final VersionCacheService versionCacheService;
    /** Docker容器管理服务 */
    private final DockerContainerService dockerContainerService;
    /** 配置管理服务 */
    private final ConfigurationService configurationService;

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
     * 获取指定容器的升级锁。如果锁不存在，则创建一个新的。
     *
     * @param containerName 容器名称
     * @return 对应容器的 {@link ReentrantLock} 实例
     */
    private ReentrantLock getUpgradeLock(final String containerName) {
        return upgradeLocks.computeIfAbsent(containerName, k -> new ReentrantLock());
    }

    /**
     * 获取容器当前版本及可用最新版本信息。
     * 返回的信息包括当前版本、可用版本列表（最近5个版本），并标识当前版本在列表中的位置。
     *
     * @param connection    SSH 连接信息
     * @param containerName 容器名称
     * @return 包含版本信息的 {@link VersionInfoDto}
     */
    public VersionInfoDto getVersionInfo(final SshConnection connection, final String containerName) {
        log.debug("获取容器 {} 的版本信息...", containerName);

        final VersionInfoDto versionInfo = new VersionInfoDto();
        versionInfo.setContainerName(containerName);
        versionInfo.setLastChecked(LocalDateTime.now());

        try {
            final String currentVersion = getCurrentContainerVersion(connection, containerName);
            versionInfo.setCurrentVersion(currentVersion);

            final List<String> availableVersions = getAvailableVersions();
            versionInfo.setAvailableVersions(availableVersions);

            if (!availableVersions.isEmpty()) {
                final String latestVersion = availableVersions.get(0);
                versionInfo.setLatestVersion(latestVersion);

                // 检查是否有更新 - 当前版本不等于最新版本且当前版本不在最近5个版本中时才认为需要更新
                boolean hasUpdate = !currentVersion.equals(latestVersion) &&
                                  !currentVersion.equals("latest") &&
                                  !currentVersion.equals("unknown");
                versionInfo.setHasUpdate(hasUpdate);
            }

            log.debug("版本信息获取完成 - 当前: {}, 最新: {}, 可用版本数: {}",
                     currentVersion, versionInfo.getLatestVersion(), availableVersions.size());

        } catch (Exception e) {
            log.error("获取容器 {} 版本信息失败: {}", containerName, e.getMessage(), e);
            versionInfo.setError("获取版本信息失败: " + e.getMessage());
        }

        return versionInfo;
    }

    /**
     * 获取容器当前正在使用的镜像版本标签。
     *
     * @param connection    SSH 连接信息
     * @param containerName 容器名称
     * @return 镜像的版本标签。如果无法获取，则返回 "unknown"。
     */
    private String getCurrentContainerVersion(final SshConnection connection, final String containerName) {
        try {
            final String imageFullName = executeCommand(connection,
                    String.format("sudo docker inspect %s --format='{{.Config.Image}}'", containerName));
            if (imageFullName.contains(":")) {
                return imageFullName.substring(imageFullName.lastIndexOf(':') + 1).trim();
            }
            return DEFAULT_VERSION;
        } catch (Exception e) {
            log.warn("无法获取容器 {} 的版本信息: {}", containerName, e.getMessage());
            return "unknown";
        }
    }

    /**
     * 从 GitHub Releases API 获取最新的正式版本列表。
     * <p>
     * 此方法会调用 GitHub API，筛选出非草稿、非预发布的前5个版本。
     * 结果会被缓存30分钟以减少网络请求。如果 API 调用失败，将返回一个包含默认值的列表。
     * </p>
     *
     * @return 可用版本号的列表 (例如 ["1.11.0", "1.10.3"])。
     */
    public List<String> getAvailableVersions() {
        log.debug("正在获取可用版本列表...");

        // 先尝试从缓存获取
        List<String> cachedVersions = versionCacheService.getCachedVersions();
        if (cachedVersions != null) {
            log.debug("返回缓存的版本信息，共 {} 个版本", cachedVersions.size());
            return cachedVersions;
        }

        // 缓存未命中，从GitHub API获取
        log.debug("缓存未命中，正在从 GitHub API 获取版本信息...");
        try {
            final String response = restTemplate.getForObject(GITHUB_API_URL + "?per_page=10", String.class);
            final JsonNode releasesNode = objectMapper.readTree(response);

            final List<String> versions = StreamSupport.stream(releasesNode.spliterator(), false)
                    .filter(node -> node.has("tag_name") && !node.path("prerelease").asBoolean(true) && !node.path("draft").asBoolean(true))
                    .map(node -> node.get("tag_name").asText())
                    .map(tagName -> tagName.startsWith("v") ? tagName.substring(1) : tagName)
                    .limit(5)  // 最近5个版本
                    .toList(); // JDK 16+

            if (versions.isEmpty()) {
                log.warn("未能从 GitHub API 获取到任何有效的正式版本，将使用默认版本。");
                final List<String> defaultVersions = List.of(DEFAULT_VERSION);
                versionCacheService.cacheVersions(defaultVersions);
                return defaultVersions;
            }

            log.info("成功从GitHub API获取到 {} 个可用版本", versions.size());
            // 缓存获取到的版本信息
            versionCacheService.cacheVersions(versions);
            return versions;

        } catch (Exception e) {
            log.error("从 GitHub API 获取可用版本失败，将返回默认列表: {}", e.getMessage(), e);
            final List<String> defaultVersions = List.of(DEFAULT_VERSION, "staging", "release");
            versionCacheService.cacheVersions(defaultVersions);
            return defaultVersions;
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
     * @throws IllegalStateException 如果该容器的升级操作已在进行中
     */
    public CompletableFuture<Void> upgradeToVersion(final SshConnection connection, final String containerName,
                                                    final String targetVersion, final Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            final ReentrantLock lock = getUpgradeLock(containerName);
            if (!lock.tryLock()) {
                final String errorMsg = "容器 " + containerName + " 的升级操作已在进行中，请稍后再试。";
                log.warn(errorMsg);
                progressCallback.accept(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
            try {
                log.info("开始升级容器 {} 到版本 {}", containerName, targetVersion);
                final String targetImage = SILLYTAVERN_IMAGE_REPO + ":" + targetVersion;

                progressCallback.accept("检查容器状态...");
                if (!checkContainerExists(connection, containerName)) {
                    throw new RuntimeException("容器 " + containerName + " 不存在。");
                }

                final String currentImage = getCurrentImage(connection, containerName);

                progressCallback.accept("停止当前容器...");
                executeCommand(connection, String.format("sudo docker stop %s", containerName));

                progressCallback.accept("拉取新版本镜像: " + targetVersion + "...");
                executeCommand(connection, String.format("sudo docker pull %s", targetImage));

                progressCallback.accept("移除旧容器...");
                executeCommand(connection, String.format("sudo docker rm %s", containerName));

                progressCallback.accept("使用新镜像重建容器...");
                final String createCommand = buildCreateCommand(connection, containerName, targetImage);
                executeCommand(connection, createCommand);

                progressCallback.accept("启动更新后的容器...");
                executeCommand(connection, String.format("sudo docker start %s", containerName));

                progressCallback.accept("等待容器启动完成...");
                waitForContainerToStart(connection, containerName);

                progressCallback.accept("清理旧镜像...");
                if (currentImage != null && !currentImage.equals(targetImage)) {
                    cleanupOldImage(connection, currentImage);
                }

                progressCallback.accept("版本升级完成: " + targetVersion);
                log.info("容器 {} 成功升级到版本 {}", containerName, targetVersion);

            } catch (Exception e) {
                final String failureMsg = "升级失败: " + e.getMessage();
                log.error("容器 {} 升级失败: {}", containerName, e.getMessage(), e);
                progressCallback.accept(failureMsg);
                throw new RuntimeException(failureMsg, e);
            } finally {
                lock.unlock();
                log.debug("容器 {} 的升级锁已释放。", containerName);
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
     * @param connection    SSH 连接信息
     * @param containerName 容器名称
     * @throws InterruptedException 如果线程在休眠时被中断
     * @throws RuntimeException 如果在规定时间内容器未能启动
     */
    private void waitForContainerToStart(final SshConnection connection, final String containerName) throws InterruptedException {
        log.debug("等待容器 {} 启动...", containerName);
        for (int i = 0; i < CONTAINER_START_RETRIES; i++) {
            try {
                final String status = executeCommand(connection,
                        String.format("sudo docker inspect %s --format='{{.State.Running}}'", containerName));
                if ("true".equalsIgnoreCase(status.trim())) {
                    log.info("容器 {} 已成功启动。", containerName);
                    return;
                }
            } catch (Exception e) {
                log.warn("检查容器 {} 状态失败 (尝试 {}/{})，将重试: {}", containerName, i + 1, CONTAINER_START_RETRIES, e.getMessage());
            }
            Thread.sleep(CONTAINER_START_INTERVAL_MS);
        }
        throw new RuntimeException("容器 " + containerName + " 在规定时间内未能启动。");
    }

    /**
     * 高效清理未被任何容器使用的 `sillytavern` 镜像。
     * <p>
     * 此方法通过两次 SSH 命令批量获取数据，然后在本地进行比较，避免了 N+1 查询问题。
     * 1. 获取所有 `sillytavern` 镜像的 ID 和标签。
     * 2. 获取所有正在被容器使用的镜像的 ID。
     * 3. 计算差集，得到未使用的镜像并删除。
     * </p>
     *
     * @param connection SSH 连接信息
     */
    public void cleanupUnusedImages(final SshConnection connection) {
        log.info("开始清理未使用的 Docker 镜像...");
        try {
            // 1. 获取所有 sillytavern 镜像的 ID -> 标签 映射
            final String allImagesOutput = executeCommand(connection,
                    String.format("sudo docker images %s --format '{{.ID}} {{.Tag}}'", SILLYTAVERN_IMAGE_REPO));
            final Map<String, String> allSillyTavernImages = Arrays.stream(allImagesOutput.split("\n"))
                    .filter(StringUtils::hasText)
                    .map(line -> line.split("\\s+", 2))
                    .filter(parts -> parts.length == 2)
                    .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (tag1, tag2) -> tag1)); // (ID, Tag)

            // 2. 获取所有正在被容器使用的镜像的 ID (使用 inspect 获取更可靠的完整 ID)
            final String usedImageIdsOutput = executeCommand(connection,
                    "sudo docker inspect $(sudo docker ps -a -q) --format '{{.Image}}'");
            final Set<String> usedImageIds = Arrays.stream(usedImageIdsOutput.split("\n"))
                    .filter(StringUtils::hasText)
                    .map(id -> id.startsWith("sha256:") ? id.substring(7) : id) // 规范化ID
                    .collect(Collectors.toSet());

            // 3. 找出未被使用的镜像并删除
            final List<String> imagesToRemove = allSillyTavernImages.entrySet().stream()
                    .filter(entry -> !usedImageIds.contains(entry.getKey())) // 过滤出未被使用的镜像
                    .map(entry -> SILLYTAVERN_IMAGE_REPO + ":" + entry.getValue()) // 构造成 "repo:tag" 格式
                    .toList();

            if (imagesToRemove.isEmpty()) {
                log.info("没有发现可清理的未使用镜像。");
                return;
            }

            log.info("发现 {} 个未使用的镜像，准备清理: {}", imagesToRemove.size(), imagesToRemove);
            for (final String image : imagesToRemove) {
                try {
                    executeCommand(connection, String.format("sudo docker rmi %s", image));
                    log.info("已删除未使用的镜像: {}", image);
                } catch (Exception e) {
                    log.warn("删除镜像 {} 失败: {}", image, e.getMessage());
                }
            }
            log.info("镜像清理完成，共处理了 {} 个镜像。", imagesToRemove.size());

        } catch (Exception e) {
            log.error("清理未使用镜像时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查指定的 Docker 容器是否存在。
     *
     * @param connection    SSH 连接信息
     * @param containerName 容器名称
     * @return 如果容器存在，返回 {@code true}；否则返回 {@code false}。
     */
    private boolean checkContainerExists(final SshConnection connection, final String containerName) {
        try {
            final String result = executeCommand(connection,
                    String.format("sudo docker ps -a --filter name=^%s$ --format '{{.ID}}'", containerName));
            return !result.trim().isEmpty();
        } catch (Exception e) {
            log.warn("检查容器 {} 是否存在时出错: {}", containerName, e.getMessage());
            return false;
        }
    }

    /**
     * 获取容器当前使用的完整镜像名称（包括仓库和标签）。
     *
     * @param connection    SSH 连接信息
     * @param containerName 容器名称
     * @return 完整的镜像名称，例如 "ghcr.io/sillytavern/sillytavern:1.11.0"。获取失败则返回 {@code null}。
     */
    private String getCurrentImage(final SshConnection connection, final String containerName) {
        try {
            return executeCommand(connection,
                    String.format("sudo docker inspect %s --format='{{.Config.Image}}'", containerName)).trim();
        } catch (Exception e) {
            log.warn("获取容器 {} 的当前镜像信息失败: {}", containerName, e.getMessage());
            return null;
        }
    }

    /**
     * 基于现有容器的配置，构建一个新的 `docker create` 命令。
     * <p>
     * 此方法会尝试保留原容器的端口映射和卷挂载配置。
     * 如果获取配置失败，将回退到一套默认配置。
     * </p>
     *
     * @param connection    SSH 连接信息
     * @param containerName 容器名称
     * @param image         新容器要使用的镜像
     * @return 完整的 `docker create` 命令字符串
     */
    private String buildCreateCommand(final SshConnection connection, final String containerName, final String image) {
        try {
            final String portInfo = executeCommand(connection,
                    String.format("sudo docker inspect %s --format='{{range $p, $conf := .NetworkSettings.Ports}}{{range $conf}} -p {{.HostPort}}:{{$p | split \"/\" | index 0}}{{end}}{{end}}'", containerName));
            final String volumeInfo = executeCommand(connection,
                    String.format("sudo docker inspect %s --format='{{range .Mounts}} -v {{.Source}}:{{.Destination}}{{end}}'", containerName));
            return String.format("sudo docker create --name %s %s %s %s",
                    containerName, portInfo.trim(), volumeInfo.trim(), image);
        } catch (Exception e) {
            log.warn("获取容器 {} 的配置失败，将使用默认配置回退: {}", containerName, e.getMessage());
            return String.format("sudo docker create --name %s -p 8000:8000 -v /opt/sillytavern/data:/app/data %s",
                    containerName, image);
        }
    }

    /**
     * 切换容器到指定版本，并清理旧镜像。
     * <p>
     * 此操作与upgradeToVersion类似，但专门用于版本切换功能。
     * 切换流程包括：停止当前容器、拉取目标版本镜像、使用原配置重建容器、启动新容器、清理旧镜像。
     * 进度会通过 {@code progressCallback} 回调函数实时反馈。
     * </p>
     *
     * @param connection       SSH 连接信息
     * @param containerName    要切换版本的容器名称
     * @param targetVersion    目标版本号
     * @param progressCallback 用于接收进度更新的消费者回调
     * @return 一个代表异步版本切换任务的 {@link CompletableFuture}
     * @throws IllegalStateException 如果该容器的切换操作已在进行中
     */
    public CompletableFuture<Void> switchToVersion(final SshConnection connection, final String containerName,
                                                   final String targetVersion, final Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            final ReentrantLock lock = getUpgradeLock(containerName);
            if (!lock.tryLock()) {
                final String errorMsg = "容器 " + containerName + " 的版本切换操作已在进行中，请稍后再试。";
                log.warn(errorMsg);
                progressCallback.accept(errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            final long startTime = System.currentTimeMillis();

            try {
                log.info("开始切换容器 {} 到版本 {}", containerName, targetVersion);
                final String targetImage = SILLYTAVERN_IMAGE_REPO + ":" + targetVersion;

                progressCallback.accept("步骤 1/8: 检查容器状态...");
                if (!checkContainerExists(connection, containerName)) {
                    throw new RuntimeException("容器 " + containerName + " 不存在。");
                }

                final String currentImage = getCurrentImage(connection, containerName);

                // 动态检测镜像仓库地址
                String actualTargetImage = targetImage;
                if (currentImage != null && !currentImage.equals("null")) {
                    // 从当前镜像中提取仓库地址
                    final String currentRepo;
                    if (currentImage.contains(":")) {
                        currentRepo = currentImage.substring(0, currentImage.lastIndexOf(":"));
                    } else {
                        currentRepo = currentImage;
                    }

                    // 如果目标镜像没有包含实际仓库地址，则使用当前仓库地址
                    if (!targetImage.contains(currentRepo)) {
                        if (targetImage.contains(":")) {
                            final String targetVersionFromImage = targetImage.substring(targetImage.lastIndexOf(":") + 1);
                            actualTargetImage = currentRepo + ":" + targetVersionFromImage;
                        } else {
                            actualTargetImage = currentRepo + ":" + targetVersion;
                        }
                    }

                    log.info("动态检测镜像仓库 - 当前: {}, 目标: {}", currentImage, actualTargetImage);
                }

                progressCallback.accept("步骤 2/8: 正在停止当前容器...");
                dockerContainerService.stopContainer(connection, containerName);
                progressCallback.accept("步骤 2/8: 当前容器已停止");

                progressCallback.accept("步骤 3/8: 正在切换配置文件中的版本...");
                updateContainerToNewVersion(connection, containerName, actualTargetImage);
                progressCallback.accept("步骤 3/8: 切换配置中的版本完成");

                progressCallback.accept("步骤 4/8: 正在下载 " + targetVersion + " 版本的镜像...");
                final long downloadStartTime = System.currentTimeMillis();
                pullImageWithProgress(connection, actualTargetImage, targetVersion, progressCallback);
                final long downloadEndTime = System.currentTimeMillis();
                final long downloadDuration = (downloadEndTime - downloadStartTime) / 1000;
                progressCallback.accept("步骤 4/8: 镜像下载完成，耗时 " + downloadDuration + " 秒");

                progressCallback.accept("步骤 5/8: 正在启动新版本容器...");
                dockerContainerService.startContainer(connection, containerName);

                progressCallback.accept("步骤 6/8: 等待容器启动完成...");
                waitForContainerToStart(connection, containerName);
                progressCallback.accept("步骤 6/8: 容器启动完成");

                progressCallback.accept("步骤 7/8: 正在清理历史镜像...");
                if (currentImage != null && !currentImage.equals(actualTargetImage)) {
                    cleanupOldImageAggressive(connection, currentImage);
                }
                progressCallback.accept("步骤 7/8: 历史镜像清理完成");

                final long endTime = System.currentTimeMillis();
                final long totalDuration = (endTime - startTime) / 1000;

                final String completionMessage = "步骤 8/8: 版本切换完成！切换到版本 " + targetVersion + "，总耗时 " + totalDuration + " 秒";
                progressCallback.accept(completionMessage);
                
                // 更新deployment-info.json中的版本信息
                try {
                    configurationService.updateDeploymentVersion(connection, containerName, targetVersion);
                    log.info("成功更新deployment-info.json中的版本信息: {}", targetVersion);
                } catch (Exception e) {
                    log.warn("更新deployment-info.json失败，但版本切换已完成: {}", e.getMessage());
                }
                
                log.info("容器 {} 成功切换到版本 {}，总耗时 {} 秒", containerName, targetVersion, totalDuration);

            } catch (Exception e) {
                final long endTime = System.currentTimeMillis();
                final long totalDuration = (endTime - startTime) / 1000;
                final String failureMsg = "版本切换失败: " + e.getMessage() + "，耗时 " + totalDuration + " 秒";
                log.error("容器 {} 版本切换失败: {}", containerName, e.getMessage(), e);
                progressCallback.accept(failureMsg);
                throw new RuntimeException(failureMsg, e);
            } finally {
                lock.unlock();
                log.debug("容器 {} 的版本切换锁已释放。", containerName);
            }
        });
    }

    /**
     * 积极清理指定的旧 Docker 镜像及其相关的未使用镜像层。
     *
     * @param connection    SSH 连接信息
     * @param imageToRemove 要移除的镜像的完整名称（例如 "repo:tag"）
     */
    private void cleanupOldImageAggressive(final SshConnection connection, final String imageToRemove) {
        try {
            // 删除指定镜像
            executeCommand(connection, String.format("sudo docker rmi %s", imageToRemove));
            log.info("已成功删除旧镜像: {}", imageToRemove);

            // 清理未使用的镜像层和悬空镜像以节省磁盘空间
            try {
                executeCommand(connection, "sudo docker system prune -f");
                log.info("已清理Docker系统中的未使用资源");
            } catch (Exception e) {
                log.warn("清理Docker系统资源失败: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.warn("删除旧镜像 {} 失败 (可能仍有其他容器在使用): {}", imageToRemove, e.getMessage());
        }
    }

    /**
     * 清理指定的旧 Docker 镜像（简单版本，用于升级功能）。
     *
     * @param connection    SSH 连接信息
     * @param imageToRemove 要移除的镜像的完整名称（例如 "repo:tag"）
     */
    private void cleanupOldImage(final SshConnection connection, final String imageToRemove) {
        try {
            executeCommand(connection, String.format("sudo docker rmi %s", imageToRemove));
            log.info("已成功清理旧镜像: {}", imageToRemove);
        } catch (Exception e) {
            log.warn("清理旧镜像 {} 失败 (可能仍有其他容器在使用): {}", imageToRemove, e.getMessage());
        }
    }

    /**
     * 更新容器到新版本。
     * 自动检测是否使用Docker Compose，如果是则更新compose文件，否则重建容器
     *
     * @param connection SSH 连接信息
     * @param containerName 容器名称
     * @param targetImage 目标镜像（包含版本）
     */
    private void updateContainerToNewVersion(final SshConnection connection, final String containerName, final String targetImage) {
        try {
            // 检查是否存在 docker-compose.yaml 文件
            final String deploymentPath = "/data/docker/sillytavern";
            final String checkComposeFile = String.format("test -f %s/docker-compose.yaml", deploymentPath);
            final CommandResult result = sshCommandService.executeInternal(connection.getJschSession(), checkComposeFile);

            if (result.exitStatus() == 0) {
                // 存在 compose 文件，更新 compose 文件中的镜像版本
                log.info("检测到Docker Compose配置，使用Compose方式更新");
                updateDockerComposeImage(connection, deploymentPath, targetImage);
            } else {
                // 不存在 compose 文件，使用传统方式重建容器
                log.info("未检测到Docker Compose配置，使用传统Docker方式重建容器");
                executeCommand(connection, String.format("sudo docker rm %s", containerName));
                final String createCommand = buildCreateCommand(connection, containerName, targetImage);
                executeCommand(connection, createCommand);
            }
        } catch (Exception e) {
            log.warn("更新容器配置失败，回退到传统重建方式: {}", e.getMessage());
            // 回退到传统方式
            try {
                executeCommand(connection, String.format("sudo docker rm %s", containerName));
                final String createCommand = buildCreateCommand(connection, containerName, targetImage);
                executeCommand(connection, createCommand);
            } catch (Exception fallbackError) {
                throw new RuntimeException("容器重建失败: " + fallbackError.getMessage(), fallbackError);
            }
        }
    }

    /**
     * 更新docker-compose.yaml文件中的镜像版本
     *
     * @param connection SSH 连接信息
     * @param deploymentPath 部署路径
     * @param targetImage 目标镜像
     */
    private void updateDockerComposeImage(final SshConnection connection, final String deploymentPath, final String targetImage) {
        try {
            // 首先读取当前的docker-compose.yaml文件，找到当前使用的镜像
            final String readComposeCommand = String.format("cat %s/docker-compose.yaml", deploymentPath);
            final CommandResult readResult = sshCommandService.executeInternal(connection.getJschSession(), readComposeCommand);

            if (readResult.exitStatus() != 0) {
                throw new RuntimeException("无法读取docker-compose.yaml文件: " + readResult.stderr());
            }

            final String composeContent = readResult.stdout();
            log.debug("docker-compose.yaml内容: {}", composeContent);

            // 解析当前使用的镜像仓库和版本
            String currentImageRepo = null;
            final String[] lines = composeContent.split("\n");
            for (String line : lines) {
                final String trimmedLine = line.trim();
                if (trimmedLine.startsWith("image:")) {
                    // 提取镜像行，格式如 "image: ghcr.nju.edu.cn/sillytavern/sillytavern:latest"
                    final String imageLine = trimmedLine.substring(6).trim(); // 去掉 "image:"
                    if (imageLine.contains("sillytavern")) {
                        // 提取仓库部分（去掉版本号）
                        if (imageLine.contains(":")) {
                            currentImageRepo = imageLine.substring(0, imageLine.lastIndexOf(":"));
                        } else {
                            currentImageRepo = imageLine;
                        }
                        break;
                    }
                }
            }

            if (currentImageRepo == null) {
                log.warn("未能从docker-compose.yaml中找到SillyTavern镜像，使用默认仓库");
                currentImageRepo = SILLYTAVERN_IMAGE_REPO.substring(0, SILLYTAVERN_IMAGE_REPO.lastIndexOf(":"));
            }

            log.info("检测到当前镜像仓库: {}", currentImageRepo);

            // 构建目标镜像（使用检测到的仓库地址 + 目标版本）
            final String actualTargetImage;
            if (targetImage.contains(currentImageRepo)) {
                // 目标镜像已包含正确的仓库地址
                actualTargetImage = targetImage;
            } else {
                // 从targetImage中提取版本号，与检测到的仓库地址组合
                final String targetVersion;
                if (targetImage.contains(":")) {
                    targetVersion = targetImage.substring(targetImage.lastIndexOf(":") + 1);
                } else {
                    targetVersion = "latest";
                }
                actualTargetImage = currentImageRepo + ":" + targetVersion;
            }

            log.info("实际目标镜像: {}", actualTargetImage);

            // 使用sed命令更新docker-compose.yaml中的镜像版本
            // 匹配任何包含sillytavern的镜像行
            final String sedCommand = String.format(
                "sed -i 's|image: %s:[^[:space:]]*|image: %s|g' %s/docker-compose.yaml",
                currentImageRepo.replaceAll("/", "\\/"), // 转义斜杠
                actualTargetImage.replaceAll("/", "\\/"), // 转义斜杠
                deploymentPath
            );

            log.info("更新Docker Compose镜像版本，命令: {}", sedCommand);
            executeCommand(connection, sedCommand);

            // 验证更新是否成功
            final String checkCommand = String.format("grep 'image: %s' %s/docker-compose.yaml", actualTargetImage, deploymentPath);
            final CommandResult checkResult = sshCommandService.executeInternal(connection.getJschSession(), checkCommand);

            if (checkResult.exitStatus() == 0) {
                log.info("Docker Compose文件已成功更新到镜像: {}", actualTargetImage);
            } else {
                log.warn("Docker Compose文件更新验证失败，但继续执行");
                // 再次读取文件内容查看实际结果
                final CommandResult verifyResult = sshCommandService.executeInternal(connection.getJschSession(), readComposeCommand);
                if (verifyResult.exitStatus() == 0) {
                    log.debug("更新后的docker-compose.yaml内容: {}", verifyResult.stdout());
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("更新Docker Compose文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 拉取Docker镜像并显示详细进度信息
     *
     * @param connection SSH 连接信息
     * @param targetImage 目标镜像（包含版本）
     * @param targetVersion 目标版本
     * @param progressCallback 进度回调函数
     */
    private void pullImageWithProgress(final SshConnection connection, final String targetImage,
                                     final String targetVersion, final Consumer<String> progressCallback) {
        try {
            // 首先检查镜像是否已经存在
            progressCallback.accept("检查本地镜像: " + targetVersion);
            final String checkImageCommand = String.format("sudo docker images %s --format '{{.Repository}}:{{.Tag}}\t{{.Size}}\t{{.CreatedSince}}'", targetImage);

            try {
                final CommandResult checkResult = sshCommandService.executeInternal(connection.getJschSession(), checkImageCommand);
                if (checkResult.exitStatus() == 0 && !checkResult.stdout().trim().isEmpty()) {
                    final String[] imageInfo = checkResult.stdout().trim().split("\t");
                    if (imageInfo.length >= 2) {
                        progressCallback.accept("发现本地镜像 " + targetVersion + " (大小: " + imageInfo[1] +
                                              (imageInfo.length > 2 ? ", 创建: " + imageInfo[2] + ")" : ")"));
                    } else {
                        progressCallback.accept("发现本地镜像 " + targetVersion);
                    }
                    return; // 镜像已存在，无需下载
                }
            } catch (Exception e) {
                log.debug("检查本地镜像失败，将继续下载: {}", e.getMessage());
            }

            // 镜像不存在，开始下载
            progressCallback.accept("开始下载镜像: " + targetVersion + " (这可能需要几分钟时间)");

            // 获取镜像大小信息（从Docker Hub API或者registry）
            try {
                final String manifestCommand = String.format(
                    "sudo docker manifest inspect %s 2>/dev/null | grep -o '\"size\":[0-9]*' | head -1 | cut -d':' -f2",
                    targetImage
                );
                final CommandResult manifestResult = sshCommandService.executeInternal(connection.getJschSession(), manifestCommand);
                if (manifestResult.exitStatus() == 0 && !manifestResult.stdout().trim().isEmpty()) {
                    final long sizeBytes = Long.parseLong(manifestResult.stdout().trim());
                    final String sizeStr = formatBytes(sizeBytes);
                    progressCallback.accept("镜像大小约: " + sizeStr + ", 正在下载...");
                }
            } catch (Exception e) {
                log.debug("获取镜像大小失败: {}", e.getMessage());
                progressCallback.accept("正在下载镜像 " + targetVersion + "...");
            }

            // 执行拉取命令，超时时间设为10分钟
            final long startTime = System.currentTimeMillis();
            //将 targetImage 按照:切割取后面的值
            final String[] imageParts = targetImage.split(":");
            final String imageVer = imageParts[imageParts.length - 1];


            progressCallback.accept("执行 下载镜像 镜像版本号为" + imageVer);

            try {
                executeCommand(connection, String.format("sudo docker pull %s", targetImage));

                final long endTime = System.currentTimeMillis();
                final long durationSeconds = (endTime - startTime) / 1000;
                progressCallback.accept("镜像下载完成 " + targetVersion + " (耗时: " + durationSeconds + "秒)");

                // 获取下载后的镜像信息
                try {
                    final CommandResult imageInfoResult = sshCommandService.executeInternal(connection.getJschSession(), checkImageCommand);
                    if (imageInfoResult.exitStatus() == 0 && !imageInfoResult.stdout().trim().isEmpty()) {
                        final String[] imageInfo = imageInfoResult.stdout().trim().split("\t");
                        if (imageInfo.length >= 2) {
                            progressCallback.accept("镜像信息: " + targetVersion + " - 大小: " + imageInfo[1] +
                                                  (imageInfo.length > 2 ? ", 创建: " + imageInfo[2] : ""));
                        }
                    }
                } catch (Exception e) {
                    log.debug("获取镜像信息失败: {}", e.getMessage());
                }

            } catch (Exception e) {
                progressCallback.accept("镜像下载失败: " + e.getMessage());
                throw e;
            }

        } catch (Exception e) {
            log.error("拉取镜像失败: {}", e.getMessage(), e);
            throw new RuntimeException("拉取镜像失败: " + e.getMessage(), e);
        }
    }

    /**
     * 格式化字节数为可读的大小字符串
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * 执行 SSH 命令并返回其标准输出。
     *
     * @param connection SSH 连接信息
     * @param command    要执行的命令字符串
     * @return 命令的标准输出内容
     * @throws InterruptedException 如果命令执行被中断
     * @throws RuntimeException 如果命令执行失败（退出码非0）
     */
    private String executeCommand(final SshConnection connection, final String command) throws InterruptedException {
        try {
            final CommandResult result = sshCommandService.executeInternal(connection.getJschSession(), command);
            if (result.exitStatus() != 0) {
                final String errorMsg = String.format("命令执行失败 (退出码: %d): %s", result.exitStatus(), result.stderr());
                log.debug("命令 '{}' 执行失败: {}", command, errorMsg);
                throw new RuntimeException(errorMsg);
            }
            return result.stdout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("命令执行被中断: " + command);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
