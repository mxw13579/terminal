package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 外网访问配置服务
 * 负责配置SillyTavern的外网访问和用户认证
 * 基于linux-silly-tavern-docker-deploy.sh脚本的外网访问配置功能
 *
 * @author Claude
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalAccessService {

    private final SshCommandService sshCommandService;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String RANDOM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * 配置SillyTavern外网访问
     *
     * @param connection SSH连接
     * @param accessConfig 访问配置
     * @param progressCallback 进度回调函数
     * @return 配置结果
     */
    public CompletableFuture<ExternalAccessConfigResult> configureExternalAccess(SshConnection connection,
                                                                                ExternalAccessConfig accessConfig,
                                                                                Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!accessConfig.isEnableExternalAccess()) {
                    progressCallback.accept("跳过外网访问配置");
                    return ExternalAccessConfigResult.builder()
                        .success(true)
                        .message("未开启外网访问，使用默认配置")
                        .username("")
                        .password("")
                        .configPath("")
                        .build();
                }

                progressCallback.accept("配置SillyTavern外网访问...");

                // 1. 创建配置目录
                createConfigDirectory(connection, progressCallback);

                // 2. 生成或验证用户凭据
                ExternalAccessCredentials credentials = processCredentials(accessConfig, progressCallback);

                // 3. 生成config.yaml配置文件
                generateConfigFile(connection, credentials, accessConfig, progressCallback);

                // 4. 验证配置文件格式
                boolean configValid = validateConfigFile(connection, progressCallback);

                return ExternalAccessConfigResult.builder()
                    .success(configValid)
                    .message(configValid ? "外网访问配置成功" : "配置文件生成失败")
                    .username(credentials.getUsername())
                    .password(credentials.getPassword())
                    .configPath("/data/docker/sillytavem/config/config.yaml")
                    .port(accessConfig.getPort())
                    .build();

            } catch (Exception e) {
                log.error("配置外网访问失败", e);
                progressCallback.accept("配置外网访问失败: " + e.getMessage());
                return ExternalAccessConfigResult.builder()
                    .success(false)
                    .message("配置失败: " + e.getMessage())
                    .username("")
                    .password("")
                    .configPath("")
                    .build();
            }
        });
    }

    /**
     * 创建配置目录
     */
    private void createConfigDirectory(SshConnection connection, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("创建配置目录...");

        CommandResult mkdirResult = sshCommandService.executeCommand(connection.getJschSession(),
            "sudo mkdir -p /data/docker/sillytavem/config");

        if (mkdirResult.exitStatus() != 0) {
            throw new RuntimeException("创建配置目录失败: " + mkdirResult.stderr());
        }
    }

    /**
     * 处理用户凭据
     */
    private ExternalAccessCredentials processCredentials(ExternalAccessConfig accessConfig,
                                                       Consumer<String> progressCallback) {

        String username, password;

        if (accessConfig.isUseRandomCredentials()) {
            progressCallback.accept("生成随机用户名和密码...");
            username = generateRandomString(16);
            password = generateRandomString(16);
            progressCallback.accept("已生成随机用户名: " + username);
            progressCallback.accept("已生成随机密码: " + password);
        } else {
            username = accessConfig.getUsername();
            password = accessConfig.getPassword();
            progressCallback.accept("使用手动输入的用户名: " + username);

            // 验证用户名密码格式
            if (!isValidCredentials(username, password)) {
                throw new RuntimeException("用户名或密码格式不正确（不能为纯数字）");
            }
        }

        return ExternalAccessCredentials.builder()
            .username(username)
            .password(password)
            .build();
    }

    /**
     * 验证用户名密码格式
     */
    private boolean isValidCredentials(String username, String password) {
        // 检查不能为空
        if (username == null || username.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            return false;
        }

        // 检查不能为纯数字
        return !username.matches("^\\d+$") && !password.matches("^\\d+$");
    }

    /**
     * 生成随机字符串
     */
    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM_CHARS.charAt(RANDOM.nextInt(RANDOM_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 生成config.yaml配置文件
     */
    private void generateConfigFile(SshConnection connection,
                                  ExternalAccessCredentials credentials,
                                  ExternalAccessConfig accessConfig,
                                  Consumer<String> progressCallback) throws Exception {

        progressCallback.accept("生成SillyTavern配置文件...");

        String configContent = generateConfigContent(credentials, accessConfig);

        String writeCommand = String.format(
            "sudo tee /data/docker/sillytavem/config/config.yaml > /dev/null <<'EOF'\n%s\nEOF",
            configContent);

        CommandResult writeResult = sshCommandService.executeCommand(connection.getJschSession(), writeCommand);

        if (writeResult.exitStatus() != 0) {
            throw new RuntimeException("写入配置文件失败: " + writeResult.stderr());
        }

        // 设置配置文件权限
        sshCommandService.executeCommand(connection.getJschSession(),
            "sudo chmod 644 /data/docker/sillytavem/config/config.yaml");
    }

    /**
     * 生成config.yaml文件内容
     */
    private String generateConfigContent(ExternalAccessCredentials credentials, ExternalAccessConfig accessConfig) {
        String cookieSecret = generateRandomString(64);

        return "dataRoot: ./data\n" +
               "cardsCacheCapacity: 100\n" +
               "listen: true\n" +
               "protocol:\n" +
               "  ipv4: true\n" +
               "  ipv6: false\n" +
               "dnsPreferIPv6: false\n" +
               "autorunHostname: auto\n" +
               "port: 8000\n" +
               "autorunPortOverride: -1\n" +
               "whitelistMode: false\n" +
               "enableForwardedWhitelist: true\n" +
               "whitelist:\n" +
               "  - ::1\n" +
               "  - 127.0.0.1\n" +
               "  - 0.0.0.0\n" +
               "basicAuthMode: true\n" +
               "basicAuthUser:\n" +
               "  username: " + credentials.getUsername() + "\n" +
               "  password: " + credentials.getPassword() + "\n" +
               "enableCorsProxy: false\n" +
               "requestProxy:\n" +
               "  enabled: false\n" +
               "  url: socks5://username:password@example.com:1080\n" +
               "  bypass:\n" +
               "    - localhost\n" +
               "    - 127.0.0.1\n" +
               "enableUserAccounts: false\n" +
               "enableDiscreetLogin: false\n" +
               "autheliaAuth: false\n" +
               "perUserBasicAuth: false\n" +
               "sessionTimeout: 86400\n" +
               "cookieSecret: " + cookieSecret + "\n" +
               "disableCsrfProtection: false\n" +
               "securityOverride: false\n" +
               "autorun: true\n" +
               "avoidLocalhost: false\n" +
               "backups:\n" +
               "  common:\n" +
               "    numberOfBackups: 50\n" +
               "  chat:\n" +
               "    enabled: true\n" +
               "    maxTotalBackups: -1\n" +
               "    throttleInterval: 10000\n" +
               "thumbnails:\n" +
               "  enabled: true\n" +
               "  format: jpg\n" +
               "  quality: 95\n" +
               "  dimensions:\n" +
               "    bg:\n" +
               "      - 160\n" +
               "      - 90\n" +
               "    avatar:\n" +
               "      - 96\n" +
               "      - 144\n" +
               "allowKeysExposure: false\n" +
               "skipContentCheck: false\n" +
               "whitelistImportDomains:\n" +
               "  - localhost\n" +
               "  - cdn.discordapp.com\n" +
               "  - files.catbox.moe\n" +
               "  - raw.githubusercontent.com\n" +
               "requestOverrides: []\n" +
               "enableExtensions: true\n" +
               "enableExtensionsAutoUpdate: true\n" +
               "enableDownloadableTokenizers: true\n" +
               "extras:\n" +
               "  disableAutoDownload: false\n" +
               "  classificationModel: Cohee/distilbert-base-uncased-go-emotions-onnx\n" +
               "  captioningModel: Xenova/vit-gpt2-image-captioning\n" +
               "  embeddingModel: Cohee/jina-embeddings-v2-base-en\n" +
               "  speechToTextModel: Xenova/whisper-small\n" +
               "  textToSpeechModel: Xenova/speecht5_tts\n" +
               "promptPlaceholder: \"[Start a new chat]\"\n" +
               "openai:\n" +
               "  randomizeUserId: false\n" +
               "  captionSystemPrompt: \"\"\n" +
               "deepl:\n" +
               "  formality: default\n" +
               "mistral:\n" +
               "  enablePrefix: false\n" +
               "ollama:\n" +
               "  keepAlive: -1\n" +
               "claude:\n" +
               "  enableSystemPromptCache: false\n" +
               "  cachingAtDepth: -1\n" +
               "enableServerPlugins: false\n";
    }

    /**
     * 验证配置文件格式
     */
    private boolean validateConfigFile(SshConnection connection, Consumer<String> progressCallback) {
        try {
            progressCallback.accept("验证配置文件格式...");

            // 检查配置文件是否存在
            CommandResult existsResult = sshCommandService.executeCommand(connection.getJschSession(),
                "sudo test -f /data/docker/sillytavem/config/config.yaml");

            if (existsResult.exitStatus() != 0) {
                progressCallback.accept("配置文件不存在");
                return false;
            }

            // 验证YAML格式（使用python验证）
            CommandResult validateResult = sshCommandService.executeCommand(connection.getJschSession(),
                "sudo python3 -c \"import yaml; yaml.safe_load(open('/data/docker/sillytavem/config/config.yaml'))\"");

            if (validateResult.exitStatus() == 0) {
                progressCallback.accept("配置文件格式验证通过");
                return true;
            } else {
                progressCallback.accept("配置文件格式验证失败");
                return false;
            }

        } catch (Exception e) {
            log.error("验证配置文件失败", e);
            progressCallback.accept("验证配置文件时发生错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 生成随机凭据
     *
     * @return 随机生成的用户名和密码
     */
    public ExternalAccessCredentials generateRandomCredentials() {
        return ExternalAccessCredentials.builder()
            .username(generateRandomString(16))
            .password(generateRandomString(16))
            .build();
    }

    /**
     * 验证外网访问配置
     *
     * @param connection SSH连接
     * @param port 端口号
     * @return 配置是否有效
     */
    public CompletableFuture<Boolean> validateExternalAccessConfig(SshConnection connection, String port) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 检查端口是否被正确映射
                CommandResult portResult = sshCommandService.executeCommand(connection.getJschSession(),
                    String.format("sudo docker port sillytavern 8000 | grep '%s'", port));

                // 检查配置文件是否存在且有效
                CommandResult configResult = sshCommandService.executeCommand(connection.getJschSession(),
                    "sudo test -f /data/docker/sillytavem/config/config.yaml");

                return portResult.exitStatus() == 0 && configResult.exitStatus() == 0;

            } catch (Exception e) {
                log.error("验证外网访问配置失败", e);
                return false;
            }
        });
    }

    /**
     * 外网访问配置数据类
     */
    @lombok.Data
    @lombok.Builder
    public static class ExternalAccessConfig {
        private boolean enableExternalAccess;
        private boolean useRandomCredentials;
        private String username;
        private String password;
        private String port;
    }

    /**
     * 外网访问凭据数据类
     */
    @lombok.Data
    @lombok.Builder
    public static class ExternalAccessCredentials {
        private String username;
        private String password;
    }

    /**
     * 外网访问配置结果数据类
     */
    @lombok.Data
    @lombok.Builder
    public static class ExternalAccessConfigResult {
        private boolean success;
        private String message;
        @lombok.Builder.Default
        private String username = "";
        @lombok.Builder.Default
        private String password = "";
        @lombok.Builder.Default
        private String configPath = "";
        @lombok.Builder.Default
        private String port = "";
    }

}
