package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.dto.sillytavern.*;
import com.fufu.terminal.model.SshConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.List;

/**
 * SillyTavern 管理核心服务类。
 * 提供部署、状态检查、生命周期管理等高阶操作。
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
     * 校验 SillyTavern 部署所需的系统要求。
     *
     * @param connection SSH 连接信息
     * @return 系统信息 DTO，包含校验结果
     */
    public SystemInfoDto validateSystemRequirements(SshConnection connection) {
        log.info("正在校验 SillyTavern 部署的系统要求");
        return systemDetectionService.validateSystemRequirements(connection);
    }

    /**
     * 获取默认容器的当前状态。
     *
     * @param connection SSH 连接信息
     * @return 容器状态 DTO
     */
    public ContainerStatusDto getContainerStatus(SshConnection connection) {
        return getContainerStatus(connection, DEFAULT_CONTAINER_NAME);
    }

    /**
     * 获取指定容器名的容器状态。
     *
     * @param connection SSH 连接信息
     * @param containerName 容器名称
     * @return 容器状态 DTO
     */
    public ContainerStatusDto getContainerStatus(SshConnection connection, String containerName) {
        log.debug("获取容器状态: {}", containerName);
        return dockerService.getContainerStatus(connection, containerName);
    }

    /**
     * 判断默认 SillyTavern 容器是否正在运行。
     *
     * @param connection SSH 连接信息
     * @return 是否运行中
     */
    public boolean isContainerRunning(SshConnection connection) {
        return isContainerRunning(connection, DEFAULT_CONTAINER_NAME);
    }

    /**
     * 判断指定容器是否正在运行。
     *
     * @param connection SSH 连接信息
     * @param containerName 容器名称
     * @return 是否运行中
     */
    public boolean isContainerRunning(SshConnection connection, String containerName) {
        ContainerStatusDto status = getContainerStatus(connection, containerName);
        return status.getExists() && status.getRunning();
    }

    /**
     * 异步部署 SillyTavern 容器，并通过回调实时反馈进度。
     *
     * @param connection SSH 连接信息
     * @param request 部署请求参数
     * @param progressCallback 进度回调，实时反馈部署阶段
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> deployContainer(SshConnection connection,
                                                   DeploymentRequestDto request,
                                                   Consumer<DeploymentProgressDto> progressCallback) {
        log.info("开始部署 SillyTavern，参数: {}", request);

        return CompletableFuture.runAsync(() -> {
            try {
                // 阶段 1：系统校验
                progressCallback.accept(DeploymentProgressDto.success("validation", 10, "验证系统要求..."));

                SystemInfoDto systemInfo = validateSystemRequirements(connection);
                if (!systemInfo.getMeetsRequirements()) {
                    String errorMessage = "系统不满足部署要求:\n" + String.join("\n", systemInfo.getRequirementChecks());
                    progressCallback.accept(DeploymentProgressDto.error("validation", errorMessage));
                    return;
                }

                // 阶段 2：检查容器是否已存在
                progressCallback.accept(DeploymentProgressDto.success("check-existing", 20, "检查现有容器..."));

                ContainerStatusDto existingStatus = getContainerStatus(connection, request.getContainerName());
                if (existingStatus.getExists()) {
                    progressCallback.accept(DeploymentProgressDto.error("check-existing",
                            "容器 '" + request.getContainerName() + "' 已存在。请先删除现有容器或使用不同的名称。"));
                    return;
                }

                // 阶段 3：拉取 Docker 镜像
                progressCallback.accept(DeploymentProgressDto.success("pull-image", 30, "拉取 Docker 镜像..."));

                dockerService.pullImage(connection, request.getDockerImage(), (pullProgress) -> {
                    progressCallback.accept(DeploymentProgressDto.success("pull-image", 50, pullProgress));
                }).get(); // 等待镜像拉取完成

                // 阶段 4：创建容器
                progressCallback.accept(DeploymentProgressDto.success("create-container", 70, "创建容器..."));

                String containerId = dockerService.createContainer(
                        connection,
                        request.getContainerName(),
                        request.getDockerImage(),
                        request.getPort(),
                        request.getDataPath()
                );

                // 阶段 5：验证部署
                progressCallback.accept(DeploymentProgressDto.success("verify", 90, "验证部署..."));

                // 等待容器启动
                Thread.sleep(2000);

                ContainerStatusDto finalStatus = getContainerStatus(connection, request.getContainerName());
                if (finalStatus.getExists() && finalStatus.getRunning()) {
                    progressCallback.accept(DeploymentProgressDto.completed(
                            "SillyTavern 部署成功！容器正在运行，可通过端口 " + request.getPort() + " 访问。"));
                    log.info("SillyTavern 部署成功");
                } else {
                    progressCallback.accept(DeploymentProgressDto.error("verify",
                            "容器创建成功但未正常启动。请检查日志获取详细信息。"));
                }

            } catch (Exception e) {
                log.error("部署 SillyTavern 过程中发生异常", e);
                progressCallback.accept(DeploymentProgressDto.error("deployment",
                        "部署过程中发生错误: " + e.getMessage()));
            }
        });
    }

    /**
     * 启动默认 SillyTavern 容器。
     *
     * @param connection SSH 连接信息
     * @throws Exception 启动失败时抛出
     */
    public void startContainer(SshConnection connection) throws Exception {
        startContainer(connection, DEFAULT_CONTAINER_NAME);
    }

    /**
     * 启动指定容器。
     *
     * @param connection SSH 连接信息
     * @param containerName 容器名称
     * @throws Exception 启动失败时抛出
     */
    public void startContainer(SshConnection connection, String containerName) throws Exception {
        log.info("启动 SillyTavern 容器: {}", containerName);

        ContainerStatusDto status = getContainerStatus(connection, containerName);
        if (!status.getExists()) {
            throw new IllegalStateException("容器 '" + containerName + "' 不存在");
        }

        if (status.getRunning()) {
            throw new IllegalStateException("容器 '" + containerName + "' 已经在运行");
        }

        dockerService.startContainer(connection, containerName);
    }

    /**
     * 停止默认 SillyTavern 容器。
     *
     * @param connection SSH 连接信息
     * @throws Exception 停止失败时抛出
     */
    public void stopContainer(SshConnection connection) throws Exception {
        stopContainer(connection, DEFAULT_CONTAINER_NAME);
    }

    /**
     * 停止指定容器。
     *
     * @param connection SSH 连接信息
     * @param containerName 容器名称
     * @throws Exception 停止失败时抛出
     */
    public void stopContainer(SshConnection connection, String containerName) throws Exception {
        log.info("停止 SillyTavern 容器: {}", containerName);

        ContainerStatusDto status = getContainerStatus(connection, containerName);
        if (!status.getExists()) {
            throw new IllegalStateException("容器 '" + containerName + "' 不存在");
        }

        if (!status.getRunning()) {
            throw new IllegalStateException("容器 '" + containerName + "' 未在运行");
        }

        dockerService.stopContainer(connection, containerName);
    }

    /**
     * 重启默认 SillyTavern 容器。
     *
     * @param connection SSH 连接信息
     * @throws Exception 重启失败时抛出
     */
    public void restartContainer(SshConnection connection) throws Exception {
        restartContainer(connection, DEFAULT_CONTAINER_NAME);
    }

    /**
     * 重启指定容器。
     *
     * @param connection SSH 连接信息
     * @param containerName 容器名称
     * @throws Exception 重启失败时抛出
     */
    public void restartContainer(SshConnection connection, String containerName) throws Exception {
        log.info("重启 SillyTavern 容器: {}", containerName);

        ContainerStatusDto status = getContainerStatus(connection, containerName);
        if (!status.getExists()) {
            throw new IllegalStateException("容器 '" + containerName + "' 不存在");
        }

        dockerService.restartContainer(connection, containerName);
    }

    /**
     * 升级默认 SillyTavern 容器（拉取最新镜像并重启）。
     *
     * @param connection SSH 连接信息
     * @param progressCallback 进度回调
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> upgradeContainer(SshConnection connection,
                                                    Consumer<String> progressCallback) {
        return upgradeContainer(connection, DEFAULT_CONTAINER_NAME, progressCallback);
    }

    /**
     * 升级指定容器（拉取最新镜像并重启）。
     *
     * @param connection SSH 连接信息
     * @param containerName 容器名称
     * @param progressCallback 进度回调
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> upgradeContainer(SshConnection connection, String containerName,
                                                    Consumer<String> progressCallback) {
        log.info("升级 SillyTavern 容器: {}", containerName);

        return CompletableFuture.runAsync(() -> {
            try {
                ContainerStatusDto status = getContainerStatus(connection, containerName);
                if (!status.getExists()) {
                    throw new IllegalStateException("容器 '" + containerName + "' 不存在");
                }

                String image = status.getImage();
                if (image == null) {
                    image = DEFAULT_IMAGE;
                }

                // 停止容器（如已运行）
                if (status.getRunning()) {
                    progressCallback.accept("正在停止容器...");
                    dockerService.stopContainer(connection, containerName);
                }

                // 拉取最新镜像
                progressCallback.accept("正在拉取最新镜像...");
                dockerService.pullImage(connection, image, progressCallback).get();

                // 启动容器
                progressCallback.accept("正在启动容器...");
                dockerService.startContainer(connection, containerName);

                progressCallback.accept("升级完成");

            } catch (Exception e) {
                log.error("升级容器 '{}' 失败", containerName, e);
                throw new RuntimeException("升级失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 删除默认 SillyTavern 容器。
     *
     * @param connection SSH 连接信息
     * @param removeData 是否同时删除数据目录
     * @throws Exception 删除失败时抛出
     */
    public void deleteContainer(SshConnection connection, boolean removeData) throws Exception {
        deleteContainer(connection, DEFAULT_CONTAINER_NAME, removeData);
    }

    /**
     * 删除指定容器。
     *
     * @param connection SSH 连接信息
     * @param containerName 容器名称
     * @param removeData 是否同时删除数据目录
     * @throws Exception 删除失败时抛出
     */
    public void deleteContainer(SshConnection connection, String containerName, boolean removeData) throws Exception {
        log.info("删除 SillyTavern 容器: {} (removeData: {})", containerName, removeData);

        ContainerStatusDto status = getContainerStatus(connection, containerName);
        if (!status.getExists()) {
            throw new IllegalStateException("容器 '" + containerName + "' 不存在");
        }

        // 如容器正在运行，先停止
        if (status.getRunning()) {
            dockerService.stopContainer(connection, containerName);
        }

        // 删除容器
        dockerService.removeContainer(connection, containerName, true);

        // 可选：删除数据目录（具体实现需根据数据路径管理方式补充）
        if (removeData) {
            log.info("请求删除数据目录，但尚未实现具体逻辑");
        }
    }

    /**
     * 获取容器日志。
     *
     * @param connection SSH 连接信息
     * @param request 日志请求参数
     * @return 日志内容列表
     * @throws Exception 获取日志失败时抛出
     */
    public List<String> getContainerLogs(SshConnection connection, LogRequestDto request) throws Exception {
        return dockerService.getContainerLogs(connection, request.getContainerName(),
                request.getTailLines(), request.getDays());
    }
}
