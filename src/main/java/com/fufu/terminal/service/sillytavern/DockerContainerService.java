package com.fufu.terminal.service.sillytavern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.dto.sillytavern.ConfigurationDto;
import com.fufu.terminal.dto.sillytavern.ContainerStatusDto;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Docker 容器管理服务。
 * 通过 SSH 远程执行 Docker 命令，管理容器生命周期、查询状态及获取日志。
 * 优化了远程命令执行效率，将多次SSH调用合并为单次调用。
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
    private static final String OUTPUT_DELIMITER = "---FUFU_TERMINAL_DELIMITER---";

    /**
     * 获取指定容器的详细状态信息。
     * <p>
     * <b>[优化]</b>: 此方法通过单次SSH调用执行多条命令，显著减少了网络开销和延迟。
     * 它一次性获取容器的inspect信息、资源使用情况和SillyTavern配置文件内容，然后在本地进行解析。
     *
     * @param connection    SSH 连接信息
     * @param containerName 容器名称
     * @return 包含容器详细状态的 DTO
     */
    public ContainerStatusDto getContainerStatus(SshConnection connection, String containerName) {
        log.debug("开始获取容器状态: {}", containerName);
        ContainerStatusDto status = new ContainerStatusDto();
        status.setContainerName(containerName);
        status.setLastUpdated(LocalDateTime.now());

        try {
            if (!isDockerAvailable(connection)) {
                log.warn("目标系统未检测到 Docker 或 Docker 服务未运行。");
                return ContainerStatusDto.dockerNotAvailable();
            }

            // [优化] 将多条命令合并到一次SSH执行中，使用分隔符区分输出
            String configPath = configurationService.getConfigurationPath(containerName);
            String combinedCommand = String.format(
                    "%s inspect --format '{{json .}}' %s 2>/dev/null; echo %s; " +
                            "%s stats --no-stream --format '{{.MemUsage}}|{{.CPUPerc}}' %s 2>/dev/null; echo %s; " +
                            "cat %s 2>/dev/null",
                    DOCKER_CMD, containerName, OUTPUT_DELIMITER,
                    DOCKER_CMD, containerName, OUTPUT_DELIMITER,
                    configPath
            );

            log.debug("执行合并后的状态获取命令: {}", combinedCommand);
            String combinedOutput = executeCommand(connection, combinedCommand);
            String[] outputs = combinedOutput.split(OUTPUT_DELIMITER, -1);

            String inspectJson = outputs.length > 0 ? outputs[0].trim() : "";
            String statsOutput = outputs.length > 1 ? outputs[1].trim() : "";
            String configJson = outputs.length > 2 ? outputs[2].trim() : "";

            if (inspectJson.isEmpty() || inspectJson.contains("No such object")) {
                log.warn("容器 '{}' 不存在。", containerName);
                return ContainerStatusDto.notExists();
            }

            JsonNode root = objectMapper.readTree(inspectJson);
            parseInspectOutput(root, status);
            status.setHostAddress(connection.getSession().getHost());

            if (status.getRunning()) {
                parseStatsOutput(statsOutput, status);
            }

            parseSillyTavernConfig(configJson, status);

            log.debug("容器状态获取完成: {}", status);
            return status;

        } catch (Exception e) {
            log.error("获取容器 '{}' 状态时发生异常", containerName, e);
            ContainerStatusDto errorStatus = ContainerStatusDto.notExists();
            errorStatus.setStatus("错误: " + e.getMessage());
            return errorStatus;
        }
    }

    /**
     * 异步拉取 Docker 镜像，并通过回调函数报告进度。
     *
     * @param connection       SSH 连接信息
     * @param image            要拉取的镜像名称
     * @param progressCallback 用于接收进度更新的消费者
     * @return 代表异步拉取操作的 CompletableFuture
     */
    public CompletableFuture<Void> pullImage(SshConnection connection,
                                             String image,
                                             Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            try {
                progressCallback.accept("正在拉取镜像: " + image);
                String cmd = String.format("%s pull %s", DOCKER_CMD, image);
                // 对于 pull 这种长时间运行的命令，使用流式输出更佳，但此处为简化，保持原有逻辑
                executeCommand(connection, cmd);
                progressCallback.accept("镜像拉取完成: " + image);
            } catch (Exception e) {
                log.error("拉取镜像异常: {}", image, e);
                // 在异步任务中，最好抛出未检查异常
                throw new RuntimeException("拉取镜像失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 创建并启动一个新的 Docker 容器。
     *
     * @param connection    SSH 连接信息
     * @param containerName 容器的名称
     * @param image         用于创建容器的镜像
     * @param port          要映射到容器8000端口的主机端口
     * @param workingDir    容器的工作目录
     */
    public void createContainer(SshConnection connection, String containerName, String image, Integer port, String workingDir) {
        log.debug("创建容器: {}, 镜像: {}, 端口: {}", containerName, image, port);
        String portMapping = (port != null) ? String.format("-p %d:8000", port) : "";
        String workDirParam = StringUtils.hasText(workingDir) ? String.format("-w %s", workingDir) : "";

        // [优化] 使用 Collectors.joining 优雅地处理可选参数，避免空字符串问题
        String options = Arrays.stream(new String[]{portMapping, workDirParam})
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));

        String cmd = String.format("%s run -d --name %s %s %s", DOCKER_CMD, containerName, options, image);
        executeSimpleDockerCommand(connection, cmd, "创建");
    }

    /**
     * 启动一个已存在的 Docker 容器。
     *
     * @param connection    SSH 连接信息
     * @param containerName 要启动的容器名称
     */
    public void startContainer(SshConnection connection, String containerName) {
        executeSimpleDockerCommand(connection, String.format("%s start %s", DOCKER_CMD, containerName), "启动");
    }

    /**
     * 停止一个正在运行的 Docker 容器。
     *
     * @param connection    SSH 连接信息
     * @param containerName 要停止的容器名称
     */
    public void stopContainer(SshConnection connection, String containerName) {
        executeSimpleDockerCommand(connection, String.format("%s stop %s", DOCKER_CMD, containerName), "停止");
    }

    /**
     * 重启一个 Docker 容器。
     *
     * @param connection    SSH 连接信息
     * @param containerName 要重启的容器名称
     */
    public void restartContainer(SshConnection connection, String containerName) {
        executeSimpleDockerCommand(connection, String.format("%s restart %s", DOCKER_CMD, containerName), "重启");
    }

    /**
     * 删除一个 Docker 容器。
     *
     * @param connection    SSH 连接信息
     * @param containerName 要删除的容器名称
     * @param force         是否强制删除（即使容器正在运行）
     */
    public void removeContainer(SshConnection connection, String containerName, boolean force) {
        String forceParam = force ? "-f" : "";
        String cmd = String.format("%s rm %s %s", DOCKER_CMD, forceParam, containerName).trim();
        executeSimpleDockerCommand(connection, cmd, "删除");
    }

    /**
     * 获取指定容器的日志。
     *
     * @param connection    SSH 连接信息
     * @param containerName 容器名称
     * @param days          获取过去指定天数的日志 (可选)
     * @param tailLines     获取日志的最后 N 行 (可选)
     * @return 日志行列表
     */
    public List<String> getContainerLogs(SshConnection connection, String containerName, Integer days, Integer tailLines) {
        log.debug("获取容器日志: {}, 天数: {}, 行数: {}", containerName, days, tailLines);
        try {
            String sinceParam = (days != null && days > 0) ? String.format("--since %dh", days * 24) : "";
            String tailParam = (tailLines != null && tailLines > 0) ? String.format("--tail %d", tailLines) : "";

            String cmd = String.format("%s logs %s %s %s", DOCKER_CMD, sinceParam, tailParam, containerName).trim();
            String output = executeCommand(connection, cmd);

            // [优化] 使用 Stream API，更现代且具表达力
            return output.lines().toList();
        } catch (Exception e) {
            log.error("获取容器 '{}' 日志失败", containerName, e);
            throw new RuntimeException("获取容器日志失败: " + e.getMessage(), e);
        }
    }

    /**
     * <b>[新增]</b> 解析从 "docker inspect" 命令获取的JSON输出，并填充DTO。
     */
    private void parseInspectOutput(JsonNode root, ContainerStatusDto status) {
        JsonNode state = root.path("State");
        JsonNode config = root.path("Config");
        JsonNode network = root.path("NetworkSettings").path("Ports");

        status.setExists(true);
        status.setStatus(state.path("Status").asText("unknown"));
        status.setRunning(state.path("Running").asBoolean(false));
        status.setImage(config.path("Image").asText(""));
        String id = root.path("Id").asText("");
        status.setContainerId(id.length() > 12 ? id.substring(0, 12) : id);

        if (status.getRunning()) {
            // [优化] 使用 Instant.parse 解析ISO 8601时间戳，更健壮
            Optional.ofNullable(state.path("StartedAt").asText(null))
                    .ifPresent(startedAt -> {
                        try {
                            Instant startTime = Instant.parse(startedAt);
                            status.setUptimeSeconds(Duration.between(startTime, Instant.now()).getSeconds());
                        } catch (Exception e) {
                            log.warn("解析容器启动时间失败: {}", startedAt, e);
                        }
                    });
        }

        // 解析端口映射
        network.fields().forEachRemaining(entry -> {
            JsonNode bindings = entry.getValue();
            if (bindings != null && bindings.isArray() && !bindings.isEmpty()) {
                Optional.ofNullable(bindings.get(0).path("HostPort").asText(null))
                        .ifPresent(portStr -> {
                            try {
                                status.setPort(Integer.parseInt(portStr));
                            } catch (NumberFormatException e) {
                                log.warn("解析端口号 '{}' 失败", portStr);
                            }
                        });
            }
        });
    }

    /**
     * <b>[新增]</b> 解析从 "docker stats" 命令获取的资源使用情况输出。
     */
    private void parseStatsOutput(String statsOutput, ContainerStatusDto status) {
        if (!StringUtils.hasText(statsOutput) || !statsOutput.contains("|")) {
            log.warn("获取资源使用情况失败，输出为空或格式不正确: {}", statsOutput);
            return;
        }
        try {
            String[] parts = statsOutput.split("\\|", 2);
            String memPart = parts[0];
            String cpuPart = parts[1];

            // 解析内存
            String[] memUsage = memPart.split("/")[0].trim().toLowerCase().split("\\s+");
            if (memUsage.length == 2) {
                double value = Double.parseDouble(memUsage[0]);
                if (memUsage[1].startsWith("gib")) {
                    value *= 1024;
                } else if (memUsage[1].startsWith("kib")) {
                    value /= 1024;
                }
                status.setMemoryUsageMB(Math.round(value));
            }

            // 解析CPU
            status.setCpuUsagePercent(Double.parseDouble(cpuPart.replace("%", "").trim()));
        } catch (Exception e) {
            log.warn("解析资源使用情况失败: '{}'", statsOutput, e);
        }
    }

    /**
     * <b>[新增]</b> 解析 SillyTavern 的配置文件内容，并填充DTO。
     */
    private void parseSillyTavernConfig(String configJson, ContainerStatusDto status) {
        if (StringUtils.hasText(configJson)) {
            try {
                ConfigurationDto configDto = objectMapper.readValue(configJson, ConfigurationDto.class);
                status.setUsername(configDto.getUsername() != null ? configDto.getUsername() : "admin");
                status.setPassword(configDto.getPassword() != null ? configDto.getPassword() : "password");
                status.setAcceleratedUrl("暂无"); // 可根据需要扩展
                log.debug("成功从远程配置文件加载SillyTavern访问信息。");
            } catch (IOException e) {
                log.warn("解析远程配置文件失败，将使用默认账号密码: {}", e.getMessage());
                setDefaultCredentials(status);
            }
        } else {
            log.warn("未找到远程配置文件，将使用默认账号密码。");
            setDefaultCredentials(status);
        }
    }

    private void setDefaultCredentials(ContainerStatusDto status) {
        status.setUsername("admin");
        status.setPassword("password");
        status.setAcceleratedUrl("暂无");
    }

    /**
     * <b>[新增]</b> 封装了对简单Docker命令（如start, stop）的调用，减少了重复代码。
     */
    private void executeSimpleDockerCommand(SshConnection connection, String command, String operationName) {
        log.debug("{}容器: {}", operationName, command);
        try {
            executeCommand(connection, command);
            log.info("容器{}成功。", operationName);
        } catch (Exception e) {
            log.error("容器{}失败: {}", operationName, e.getMessage());
            throw new RuntimeException(String.format("容器%s失败: %s", operationName, e.getMessage()), e);
        }
    }

    /**
     * 检查目标系统 Docker 是否可用。
     * <p>
     * <b>[优化]</b>: 使用单次SSH调用尝试多个命令，提高了效率。
     *
     * @param connection SSH 连接信息
     * @return 如果 Docker 可用则返回 true，否则 false
     */
    private boolean isDockerAvailable(SshConnection connection) {
        log.debug("检查Docker可用性...");
        // [优化] 将两个命令组合到一次SSH调用中，如果任一成功，则返回"OK"
        String checkCmd = String.format("(%s || %s --version) > /dev/null 2>&1 && echo OK", DOCKER_VERSION_CHECK, DOCKER_CMD);
        try {
            String output = executeCommand(connection, checkCmd);
            boolean available = output.trim().equals("OK");
            log.debug("Docker可用性检查结果: {}", available);
            return available;
        } catch (Exception e) {
            log.warn("Docker可用性检查命令执行失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 通过SSH执行指定的Shell命令。
     *
     * @param connection SSH 连接
     * @param command    要执行的命令
     * @return 命令的标准输出
     * @throws Exception 如果命令执行失败或被中断
     */
    private String executeCommand(SshConnection connection, String command) throws Exception {
        try {
            CommandResult result = sshCommandService.executeInternal(connection.getJschSession(), command);
            // 对于合并的命令，即使部分失败（如文件不存在），也不应直接抛出异常，而是返回输出供上层解析
            if (result.exitStatus() != 0 && !command.contains(OUTPUT_DELIMITER)) {
                String errorMsg = buildErrorMessage(result.stderr(), command, result.exitStatus());
                throw new IOException(errorMsg);
            }
            return result.stdout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("命令执行被中断: " + command, e);
        }
    }

    /**
     * 根据命令执行的错误输出，构造用户友好的错误消息。
     *
     * @param stderr     标准错误输出
     * @param cmd        执行的命令
     * @param exitStatus 退出码
     * @return 格式化后的错误消息
     */
    private String buildErrorMessage(String stderr, String cmd, int exitStatus) {
        String err = stderr.toLowerCase();
        if (err.contains("no such object") || err.contains("not found")) {
            return "操作失败：找不到指定的容器或镜像。";
        }
        if (err.contains("cannot connect to the docker daemon")) {
            return "无法连接到Docker守护进程，请确认Docker服务是否正在运行。";
        }
        if (err.contains("permission denied")) {
            return "权限不足，请检查执行用户是否在 'docker' 组中或是否已配置sudo免密。";
        }
        if (StringUtils.hasText(stderr)) {
            return String.format("命令执行失败 (退出码 %d): %s", exitStatus, stderr.trim());
        }
        return String.format("命令执行失败，退出码: %d", exitStatus);
    }
}
