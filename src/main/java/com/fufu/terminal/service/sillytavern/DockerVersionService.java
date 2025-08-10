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
import org.springframework.cache.annotation.Cacheable;
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
    @Cacheable(value = "sillytavern-versions", unless = "#result == null || #result.isEmpty()")
    public List<String> getAvailableVersions() {
        log.debug("正在从 GitHub API 获取可用版本列表...");
        try {
            final String response = restTemplate.getForObject(GITHUB_API_URL + "?per_page=10", String.class);
            final JsonNode releasesNode = objectMapper.readTree(response);

            final List<String> versions = StreamSupport.stream(releasesNode.spliterator(), false)
                    .filter(node -> node.has("tag_name") && !node.path("prerelease").asBoolean(true) && !node.path("draft").asBoolean(true))
                    .map(node -> node.get("tag_name").asText())
                    .map(tagName -> tagName.startsWith("v") ? tagName.substring(1) : tagName)
                    .limit(5)  // 修改为5个版本
                    .toList(); // JDK 16+

            if (versions.isEmpty()) {
                log.warn("未能从 GitHub API 获取到任何有效的正式版本，将使用默认版本。");
                return List.of(DEFAULT_VERSION);
            }

            log.info("成功获取到 {} 个可用版本。", versions.size());
            return versions;
        } catch (Exception e) {
            log.error("从 GitHub API 获取可用版本失败，将返回默认列表: {}", e.getMessage(), e);
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
            try {
                log.info("开始切换容器 {} 到版本 {}", containerName, targetVersion);
                final String targetImage = SILLYTAVERN_IMAGE_REPO + ":" + targetVersion;

                progressCallback.accept("检查容器状态...");
                if (!checkContainerExists(connection, containerName)) {
                    throw new RuntimeException("容器 " + containerName + " 不存在。");
                }

                final String currentImage = getCurrentImage(connection, containerName);

                progressCallback.accept("停止当前容器...");
                executeCommand(connection, String.format("sudo docker stop %s", containerName));

                progressCallback.accept("拉取目标版本镜像: " + targetVersion + "...");
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

                progressCallback.accept("清理旧镜像以节省磁盘空间...");
                if (currentImage != null && !currentImage.equals(targetImage)) {
                    cleanupOldImageAggressive(connection, currentImage);
                }

                progressCallback.accept("版本切换完成: " + targetVersion);
                log.info("容器 {} 成功切换到版本 {}", containerName, targetVersion);

            } catch (Exception e) {
                final String failureMsg = "版本切换失败: " + e.getMessage();
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
