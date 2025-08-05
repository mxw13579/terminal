package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * SillyTavern容器部署服务
 * 负责创建和部署SillyTavern容器，包括docker-compose配置和容器启动
 * 基于linux-silly-tavern-docker-deploy.sh脚本的SillyTavern部署功能
 * 
 * @author Claude
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SillyTavernDeploymentService {

    private final SshCommandService sshCommandService;

    /**
     * 部署SillyTavern容器
     * 
     * @param connection SSH连接
     * @param deploymentConfig 部署配置
     * @param useChineseMirror 是否使用国内镜像源
     * @param progressCallback 进度回调函数
     * @return 部署结果
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
                pullDockerImages(connection, deploymentConfig.getSelectedVersion(), useChineseMirror, progressCallback);
                
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
                    .containerName("sillytavern")
                    .deploymentPath("/data/docker/sillytavem")
                    .build();
            }
        });
    }

    /**
     * 创建部署目录
     */
    private void createDeploymentDirectory(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("创建SillyTavern部署目录...");
        
        CommandResult mkdirResult = sshCommandService.executeCommand(connection.getJschSession(), 
            "sudo mkdir -p /data/docker/sillytavem");
        
        if (mkdirResult.exitStatus() != 0) {
            throw new RuntimeException("创建部署目录失败: " + mkdirResult.stderr());
        }
        
        // 设置目录权限
        sshCommandService.executeCommand(connection.getJschSession(), 
            "sudo chmod 755 /data/docker/sillytavem");
    }

    /**
     * 生成docker-compose.yaml文件
     */
    private void generateDockerComposeFile(SshConnection connection, 
                                         SillyTavernDeploymentConfig config,
                                         boolean useChineseMirror,
                                         Consumer<String> progressCallback) throws Exception {
        
        progressCallback.accept("生成docker-compose.yaml配置文件...");
        
        // 确定镜像地址
        String sillyTavernImage = useChineseMirror ? 
            "ghcr.nju.edu.cn/sillytavern/sillytavern:" + config.getSelectedVersion() :
            "ghcr.io/sillytavern/sillytavern:" + config.getSelectedVersion();
            
        String watchtowerImage = useChineseMirror ? 
            "ghcr.nju.edu.cn/containrrr/watchtower" :
            "containrrr/watchtower";
        
        progressCallback.accept("SillyTavern镜像将使用: " + sillyTavernImage);
        progressCallback.accept("Watchtower镜像将使用: " + watchtowerImage);
        
        // 生成docker-compose.yaml内容
        String dockerComposeContent = generateDockerComposeContent(sillyTavernImage, watchtowerImage, config);
        
        // 写入文件
        String writeCommand = String.format(
            "sudo tee /data/docker/sillytavem/docker-compose.yaml > /dev/null <<'EOF'\n%s\nEOF", 
            dockerComposeContent);
        
        CommandResult writeResult = sshCommandService.executeCommand(connection.getJschSession(), writeCommand);
        
        if (writeResult.exitStatus() != 0) {
            throw new RuntimeException("写入docker-compose.yaml失败: " + writeResult.stderr());
        }
    }

    /**
     * 生成docker-compose.yaml文件内容
     */
    private String generateDockerComposeContent(String sillyTavernImage, String watchtowerImage, 
                                              SillyTavernDeploymentConfig config) {
        return "services:\n" +
               "  sillytavern:\n" +
               "    image: " + sillyTavernImage + "\n" +
               "    container_name: sillytavern\n" +
               "    networks:\n" +
               "      - DockerNet\n" +
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
               "    container_name: watchtower\n" +
               "    volumes:\n" +
               "      - /var/run/docker.sock:/var/run/docker.sock\n" +
               "    command: --interval 86400 --cleanup --label-enable\n" +
               "    restart: always\n" +
               "    networks:\n" +
               "      - DockerNet\n" +
               "networks:\n" +
               "  DockerNet:\n" +
               "    name: DockerNet\n";
    }

    /**
     * 拉取Docker镜像
     */
    private void pullDockerImages(SshConnection connection, String version, boolean useChineseMirror, 
                                Consumer<String> progressCallback) throws Exception {
        
        progressCallback.accept("正在拉取所需镜像...");
        
        // 切换到部署目录
        String pullCommand = "cd /data/docker/sillytavem && sudo docker-compose pull";
        
        // 执行镜像拉取，设置较长的超时时间
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
     */
    private void startContainerService(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("正在启动SillyTavern服务...");
        
        String startCommand = "cd /data/docker/sillytavem && sudo docker-compose up -d";
        CommandResult startResult = sshCommandService.executeCommand(connection.getJschSession(), startCommand);
        
        if (startResult.exitStatus() != 0) {
            throw new RuntimeException("启动容器失败: " + startResult.stderr());
        }
        
        // 等待容器完全启动
        progressCallback.accept("等待容器完全启动...");
        Thread.sleep(5000);
    }

    /**
     * 验证部署结果
     */
    private SillyTavernDeploymentResult verifyDeployment(SshConnection connection, 
                                                       SillyTavernDeploymentConfig config,
                                                       Consumer<String> progressCallback) throws Exception {
        
        progressCallback.accept("验证部署结果...");
        
        // 检查容器状态
        CommandResult statusResult = sshCommandService.executeCommand(connection.getJschSession(), 
            "sudo docker ps --filter name=sillytavern --format \"table {{.Names}}\\t{{.Status}}\\t{{.Ports}}\"");
        
        boolean containerRunning = statusResult.exitStatus() == 0 && 
                                 statusResult.stdout().contains("sillytavern") &&
                                 statusResult.stdout().contains("Up");
        
        // 获取服务器公网IP
        String publicIp = getServerPublicIp(connection);
        
        String accessUrl = String.format("http://%s:%s", 
            publicIp.isEmpty() ? "<服务器IP>" : publicIp, config.getPort());
        
        return SillyTavernDeploymentResult.builder()
            .success(containerRunning)
            .message(containerRunning ? "SillyTavern部署成功" : "容器启动失败")
            .containerName("sillytavern")
            .deploymentPath("/data/docker/sillytavem")
            .accessUrl(accessUrl)
            .port(config.getPort())
            .version(config.getSelectedVersion())
            .containerStatus(containerRunning ? "运行中" : "未运行")
            .build();
    }

    /**
     * 获取服务器公网IP
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
     * @param connection SSH连接
     * @param useChineseMirror 是否使用国内镜像源
     * @return 可用版本列表
     */
    public CompletableFuture<SillyTavernVersionInfo> getAvailableVersions(SshConnection connection, 
                                                                         boolean useChineseMirror) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 这里简化处理，实际应该调用Docker Hub API或镜像源API
                // 返回最常用的几个版本
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
     * @param port 要检查的端口
     * @return 端口是否可用
     */
    public CompletableFuture<Boolean> checkPortAvailability(SshConnection connection, String port) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                CommandResult checkResult = sshCommandService.executeCommand(connection.getJschSession(), 
                    String.format("sudo netstat -tuln | grep ':%s '", port));
                
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
        private String selectedVersion;
        private String port;
        private boolean enableExternalAccess;
        private String username;
        private String password;
    }

    /**
     * SillyTavern部署结果数据类
     */
    @lombok.Data
    @lombok.Builder
    public static class SillyTavernDeploymentResult {
        private boolean success;
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