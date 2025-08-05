package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.dto.sillytavern.ContainerStatusDto;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Docker容器管理服务。负责通过SSH远程执行Docker命令，实现容器生命周期管理及状态查询。
 * <p>
 * 主要功能包括：容器状态查询、镜像拉取、容器创建/启动/停止/重启/删除、日志获取、资源监控等。
 * </p>
 *
 * @author
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerContainerService {

    private final SshCommandService sshCommandService;

    private static final String DOCKER_COMMAND_PREFIX = "sudo docker";
    private static final String DOCKER_PORT_SPLITTER = "->";
    private static final int CONTAINER_ID_LENGTH = 12;
    private static final String DOCKER_VERSION_CMD = "docker --version";
    private static final String SUDO_DOCKER_VERSION_CMD = "sudo docker --version";
    private static final String DOCKER_VERSION_KEYWORD = "docker version";

    /**
     * 获取指定容器的详细状态信息。
     *
     * @param connection    SSH连接信息
     * @param containerName 容器名称
     * @return 容器状态数据传输对象
     */
    public ContainerStatusDto getContainerStatus(SshConnection connection, String containerName) {
        log.debug("获取容器状态: {}", containerName);

        try {
            if (!isDockerAvailable(connection)) {
                log.warn("目标系统未检测到Docker");
                return ContainerStatusDto.dockerNotAvailable();
            }

            String existsResult = executeCommand(connection,
                    DOCKER_COMMAND_PREFIX + " ps -a --filter name=" + containerName + " --format '{{.Names}}'");

            if (existsResult.trim().isEmpty()) {
                return ContainerStatusDto.notExists();
            }

            ContainerStatusDto status = new ContainerStatusDto();
            status.setExists(true);
            status.setContainerName(containerName);
            status.setLastUpdated(LocalDateTime.now());

            String containerInfo = executeCommand(connection,
                    DOCKER_COMMAND_PREFIX + " inspect " + containerName + " --format " +
                            "'{{.State.Running}}|{{.State.Status}}|{{.Config.Image}}|{{.Id}}|{{.State.StartedAt}}'");

            String[] infoParts = containerInfo.trim().split("\\|");
            if (infoParts.length >= 5) {
                status.setRunning(Boolean.parseBoolean(infoParts[0]));
                status.setStatus(infoParts[1]);
                status.setImage(infoParts[2]);
                status.setContainerId(infoParts[3].substring(0, Math.min(CONTAINER_ID_LENGTH, infoParts[3].length())));

                if (status.getRunning()) {
                    try {
                        LocalDateTime startTime = LocalDateTime.parse(infoParts[4].substring(0, 19),
                                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                        long uptimeSeconds = Duration.between(startTime, LocalDateTime.now()).getSeconds();
                        status.setUptimeSeconds(uptimeSeconds);
                    } catch (Exception e) {
                        log.warn("解析容器启动时间失败", e);
                    }
                }
            }

            if (status.getRunning()) {
                parseContainerPort(connection, containerName, status);
                getResourceUsage(connection, containerName, status);
            }

            return status;

        } catch (Exception e) {
            log.error("获取容器状态异常: {}", containerName, e);
            ContainerStatusDto errorStatus = ContainerStatusDto.notExists();
            errorStatus.setStatus("Error: " + e.getMessage());
            return errorStatus;
        }
    }

    /**
     * 异步拉取Docker镜像，并通过回调返回进度信息。
     *
     * @param connection       SSH连接信息
     * @param image            镜像名
     * @param progressCallback 拉取进度回调
     * @return 异步任务
     */
    public CompletableFuture<Void> pullImage(SshConnection connection, String image,
                                             Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("拉取Docker镜像: {}", image);
                progressCallback.accept("正在拉取Docker镜像: " + image);

                executeCommand(connection, DOCKER_COMMAND_PREFIX + " pull " + image);

                progressCallback.accept("镜像拉取完成: " + image);
                log.info("镜像拉取成功: {}", image);

            } catch (Exception e) {
                log.error("拉取镜像异常: {}", image, e);
                throw new RuntimeException("拉取镜像失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 创建并启动新容器。
     *
     * @param connection    SSH连接信息
     * @param containerName 容器名称
     * @param image         镜像名
     * @param port          映射端口
     * @param dataPath      数据卷路径
     * @return 容器ID
     * @throws Exception 命令执行异常
     */
    public String createContainer(SshConnection connection, String containerName, String image,
                                  int port, String dataPath) throws Exception {
        log.info("创建容器: {} 镜像: {} 端口: {}", containerName, image, port);

        executeCommand(connection, "mkdir -p " + dataPath);

        String createCommand = String.format(
                "%s run -d --name %s -p %d:8000 -v %s:/app/data %s",
                DOCKER_COMMAND_PREFIX, containerName, port, dataPath, image
        );

        String result = executeCommand(connection, createCommand);
        String containerId = result.trim();

        log.info("容器创建成功, ID: {}", containerId);
        return containerId;
    }

    /**
     * 启动已存在的容器。
     *
     * @param connection    SSH连接信息
     * @param containerName 容器名称
     * @throws Exception 命令执行异常
     */
    public void startContainer(SshConnection connection, String containerName) throws Exception {
        log.info("启动容器: {}", containerName);
        executeCommand(connection, DOCKER_COMMAND_PREFIX + " start " + containerName);
    }

    /**
     * 停止容器。
     *
     * @param connection    SSH连接信息
     * @param containerName 容器名称
     * @throws Exception 命令执行异常
     */
    public void stopContainer(SshConnection connection, String containerName) throws Exception {
        log.info("停止容器: {}", containerName);
        executeCommand(connection, DOCKER_COMMAND_PREFIX + " stop " + containerName);
    }

    /**
     * 重启容器。
     *
     * @param connection    SSH连接信息
     * @param containerName 容器名称
     * @throws Exception 命令执行异常
     */
    public void restartContainer(SshConnection connection, String containerName) throws Exception {
        log.info("重启容器: {}", containerName);
        executeCommand(connection, DOCKER_COMMAND_PREFIX + " restart " + containerName);
    }

    /**
     * 移除容器。
     *
     * @param connection    SSH连接信息
     * @param containerName 容器名称
     * @param force         是否强制移除
     * @throws Exception 命令执行异常
     */
    public void removeContainer(SshConnection connection, String containerName, boolean force) throws Exception {
        log.info("移除容器: {} (force: {})", containerName, force);
        String command = DOCKER_COMMAND_PREFIX + " rm " + (force ? "-f " : "") + containerName;
        executeCommand(connection, command);
    }

    /**
     * 获取容器日志。
     *
     * @param connection    SSH连接信息
     * @param containerName 容器名称
     * @param tailLines     日志行数
     * @param days          查询天数
     * @return 日志列表
     * @throws Exception 命令执行异常
     */
    public List<String> getContainerLogs(SshConnection connection, String containerName,
                                         int tailLines, int days) throws Exception {
        log.debug("获取容器日志: {} (tail: {}, days: {})", containerName, tailLines, days);

        String command = String.format("%s logs --since %dd --tail %d %s",
                DOCKER_COMMAND_PREFIX, days, tailLines, containerName);

        String result = executeCommand(connection, command);

        List<String> logs = new ArrayList<>();
        if (!result.trim().isEmpty()) {
            String[] lines = result.split("\n");
            for (String line : lines) {
                logs.add(line);
            }
        }

        return logs;
    }

    /**
     * 获取容器资源使用情况（内存、CPU）。
     *
     * @param connection    SSH连接信息
     * @param containerName 容器名称
     * @param status        容器状态对象
     */
    private void getResourceUsage(SshConnection connection, String containerName, ContainerStatusDto status) {
        try {
            String statsResult = executeCommand(connection,
                    DOCKER_COMMAND_PREFIX + " stats --no-stream --format " +
                            "'{{.MemUsage}}|{{.CPUPerc}}' " + containerName);

            String[] statsParts = statsResult.trim().split("\\|");
            if (statsParts.length >= 2) {
                // 解析内存使用（如 "123.4MiB / 2GiB"）
                String memUsage = statsParts[0].trim();
                if (memUsage.contains("/")) {
                    String usedMem = memUsage.split("/")[0].trim();
                    if (usedMem.toLowerCase().contains("mib")) {
                        String memValue = usedMem.replaceAll("[^0-9.]", "");
                        status.setMemoryUsageMB(Math.round(Double.parseDouble(memValue)));
                    } else if (usedMem.toLowerCase().contains("gib")) {
                        String memValue = usedMem.replaceAll("[^0-9.]", "");
                        status.setMemoryUsageMB(Math.round(Double.parseDouble(memValue) * 1024));
                    }
                }

                // 解析CPU使用率（如 "1.23%"）
                String cpuUsage = statsParts[1].trim().replace("%", "");
                try {
                    status.setCpuUsagePercent(Double.parseDouble(cpuUsage));
                } catch (NumberFormatException e) {
                    log.warn("CPU使用率解析失败: {}", cpuUsage);
                }
            }
        } catch (Exception e) {
            log.warn("获取容器资源使用失败: {}", containerName, e);
        }
    }

    /**
     * 解析容器端口映射并设置到状态对象。
     *
     * @param connection    SSH连接信息
     * @param containerName 容器名称
     * @param status        容器状态对象
     */
    private void parseContainerPort(SshConnection connection, String containerName, ContainerStatusDto status) {
        try {
            String portInfo = executeCommand(connection,
                    DOCKER_COMMAND_PREFIX + " port " + containerName);
            if (!portInfo.trim().isEmpty()) {
                String[] portLines = portInfo.trim().split("\n");
                for (String portLine : portLines) {
                    if (portLine.contains(DOCKER_PORT_SPLITTER)) {
                        String[] portParts = portLine.split(DOCKER_PORT_SPLITTER);
                        if (portParts.length > 1) {
                            String[] hostPortArr = portParts[1].trim().split(":");
                            if (hostPortArr.length > 1) {
                                try {
                                    status.setPort(Integer.parseInt(hostPortArr[1]));
                                    break;
                                } catch (NumberFormatException e) {
                                    log.warn("端口解析失败: {}", portLine);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取容器端口信息失败: {}", containerName, e);
        }
    }

    /**
     * 检查目标系统是否安装并可用Docker。
     *
     * @param connection SSH连接信息
     * @return Docker可用返回true，否则false
     */
    private boolean isDockerAvailable(SshConnection connection) {
        try {
            try {
                String result = executeCommand(connection, DOCKER_VERSION_CMD);
                return result.toLowerCase().contains(DOCKER_VERSION_KEYWORD);
            } catch (Exception e) {
                try {
                    String result = executeCommand(connection, SUDO_DOCKER_VERSION_CMD);
                    return result.toLowerCase().contains(DOCKER_VERSION_KEYWORD);
                } catch (Exception e2) {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通过SSH执行命令并返回标准输出。
     *
     * @param connection SSH连接信息
     * @param command    执行命令
     * @return 命令标准输出
     * @throws Exception 命令执行异常
     */
    private String executeCommand(SshConnection connection, String command) throws Exception {
        try {
            CommandResult result = sshCommandService.executeCommand(connection.getJschSession(), command);

            if (result.exitStatus() != 0) {
                String errorMsg = "命令执行失败, exit code " + result.exitStatus() +
                        ": " + command;
                String stderr = result.stderr();
                if (stderr.contains("docker: command not found") || stderr.contains("docker: not found")) {
                    errorMsg = "未检测到Docker，请先安装Docker。";
                } else if (stderr.contains("Cannot connect to the Docker daemon")) {
                    errorMsg = "Docker守护进程未运行，请先启动Docker服务。";
                } else if (stderr.contains("permission denied")) {
                    errorMsg = "访问Docker权限不足，请将用户加入docker组或使用sudo。";
                } else {
                    errorMsg += " - " + stderr;
                }
                log.warn("命令执行失败: {} - {}", command, errorMsg);
                throw new RuntimeException(errorMsg);
            }

            return result.stdout();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("命令执行被中断: " + command, e);
        }
    }
}
