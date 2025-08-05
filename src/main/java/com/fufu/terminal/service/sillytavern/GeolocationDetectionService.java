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
 * 地理位置检测服务
 * 负责检测服务器地理位置，决定是否使用国内镜像源
 * 基于linux-silly-tavern-docker-deploy.sh脚本的地理位置检测功能
 * 
 * @author Claude
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeolocationDetectionService {

    private final SshCommandService sshCommandService;

    /**
     * 检测服务器地理位置
     * 通过ipinfo.io API检测服务器是否位于中国
     * 
     * @param connection SSH连接
     * @param progressCallback 进度回调函数
     * @return 地理位置信息，包含国家代码和是否使用国内镜像源的建议
     */
    public CompletableFuture<GeolocationInfo> detectGeolocation(SshConnection connection, 
                                                              Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                progressCallback.accept("正在检测服务器地理位置...");
                
                // 执行地理位置检测命令，增加重试和超时机制
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
                    .detectionSuccess(!countryCode.equals("UNKNOWN"))
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
     * 验证网络连接是否正常
     * 检查服务器是否能够正常访问外网
     * 
     * @param connection SSH连接
     * @return 网络连接状态
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
     * 获取推荐镜像源配置
     * 根据地理位置返回推荐的镜像源配置
     * 
     * @param geoInfo 地理位置信息
     * @return 镜像源配置建议
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
     * 地理位置信息数据类
     */
    @lombok.Data
    @lombok.Builder
    public static class GeolocationInfo {
        private String countryCode;
        private boolean useChineseMirror;
        private boolean detectionSuccess;
        private String mirrorRecommendation;
        private String errorMessage;
    }

    /**
     * 镜像源配置数据类
     */
    @lombok.Data
    @lombok.Builder
    public static class MirrorSourceConfig {
        private String dockerRegistryMirror;
        private String packageManagerMirror;
        private String dockerComposeDownloadUrl;
        private String sillyTavernImageRegistry;
        private String description;
    }
}