package com.fufu.terminal.service.sillytavern;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Docker 版本管理服务，负责查询最新镜像版本、升级和清理镜像，确保线程安全。
 * <p>
 * 支持并发控制，防止同一容器重复升级。
 * </p>
 * <p>
 * 主要功能包括：
 * <ul>
 *     <li>获取容器当前及可用的版本信息</li>
 *     <li>异步升级容器镜像并清理旧镜像</li>
 *     <li>清理未使用的镜像</li>
 * </ul>
 * </p>
 *
 * @author
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerVersionService {

    private final SshCommandService sshCommandService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** 并发控制：每个容器独立锁，防止重复升级 */
    private final Map<String, ReentrantLock> upgradeLocks = new ConcurrentHashMap<>();

    private static final String SILLYTAVERN_IMAGE_REPO = "ghcr.io/sillytavern/sillytavern";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/sillytavern/sillytavern/releases";
    private static final int MAX_VERSION_COUNT = 3;
    private static final String DEFAULT_VERSION = "latest";

    /**
     * 获取指定容器的升级锁。
     *
     * @param containerName 容器名称
     * @return 对应的 ReentrantLock
     */
    private ReentrantLock getUpgradeLock(String containerName) {
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
     * 获取 GitHub Releases 上最新的 3 个正式版本（带缓存）。
     *
     * @return 可用版本列表
     */
    @Cacheable(value = "sillytavern-versions", unless = "#result == null || #result.isEmpty()")
    public List<String> getAvailableVersions() {
        List<String> versions = new ArrayList<>();
        try {
            log.debug("从 GitHub API 获取版本信息...");
            String response = restTemplate.getForObject(GITHUB_API_URL + "?per_page=10", String.class);
            JsonNode releases = objectMapper.readTree(response);

            int count = 0;
            for (JsonNode release : releases) {
                if (count >= MAX_VERSION_COUNT) break;
                boolean prerelease = release.get("prerelease").asBoolean();
                boolean draft = release.get("draft").asBoolean();
                if (!prerelease && !draft) {
                    String tagName = release.get("tag_name").asText();
                    if (tagName.startsWith("v")) {
                        tagName = tagName.substring(1);
                    }
                    versions.add(tagName);
                    count++;
                }
            }
            if (versions.isEmpty()) {
                versions.add(DEFAULT_VERSION);
            }
            log.info("成功获取到 {} 个可用版本", versions.size());
        } catch (Exception e) {
            log.error("获取可用版本失败: {}", e.getMessage(), e);
            versions.add(DEFAULT_VERSION);
            versions.add("staging");
            versions.add("release");
        }
        return versions;
    }

    /**
     * 异步升级指定容器到目标版本，并清理旧镜像（线程安全）。
     *
     * @param connection       SSH 连接
     * @param containerName    容器名称
     * @param targetVersion    目标版本
     * @param progressCallback 升级进度回调
     * @return 升级任务的 CompletableFuture
     */
    public CompletableFuture<Void> upgradeToVersion(SshConnection connection, String containerName,
                                                    String targetVersion, Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            ReentrantLock lock = getUpgradeLock(containerName);
            if (!lock.tryLock()) {
                String errorMsg = "容器 " + containerName + " 正在进行升级操作，请稍后再试";
                log.warn(errorMsg);
                progressCallback.accept(errorMsg);
                throw new RuntimeException(errorMsg);
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

                Thread.sleep(5000);

                progressCallback.accept("清理旧镜像...");
                if (currentImage != null && !currentImage.equals(targetImage)) {
                    cleanupOldImage(connection, currentImage);
                }

                progressCallback.accept("版本升级完成: " + targetVersion);
                log.info("容器 {} 成功升级到版本 {}", containerName, targetVersion);

            } catch (Exception e) {
                log.error("版本升级失败，容器: {} - {}", containerName, e.getMessage(), e);
                progressCallback.accept("升级失败: " + e.getMessage());
                throw new RuntimeException("版本升级失败: " + e.getMessage(), e);
            } finally {
                lock.unlock();
                log.debug("容器 {} 的升级锁已释放", containerName);
            }
        });
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
