package com.fufu.terminal.service.sillytavern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.dto.sillytavern.ContainerStatusDto;
import com.fufu.terminal.dto.sillytavern.ConfigurationDto;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Docker 容器管理服务。通过 SSH 远程执行 Docker 命令，管理容器生命周期并查询状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerContainerService {

    private final SshCommandService sshCommandService;
    private final ObjectMapper objectMapper;
    private final ConfigurationService configurationService;

    private static final String DOCKER_CMD = "sudo docker";
    private static final String DOCKER_VERSION_CHECK = "docker --version";
    private static final String DOCKER_VERSION_KEYWORD = "docker version";

    /**
     * 获取指定容器的详细状态信息，一次 SSH 调用获取完整 JSON，再解析填充 DTO。
     *
     * @param connection    SSH 连接信息
     * @param containerName 容器名称
     * @return 容器状态 DTO
     */
    public ContainerStatusDto getContainerStatus(SshConnection connection, String containerName) {
        log.debug("获取容器状态: {}", containerName);
        ContainerStatusDto status = new ContainerStatusDto();
        status.setContainerName(containerName);
        status.setLastUpdated(LocalDateTime.now());

        try {
            // 1. 检测 Docker 可用性
            if (!isDockerAvailable(connection)) {
                log.warn("目标系统未检测到 Docker");
                return ContainerStatusDto.dockerNotAvailable();
            }
            log.debug("Docker可用性检查通过");

            // 2. Inspect 一次性获取全部信息
            String inspectCmd = String.format(
                    "%s inspect --format '{{json .}}' %s",
                    DOCKER_CMD, containerName
            );
            log.debug("执行容器检查命令: {}", inspectCmd);
            String json = executeCommand(connection, inspectCmd).trim();
            log.debug("容器检查命令输出长度: {}", json.length());
            if (json.isEmpty()) {
                log.debug("容器不存在: {}", containerName);
                return ContainerStatusDto.notExists();
            }

            // 3. 解析 JSON
            JsonNode root = objectMapper.readTree(json);
            JsonNode state = root.path("State");
            JsonNode config = root.path("Config");
            JsonNode network = root.path("NetworkSettings").path("Ports");

            status.setExists(true);
            status.setStatus(state.path("Status").asText(""));
            status.setRunning(state.path("Running").asBoolean(false));
            status.setImage(config.path("Image").asText(""));
            String id = root.path("Id").asText("");
            status.setContainerId(id.length() > 12 ? id.substring(0, 12) : id);

            log.debug("容器状态解析结果: exists={}, running={}, status={}",
                     status.getExists(), status.getRunning(), status.getStatus());

            // 启动时长计算
            if (status.getRunning()) {
                String startedAt = state.path("StartedAt").asText("");
                try {
                    LocalDateTime startTime = LocalDateTime.parse(
                            startedAt.substring(0, 19),
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                    );
                    long uptime = Duration.between(startTime, LocalDateTime.now()).getSeconds();
                    status.setUptimeSeconds(uptime);
                } catch (Exception e) {
                    log.warn("解析容器启动时间失败", e);
                }
            }

            // 4. 解析端口映射（取第一个 host 端口）
            if (network.isObject()) {
                network.fields().forEachRemaining(entry -> {
                    JsonNode bindings = entry.getValue();
                    if (bindings.isArray() && bindings.size() > 0) {
                        JsonNode host = bindings.get(0);
                        Optional.ofNullable(host.path("HostPort").asText(null))
                                .map(p -> {
                                    try {
                                        return Integer.parseInt(p);
                                    } catch (NumberFormatException ex) {
                                        return null;
                                    }
                                })
                                .ifPresent(status::setPort);
                    }
                });
            }
            log.debug("容器端口映射: {}", status.getPort());

            // 设置主机地址（SSH连接的目标主机）
            status.setHostAddress(connection.getSession().getHost());
            log.debug("服务器主机地址: {}", status.getHostAddress());

            // 从配置文件读取SillyTavern账号密码
            try {
                ConfigurationDto configurationDto = configurationService.readConfiguration(connection, containerName);
                status.setUsername(configurationDto.getUsername() != null ? configurationDto.getUsername() : "admin");
                status.setPassword(configurationDto.getPassword() != null ? configurationDto.getPassword() : "password");
                status.setAcceleratedUrl("暂无");
                log.debug("SillyTavern访问信息: username={}, hasPassword={}", status.getUsername(), configurationDto.getHasPassword());
            } catch (Exception e) {
                log.warn("读取配置文件失败，使用默认账号密码: {}", e.getMessage());
                // 使用默认值
                status.setUsername("admin");
                status.setPassword("password");
                status.setAcceleratedUrl("暂无");
            }

            // 5. 获取实时资源使用（内存 + CPU）
            if (status.getRunning()) {
                enrichResourceUsage(connection, containerName, status);
            }

            log.debug("容器状态获取完成: {}", status);
            return status;

        } catch (Exception e) {
            log.error("获取容器状态异常: {}", containerName, e);
            ContainerStatusDto err = ContainerStatusDto.notExists();
            err.setStatus("Error: " + e.getMessage());
            return err;
        }
    }

    /**
     * 异步拉取 Docker 镜像，并通过回调返回进度信息。
     *
     * @param connection       SSH 连接信息
     * @param image            镜像名
     * @param progressCallback 进度回调
     * @return 异步任务
     */
    public CompletableFuture<Void> pullImage(SshConnection connection,
                                             String image,
                                             Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            try {
                progressCallback.accept("正在拉取镜像: " + image);
                String cmd = String.format("%s pull %s", DOCKER_CMD, image);
                executeCommand(connection, cmd);
                progressCallback.accept("镜像拉取完成: " + image);
            } catch (Exception e) {
                log.error("拉取镜像异常: {}", image, e);
                throw new RuntimeException("拉取镜像失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 创建 Docker 容器。
     *
     * @param connection    SSH 连接信息
     * @param containerName 容器名称
     * @param image         镜像名称
     * @param port          映射端口
     * @param workingDir    工作目录
     */
    public void createContainer(SshConnection connection, String containerName, String image, Integer port, String workingDir) {
        log.debug("创建容器: {}, 镜像: {}, 端口: {}", containerName, image, port);
        try {
            String portMapping = port != null ? String.format("-p %d:8000", port) : "";
            String workDirParam = workingDir != null ? String.format("-w %s", workingDir) : "";

            String cmd = String.format(
                    "%s run -d --name %s %s %s %s",
                    DOCKER_CMD, containerName, portMapping, workDirParam, image
            );
            executeCommand(connection, cmd);
            log.info("容器创建成功: {}", containerName);
        } catch (Exception e) {
            log.error("创建容器失败: {}", containerName, e);
            throw new RuntimeException("创建容器失败: " + e.getMessage(), e);
        }
    }

    /**
     * 启动容器。
     *
     * @param connection    SSH 连接信息
     * @param containerName 容器名称
     */
    public void startContainer(SshConnection connection, String containerName) {
        log.debug("启动容器: {}", containerName);
        try {
            String cmd = String.format("%s start %s", DOCKER_CMD, containerName);
            executeCommand(connection, cmd);
            log.info("容器启动成功: {}", containerName);
        } catch (Exception e) {
            log.error("启动容器失败: {}", containerName, e);
            throw new RuntimeException("启动容器失败: " + e.getMessage(), e);
        }
    }

    /**
     * 停止容器。
     *
     * @param connection    SSH 连接信息
     * @param containerName 容器名称
     */
    public void stopContainer(SshConnection connection, String containerName) {
        log.debug("停止容器: {}", containerName);
        try {
            String cmd = String.format("%s stop %s", DOCKER_CMD, containerName);
            executeCommand(connection, cmd);
            log.info("容器停止成功: {}", containerName);
        } catch (Exception e) {
            log.error("停止容器失败: {}", containerName, e);
            throw new RuntimeException("停止容器失败: " + e.getMessage(), e);
        }
    }

    /**
     * 重启容器。
     *
     * @param connection    SSH 连接信息
     * @param containerName 容器名称
     */
    public void restartContainer(SshConnection connection, String containerName) {
        log.debug("重启容器: {}", containerName);
        try {
            String cmd = String.format("%s restart %s", DOCKER_CMD, containerName);
            executeCommand(connection, cmd);
            log.info("容器重启成功: {}", containerName);
        } catch (Exception e) {
            log.error("重启容器失败: {}", containerName, e);
            throw new RuntimeException("重启容器失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除容器。
     *
     * @param connection    SSH 连接信息
     * @param containerName 容器名称
     * @param force         是否强制删除
     */
    public void removeContainer(SshConnection connection, String containerName, boolean force) {
        log.debug("删除容器: {}, 强制: {}", containerName, force);
        try {
            String forceParam = force ? "-f" : "";
            String cmd = String.format("%s rm %s %s", DOCKER_CMD, forceParam, containerName);
            executeCommand(connection, cmd);
            log.info("容器删除成功: {}", containerName);
        } catch (Exception e) {
            log.error("删除容器失败: {}", containerName, e);
            throw new RuntimeException("删除容器失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取容器日志。
     *
     * @param connection    SSH 连接信息
     * @param containerName 容器名称
     * @param days          天数
     * @param tailLines     尾部行数
     * @return 日志列表
     */
    public java.util.List<String> getContainerLogs(SshConnection connection, String containerName, Integer days, Integer tailLines) {
        log.debug("获取容器日志: {}, 天数: {}, 行数: {}", containerName, days, tailLines);
        try {
            String sinceParam = days != null ? String.format("--since %dh", days * 24) : "";
            String tailParam = tailLines != null ? String.format("--tail %d", tailLines) : "";

            String cmd = String.format("%s logs %s %s %s", DOCKER_CMD, sinceParam, tailParam, containerName);
            String output = executeCommand(connection, cmd);

            return java.util.Arrays.asList(output.split("\n"));
        } catch (Exception e) {
            log.error("获取容器日志失败: {}", containerName, e);
            throw new RuntimeException("获取容器日志失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取容器资源使用情况（内存、CPU），设置到 DTO 中。
     *
     * @param connection    SSH 连接信息
     * @param containerName 容器名称
     * @param status        容器状态 DTO
     */
    private void enrichResourceUsage(SshConnection connection,
                                     String containerName,
                                     ContainerStatusDto status) {
        try {
            String cmd = String.format(
                    "%s stats --no-stream --format '{{.MemUsage}}|{{.CPUPerc}}' %s",
                    DOCKER_CMD, containerName
            );
            String out = executeCommand(connection, cmd).trim();
            String[] parts = out.split("\\|", 2);
            // 内存
            String used = parts[0].split("/")[0].trim();
            double factor = used.toLowerCase().endsWith("gib") ? 1024 : 1;
            double val = Double.parseDouble(used.replaceAll("[^0-9.]", ""));
            status.setMemoryUsageMB(Math.round(val * factor));
            // CPU
            String cpu = parts[1].replace("%", "").trim();
            status.setCpuUsagePercent(Double.parseDouble(cpu));
        } catch (Exception e) {
            log.warn("获取资源使用失败: {}", containerName, e);
        }
    }

    /**
     * 检查目标系统是否可用 Docker，一次 SSH 尝试即可。
     *
     * @param connection SSH 连接信息
     * @return 可用返回 true，否则 false
     */
    private boolean isDockerAvailable(SshConnection connection) {
        log.debug("检查Docker可用性...");
        return Stream.of(DOCKER_VERSION_CHECK, DOCKER_CMD + " --version")
                .anyMatch(cmd -> {
                    try {
                        log.debug("执行Docker版本检查命令: {}", cmd);
                        String out = executeCommand(connection, cmd).toLowerCase();
                        log.debug("Docker版本检查命令输出: {}", out);
                        boolean available = out.contains("docker version");
                        log.debug("Docker可用性检查结果: {} (期望包含: docker version)", available);
                        return available;
                    } catch (Exception e) {
                        log.debug("Docker版本检查命令执行失败: {} - {}", cmd, e.getMessage());
                        return false;
                    }
                });
    }

    /**
     * 执行 SSH 命令并返回标准输出，失败时抛出异常。
     *
     * @param connection SSH 连接
     * @param command    Shell 命令
     * @return 标准输出文本
     * @throws Exception 执行失败或中断
     */
    private String executeCommand(SshConnection connection, String command) throws Exception {
        try {
            CommandResult res = sshCommandService.executeCommand(connection.getJschSession(), command);
            if (res.exitStatus() != 0) {
                String msg = buildErrorMessage(res.stderr(), command, res.exitStatus());
                log.warn("命令失败: {} - {}", command, msg);
                throw new RuntimeException(msg);
            }
            return res.stdout();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new Exception("命令被中断: " + command, ie);
        }
    }

    /**
     * 构造用户友好的错误消息。
     *
     * @param stderr     标准错误输出
     * @param cmd        执行命令
     * @param exitStatus 退出码
     * @return 格式化后的错误消息
     */
    private String buildErrorMessage(String stderr, String cmd, int exitStatus) {
        String err = stderr.toLowerCase();
        if (err.contains("not found")) {
            return "未检测到 Docker，请先安装。";
        }
        if (err.contains("cannot connect to the docker daemon")) {
            return "Docker 守护进程未运行，请启动服务。";
        }
        if (err.contains("permission denied")) {
            return "权限不足，请使用 sudo 或将用户加入 docker 组。";
        }
        return String.format("命令失败（exit %d）：%s - %s", exitStatus, cmd, stderr.trim());
    }
}
