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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Docker版本管理服务 - 增强并发控制
 * 负责查询Docker Hub API获取最新版本信息，管理镜像更新和清理，确保线程安全
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerVersionService {
    
    private final SshCommandService sshCommandService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    // 并发控制 - 为每个容器名称维护一个独立的锁，避免同时升级
    private final Map<String, ReentrantLock> upgradeLocks = new ConcurrentHashMap<>();
    
    private static final String SILLYTAVERN_IMAGE_REPO = "ghcr.io/sillytavern/sillytavern";
    private static final String DOCKER_HUB_API_URL = "https://registry.hub.docker.com/v2/repositories/sillytavern/sillytavern/tags";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/sillytavern/sillytavern/releases";
    
    /**
     * 获取容器升级锁
     */
    private ReentrantLock getUpgradeLock(String containerName) {
        return upgradeLocks.computeIfAbsent(containerName, k -> new ReentrantLock());
    }
    
    /**
     * 获取容器当前版本和最新版本信息
     */
    public VersionInfoDto getVersionInfo(SshConnection connection, String containerName) {
        log.debug("获取版本信息，容器: {}", containerName);
        
        VersionInfoDto versionInfo = new VersionInfoDto();
        versionInfo.setContainerName(containerName);
        versionInfo.setLastChecked(LocalDateTime.now());
        
        try {
            // 获取当前容器版本
            String currentVersion = getCurrentContainerVersion(connection, containerName);
            versionInfo.setCurrentVersion(currentVersion);
            
            // 获取最新版本列表（异步，使用缓存）
            List<String> availableVersions = getAvailableVersions();
            versionInfo.setAvailableVersions(availableVersions);
            
            if (!availableVersions.isEmpty()) {
                versionInfo.setLatestVersion(availableVersions.get(0));
                
                // 检查是否有更新
                boolean hasUpdate = !currentVersion.equals(availableVersions.get(0));
                versionInfo.setHasUpdate(hasUpdate);
            }
            
        } catch (Exception e) {
            log.error("获取版本信息失败: {}", e.getMessage());
            versionInfo.setError("获取版本信息失败: " + e.getMessage());
        }
        
        return versionInfo;
    }
    
    /**
     * 获取容器当前使用的镜像版本
     */
    private String getCurrentContainerVersion(SshConnection connection, String containerName) throws Exception {
        try {
            // 获取容器镜像信息
            String result = executeCommand(connection, 
                String.format("sudo docker inspect %s --format='{{.Config.Image}}'", containerName));
            
            if (result.contains(":")) {
                String[] parts = result.trim().split(":");
                return parts[parts.length - 1];
            }
            
            return "latest";
            
        } catch (Exception e) {
            log.warn("无法获取容器版本信息: {}", e.getMessage());
            return "unknown";
        }
    }
    
    /**
     * 从GitHub Releases API获取最新的3个版本
     * 使用缓存，避免频繁请求API
     */
    @Cacheable(value = "sillytavern-versions", unless = "#result == null || #result.isEmpty()")
    public List<String> getAvailableVersions() {
        List<String> versions = new ArrayList<>();
        
        try {
            log.debug("从GitHub API获取版本信息...");
            
            // 调用GitHub Releases API
            String response = restTemplate.getForObject(GITHUB_API_URL + "?per_page=10", String.class);
            JsonNode releases = objectMapper.readTree(response);
            
            // 解析最新的3个正式版本（非预发布版本）
            int count = 0;
            for (JsonNode release : releases) {
                if (count >= 3) break;
                
                boolean prerelease = release.get("prerelease").asBoolean();
                boolean draft = release.get("draft").asBoolean();
                
                if (!prerelease && !draft) {
                    String tagName = release.get("tag_name").asText();
                    if (tagName.startsWith("v")) {
                        tagName = tagName.substring(1); // 移除 'v' 前缀
                    }
                    versions.add(tagName);
                    count++;
                }
            }
            
            // 如果没有找到正式版本，添加 latest 作为默认版本
            if (versions.isEmpty()) {
                versions.add("latest");
            }
            
            log.info("成功获取到 {} 个可用版本", versions.size());
            
        } catch (Exception e) {
            log.error("获取可用版本失败: {}", e.getMessage());
            // 返回默认版本列表
            versions.add("latest");
            versions.add("staging");
            versions.add("release");
        }
        
        return versions;
    }
    
    /**
     * 升级到指定版本并清理旧镜像 - 并发安全版本
     */
    public CompletableFuture<Void> upgradeToVersion(SshConnection connection, String containerName, 
                                                   String targetVersion, Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            ReentrantLock lock = getUpgradeLock(containerName);
            
            // 尝试获取锁，如果获取不到则说明正在升级
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
                
                // 检查容器是否存在
                boolean containerExists = checkContainerExists(connection, containerName);
                if (!containerExists) {
                    throw new RuntimeException("容器 " + containerName + " 不存在");
                }
                
                // 获取当前镜像信息用于后续清理
                String currentImage = getCurrentImage(connection, containerName);
                
                progressCallback.accept("停止容器...");
                
                // 停止容器
                executeCommand(connection, String.format("sudo docker stop %s", containerName));
                
                progressCallback.accept("拉取新版本镜像: " + targetVersion);
                
                // 拉取新镜像
                executeCommand(connection, String.format("sudo docker pull %s", targetImage));
                
                progressCallback.accept("更新容器镜像...");
                
                // 删除旧容器
                executeCommand(connection, String.format("sudo docker rm %s", containerName));
                
                // 使用新镜像重新创建容器（这里需要获取原容器的配置）
                String createCommand = buildCreateCommand(connection, containerName, targetImage);
                executeCommand(connection, createCommand);
                
                progressCallback.accept("启动更新后的容器...");
                
                // 启动新容器
                executeCommand(connection, String.format("sudo docker start %s", containerName));
                
                // 等待容器启动
                Thread.sleep(5000);
                
                progressCallback.accept("清理旧镜像...");
                
                // 清理旧镜像（如果不同）
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
                lock.unlock(); // 确保释放锁
                log.debug("容器 {} 的升级锁已释放", containerName);
            }
        });
    }
    
    /**
     * 清理未使用的Docker镜像
     */
    public void cleanupUnusedImages(SshConnection connection) throws Exception {
        log.info("开始清理未使用的Docker镜像...");
        
        // 清理悬空镜像
        executeCommand(connection, "sudo docker image prune -f");
        
        // 清理未使用的SillyTavern镜像
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
                
                // 检查镜像是否被任何容器使用
                if (!isImageInUse(connection, imageId)) {
                    imagesToRemove.add(imageTag);
                }
            }
        }
        
        // 删除未使用的镜像
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
     * 检查容器是否存在
     */
    private boolean checkContainerExists(SshConnection connection, String containerName) throws Exception {
        try {
            String result = executeCommand(connection, 
                String.format("sudo docker ps -a --filter name=%s --format '{{.ID}}'", containerName));
            return !result.trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取容器当前使用的镜像
     */
    private String getCurrentImage(SshConnection connection, String containerName) throws Exception {
        try {
            return executeCommand(connection, 
                String.format("sudo docker inspect %s --format='{{.Config.Image}}'", containerName)).trim();
        } catch (Exception e) {
            log.warn("获取容器镜像信息失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 构建容器创建命令（保持原有配置）
     */
    private String buildCreateCommand(SshConnection connection, String containerName, String image) throws Exception {
        try {
            // 获取原容器的端口映射和卷挂载信息
            String portInfo = executeCommand(connection, 
                String.format("sudo docker inspect %s --format='{{range $p, $conf := .NetworkSettings.Ports}} -p {{(index $conf 0).HostPort}}:{{$p}} {{end}}'", containerName));
            
            String volumeInfo = executeCommand(connection, 
                String.format("sudo docker inspect %s --format='{{range .Mounts}} -v {{.Source}}:{{.Destination}} {{end}}'", containerName));
            
            // 构建基本创建命令
            return String.format("sudo docker create --name %s %s %s %s", 
                containerName, portInfo.trim(), volumeInfo.trim(), image);
                
        } catch (Exception e) {
            log.warn("获取容器配置失败，使用默认配置: {}", e.getMessage());
            // 使用默认配置
            return String.format("sudo docker create --name %s -p 8000:8000 -v /opt/sillytavern/data:/app/data %s", 
                containerName, image);
        }
    }
    
    /**
     * 清理指定的旧镜像
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
     * 检查镜像是否被任何容器使用
     */
    private boolean isImageInUse(SshConnection connection, String imageId) throws Exception {
        try {
            String result = executeCommand(connection, 
                String.format("sudo docker ps -a --filter ancestor=%s --format '{{.ID}}'", imageId));
            return !result.trim().isEmpty();
        } catch (Exception e) {
            return true; // 出错时保守处理，认为镜像在使用中
        }
    }
    
    /**
     * 执行SSH命令
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