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
 * <p>服务验证服务</p>
 * <p>负责验证SillyTavern部署后的服务状态和可访问性，基于linux-silly-tavern-docker-deploy.sh脚本的服务验证功能。</p>
 *
 * @author Claude
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceValidationService {

    private final SshCommandService sshCommandService;

    /**
     * 验证SillyTavern服务状态
     *
     * @param connection        SSH连接信息
     * @param expectedPort      预期服务端口
     * @param progressCallback  进度回调函数
     * @return 服务验证结果的CompletableFuture
     */
    public CompletableFuture<ServiceValidationResult> validateSillyTavernService(SshConnection connection,
                                                                                 String expectedPort,
                                                                                 Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                progressCallback.accept("开始验证SillyTavern服务状态...");

                ServiceValidationResult.ServiceValidationResultBuilder resultBuilder =
                        ServiceValidationResult.builder();

                // 1. 验证Docker容器状态
                boolean containerRunning = validateContainerStatus(connection, progressCallback);
                resultBuilder.containerRunning(containerRunning);

                // 2. 验证端口监听状态
                boolean portListening = validatePortListening(connection, expectedPort, progressCallback);
                resultBuilder.portListening(portListening);

                // 3. 验证服务HTTP响应
                boolean httpResponsive = validateHttpResponse(connection, expectedPort, progressCallback);
                resultBuilder.httpResponsive(httpResponsive);

                // 4. 验证配置文件
                boolean configValid = validateConfigFile(connection, progressCallback);
                resultBuilder.configValid(configValid);

                // 5. 获取服务访问信息
                ServiceAccessInfo accessInfo = getServiceAccessInfo(connection, expectedPort, progressCallback);
                resultBuilder.accessInfo(accessInfo);

                // 6. 计算总体验证结果
                boolean overallSuccess = containerRunning && portListening && configValid;
                String message = generateValidationMessage(containerRunning, portListening, httpResponsive, configValid);

                progressCallback.accept(overallSuccess ? "✅ 服务验证完成" : "⚠️ 服务验证发现问题");

                return resultBuilder
                        .success(overallSuccess)
                        .message(message)
                        .build();

            } catch (Exception e) {
                log.error("服务验证过程中发生异常", e);
                progressCallback.accept("服务验证失败: " + e.getMessage());
                return ServiceValidationResult.builder()
                        .success(false)
                        .message("验证过程中发生异常: " + e.getMessage())
                        .containerRunning(false)
                        .portListening(false)
                        .httpResponsive(false)
                        .configValid(false)
                        .build();
            }
        });
    }

    /**
     * 检查SillyTavern Docker容器是否正在运行
     *
     * @param connection        SSH连接信息
     * @param progressCallback  进度回调
     * @return 容器是否运行
     * @throws Exception SSH命令执行异常
     */
    private boolean validateContainerStatus(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("检查Docker容器状态...");

        CommandResult statusResult = sshCommandService.executeCommand(connection.getJschSession(),
                "sudo docker ps --filter name=sillytavern --format \"{{.Status}}\"");

        if (statusResult.exitStatus() == 0 && statusResult.stdout().contains("Up")) {
            progressCallback.accept("✅ SillyTavern容器正在运行");
            return true;
        } else {
            progressCallback.accept("❌ SillyTavern容器未运行或状态异常");

            // 获取容器详细状态
            CommandResult detailResult = sshCommandService.executeCommand(connection.getJschSession(),
                    "sudo docker ps -a --filter name=sillytavern --format \"table {{.Names}}\\t{{.Status}}\\t{{.Ports}}\"");

            if (detailResult.exitStatus() == 0) {
                progressCallback.accept("容器状态详情: " + detailResult.stdout().trim());
            }

            return false;
        }
    }

    /**
     * 检查端口监听状态
     *
     * @param connection        SSH连接信息
     * @param expectedPort      预期端口
     * @param progressCallback  进度回调
     * @return 端口监听是否正常
     * @throws Exception SSH命令执行异常
     */
    private boolean validatePortListening(SshConnection connection, String expectedPort, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("检查端口监听状态...");

        // 检查容器端口映射
        CommandResult portMappingResult = sshCommandService.executeCommand(connection.getJschSession(),
                String.format("sudo docker port sillytavern 8000 2>/dev/null | grep ':%s'", expectedPort));

        if (portMappingResult.exitStatus() == 0) {
            progressCallback.accept("✅ 端口映射正常: " + portMappingResult.stdout().trim());

            // 进一步检查系统端口监听
            CommandResult systemPortResult = sshCommandService.executeCommand(connection.getJschSession(),
                    String.format("sudo netstat -tuln | grep ':%s '", expectedPort));

            if (systemPortResult.exitStatus() == 0) {
                progressCallback.accept("✅ 系统端口监听正常");
                return true;
            } else {
                progressCallback.accept("⚠️ 容器端口映射正常，但系统端口可能未监听");
                return true; // 容器映射正常就认为成功
            }
        } else {
            progressCallback.accept("❌ 端口映射异常，预期端口: " + expectedPort);
            return false;
        }
    }

    /**
     * 检查HTTP服务响应
     *
     * @param connection        SSH连接信息
     * @param expectedPort      预期端口
     * @param progressCallback  进度回调
     * @return HTTP服务是否响应
     * @throws Exception SSH命令执行异常
     */
    private boolean validateHttpResponse(SshConnection connection, String expectedPort, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("测试HTTP服务响应...");

        // 测试本地访问
        CommandResult httpResult = sshCommandService.executeCommand(connection.getJschSession(),
                String.format("curl -s -o /dev/null -w \"%%{http_code}\" --connect-timeout 10 http://localhost:%s/ 2>/dev/null", expectedPort));

        if (httpResult.exitStatus() == 0) {
            String httpCode = httpResult.stdout().trim();
            if ("200".equals(httpCode) || "401".equals(httpCode)) {
                progressCallback.accept("✅ HTTP服务响应正常 (状态码: " + httpCode + ")");
                return true;
            } else {
                progressCallback.accept("⚠️ HTTP服务响应异常 (状态码: " + httpCode + ")");
                return false;
            }
        } else {
            progressCallback.accept("❌ HTTP服务无响应或连接超时");
            return false;
        }
    }

    /**
     * 检查配置文件存在性及格式
     *
     * @param connection        SSH连接信息
     * @param progressCallback  进度回调
     * @return 配置文件是否有效
     * @throws Exception SSH命令执行异常
     */
    private boolean validateConfigFile(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("验证配置文件...");

        // 检查配置文件是否存在
        CommandResult configExistsResult = sshCommandService.executeCommand(connection.getJschSession(),
                "sudo test -f /data/docker/sillytavern/config/config.yaml");

        if (configExistsResult.exitStatus() == 0) {
            progressCallback.accept("✅ 配置文件存在");

            // 验证配置文件格式
            CommandResult yamlValidResult = sshCommandService.executeCommand(connection.getJschSession(),
                    "sudo python3 -c \"import yaml; yaml.safe_load(open('/data/docker/sillytavern/config/config.yaml'))\" 2>/dev/null");

            if (yamlValidResult.exitStatus() == 0) {
                progressCallback.accept("✅ 配置文件格式正确");
                return true;
            } else {
                progressCallback.accept("⚠️ 配置文件格式可能有问题");
                return false;
            }
        } else {
            progressCallback.accept("⚠️ 配置文件不存在，将使用默认配置");
            return true; // 没有配置文件也可以运行，只是使用默认配置
        }
    }

    /**
     * 获取服务访问信息
     *
     * @param connection        SSH连接信息
     * @param expectedPort      预期端口
     * @param progressCallback  进度回调
     * @return 服务访问信息
     * @throws Exception SSH命令执行异常
     */
    private ServiceAccessInfo getServiceAccessInfo(SshConnection connection, String expectedPort, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("获取服务访问信息...");

        // 获取公网IP
        String publicIp = getPublicIp(connection);

        // 获取内网IP
        String privateIp = getPrivateIp(connection);

        // 检查是否配置了用户认证
        boolean hasAuth = checkBasicAuthConfig(connection);

        return ServiceAccessInfo.builder()
                .publicIp(publicIp)
                .privateIp(privateIp)
                .port(expectedPort)
                .publicUrl(publicIp.isEmpty() ? "" : String.format("http://%s:%s", publicIp, expectedPort))
                .privateUrl(privateIp.isEmpty() ? "" : String.format("http://%s:%s", privateIp, expectedPort))
                .hasAuthentication(hasAuth)
                .build();
    }

    /**
     * 获取公网IP
     *
     * @param connection SSH连接信息
     * @return 公网IP字符串，获取失败返回空字符串
     */
    private String getPublicIp(SshConnection connection) {
        try {
            CommandResult ipResult = sshCommandService.executeCommand(connection.getJschSession(),
                    "curl -sS --connect-timeout 10 ipinfo.io | grep '\"ip\":' | cut -d'\"' -f4");

            if (ipResult.exitStatus() == 0 && !ipResult.stdout().trim().isEmpty()) {
                return ipResult.stdout().trim();
            }
        } catch (Exception e) {
            log.debug("获取公网IP失败: {}", e.getMessage());
        }
        return "";
    }

    /**
     * 获取内网IP
     *
     * @param connection SSH连接信息
     * @return 内网IP字符串，获取失败返回空字符串
     */
    private String getPrivateIp(SshConnection connection) {
        try {
            CommandResult ipResult = sshCommandService.executeCommand(connection.getJschSession(),
                    "hostname -I | awk '{print $1}'");

            if (ipResult.exitStatus() == 0 && !ipResult.stdout().trim().isEmpty()) {
                return ipResult.stdout().trim();
            }
        } catch (Exception e) {
            log.debug("获取内网IP失败: {}", e.getMessage());
        }
        return "";
    }

    /**
     * 检查配置文件中是否启用了基础认证
     *
     * @param connection SSH连接信息
     * @return 是否启用基础认证
     */
    private boolean checkBasicAuthConfig(SshConnection connection) {
        try {
            CommandResult authResult = sshCommandService.executeCommand(connection.getJschSession(),
                    "sudo grep -q 'basicAuthMode: true' /data/docker/sillytavern/config/config.yaml 2>/dev/null");

            return authResult.exitStatus() == 0;
        } catch (Exception e) {
            log.debug("检查认证配置失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 生成服务验证结果消息
     *
     * @param containerRunning 容器是否运行
     * @param portListening    端口监听是否正常
     * @param httpResponsive   HTTP服务是否响应
     * @param configValid      配置文件是否有效
     * @return 验证结果消息
     */
    private String generateValidationMessage(boolean containerRunning, boolean portListening,
                                             boolean httpResponsive, boolean configValid) {
        StringBuilder sb = new StringBuilder();

        if (containerRunning && portListening && httpResponsive && configValid) {
            sb.append("SillyTavern服务运行正常，所有检查项都通过");
        } else {
            sb.append("SillyTavern服务验证发现以下问题：");
            if (!containerRunning) sb.append("\n- 容器未正常运行");
            if (!portListening) sb.append("\n- 端口监听异常");
            if (!httpResponsive) sb.append("\n- HTTP服务无响应");
            if (!configValid) sb.append("\n- 配置文件有问题");
        }

        return sb.toString();
    }

    /**
     * 快速健康检查，仅验证容器是否运行
     *
     * @param connection SSH连接信息
     * @return 服务是否健康
     */
    public CompletableFuture<Boolean> quickHealthCheck(SshConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                CommandResult statusResult = sshCommandService.executeCommand(connection.getJschSession(),
                        "sudo docker ps --filter name=sillytavern --format \"{{.Status}}\"");

                return statusResult.exitStatus() == 0 && statusResult.stdout().contains("Up");

            } catch (Exception e) {
                log.error("快速健康检查失败", e);
                return false;
            }
        });
    }

    /**
     * 服务验证结果数据类
     */
    @lombok.Data
    @lombok.Builder
    public static class ServiceValidationResult {
        /** 总体验证是否通过 */
        private boolean success;
        /** 验证结果消息 */
        private String message;
        /** 容器是否运行 */
        private boolean containerRunning;
        /** 端口监听是否正常 */
        private boolean portListening;
        /** HTTP服务是否响应 */
        private boolean httpResponsive;
        /** 配置文件是否有效 */
        private boolean configValid;
        /** 服务访问信息 */
        private ServiceAccessInfo accessInfo;

        /**
         * 获取验证是否成功
         * @return 是否成功
         */
        public boolean isSuccess() {
            return success;
        }

        /**
         * 获取验证结果消息
         * @return 结果消息
         */
        public String getMessage() {
            return message;
        }

        /**
         * 获取容器是否运行
         * @return 是否运行
         */
        public boolean isContainerRunning() {
            return containerRunning;
        }

        /**
         * 获取端口监听是否正常
         * @return 是否监听
         */
        public boolean isPortListening() {
            return portListening;
        }

        /**
         * 获取HTTP服务是否响应
         * @return 是否响应
         */
        public boolean isHttpResponsive() {
            return httpResponsive;
        }
    }

    /**
     * 便捷方法：使用默认端口验证SillyTavern服务
     *
     * @param connection SSH连接信息
     * @return 服务验证结果的CompletableFuture
     */
    public CompletableFuture<ServiceValidationResult> validateDeployment(SshConnection connection) {
        return validateSillyTavernService(connection, "8000", msg -> log.info("服务验证: {}", msg));
    }

    /**
     * 服务访问信息数据类
     */
    @lombok.Data
    @lombok.Builder
    public static class ServiceAccessInfo {
        /** 公网IP */
        private String publicIp;
        /** 内网IP */
        private String privateIp;
        /** 服务端口 */
        private String port;
        /** 公网访问URL */
        private String publicUrl;
        /** 内网访问URL */
        private String privateUrl;
        /** 是否启用认证 */
        private boolean hasAuthentication;
    }
}
