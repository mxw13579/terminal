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
 * <p>地理位置检测服务</p>
 * <p>负责检测服务器地理位置，决定是否使用国内镜像源，基于linux-silly-tavern-docker-deploy.sh脚本的地理位置检测功能。</p>
 * <p>本服务通过SSH远程执行命令，自动判断服务器是否位于中国，并给出镜像源使用建议。</p>
 *
 * @author Claude
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeolocationDetectionService {

    private final SshCommandService sshCommandService;

    /**
     * 检测服务器地理位置，并给出镜像源使用建议。
     * <p>通过ipinfo.io API检测服务器是否位于中国，自动推荐是否使用国内镜像源。</p>
     *
     * @param connection        SSH连接信息，不能为空
     * @param progressCallback  进度回调函数，用于实时反馈检测进度，不能为空
     * @return CompletableFuture，异步返回地理位置信息对象
     */
    public CompletableFuture<GeolocationInfo> detectGeolocation(SshConnection connection,
                                                                Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                progressCallback.accept("正在检测服务器地理位置...");

                // 执行地理位置检测命令，包含超时与重试机制
                String command = "curl -sS --connect-timeout 30 --max-time 30 -w \"%{http_code}\" ipinfo.io/country | sed 's/200$//'";
                CommandResult result = sshCommandService.executeCommand(connection.getJschSession(), command);

                String countryCode = "";
                boolean useChineseMirror = false;

                if (result.exitStatus() == 0 && !result.stdout().trim().isEmpty()) {
                    countryCode = result.stdout().trim();
                    log.info("检测到服务器位于国家/地区: {}", countryCode);

                    if ("CN".equals(countryCode)) {
                        useChineseMirror = true;
                        progressCallback.accept("检测到服务器位于中国 (CN)，建议使用国内镜像源进行加速");
                    } else {
                        progressCallback.accept(String.format("服务器位于 %s，建议使用官方源",
                                countryCode.isEmpty() ? "未知地区" : countryCode));
                    }
                } else {
                    log.warn("地理位置检测失败，错误信息: {}", result.stderr());
                    progressCallback.accept("地理位置检测失败，将使用官方源");
                    countryCode = "UNKNOWN";
                }

                GeolocationInfo geoInfo = GeolocationInfo.builder()
                        .countryCode(countryCode)
                        .useChineseMirror(useChineseMirror)
                        .detectionSuccess(!"UNKNOWN".equals(countryCode))
                        .mirrorRecommendation(useChineseMirror ? "国内镜像源" : "官方源")
                        .build();

                log.info("地理位置检测完成: {}", geoInfo);
                return geoInfo;

            } catch (Exception e) {
                log.error("地理位置检测过程中发生异常", e);
                progressCallback.accept("地理位置检测失败: " + e.getMessage());

                // 返回默认配置
                return GeolocationInfo.builder()
                        .countryCode("UNKNOWN")
                        .useChineseMirror(false)
                        .detectionSuccess(false)
                        .mirrorRecommendation("官方源")
                        .errorMessage(e.getMessage())
                        .build();
            }
        });
    }

    /**
     * 检查服务器网络连接状态。
     * <p>通过ping公共地址判断服务器是否能正常访问外网。</p>
     *
     * @param connection SSH连接信息，不能为空
     * @return CompletableFuture，异步返回网络连接状态（true为正常，false为异常）
     */
    public CompletableFuture<Boolean> checkNetworkConnectivity(SshConnection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("检查网络连接状态");

                // 尝试ping一个稳定的公网地址
                String command = "ping -c 3 -W 5 8.8.8.8 > /dev/null 2>&1";
                CommandResult result = sshCommandService.executeCommand(connection.getJschSession(), command);

                boolean isConnected = result.exitStatus() == 0;
                log.info("网络连接状态: {}", isConnected ? "正常" : "异常");

                return isConnected;

            } catch (Exception e) {
                log.error("网络连接检查失败", e);
                return false;
            }
        });
    }

    /**
     * 获取推荐镜像源配置。
     * <p>根据地理位置信息，返回适合的镜像源配置建议。</p>
     *
     * @param geoInfo 地理位置信息对象，不能为空
     * @return 推荐的镜像源配置
     */
    public MirrorSourceConfig getRecommendedMirrorConfig(GeolocationInfo geoInfo) {
        if (geoInfo.isUseChineseMirror()) {
            return MirrorSourceConfig.builder()
                    .dockerRegistryMirror("https://hub-mirror.c.163.com")
                    .packageManagerMirror("aliyun")
                    .dockerComposeDownloadUrl("https://get.daocloud.io/docker/compose/releases/download")
                    .sillyTavernImageRegistry("ghcr.nju.edu.cn")
                    .description("国内镜像源配置，提供更快的下载速度")
                    .build();
        } else {
            return MirrorSourceConfig.builder()
                    .dockerRegistryMirror("")
                    .packageManagerMirror("official")
                    .dockerComposeDownloadUrl("https://github.com/docker/compose/releases/latest/download")
                    .sillyTavernImageRegistry("ghcr.io")
                    .description("官方源配置，提供最新稳定版本")
                    .build();
        }
    }

    /**
     * 地理位置信息数据类。
     * <p>用于封装地理位置检测结果及相关建议。</p>
     */
    @lombok.Data
    @lombok.Builder
    public static class GeolocationInfo {
        /**
         * 国家/地区代码（如CN、US、UNKNOWN等）
         */
        private String countryCode;
        /**
         * 是否建议使用国内镜像源
         */
        private boolean useChineseMirror;
        /**
         * 检测是否成功
         */
        private boolean detectionSuccess;
        /**
         * 推荐镜像源类型（国内镜像源/官方源）
         */
        private String mirrorRecommendation;
        /**
         * 错误信息（如有异常时填充）
         */
        private String errorMessage;

        /**
         * 获取国家/地区代码
         * @return 国家代码
         */
        public String getCountryCode() {
            return countryCode;
        }

        /**
         * 获取是否建议使用国内镜像源
         * @return 是否使用中国镜像
         */
        public boolean isUseChineseMirror() {
            return useChineseMirror;
        }
    }

    /**
     * 镜像源配置数据类。
     * <p>用于封装推荐的镜像源配置信息。</p>
     */
    @lombok.Data
    @lombok.Builder
    public static class MirrorSourceConfig {
        /**
         * Docker官方镜像加速地址
         */
        private String dockerRegistryMirror;
        /**
         * 包管理器镜像源（如aliyun、official等）
         */
        private String packageManagerMirror;
        /**
         * Docker Compose下载地址
         */
        private String dockerComposeDownloadUrl;
        /**
         * SillyTavern镜像仓库地址
         */
        private String sillyTavernImageRegistry;
        /**
         * 配置描述
         */
        private String description;
    }
}
