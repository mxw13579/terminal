package com.fufu.terminal.controller;

import com.fufu.terminal.dto.sillytavern.*;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.StompSessionManager;
import com.fufu.terminal.service.sillytavern.SillyTavernService;
import com.fufu.terminal.service.sillytavern.ConfigurationService;
import com.fufu.terminal.service.sillytavern.DockerVersionService;
import com.fufu.terminal.service.sillytavern.DataManagementService;
import com.fufu.terminal.service.sillytavern.RealTimeLogService;
import com.fufu.terminal.service.sillytavern.InteractiveDeploymentService;
import com.fufu.terminal.service.sillytavern.SystemConfigurationService;
import com.fufu.terminal.service.sillytavern.DockerInstallationService;
import com.fufu.terminal.service.sillytavern.SystemDetectionService;
import com.fufu.terminal.service.sillytavern.DockerHubApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * SillyTavern管理操作STOMP控制器
 * 负责处理SillyTavern的部署、状态监控、配置、数据管理、日志等操作。
 * 支持WebSocket STOMP协议，提供与前端实时交互能力。
 *
 * <p>主要功能包括：</p>
 * <ul>
 *     <li>系统环境校验</li>
 *     <li>容器部署与生命周期管理</li>
 *     <li>配置管理</li>
 *     <li>数据导入导出</li>
 *     <li>实时与历史日志流</li>
 *     <li>交互式部署流程</li>
 * </ul>
 *
 * @author lizelin
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SillyTavernStompController {

    /** 默认容器名称 */
    private static final String DEFAULT_CONTAINER_NAME = "sillytavern";

    /** SillyTavern核心服务 */
    private final SillyTavernService sillyTavernService;
    /** 配置管理服务 */
    private final ConfigurationService configurationService;
    /** Docker版本与镜像管理服务 */
    private final DockerVersionService dockerVersionService;
    /** 实时日志服务 */
    private final RealTimeLogService realTimeLogService;
    /** 数据管理服务 */
    private final DataManagementService dataManagementService;
    /** 交互式部署服务 */
    private final InteractiveDeploymentService interactiveDeploymentService;
    /** 系统配置服务 */
    private final SystemConfigurationService systemConfigurationService;
    /** Docker安装服务 */
    private final DockerInstallationService dockerInstallationService;
    /** 系统检测服务 */
    private final SystemDetectionService systemDetectionService;
    /** Docker Hub API服务 */
    private final DockerHubApiService dockerHubApiService;
    /** STOMP会话管理器 */
    private final StompSessionManager sessionManager;
    /** 消息模板 */
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 处理系统需求验证请求，校验当前系统是否满足运行SillyTavern的要求。
     *
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/validate-system")
    public void handleSystemValidation(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("处理系统需求验证，请求会话: {}", sessionId);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }
            SystemInfoDto systemInfo = sillyTavernService.validateSystemRequirements(connection);
            sendSuccessMessage(sessionId, "system-validation", systemInfo);
        } catch (Exception e) {
            log.error("系统需求验证失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "系统需求验证失败: " + e.getMessage());
        }
    }

    /**
     * 查询容器运行状态。
     *
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/status")
    public void handleStatusRequest(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("查询容器状态，请求会话: {}", sessionId);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }
            ContainerStatusDto status = sillyTavernService.getContainerStatus(connection);
            sendSuccessMessage(sessionId, "status", status);
        } catch (Exception e) {
            log.error("获取容器状态失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "获取容器状态失败: " + e.getMessage());
        }
    }

    /**
     * 处理容器部署请求，异步部署并推送进度。
     *
     * @param request 部署请求参数
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/deploy")
    public void handleDeployment(
            @Valid DeploymentRequestDto request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("处理部署请求，会话: {} 请求: {}", sessionId, request);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }
            // 异步部署并推送进度
            sillyTavernService.deployContainer(connection, request, progress ->
                    sendSuccessMessage(sessionId, "deployment-progress", progress)
            );
        } catch (Exception e) {
            log.error("部署启动失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "部署启动失败: " + e.getMessage());
        }
    }

    /**
     * 处理容器服务控制操作（启动、停止、重启、升级、删除）。
     *
     * @param request 操作请求参数
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/service-action")
    public void handleServiceAction(
            @Valid ServiceActionDto request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("处理服务操作，请求会话: {} 操作: {}", sessionId, request.getAction());

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }

            switch (request.getAction().toLowerCase()) {
                case "start" -> {
                    sillyTavernService.startContainer(connection);
                    sendActionResult(sessionId, true, "容器启动成功", null);
                }
                case "stop" -> {
                    sillyTavernService.stopContainer(connection);
                    sendActionResult(sessionId, true, "容器已停止", null);
                }
                case "restart" -> {
                    sillyTavernService.restartContainer(connection);
                    sendActionResult(sessionId, true, "容器已重启", null);
                }
                case "upgrade" -> {
                    sillyTavernService.upgradeContainer(connection, progress ->
                            sendSuccessMessage(sessionId, "upgrade-progress", Map.of("message", progress))
                    ).thenRun(() ->
                            sendActionResult(sessionId, true, "容器升级成功", null)
                    ).exceptionally(throwable -> {
                        sendActionResult(sessionId, false, "升级失败", throwable.getMessage());
                        return null;
                    });
                }
                case "delete" -> {
                    sillyTavernService.deleteContainer(connection, request.getRemoveData());
                    sendActionResult(sessionId, true, "容器已删除", null);
                }
                default -> sendActionResult(sessionId, false, "未知操作", "不支持的操作: " + request.getAction());
            }
        } catch (Exception e) {
            log.error("服务操作失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendActionResult(sessionId, false, "操作失败", e.getMessage());
        }
    }

    /**
     * 获取容器日志。
     *
     * @param request 日志请求参数
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/get-logs")
    public void handleLogRequest(
            @Valid LogRequestDto request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("获取日志，请求会话: {} 请求: {}", sessionId, request);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }
            var logs = sillyTavernService.getContainerLogs(connection, request);
            Map<String, Object> logResponse = Map.of(
                    "logs", logs,
                    "totalLines", logs.size(),
                    "truncated", logs.size() >= request.getTailLines(),
                    "containerName", request.getContainerName()
            );
            sendSuccessMessage(sessionId, "logs", logResponse);
        } catch (Exception e) {
            log.error("获取日志失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "获取日志失败: " + e.getMessage());
        }
    }

    /**
     * 获取配置内容。
     *
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/get-config")
    public void handleGetConfiguration(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("获取配置，请求会话: {}", sessionId);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }
            ConfigurationDto config = configurationService.readConfiguration(connection, DEFAULT_CONTAINER_NAME);
            sendSuccessMessage(sessionId, "config", config);
        } catch (Exception e) {
            log.error("获取配置失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "获取配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新配置并自动重启容器。
     *
     * @param request 配置内容
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/update-config")
    public void handleUpdateConfiguration(
            @Valid ConfigurationDto request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("更新配置，请求会话: {} 请求: {}", sessionId, request);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }
            // 配置校验
            Map<String, String> validationErrors = configurationService.validateConfiguration(request);
            if (!validationErrors.isEmpty()) {
                Map<String, Object> errorResponse = Map.of(
                        "success", false,
                        "message", "配置验证失败",
                        "errors", validationErrors
                );
                messagingTemplate.convertAndSend("/queue/sillytavern/config-updated-user" + sessionId, errorResponse);
                return;
            }
            String containerName = request.getContainerName() != null ? request.getContainerName() : DEFAULT_CONTAINER_NAME;
            boolean updated = configurationService.updateConfigurationWithRestart(connection, containerName, request);

            Map<String, Object> response = Map.of(
                    "success", updated,
                    "message", updated ? "配置更新成功，容器已自动重启" : "配置更新失败",
                    "requiresRestart", false
            );
            messagingTemplate.convertAndSend("/queue/sillytavern/config-updated-user" + sessionId, response);
        } catch (Exception e) {
            log.error("配置更新失败，会话 {}: {}", sessionId, e.getMessage(), e);
            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "message", "配置更新失败",
                    "error", e.getMessage()
            );
            messagingTemplate.convertAndSend("/queue/sillytavern/config-updated-user" + sessionId, errorResponse);
        }
    }

    /**
     * 导出数据，异步推送进度。
     *
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/export-data")
    public void handleDataExport(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("导出数据，请求会话: {}", sessionId);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }
            dataManagementService.exportData(connection, DEFAULT_CONTAINER_NAME, progress -> {
                Map<String, Object> progressMessage = Map.of(
                        "type", "export-progress",
                        "message", progress
                );
                messagingTemplate.convertAndSend("/queue/sillytavern/export-progress-user" + sessionId, progressMessage);
            }).thenAccept(exportDto ->
                    sendSuccessMessage(sessionId, "export", exportDto)
            ).exceptionally(throwable -> {
                log.error("数据导出失败，会话 {}: {}", sessionId, throwable.getMessage());
                sendErrorMessage(sessionId, "数据导出失败: " + throwable.getMessage());
                return null;
            });
        } catch (Exception e) {
            log.error("数据导出启动失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "数据导出启动失败: " + e.getMessage());
        }
    }

    /**
     * 导入数据，异步推送进度。
     *
     * @param request 导入请求参数
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/import-data")
    public void handleDataImport(
            Map<String, String> request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        String uploadedFileName = request.get("uploadedFileName");
        log.debug("导入数据，请求会话: {} 文件: {}", sessionId, uploadedFileName);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }
            if (uploadedFileName == null || uploadedFileName.trim().isEmpty()) {
                sendErrorMessage(sessionId, "未指定上传文件");
                return;
            }
            dataManagementService.importData(connection, DEFAULT_CONTAINER_NAME, uploadedFileName, progress -> {
                Map<String, Object> progressMessage = Map.of(
                        "type", "import-progress",
                        "message", progress
                );
                messagingTemplate.convertAndSend("/queue/sillytavern/import-progress-user" + sessionId, progressMessage);
            }).thenAccept(success -> {
                Map<String, Object> result = Map.of(
                        "success", success,
                        "message", success ? "数据导入成功" : "数据导入失败",
                        "requiresRestart", true
                );
                messagingTemplate.convertAndSend("/queue/sillytavern/import-user" + sessionId, result);
            }).exceptionally(throwable -> {
                log.error("数据导入失败，会话 {}: {}", sessionId, throwable.getMessage());
                Map<String, Object> result = Map.of(
                        "success", false,
                        "message", "数据导入失败",
                        "error", throwable.getMessage()
                );
                messagingTemplate.convertAndSend("/queue/sillytavern/import-user" + sessionId, result);
                return null;
            });
        } catch (Exception e) {
            log.error("数据导入启动失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "数据导入启动失败: " + e.getMessage());
        }
    }

    /**
     * 获取容器版本信息。
     *
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/get-version-info")
    public void handleGetVersionInfo(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("获取版本信息，请求会话: {}", sessionId);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }
            VersionInfoDto versionInfo = dockerVersionService.getVersionInfo(connection, DEFAULT_CONTAINER_NAME);
            sendSuccessMessage(sessionId, "version-info", versionInfo);
        } catch (Exception e) {
            log.error("获取版本信息失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "获取版本信息失败: " + e.getMessage());
        }
    }

    /**
     * 升级容器版本，异步推送进度。
     *
     * @param request 升级请求参数
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/upgrade-version")
    public void handleUpgradeVersion(
            Map<String, String> request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        String targetVersion = request.get("targetVersion");
        String containerName = request.getOrDefault("containerName", DEFAULT_CONTAINER_NAME);

        log.debug("升级版本，请求会话: {} 目标版本: {}", sessionId, targetVersion);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }
            if (targetVersion == null || targetVersion.trim().isEmpty()) {
                sendErrorMessage(sessionId, "目标版本不能为空");
                return;
            }
            dockerVersionService.upgradeToVersion(connection, containerName, targetVersion, progress -> {
                Map<String, Object> progressMessage = Map.of(
                        "type", "version-upgrade-progress",
                        "message", progress
                );
                messagingTemplate.convertAndSend("/queue/sillytavern/version-upgrade-progress-user" + sessionId, progressMessage);
            }).thenRun(() -> {
                Map<String, Object> result = Map.of(
                        "success", true,
                        "message", "版本升级完成: " + targetVersion,
                        "newVersion", targetVersion
                );
                messagingTemplate.convertAndSend("/queue/sillytavern/version-upgrade-user" + sessionId, result);
            }).exceptionally(throwable -> {
                log.error("版本升级失败，会话 {}: {}", sessionId, throwable.getMessage());
                Map<String, Object> result = Map.of(
                        "success", false,
                        "message", "版本升级失败",
                        "error", throwable.getMessage()
                );
                messagingTemplate.convertAndSend("/queue/sillytavern/version-upgrade-user" + sessionId, result);
                return null;
            });
        } catch (Exception e) {
            log.error("版本升级启动失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "版本升级启动失败: " + e.getMessage());
        }
    }

    /**
     * 清理未使用的Docker镜像，异步执行。
     *
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/cleanup-images")
    public void handleCleanupImages(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("清理未使用镜像，请求会话: {}", sessionId);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    dockerVersionService.cleanupUnusedImages(connection);
                    Map<String, Object> result = Map.of(
                            "success", true,
                            "message", "镜像清理完成"
                    );
                    messagingTemplate.convertAndSend("/queue/sillytavern/cleanup-images-user" + sessionId, result);
                } catch (Exception e) {
                    log.error("镜像清理失败，会话 {}: {}", sessionId, e.getMessage());
                    Map<String, Object> result = Map.of(
                            "success", false,
                            "message", "镜像清理失败",
                            "error", e.getMessage()
                    );
                    messagingTemplate.convertAndSend("/queue/sillytavern/cleanup-images-user" + sessionId, result);
                }
            });
        } catch (Exception e) {
            log.error("镜像清理启动失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "镜像清理启动失败: " + e.getMessage());
        }
    }

    /**
     * 启动实时日志流。
     *
     * @param request 请求参数（包含容器名、最大行数）
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/start-realtime-logs")
    public void handleStartRealtimeLogs(
            Map<String, Object> request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        String containerName = (String) request.getOrDefault("containerName", DEFAULT_CONTAINER_NAME);
        Integer maxLines = (Integer) request.getOrDefault("maxLines", 1000);

        log.debug("启动实时日志流，请求会话: {} 容器: {} 最大行数: {}", sessionId, containerName, maxLines);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }
            if (maxLines != null && (maxLines < 100 || maxLines > 5000)) {
                sendErrorMessage(sessionId, "最大行数必须在100-5000之间");
                return;
            }
            realTimeLogService.startLogStream(sessionId, containerName, maxLines);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "实时日志流已启动",
                    "containerName", containerName,
                    "maxLines", maxLines
            );
            sendSuccessMessage(sessionId, "realtime-logs-started", response);
        } catch (Exception e) {
            log.error("启动实时日志流失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "启动实时日志流失败: " + e.getMessage());
        }
    }

    /**
     * 停止实时日志流。
     *
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/stop-realtime-logs")
    public void handleStopRealtimeLogs(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("停止实时日志流，请求会话: {}", sessionId);

        try {
            realTimeLogService.stopLogStream(sessionId);
            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "实时日志流已停止"
            );
            sendSuccessMessage(sessionId, "realtime-logs-stopped", response);
        } catch (Exception e) {
            log.error("停止实时日志流失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "停止实时日志流失败: " + e.getMessage());
        }
    }

    /**
     * 获取历史日志。
     *
     * @param request 请求参数（容器名、行数、日志级别）
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/get-history-logs")
    public void handleGetHistoryLogs(
            Map<String, Object> request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        String containerName = (String) request.getOrDefault("containerName", DEFAULT_CONTAINER_NAME);
        Integer lines = (Integer) request.getOrDefault("lines", 500);
        String level = (String) request.getOrDefault("level", "all");

        log.debug("获取历史日志，请求会话: {} 容器: {} 行数: {} 级别: {}", sessionId, containerName, lines, level);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }
            if (lines != null && (lines < 1 || lines > 3000)) {
                sendErrorMessage(sessionId, "日志行数必须在1-3000之间");
                return;
            }
            RealTimeLogDto historyLogs = realTimeLogService.getHistoryLogs(
                    connection, containerName, lines, level);
            sendSuccessMessage(sessionId, "history-logs", historyLogs);
        } catch (Exception e) {
            log.error("获取历史日志失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "获取历史日志失败: " + e.getMessage());
        }
    }

    /**
     * 发送成功消息到指定会话队列。
     *
     * @param sessionId 会话ID
     * @param messageType 消息类型
     * @param data 负载数据
     */
    private void sendSuccessMessage(String sessionId, String messageType, Object data) {
        Map<String, Object> message = Map.of(
                "type", messageType,
                "success", true,
                "payload", data
        );
        messagingTemplate.convertAndSend("/queue/sillytavern/" + messageType + "-user" + sessionId, message);
    }

    /**
     * 发送操作结果消息。
     *
     * @param sessionId 会话ID
     * @param success 是否成功
     * @param message 消息内容
     * @param error 错误信息
     */
    private void sendActionResult(String sessionId, boolean success, String message, String error) {
        Map<String, Object> result = Map.of(
                "success", success,
                "message", message,
                "error", error != null ? error : ""
        );
        messagingTemplate.convertAndSend("/queue/sillytavern/action-result-user" + sessionId, result);
    }

    /**
     * 发送错误消息到指定会话队列。
     *
     * @param sessionId 会话ID
     * @param message 错误信息
     */
    private void sendErrorMessage(String sessionId, String message) {
        sessionManager.sendErrorMessage(sessionId, message);
    }

    // ==================== 交互式部署功能端点 ====================

    /**
     * 启动交互式SillyTavern部署流程，支持完全信任和分步确认两种模式。
     *
     * @param request 交互式部署请求参数
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/interactive-deploy")
    public void handleInteractiveDeployment(
            @Valid InteractiveDeploymentDto.RequestDto request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("交互式部署，请求会话: {} 请求: {}", sessionId, request);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }
            interactiveDeploymentService.startInteractiveDeployment(
                    sessionId,
                    connection,
                    request
            );
        } catch (Exception e) {
            log.error("交互式部署启动失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "交互式部署启动失败: " + e.getMessage());
        }
    }

    /**
     * 处理交互式部署过程中的确认步骤。
     *
     * @param request 确认请求参数
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/deployment-confirm")
    public void handleDeploymentConfirmation(
            Map<String, Object> request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        String stepId = (String) request.get("stepId");
        Boolean confirmed = (Boolean) request.getOrDefault("confirmed", false);
        Map<String, Object> userInput = (Map<String, Object>) request.getOrDefault("userInput", Map.of());

        log.debug("交互式部署确认，请求会话: {} 步骤: {} 确认: {} 用户输入: {}", sessionId, stepId, confirmed, userInput);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }
            InteractiveDeploymentDto.ConfirmationDto confirmation = InteractiveDeploymentDto.ConfirmationDto.builder()
                    .stepId(stepId)
                    .action(confirmed ? "confirm" : "cancel")
                    .userChoice(userInput)
                    .build();
            interactiveDeploymentService.handleUserConfirmation(sessionId, confirmation);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "stepId", stepId,
                    "message", confirmed ? "确认处理成功" : "操作取消"
            );
            messagingTemplate.convertAndSend("/queue/sillytavern/deployment-confirm-user" + sessionId, response);
        } catch (Exception e) {
            log.error("交互式部署确认失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "交互式部署确认失败: " + e.getMessage());
        }
    }

    /**
     * 跳过交互式部署某个步骤。
     *
     * @param request 跳过请求参数
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/deployment-skip")
    public void handleDeploymentSkip(
            Map<String, String> request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        String stepId = request.get("stepId");
        String reason = request.getOrDefault("reason", "用户选择跳过");

        log.debug("交互式部署跳过，请求会话: {} 步骤: {} 原因: {}", sessionId, stepId, reason);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }
            InteractiveDeploymentDto.ConfirmationDto confirmation = InteractiveDeploymentDto.ConfirmationDto.builder()
                    .stepId(stepId)
                    .action("skip")
                    .reason(reason)
                    .build();
            interactiveDeploymentService.handleUserConfirmation(sessionId, confirmation);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "stepId", stepId,
                    "message", "步骤已跳过",
                    "reason", reason
            );
            messagingTemplate.convertAndSend("/queue/sillytavern/deployment-skip-user" + sessionId, response);
        } catch (Exception e) {
            log.error("交互式部署跳过失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "交互式部署跳过失败: " + e.getMessage());
        }
    }

    /**
     * 取消整个交互式部署流程。
     *
     * @param request 取消请求参数
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/deployment-cancel")
    public void handleDeploymentCancel(
            Map<String, String> request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        String reason = request.getOrDefault("reason", "用户取消部署");

        log.debug("交互式部署取消，请求会话: {} 原因: {}", sessionId, reason);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }
            interactiveDeploymentService.cancelDeployment(sessionId, reason);

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "部署已取消",
                    "reason", reason
            );
            messagingTemplate.convertAndSend("/queue/sillytavern/deployment-cancel-user" + sessionId, response);
        } catch (Exception e) {
            log.error("交互式部署取消失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "交互式部署取消失败: " + e.getMessage());
        }
    }

    /**
     * 启动增强版交互式部署
     * <p>支持Docker自动安装的完整部署流程</p>
     *
     * @param request 部署请求
     * @param headerAccessor WebSocket会话头
     */
    @MessageMapping("/sillytavern/enhanced-deploy")
    public void handleEnhancedInteractiveDeployment(
            @Valid InteractiveDeploymentDto.RequestDto request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("增强版交互式部署，请求会话: {} 请求: {}", sessionId, request);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }

            // 启动增强版部署流程，包含Docker自动安装
            interactiveDeploymentService.startEnhancedInteractiveDeployment(
                    sessionId,
                    connection,
                    request
            );
        } catch (Exception e) {
            log.error("增强版交互式部署启动失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "增强版交互式部署启动失败: " + e.getMessage());
        }
    }

    /**
     * 系统配置和镜像源设置
     *
     * @param request 配置请求
     * @param headerAccessor WebSocket会话头
     */
    @MessageMapping("/sillytavern/configure-system")
    public void handleSystemConfiguration(
            Map<String, Object> request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("配置系统镜像源，请求会话: {}", sessionId);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }

            systemConfigurationService.configureSystemMirrors(connection, (progressMsg) -> {
                // 发送实时进度更新
                Map<String, Object> progressResponse = Map.of(
                        "type", "system-config-progress",
                        "message", progressMsg
                );
                messagingTemplate.convertAndSend("/queue/sillytavern/system-config-user" + sessionId, progressResponse);
            }).thenAccept(result -> {
                Map<String, Object> response = Map.of(
                        "success", result.isSuccess(),
                        "message", result.getMessage(),
                        "skipped", result.isSkipped(),
                        "geolocationInfo", result.getGeolocationInfo() != null ? result.getGeolocationInfo() : Map.of(),
                        "systemInfo", result.getSystemInfo() != null ? result.getSystemInfo() : Map.of()
                );
                messagingTemplate.convertAndSend("/queue/sillytavern/system-config-user" + sessionId, response);
            }).exceptionally(throwable -> {
                log.error("系统配置失败，会话 {}: {}", sessionId, throwable.getMessage(), throwable);
                sendErrorMessage(sessionId, "系统配置失败: " + throwable.getMessage());
                return null;
            });

        } catch (Exception e) {
            log.error("系统配置启动失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "系统配置启动失败: " + e.getMessage());
        }
    }

    /**
     * Docker安装状态检查和自动安装
     *
     * @param request 请求参数
     * @param headerAccessor WebSocket会话头
     */
    @MessageMapping("/sillytavern/docker-install-check")
    public void handleDockerInstallationCheck(
            Map<String, Object> request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("检查Docker安装状态，请求会话: {}", sessionId);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("未找到SSH连接，会话: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }

            dockerInstallationService.checkDockerInstallation(connection)
                    .thenCompose(dockerStatus -> {
                        if (dockerStatus.isInstalled() && dockerStatus.isServiceRunning()) {
                            // Docker已安装且运行正常
                            Map<String, Object> response = Map.of(
                                    "success", true,
                                    "dockerInstalled", true,
                                    "dockerStatus", Map.of(
                                            "installed", dockerStatus.isInstalled(),
                                            "version", dockerStatus.getVersion(),
                                            "serviceRunning", dockerStatus.isServiceRunning(),
                                            "message", dockerStatus.getMessage()
                                    ),
                                    "message", "Docker运行正常"
                            );
                            messagingTemplate.convertAndSend("/queue/sillytavern/docker-check-user" + sessionId, response);
                            return CompletableFuture.completedFuture(null);
                        } else {
                            // 需要安装或启动Docker
                            boolean autoInstall = (Boolean) request.getOrDefault("autoInstall", true);
                            if (!autoInstall) {
                                Map<String, Object> response = Map.of(
                                        "success", false,
                                        "dockerInstalled", false,
                                        "dockerStatus", Map.of(
                                                "installed", dockerStatus.isInstalled(),
                                                "version", dockerStatus.getVersion(),
                                                "serviceRunning", dockerStatus.isServiceRunning(),
                                                "message", dockerStatus.getMessage()
                                        ),
                                        "message", "Docker未安装或未运行"
                                );
                                messagingTemplate.convertAndSend("/queue/sillytavern/docker-check-user" + sessionId, response);
                                return CompletableFuture.completedFuture(null);
                            }

                            // 自动安装Docker
                            return systemDetectionService.detectSystemEnvironment(connection)
                                    .thenCompose(systemInfo -> {
                                        boolean useChineseMirror = (Boolean) request.getOrDefault("useChineseMirror", false);

                                        return dockerInstallationService.installDocker(
                                                connection, systemInfo, useChineseMirror, (progressMsg) -> {
                                                    // 发送Docker安装进度
                                                    Map<String, Object> progressResponse = Map.of(
                                                            "type", "docker-install-progress",
                                                            "message", progressMsg
                                                    );
                                                    messagingTemplate.convertAndSend("/queue/sillytavern/docker-install-user" + sessionId, progressResponse);
                                                }
                                        );
                                    })
                                    .thenAccept(installResult -> {
                                        Map<String, Object> response = Map.of(
                                                "success", installResult.isSuccess(),
                                                "dockerInstalled", installResult.isSuccess(),
                                                "installationResult", Map.of(
                                                        "success", installResult.isSuccess(),
                                                        "message", installResult.getMessage(),
                                                        "installedVersion", installResult.getInstalledVersion(),
                                                        "installationMethod", installResult.getInstallationMethod()
                                                ),
                                                "message", installResult.getMessage()
                                        );
                                        messagingTemplate.convertAndSend("/queue/sillytavern/docker-install-user" + sessionId, response);
                                    });
                        }
                    }).exceptionally(throwable -> {
                        log.error("Docker检查/安装失败，会话 {}: {}", sessionId, throwable.getMessage(), throwable);
                        sendErrorMessage(sessionId, "Docker检查/安装失败: " + throwable.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            log.error("Docker检查启动失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "Docker检查启动失败: " + e.getMessage());
        }
    }

    /**
     * 获取Docker Hub最新版本信息
     *
     * @param headerAccessor STOMP消息头访问器
     */
    @MessageMapping("/sillytavern/get-versions")
    public void getDockerHubVersions(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.info("获取Docker Hub版本信息，会话: {}", sessionId);

        try {
            // 检查sessionId
            if (sessionId == null || sessionId.isEmpty()) {
                log.error("Session ID为空，无法发送响应");
                return;
            }
            
            log.info("收到获取版本信息请求，会话: {}", sessionId);

            // 异步获取版本信息，避免阻塞WebSocket线程
            CompletableFuture<List<DockerHubVersionDto>> future = CompletableFuture.supplyAsync(() -> {
                log.debug("开始异步获取版本信息...");
                try {
                    List<DockerHubVersionDto> versions = dockerHubApiService.getLatestVersions("goolashe/sillytavern", 5);
                    log.info("成功获取到 {} 个版本信息", versions.size());
                    return versions;
                } catch (Exception e) {
                    log.error("获取版本信息时发生异常: {}", e.getMessage(), e);
                    throw new RuntimeException("获取版本信息失败: " + e.getMessage(), e);
                }
            });

            future.thenAccept(versions -> {
                log.debug("准备发送成功响应到前端，Session ID: {}", sessionId);
                Map<String, Object> response = Map.of(
                        "success", true,
                        "versions", versions,
                        "repository", "goolashe/sillytavern",
                        "message", "成功获取到 " + versions.size() + " 个版本信息"
                );
                
                log.debug("响应内容大小: {} 版本", versions.size());
                log.debug("发送到路径: /queue/sillytavern/versions, Session: {}", sessionId);
                
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/sillytavern/versions", response);
                log.info("版本信息已发送到前端，会话: {}, 版本数量: {}", sessionId, versions.size());
            }).exceptionally(throwable -> {
                log.error("获取Docker Hub版本信息失败，会话 {}: {}", sessionId, throwable.getMessage(), throwable);
                Map<String, Object> errorResponse = Map.of(
                        "success", false,
                        "error", "获取版本信息失败: " + throwable.getMessage(),
                        "message", "无法连接到Docker Hub API"
                );
                
                log.debug("发送错误响应到路径: /queue/sillytavern/versions, Session: {}", sessionId);
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/sillytavern/versions", errorResponse);
                log.info("错误响应已发送到前端，会话: {}", sessionId);
                return null;
            });

        } catch (Exception e) {
            log.error("启动版本信息获取失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "启动版本信息获取失败: " + e.getMessage());
        }
    }

}
