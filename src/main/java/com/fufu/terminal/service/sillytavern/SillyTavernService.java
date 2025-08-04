package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.dto.sillytavern.*;
import com.fufu.terminal.model.SshConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Core service for SillyTavern management operations.
 * Provides high-level operations for deployment, status checking, and lifecycle management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SillyTavernService {

    private final DockerContainerService dockerService;
    private final SystemDetectionService systemDetectionService;

    private static final String DEFAULT_CONTAINER_NAME = "sillytavern";
    private static final String DEFAULT_IMAGE = "ghcr.io/sillytavern/sillytavern:latest";

    /**
     * Validate system requirements for SillyTavern deployment
     */
    public SystemInfoDto validateSystemRequirements(SshConnection connection) {
        log.info("Validating system requirements for SillyTavern deployment");
        return systemDetectionService.validateSystemRequirements(connection);
    }

    /**
     * Get current container status
     */
    public ContainerStatusDto getContainerStatus(SshConnection connection) {
        return getContainerStatus(connection, DEFAULT_CONTAINER_NAME);
    }

    /**k
     * Get container status for specific container name
     */
    public ContainerStatusDto getContainerStatus(SshConnection connection, String containerName) {
        log.debug("Getting container status for: {}", containerName);
        return dockerService.getContainerStatus(connection, containerName);
    }

    /**
     * Check if SillyTavern container is running
     */
    public boolean isContainerRunning(SshConnection connection) {
        return isContainerRunning(connection, DEFAULT_CONTAINER_NAME);
    }

    /**
     * Check if specific container is running
     */
    public boolean isContainerRunning(SshConnection connection, String containerName) {
        ContainerStatusDto status = getContainerStatus(connection, containerName);
        return status.getExists() && status.getRunning();
    }

    /**
     * Deploy SillyTavern container asynchronously with progress updates
     *
     * 部署酒馆
     */
    public CompletableFuture<Void> deployContainer(SshConnection connection,
                                                  DeploymentRequestDto request,
                                                  Consumer<DeploymentProgressDto> progressCallback) {
        log.info("Starting SillyTavern deployment with request: {}", request);

        return CompletableFuture.runAsync(() -> {
            try {
                // Stage 1: System validation
                progressCallback.accept(DeploymentProgressDto.success("validation", 10, "验证系统要求..."));

                SystemInfoDto systemInfo = validateSystemRequirements(connection);
                if (!systemInfo.getMeetsRequirements()) {
                    String errorMessage = "系统不满足部署要求:\n" + String.join("\n", systemInfo.getRequirementChecks());
                    progressCallback.accept(DeploymentProgressDto.error("validation", errorMessage));
                    return;
                }

                // Stage 2: Check if container already exists
                progressCallback.accept(DeploymentProgressDto.success("check-existing", 20, "检查现有容器..."));

                ContainerStatusDto existingStatus = getContainerStatus(connection, request.getContainerName());
                if (existingStatus.getExists()) {
                    progressCallback.accept(DeploymentProgressDto.error("check-existing",
                        "容器 '" + request.getContainerName() + "' 已存在。请先删除现有容器或使用不同的名称。"));
                    return;
                }

                // Stage 3: Pull Docker image
                progressCallback.accept(DeploymentProgressDto.success("pull-image", 30, "拉取 Docker 镜像..."));

                dockerService.pullImage(connection, request.getDockerImage(), (pullProgress) -> {
                    progressCallback.accept(DeploymentProgressDto.success("pull-image", 50, pullProgress));
                }).get(); // Wait for image pull to complete

                // Stage 4: Create container
                progressCallback.accept(DeploymentProgressDto.success("create-container", 70, "创建容器..."));

                String containerId = dockerService.createContainer(
                    connection,
                    request.getContainerName(),
                    request.getDockerImage(),
                    request.getPort(),
                    request.getDataPath()
                );

                // Stage 5: Verify deployment
                progressCallback.accept(DeploymentProgressDto.success("verify", 90, "验证部署..."));

                // Wait a moment for container to start
                Thread.sleep(2000);

                ContainerStatusDto finalStatus = getContainerStatus(connection, request.getContainerName());
                if (finalStatus.getExists() && finalStatus.getRunning()) {
                    progressCallback.accept(DeploymentProgressDto.completed(
                        "SillyTavern 部署成功！容器正在运行，可通过端口 " + request.getPort() + " 访问。"));
                    log.info("SillyTavern deployment completed successfully");
                } else {
                    progressCallback.accept(DeploymentProgressDto.error("verify",
                        "容器创建成功但未正常启动。请检查日志获取详细信息。"));
                }

            } catch (Exception e) {
                log.error("Error during SillyTavern deployment", e);
                progressCallback.accept(DeploymentProgressDto.error("deployment",
                    "部署过程中发生错误: " + e.getMessage()));
            }
        });
    }

    /**
     * Start SillyTavern container
     */
    public void startContainer(SshConnection connection) throws Exception {
        startContainer(connection, DEFAULT_CONTAINER_NAME);
    }

    /**
     * Start specific container
     */
    public void startContainer(SshConnection connection, String containerName) throws Exception {
        log.info("Starting SillyTavern container: {}", containerName);

        ContainerStatusDto status = getContainerStatus(connection, containerName);
        if (!status.getExists()) {
            throw new IllegalStateException("Container '" + containerName + "' does not exist");
        }

        if (status.getRunning()) {
            throw new IllegalStateException("Container '" + containerName + "' is already running");
        }

        dockerService.startContainer(connection, containerName);
    }

    /**
     * Stop SillyTavern container
     */
    public void stopContainer(SshConnection connection) throws Exception {
        stopContainer(connection, DEFAULT_CONTAINER_NAME);
    }

    /**
     * Stop specific container
     */
    public void stopContainer(SshConnection connection, String containerName) throws Exception {
        log.info("Stopping SillyTavern container: {}", containerName);

        ContainerStatusDto status = getContainerStatus(connection, containerName);
        if (!status.getExists()) {
            throw new IllegalStateException("Container '" + containerName + "' does not exist");
        }

        if (!status.getRunning()) {
            throw new IllegalStateException("Container '" + containerName + "' is not running");
        }

        dockerService.stopContainer(connection, containerName);
    }

    /**
     * Restart SillyTavern container
     */
    public void restartContainer(SshConnection connection) throws Exception {
        restartContainer(connection, DEFAULT_CONTAINER_NAME);
    }

    /**
     * Restart specific container
     */
    public void restartContainer(SshConnection connection, String containerName) throws Exception {
        log.info("Restarting SillyTavern container: {}", containerName);

        ContainerStatusDto status = getContainerStatus(connection, containerName);
        if (!status.getExists()) {
            throw new IllegalStateException("Container '" + containerName + "' does not exist");
        }

        dockerService.restartContainer(connection, containerName);
    }

    /**
     * Upgrade SillyTavern container (pull latest image and restart)
     */
    public CompletableFuture<Void> upgradeContainer(SshConnection connection,
                                                   Consumer<String> progressCallback) {
        return upgradeContainer(connection, DEFAULT_CONTAINER_NAME, progressCallback);
    }

    /**
     * Upgrade specific container
     */
    public CompletableFuture<Void> upgradeContainer(SshConnection connection, String containerName,
                                                   Consumer<String> progressCallback) {
        log.info("Upgrading SillyTavern container: {}", containerName);

        return CompletableFuture.runAsync(() -> {
            try {
                ContainerStatusDto status = getContainerStatus(connection, containerName);
                if (!status.getExists()) {
                    throw new IllegalStateException("Container '" + containerName + "' does not exist");
                }

                String image = status.getImage();
                if (image == null) {
                    image = DEFAULT_IMAGE;
                }

                // Stop container if running
                if (status.getRunning()) {
                    progressCallback.accept("Stopping container...");
                    dockerService.stopContainer(connection, containerName);
                }

                // Pull latest image
                progressCallback.accept("Pulling latest image...");
                dockerService.pullImage(connection, image, progressCallback).get();

                // Start container
                progressCallback.accept("Starting container...");
                dockerService.startContainer(connection, containerName);

                progressCallback.accept("Upgrade completed successfully");

            } catch (Exception e) {
                log.error("Error upgrading container: {}", containerName, e);
                throw new RuntimeException("Upgrade failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Delete SillyTavern container
     */
    public void deleteContainer(SshConnection connection, boolean removeData) throws Exception {
        deleteContainer(connection, DEFAULT_CONTAINER_NAME, removeData);
    }

    /**
     * Delete specific container
     */
    public void deleteContainer(SshConnection connection, String containerName, boolean removeData) throws Exception {
        log.info("Deleting SillyTavern container: {} (removeData: {})", containerName, removeData);

        ContainerStatusDto status = getContainerStatus(connection, containerName);
        if (!status.getExists()) {
            throw new IllegalStateException("Container '" + containerName + "' does not exist");
        }

        // Stop container if running
        if (status.getRunning()) {
            dockerService.stopContainer(connection, containerName);
        }

        // Remove container
        dockerService.removeContainer(connection, containerName, true);

        // Optionally remove data directory
        if (removeData) {
            // This would need to be implemented based on how data paths are managed
            log.info("Data removal requested but not implemented yet");
        }
    }

    /**
     * Get container logs with specified parameters
     */
    public java.util.List<String> getContainerLogs(SshConnection connection, LogRequestDto request) throws Exception {
        return dockerService.getContainerLogs(connection, request.getContainerName(),
                                            request.getTailLines(), request.getDays());
    }
}
