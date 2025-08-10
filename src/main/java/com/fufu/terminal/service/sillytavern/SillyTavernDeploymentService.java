package com.fufu.terminal.service.sillytavern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fufu.terminal.dto.sillytavern.DeploymentInfoDto;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * SillyTavern容器部署服务
 * <p>
 * 负责创建和部署SillyTavern容器，包括docker-compose配置和容器启动。
 * 基于linux-silly-tavern-docker-deploy.sh脚本的SillyTavern部署功能。
 * </p>
 *
 * @author Claude
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SillyTavernDeploymentService {

    /** SillyTavern容器名 */
    private static final String CONTAINER_NAME = "sillytavern";
    /** Watchtower容器名 */
    private static final String WATCHTOWER_NAME = "watchtower";
    /** 部署目录 */
    private static final String DEPLOYMENT_PATH = "/data/docker/sillytavern";
    /** docker-compose文件名 */
    private static final String DOCKER_COMPOSE_FILE = DEPLOYMENT_PATH + "/docker-compose.yaml";
    /** Docker网络名 */
    private static final String DOCKER_NETWORK = "DockerNet";
    /** 容器启动等待时间(ms) */
    private static final int CONTAINER_STARTUP_WAIT_MS = 5000;

    private volatile String cachedComposeCommand = null;

    private final SshCommandService sshCommandService;
    private final ObjectMapper objectMapper;


    /**
     * 检测服务器支持的 compose 命令（docker compose 或 docker-compose），并缓存结果
     * @param connection SSH连接
     * @return 可用的 compose 命令字符串
     * @throws RuntimeException 若两者都不可用
     */
    private String detectDockerComposeCommand(SshConnection connection) {
        if (cachedComposeCommand != null) {
            return cachedComposeCommand;
        }
        try {
            // 优先检测 docker compose
            CommandResult result = sshCommandService.executeInternal(connection.getJschSession(), "docker compose version");
            if (result.exitStatus() == 0) {
                cachedComposeCommand = "docker compose";
                return cachedComposeCommand;
            }
            // 再检测 docker-compose
            result = sshCommandService.executeInternal(connection.getJschSession(), "docker-compose version");
            if (result.exitStatus() == 0) {
                cachedComposeCommand = "docker-compose";
                return cachedComposeCommand;
            }
        } catch (Exception e) {
            log.warn("检测 compose 命令时发生异常: {}", e.getMessage());
        }
        throw new RuntimeException("服务器未安装 docker compose 或 docker-compose，请先安装其中之一");
    }


    /**
     * 部署SillyTavern容器
     *
     * @param connection        SSH连接
     * @param deploymentConfig  部署配置
     * @param useChineseMirror  是否使用国内镜像源
     * @param progressCallback  进度回调函数
     * @return 部署结果的异步CompletableFuture
     */
    public CompletableFuture<SillyTavernDeploymentResult> deploySillyTavern(SshConnection connection,
                                                                            SillyTavernDeploymentConfig deploymentConfig,
                                                                            boolean useChineseMirror,
                                                                            Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                progressCallback.accept("开始部署SillyTavern容器...");

                // 1. 创建部署目录
                createDeploymentDirectory(connection, progressCallback);

                // 2. 生成docker-compose.yaml文件
                generateDockerComposeFile(connection, deploymentConfig, useChineseMirror, progressCallback);

                // 3. 拉取Docker镜像
                pullDockerImages(connection, progressCallback);

                // 4. 启动容器服务
                startContainerService(connection, progressCallback);

                // 5. 验证部署结果
                SillyTavernDeploymentResult result = verifyDeployment(connection, deploymentConfig, progressCallback);

                // 6. 写入部署信息文件（新增步骤）
                if (result.isSuccess()) {
                    writeDeploymentInfo(connection, deploymentConfig, result, progressCallback);
                }

                if (result.isSuccess()) {
                    progressCallback.accept("✅ SillyTavern部署成功！");
                } else {
                    progressCallback.accept("❌ SillyTavern部署失败，请检查日志");
                }

                return result;

            } catch (Exception e) {
                log.error("SillyTavern部署过程中发生异常", e);
                progressCallback.accept("SillyTavern部署失败: " + e.getMessage());
                return SillyTavernDeploymentResult.builder()
                        .success(false)
                        .message("部署失败: " + e.getMessage())
                        .containerName(CONTAINER_NAME)
                        .deploymentPath(DEPLOYMENT_PATH)
                        .build();
            }
        });
    }

    /**
     * 创建部署目录并设置权限
     *
     * @param connection       SSH连接
     * @param progressCallback 进度回调
     * @throws Exception 创建目录失败时抛出
     */
    private void createDeploymentDirectory(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("创建SillyTavern部署目录...");

        CommandResult mkdirResult = sshCommandService.executeInternal(connection.getJschSession(),
                "sudo mkdir -p " + DEPLOYMENT_PATH);

        if (mkdirResult.exitStatus() != 0) {
            throw new RuntimeException("创建部署目录失败: " + mkdirResult.stderr());
        }

        // 设置目录权限
        sshCommandService.executeInternal(connection.getJschSession(),
                "sudo chmod 755 " + DEPLOYMENT_PATH);
    }

    /**
     * 生成docker-compose.yaml文件
     *
     * @param connection       SSH连接
     * @param config           部署配置
     * @param useChineseMirror 是否使用国内镜像源
     * @param progressCallback 进度回调
     * @throws Exception 写入文件失败时抛出
     */
    private void generateDockerComposeFile(SshConnection connection,
                                           SillyTavernDeploymentConfig config,
                                           boolean useChineseMirror,
                                           Consumer<String> progressCallback) throws Exception {

        progressCallback.accept("生成docker-compose.yaml配置文件...");

        // 确定镜像地址
        String sillyTavernImage = useChineseMirror ?
                "ghcr.nju.edu.cn/sillytavern/sillytavern:" + config.getSelectedVersion() :
                "goolashe/sillytavern:" + config.getSelectedVersion();

        String watchtowerImage = useChineseMirror ?
                "ghcr.nju.edu.cn/containrrr/watchtower" :
                "containrrr/watchtower";

        progressCallback.accept("SillyTavern镜像将使用: " + sillyTavernImage);
        progressCallback.accept("Watchtower镜像将使用: " + watchtowerImage);

        // 生成docker-compose.yaml内容
        String dockerComposeContent = generateDockerComposeContent(sillyTavernImage, watchtowerImage, config);

        // 写入文件
        String writeCommand = String.format(
                "sudo tee %s > /dev/null <<'EOF'\n%s\nEOF",
                DOCKER_COMPOSE_FILE, dockerComposeContent);

        CommandResult writeResult = sshCommandService.executeInternal(connection.getJschSession(), writeCommand);

        if (writeResult.exitStatus() != 0) {
            throw new RuntimeException("写入docker-compose.yaml失败: " + writeResult.stderr());
        }
    }

    /**
     * 生成docker-compose.yaml文件内容
     *
     * @param sillyTavernImage SillyTavern镜像
     * @param watchtowerImage  Watchtower镜像
     * @param config           部署配置
     * @return docker-compose.yaml内容
     */
    private String generateDockerComposeContent(String sillyTavernImage, String watchtowerImage,
                                                SillyTavernDeploymentConfig config) {
        // 这里只生成最基础的配置，如需扩展可在此处修改
        return "services:\n" +
                "  sillytavern:\n" +
                "    image: " + sillyTavernImage + "\n" +
                "    container_name: " + CONTAINER_NAME + "\n" +
                "    networks:\n" +
                "      - " + DOCKER_NETWORK + "\n" +
                "    ports:\n" +
                "      - \"" + config.getPort() + ":8000\"\n" +
                "    volumes:\n" +
                "      - ./plugins:/home/node/app/plugins:rw\n" +
                "      - ./config:/home/node/app/config:rw\n" +
                "      - ./data:/home/node/app/data:rw\n" +
                "      - ./extensions:/home/node/app/public/scripts/extensions/third-party:rw\n" +
                "    restart: always\n" +
                "    labels:\n" +
                "      - \"com.centurylinklabs.watchtower.enable=true\"\n" +
                "  watchtower:\n" +
                "    image: " + watchtowerImage + "\n" +
                "    container_name: " + WATCHTOWER_NAME + "\n" +
                "    volumes:\n" +
                "      - /var/run/docker.sock:/var/run/docker.sock\n" +
                "    command: --interval 86400 --cleanup --label-enable\n" +
                "    restart: always\n" +
                "    networks:\n" +
                "      - " + DOCKER_NETWORK + "\n" +
                "networks:\n" +
                "  " + DOCKER_NETWORK + ":\n" +
                "    name: " + DOCKER_NETWORK + "\n";
    }

    /**
     * 拉取Docker镜像，支持实时进度监控
     *
     * @param connection       SSH连接
     * @param progressCallback 进度回调
     * @throws Exception 拉取失败时抛出
     */
    private void pullDockerImages(SshConnection connection,
                                  Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("开始拉取Docker镜像...");
        
        // 检测 compose 命令
        String composeCmd = detectDockerComposeCommand(connection);
        
        // 使用带进度显示的命令，但增加错误处理
        String pullCommand = String.format("cd %s && sudo %s pull 2>&1", DEPLOYMENT_PATH, composeCmd);
        
        try {
            // 启动详细进度监控
            CompletableFuture<Void> progressMonitor = startDetailedProgressMonitoring(connection, progressCallback);
            
            // 执行拉取命令
            CommandResult pullResult = sshCommandService.executeInternal(connection.getJschSession(), pullCommand);
            
            // 停止进度监控
            progressMonitor.cancel(true);
            
            if (pullResult.exitStatus() == 0) {
                progressCallback.accept("✅ 镜像拉取成功");
            } else {
                // 详细错误诊断
                String stderr = pullResult.stderr().trim();
                String stdout = pullResult.stdout().trim();
                String output = stdout.isEmpty() ? stderr : stdout;
                
                log.error("Docker拉取命令失败，退出码: {}", pullResult.exitStatus());
                log.error("输出: {}", output);
                
                String errorMsg = "❌ 镜像拉取失败";
                if (output.contains("502") || output.contains("Bad Gateway")) {
                    errorMsg += "：网络连接问题(502 Bad Gateway)";
                } else if (output.contains("permission denied")) {
                    errorMsg += "：权限不足，请检查sudo权限";
                } else if (output.contains("not found") || output.contains("command not found")) {
                    errorMsg += "：Docker Compose命令未找到";
                } else if (!output.isEmpty()) {
                    errorMsg += "：" + output.substring(0, Math.min(output.length(), 100));
                }
                
                progressCallback.accept(errorMsg);
                throw new RuntimeException(errorMsg + " (退出码: " + pullResult.exitStatus() + ")");
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            }
            String errorMsg = "❌ 镜像拉取失败：" + e.getMessage();
            progressCallback.accept(errorMsg);
            log.error("Docker镜像拉取异常: {}", e.getMessage(), e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * 启动详细的进度监控
     */
    private CompletableFuture<Void> startDetailedProgressMonitoring(SshConnection connection, 
                                                                  Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                int checkCount = 0;
                
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(2000); // 每2秒检查一次
                    checkCount++;
                    
                    try {
                        // 检查Docker pull进程
                        String processCommand = "ps aux | grep -E '(docker.*pull|docker-compose.*pull)' | grep -v grep";
                        CommandResult processResult = sshCommandService.executeInternal(connection.getJschSession(), processCommand);
                        
                        long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                        
                        if (processResult.exitStatus() == 0 && !processResult.stdout().trim().isEmpty()) {
                            // 拉取仍在进行中，尝试获取镜像信息
                            String imageCommand = "sudo docker images --format 'table {{.Repository}}:{{.Tag}}\\t{{.Size}}' | grep -E '(sillytavern|watchtower)' | tail -n +1";
                            CommandResult imageResult = sshCommandService.executeInternal(connection.getJschSession(), imageCommand);
                            
                            if (imageResult.exitStatus() == 0 && !imageResult.stdout().trim().isEmpty()) {
                                String[] images = imageResult.stdout().trim().split("\n");
                                if (images.length > 0) {
                                    // 已经开始下载镜像
                                    String lastImage = images[images.length - 1];
                                    progressCallback.accept(String.format("正在下载镜像: %s (已耗时: %s)", 
                                        lastImage.split("\t")[0], formatDurationHelper(elapsedSeconds)));
                                } else {
                                    progressCallback.accept(String.format("正在解析镜像信息... (已耗时: %s)", 
                                        formatDurationHelper(elapsedSeconds)));
                                }
                            } else {
                                progressCallback.accept(String.format("正在连接镜像仓库... (已耗时: %s)", 
                                    formatDurationHelper(elapsedSeconds)));
                            }
                        } else {
                            // 进程可能已完成或还未开始
                            if (elapsedSeconds < 5) {
                                progressCallback.accept("准备拉取镜像...");
                            } else {
                                // 检查是否已经有镜像拉取完成
                                String completedCommand = "sudo docker images --format 'table {{.Repository}}:{{.Tag}}\\t{{.Size}}' | grep -E '(sillytavern|watchtower)'";
                                CommandResult completedResult = sshCommandService.executeInternal(connection.getJschSession(), completedCommand);
                                
                                if (completedResult.exitStatus() == 0 && !completedResult.stdout().trim().isEmpty()) {
                                    String[] completedImages = completedResult.stdout().trim().split("\n");
                                    progressCallback.accept(String.format("已完成 %d 个镜像下载 (总耗时: %s)", 
                                        completedImages.length, formatDurationHelper(elapsedSeconds)));
                                } else {
                                    progressCallback.accept(String.format("正在处理镜像... (已耗时: %s)", 
                                        formatDurationHelper(elapsedSeconds)));
                                }
                            }
                        }
                        
                        // 防止无限循环，最多监控10分钟
                        if (checkCount > 300) {
                            progressCallback.accept("镜像拉取时间较长，请耐心等待...");
                            break;
                        }
                        
                    } catch (Exception e) {
                        log.debug("进度监控检查失败: {}", e.getMessage());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * 启动简单的进度监控
     */
    private CompletableFuture<Void> startSimpleProgressMonitoring(Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            try {
                int seconds = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(3000); // 每3秒更新一次
                    seconds += 3;
                    
                    if (seconds % 15 == 0) { // 每15秒显示一次进度
                        progressCallback.accept(String.format("正在拉取镜像... (已耗时 %s)", 
                            formatDurationHelper(seconds)));
                    }
                    
                    // 最多监控5分钟
                    if (seconds >= 300) {
                        progressCallback.accept("镜像拉取时间较长，请耐心等待...");
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * 执行命令并实时解析进度信息
     *
     * @param connection       SSH连接
     * @param command         要执行的命令
     * @param progressCallback 进度回调
     * @throws Exception 执行失败时抛出
     */
    private void executeCommandWithProgress(SshConnection connection, String command, Consumer<String> progressCallback) throws Exception {
        // 创建进度跟踪器
        DockerPullProgressTracker progressTracker = new DockerPullProgressTracker();

        // 使用线程来执行命令并处理输出
        CompletableFuture<Void> commandFuture = CompletableFuture.runAsync(() -> {
            try {
                // 创建临时脚本文件来执行命令并捕获输出
                String scriptContent = String.format(
                    "#!/bin/bash\n" +
                    "set -o pipefail\n" +
                    "%s | while IFS= read -r line; do\n" +
                    "  echo \"$line\" >> /tmp/docker_pull_progress_%d.log\n" +
                    "  echo \"$line\"\n" +
                    "done\n",
                    command, System.currentTimeMillis()
                );

                String logFile = "/tmp/docker_pull_progress_" + System.currentTimeMillis() + ".log";
                String scriptFile = "/tmp/docker_pull_script_" + System.currentTimeMillis() + ".sh";

                // 写入脚本文件
                String writeScriptCommand = String.format(
                    "cat > %s << 'EOF'\n%s\nEOF",
                    scriptFile, scriptContent
                );
                sshCommandService.executeInternal(connection.getJschSession(), writeScriptCommand);

                // 设置执行权限并执行
                sshCommandService.executeInternal(connection.getJschSession(), "chmod +x " + scriptFile);

                // 启动进度监控
                CompletableFuture<Void> progressMonitor = startProgressMonitoring(connection, logFile, progressTracker, progressCallback);

                // 执行脚本
                CommandResult result = sshCommandService.executeInternal(connection.getJschSession(), scriptFile);

                // 停止监控
                progressMonitor.cancel(true);

                // 清理临时文件
                sshCommandService.executeInternal(connection.getJschSession(), "rm -f " + scriptFile + " " + logFile);

                if (result.exitStatus() != 0) {
                    throw new RuntimeException("Command failed: " + result.stderr());
                }

            } catch (Exception e) {
                throw new RuntimeException("Failed to execute command with progress", e);
            }
        });

        // 等待命令完成
        commandFuture.get();
    }

    /**
     * 启动进度监控
     */
    private CompletableFuture<Void> startProgressMonitoring(SshConnection connection, String logFile,
                                                           DockerPullProgressTracker progressTracker,
                                                           Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            long lastPosition = 0;
            int noUpdateCount = 0;

            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(2000); // 每2秒检查一次

                    try {
                        // 读取新的日志内容
                        String readCommand = String.format("tail -c +%d %s 2>/dev/null || echo ''", lastPosition + 1, logFile);
                        CommandResult logResult = sshCommandService.executeInternal(connection.getJschSession(), readCommand);

                        if (logResult.exitStatus() == 0 && !logResult.stdout().trim().isEmpty()) {
                            String[] lines = logResult.stdout().split("\n");
                            for (String line : lines) {
                                if (!line.trim().isEmpty()) {
                                    progressTracker.parseLine(line);
                                }
                            }

                            // 更新位置
                            lastPosition += logResult.stdout().length();
                            noUpdateCount = 0;

                            // 生成进度消息
                            String progressMessage = progressTracker.getProgressMessage();
                            if (progressMessage != null) {
                                progressCallback.accept(progressMessage);
                            }
                        } else {
                            noUpdateCount++;

                            // 如果超过30秒没有更新，发送心跳消息
                            if (noUpdateCount > 15) {
                                long elapsedSeconds = progressTracker.getElapsedSeconds();
                                progressCallback.accept(String.format("正在拉取镜像... (已耗时 %s)", formatDurationHelper(elapsedSeconds)));
                                noUpdateCount = 0;
                            }
                        }

                    } catch (Exception e) {
                        log.debug("进度监控检查失败: {}", e.getMessage());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Docker拉取进度跟踪器
     */
    private static class DockerPullProgressTracker {
        private final long startTime = System.currentTimeMillis();
        private long totalBytes = 0;
        private long downloadedBytes = 0;
        private String currentImage = "";
        private String currentLayer = "";
        private final java.util.Map<String, LayerProgress> layerProgress = new java.util.HashMap<>();

        public void parseLine(String line) {
            // 解析镜像名称
            if (line.contains("Pulling from")) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Pulling from (.+)");
                java.util.regex.Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    currentImage = matcher.group(1);
                }
            }

            // 解析层下载进度
            // 格式: #1 [internal] load metadata for docker.io/goolashe/sillytavern:latest
            // 或: #2 [1/2] FROM docker.io/goolashe/sillytavern:latest@sha256:abc123
            // 或: #3 [2/2] COPY . .
            if (line.matches("^#\\d+ .*")) {
                parseLayerProgress(line);
            }

            // 解析下载进度 (Docker Buildkit格式)
            // 格式: #2 extracting sha256:abc123 0.1s (5.2MB/50.3MB)
            if (line.contains("extracting") || line.contains("downloading")) {
                parseDownloadProgress(line);
            }
        }

        private void parseLayerProgress(String line) {
            // 提取层信息
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("#(\\d+)\\s+(.+)");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String layerId = matcher.group(1);
                String layerInfo = matcher.group(2);
                currentLayer = layerId;

                // 更新层状态
                if (!layerProgress.containsKey(layerId)) {
                    layerProgress.put(layerId, new LayerProgress(layerId, layerInfo));
                }
            }
        }

        private void parseDownloadProgress(String line) {
            // 解析下载进度: (5.2MB/50.3MB) 或 (完整的字节数)
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\(([0-9.]+[KMGT]?B)/([0-9.]+[KMGT]?B)\\)");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String downloaded = matcher.group(1);
                String total = matcher.group(2);

                long downloadedBytesValue = parseBytes(downloaded);
                long totalBytesValue = parseBytes(total);

                if (totalBytesValue > totalBytes) {
                    totalBytes = totalBytesValue;
                }
                if (downloadedBytesValue > downloadedBytes) {
                    downloadedBytes = downloadedBytesValue;
                }
            }
        }

        private long parseBytes(String sizeStr) {
            if (sizeStr == null || sizeStr.isEmpty()) return 0;

            sizeStr = sizeStr.toUpperCase();
            double value = Double.parseDouble(sizeStr.replaceAll("[KMGT]B?", ""));

            if (sizeStr.contains("KB")) {
                return (long) (value * 1024);
            } else if (sizeStr.contains("MB")) {
                return (long) (value * 1024 * 1024);
            } else if (sizeStr.contains("GB")) {
                return (long) (value * 1024 * 1024 * 1024);
            } else if (sizeStr.contains("TB")) {
                return (long) (value * 1024 * 1024 * 1024 * 1024);
            } else {
                return (long) value;
            }
        }

        public String getProgressMessage() {
            if (totalBytes > 0 && downloadedBytes > 0) {
                double percentage = (double) downloadedBytes / totalBytes * 100;
                long elapsedSeconds = getElapsedSeconds();

                // 计算剩余时间
                String remainingTimeStr = "";
                if (elapsedSeconds > 0 && percentage > 5) { // 避免初期预估不准确
                    long totalEstimatedSeconds = (long) (elapsedSeconds / (percentage / 100.0));
                    long remainingSeconds = totalEstimatedSeconds - elapsedSeconds;
                    remainingTimeStr = " 剩余时间: " + formatDurationInTracker(remainingSeconds);
                }

                return String.format("正在下载镜像: %s/%s (%.1f%%) 已耗时: %s%s",
                    formatBytes(downloadedBytes),
                    formatBytes(totalBytes),
                    percentage,
                    formatDurationInTracker(elapsedSeconds),
                    remainingTimeStr
                );
            }

            // 如果没有具体进度，显示活动状态
            if (!layerProgress.isEmpty()) {
                int completedLayers = (int) layerProgress.values().stream().filter(LayerProgress::isCompleted).count();
                int totalLayers = layerProgress.size();
                return String.format("正在处理镜像层: %d/%d 已完成, 已耗时: %s",
                    completedLayers, totalLayers, formatDurationInTracker(getElapsedSeconds()));
            }

            return null;
        }

        /**
         * 在跟踪器内部格式化时长
         */
        private String formatDurationInTracker(long seconds) {
            if (seconds < 60) {
                return seconds + "秒";
            } else if (seconds < 3600) {
                return (seconds / 60) + "分" + (seconds % 60) + "秒";
            } else {
                return (seconds / 3600) + "小时" + ((seconds % 3600) / 60) + "分钟";
            }
        }

        public long getElapsedSeconds() {
            return (System.currentTimeMillis() - startTime) / 1000;
        }

        private String formatBytes(long bytes) {
            if (bytes >= 1024 * 1024 * 1024) {
                return String.format("%.1fGB", bytes / (1024.0 * 1024.0 * 1024.0));
            } else if (bytes >= 1024 * 1024) {
                return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
            } else if (bytes >= 1024) {
                return String.format("%.1fKB", bytes / 1024.0);
            } else {
                return bytes + "B";
            }
        }
    }

    /**
     * 层进度信息
     */
    private static class LayerProgress {
        private final String id;
        private final String info;
        private boolean completed = false;
        private long size = 0;
        private long downloaded = 0;

        public LayerProgress(String id, String info) {
            this.id = id;
            this.info = info;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }
    }

    /**
     * 格式化时长
     */
    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        } else if (seconds < 3600) {
            return (seconds / 60) + "分" + (seconds % 60) + "秒";
        } else {
            return (seconds / 3600) + "小时" + ((seconds % 3600) / 60) + "分钟";
        }
    }

    /**
     * 格式化时长 - 辅助方法
     */
    private String formatDurationHelper(long seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        } else if (seconds < 3600) {
            return (seconds / 60) + "分" + (seconds % 60) + "秒";
        } else {
            return (seconds / 3600) + "小时" + ((seconds % 3600) / 60) + "分钟";
        }
    }

    /**
     * 启动容器服务
     *
     * @param connection       SSH连接
     * @param progressCallback 进度回调
     * @throws Exception 启动失败时抛出
     */
    private void startContainerService(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("正在启动SillyTavern服务...");
        // 检测 compose 命令
        String composeCmd = detectDockerComposeCommand(connection);
        String startCommand = String.format("cd %s && sudo %s up -d", DEPLOYMENT_PATH, composeCmd);
        CommandResult startResult = sshCommandService.executeInternal(connection.getJschSession(), startCommand);
        if (startResult.exitStatus() != 0) {
            throw new RuntimeException("启动容器失败: " + startResult.stderr());
        }
        // 等待容器完全启动
        progressCallback.accept("等待容器完全启动...");
        Thread.sleep(CONTAINER_STARTUP_WAIT_MS);
    }

    /**
     * 验证部署结果
     *
     * @param connection       SSH连接
     * @param config           部署配置
     * @param progressCallback 进度回调
     * @return 部署结果
     * @throws Exception 验证失败时抛出
     */
    private SillyTavernDeploymentResult verifyDeployment(SshConnection connection,
                                                         SillyTavernDeploymentConfig config,
                                                         Consumer<String> progressCallback) throws Exception {

        progressCallback.accept("验证部署结果...");

        // 检查容器状态
        CommandResult statusResult = sshCommandService.executeInternal(connection.getJschSession(),
                String.format("sudo docker ps --filter name=%s --format \"table {{.Names}}\\t{{.Status}}\\t{{.Ports}}\"", CONTAINER_NAME));

        boolean containerRunning = statusResult.exitStatus() == 0 &&
                statusResult.stdout().contains(CONTAINER_NAME) &&
                statusResult.stdout().contains("Up");

        // 获取服务器公网IP
        String publicIp = getServerPublicIp(connection);

        String accessUrl = String.format("http://%s:%s",
                publicIp.isEmpty() ? "<服务器IP>" : publicIp, config.getPort());

        return SillyTavernDeploymentResult.builder()
                .success(containerRunning)
                .message(containerRunning ? "SillyTavern部署成功" : "容器启动失败")
                .containerName(CONTAINER_NAME)
                .deploymentPath(DEPLOYMENT_PATH)
                .accessUrl(accessUrl)
                .port(config.getPort())
                .version(config.getSelectedVersion())
                .containerStatus(containerRunning ? "运行中" : "未运行")
                .build();
    }

    /**
     * 获取服务器公网IP
     *
     * @param connection SSH连接
     * @return 公网IP字符串，获取失败返回空字符串
     */
    private String getServerPublicIp(SshConnection connection) {
        try {
            CommandResult ipResult = sshCommandService.executeInternal(connection.getJschSession(),
                    "curl -sS ipinfo.io | grep '\"ip\":' | cut -d'\"' -f4");

            if (ipResult.exitStatus() == 0 && !ipResult.stdout().trim().isEmpty()) {
                return ipResult.stdout().trim();
            }
        } catch (Exception e) {
            log.debug("获取公网IP失败: {}", e.getMessage());
        }
        return "";
    }

    /**
     * 写入部署信息到deployment-info.json文件
     * <p>
     * 该文件包含NAT环境下的访问信息，用于解决NAT环境访问问题
     * </p>
     *
     * @param connection       SSH连接
     * @param config           部署配置
     * @param result           部署结果
     * @param progressCallback 进度回调
     */
    private void writeDeploymentInfo(SshConnection connection,
                                     SillyTavernDeploymentConfig config,
                                     SillyTavernDeploymentResult result,
                                     Consumer<String> progressCallback) {
        try {
            progressCallback.accept("写入部署信息文件...");

            // 获取服务器地址信息
            String publicIp = getServerPublicIp(connection);
            String privateIp = connection.getSession().getHost();

            // 构建部署信息DTO
            DeploymentInfoDto deploymentInfo = DeploymentInfoDto.builder()
                    .deployment(DeploymentInfoDto.DeploymentInfo.builder()
                            .time(String.valueOf(System.currentTimeMillis()))
                            .version(result.getVersion())
                            .environment("production")
                            .build())
                    .ports(DeploymentInfoDto.PortInfo.builder()
                            .internal(8000) // SillyTavern内部端口
                            .external(Integer.parseInt(config.getPort())) // 配置的外部端口
                            .natExternal(Integer.parseInt(config.getPort())) // NAT外部端口（与外部端口相同）
                            .build())
                    .network(DeploymentInfoDto.NetworkInfo.builder()
                            .internalHost("sillytavern") // 容器内部主机名
                            .externalHost(privateIp) // SSH连接的IP（内网IP）
                            .natExternalHost(!publicIp.isEmpty() ? publicIp : privateIp) // 公网IP或内网IP
                            .build())
                    .authentication(DeploymentInfoDto.AuthenticationInfo.builder()
                            .username(config.getUsername())
                            .password(config.getPassword())
                            .build())
                    .build();

            // 将DTO序列化为JSON
            String deploymentInfoJson = objectMapper.writeValueAsString(deploymentInfo);

            // 直接写入到宿主机的挂载目录 - 会自动出现在容器内
            String deploymentInfoPath = DEPLOYMENT_PATH + "/config/deployment-info.json";
            String writeCommand = String.format(
                    "sudo tee %s > /dev/null <<'EOF'\n%s\nEOF",
                    deploymentInfoPath, deploymentInfoJson);

            CommandResult writeResult = sshCommandService.executeInternal(connection.getJschSession(), writeCommand);
            
            if (writeResult.exitStatus() != 0) {
                log.warn("写入部署信息文件失败: {}", writeResult.stderr());
                progressCallback.accept("警告：部署信息文件写入失败，但不影响正常使用");
            } else {
                log.info("成功写入部署信息文件到: {}", deploymentInfoPath);
                progressCallback.accept("部署信息文件写入成功");
            }

        } catch (Exception e) {
            log.warn("写入部署信息文件时发生异常: {}", e.getMessage(), e);
            progressCallback.accept("警告：部署信息写入失败，但不影响正常使用");
        }
    }

    /**
     * 获取可用的SillyTavern镜像版本列表
     *
     * @param connection       SSH连接
     * @param useChineseMirror 是否使用国内镜像源
     * @return SillyTavern版本信息的异步CompletableFuture
     */
    public CompletableFuture<SillyTavernVersionInfo> getAvailableVersions(SshConnection connection,
                                                                          boolean useChineseMirror) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 实际应调用Docker Hub API或镜像源API，这里简化处理
                return SillyTavernVersionInfo.builder()
                        .latestVersion("latest")
                        .availableVersions(new String[]{"latest", "staging", "release"})
                        .recommendedVersion("latest")
                        .description("latest: 最新稳定版\nstaging: 预发布版本\nrelease: 正式发布版")
                        .build();

            } catch (Exception e) {
                log.error("获取SillyTavern版本信息失败", e);
                return SillyTavernVersionInfo.builder()
                        .latestVersion("latest")
                        .availableVersions(new String[]{"latest"})
                        .recommendedVersion("latest")
                        .description("版本信息获取失败，使用默认版本")
                        .build();
            }
        });
    }

    /**
     * 检查端口是否可用
     *
     * @param connection SSH连接
     * @param port       要检查的端口
     * @return 端口是否可用的异步CompletableFuture
     */
    public CompletableFuture<Boolean> checkPortAvailability(SshConnection connection, String port) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 检查端口是否被占用，若无输出则可用
                String checkCmd = String.format("sudo netstat -tuln | awk '{print $4}' | grep -w ':%s$'", port);
                CommandResult checkResult = sshCommandService.executeInternal(connection.getJschSession(), checkCmd);

                // 如果命令返回结果，说明端口被占用
                return checkResult.exitStatus() != 0 || checkResult.stdout().trim().isEmpty();

            } catch (Exception e) {
                log.error("检查端口可用性失败", e);
                return false;
            }
        });
    }

    /**
     * SillyTavern部署配置数据类
     */
    @lombok.Data
    @lombok.Builder
    public static class SillyTavernDeploymentConfig {
        /** 镜像版本 */
        private String selectedVersion;
        /** 映射端口 */
        private String port;
        /** 是否允许外部访问 */
        private boolean enableExternalAccess;
        /** 登录用户名 */
        private String username;
        /** 登录密码 */
        private String password;
    }

    /**
     * SillyTavern部署结果数据类
     */
    @lombok.Data
    @lombok.Builder
    public static class SillyTavernDeploymentResult {
        /** 部署是否成功 */
        private boolean success;
        /** 部署结果描述 */
        private String message;
        @lombok.Builder.Default
        private String containerName = "";
        @lombok.Builder.Default
        private String deploymentPath = "";
        @lombok.Builder.Default
        private String accessUrl = "";
        @lombok.Builder.Default
        private String port = "";
        @lombok.Builder.Default
        private String version = "";
        @lombok.Builder.Default
        private String containerStatus = "";
    }

    /**
     * SillyTavern版本信息数据类
     */
    @lombok.Data
    @lombok.Builder
    public static class SillyTavernVersionInfo {
        @lombok.Builder.Default
        private String latestVersion = "";
        @lombok.Builder.Default
        private String[] availableVersions = new String[0];
        @lombok.Builder.Default
        private String recommendedVersion = "";
        @lombok.Builder.Default
        private String description = "";
    }

}
