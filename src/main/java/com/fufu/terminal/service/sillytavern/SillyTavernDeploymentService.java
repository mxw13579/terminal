package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
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
            CommandResult result = sshCommandService.executeCommand(connection.getJschSession(), "docker compose version");
            if (result.exitStatus() == 0) {
                cachedComposeCommand = "docker compose";
                return cachedComposeCommand;
            }
            // 再检测 docker-compose
            result = sshCommandService.executeCommand(connection.getJschSession(), "docker-compose version");
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

        CommandResult mkdirResult = sshCommandService.executeCommand(connection.getJschSession(),
                "sudo mkdir -p " + DEPLOYMENT_PATH);

        if (mkdirResult.exitStatus() != 0) {
            throw new RuntimeException("创建部署目录失败: " + mkdirResult.stderr());
        }

        // 设置目录权限
        sshCommandService.executeCommand(connection.getJschSession(),
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
                "goolashe/sillytavern:" + config.getSelectedVersion() :
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

        CommandResult writeResult = sshCommandService.executeCommand(connection.getJschSession(), writeCommand);

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
     * 拉取Docker镜像
     *
     * @param connection       SSH连接
     * @param progressCallback 进度回调
     * @throws Exception 拉取失败时抛出
     */
    private void pullDockerImages(SshConnection connection,
                                  Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("正在拉取所需镜像...");
        // 检测 compose 命令
        String composeCmd = detectDockerComposeCommand(connection);
        // 切换到部署目录并拉取镜像
        String pullCommand = String.format("cd %s && sudo %s pull", DEPLOYMENT_PATH, composeCmd);
        CommandResult pullResult = sshCommandService.executeCommand(connection.getJschSession(), pullCommand);
        if (pullResult.exitStatus() == 0) {
            progressCallback.accept("✅ 镜像拉取成功");
        } else {
            String errorMsg = "❌ 镜像拉取失败，请检查网络连接或镜像地址是否正确";
            progressCallback.accept(errorMsg);
            log.error("Docker镜像拉取失败: {}", pullResult.stderr());
            throw new RuntimeException(errorMsg + ": " + pullResult.stderr());
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
        CommandResult startResult = sshCommandService.executeCommand(connection.getJschSession(), startCommand);
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
        CommandResult statusResult = sshCommandService.executeCommand(connection.getJschSession(),
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
            CommandResult ipResult = sshCommandService.executeCommand(connection.getJschSession(),
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
                CommandResult checkResult = sshCommandService.executeCommand(connection.getJschSession(), checkCmd);

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
