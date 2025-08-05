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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * STOMP controller for SillyTavern management operations.
 * Handles deployment, status monitoring, and container lifecycle management.
 *
 * @author lizelin
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SillyTavernStompController {

    private final SillyTavernService sillyTavernService;
    private final ConfigurationService configurationService;
    private final DockerVersionService dockerVersionService;
    private final RealTimeLogService realTimeLogService;
    private final DataManagementService dataManagementService;
    private final InteractiveDeploymentService interactiveDeploymentService;
    private final StompSessionManager sessionManager;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle system requirements validation requests.
     */
    @MessageMapping("/sillytavern/validate-system")
    public void handleSystemValidation(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("Handling system validation for session: {}", sessionId);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            SystemInfoDto systemInfo = sillyTavernService.validateSystemRequirements(connection);

            sendSuccessMessage(sessionId, "system-validation", systemInfo);

        } catch (Exception e) {
            log.error("Error validating system requirements for session {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "System validation error: " + e.getMessage());
        }
    }

    /**
     * Handle container status requests.
     */
    @MessageMapping("/sillytavern/status")
    public void handleStatusRequest(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("Handling status request for session: {}", sessionId);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            ContainerStatusDto status = sillyTavernService.getContainerStatus(connection);

            sendSuccessMessage(sessionId, "status", status);

        } catch (Exception e) {
            log.error("Error getting container status for session {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "Status check error: " + e.getMessage());
        }
    }

    /**
     * Handle deployment requests.
     */
    @MessageMapping("/sillytavern/deploy")
    public void handleDeployment(
            @Valid DeploymentRequestDto request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("Handling deployment request for session: {} with request: {}", sessionId, request);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            // Start deployment asynchronously with progress updates
            sillyTavernService.deployContainer(connection, request, (progress) -> {
                sendSuccessMessage(sessionId, "deployment-progress", progress);
            });

        } catch (Exception e) {
            log.error("Error starting deployment for session {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "Deployment error: " + e.getMessage());
        }
    }

    /**
     * Handle service control actions (start, stop, restart, upgrade, delete).
     */
    @MessageMapping("/sillytavern/service-action")
    public void handleServiceAction(
            @Valid ServiceActionDto request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("Handling service action for session: {} action: {}", sessionId, request.getAction());

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            switch (request.getAction().toLowerCase()) {
                case "start":
                    sillyTavernService.startContainer(connection);
                    sendActionResult(sessionId, true, "Container started successfully", null);
                    break;

                case "stop":
                    sillyTavernService.stopContainer(connection);
                    sendActionResult(sessionId, true, "Container stopped successfully", null);
                    break;

                case "restart":
                    sillyTavernService.restartContainer(connection);
                    sendActionResult(sessionId, true, "Container restarted successfully", null);
                    break;

                case "upgrade":
                    // Upgrade is async, send progress updates
                    sillyTavernService.upgradeContainer(connection, (progress) -> {
                        sendSuccessMessage(sessionId, "upgrade-progress", Map.of("message", progress));
                    }).thenRun(() -> {
                        sendActionResult(sessionId, true, "Container upgraded successfully", null);
                    }).exceptionally(throwable -> {
                        sendActionResult(sessionId, false, "Upgrade failed", throwable.getMessage());
                        return null;
                    });
                    break;

                case "delete":
                    sillyTavernService.deleteContainer(connection, request.getRemoveData());
                    sendActionResult(sessionId, true, "Container deleted successfully", null);
                    break;

                default:
                    sendActionResult(sessionId, false, "Unknown action", "Unsupported action: " + request.getAction());
            }

        } catch (Exception e) {
            log.error("Error handling service action for session {}: {}", sessionId, e.getMessage(), e);
            sendActionResult(sessionId, false, "Action failed", e.getMessage());
        }
    }

    /**
     * Handle log requests.
     */
    @MessageMapping("/sillytavern/get-logs")
    public void handleLogRequest(
            @Valid LogRequestDto request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("Handling log request for session: {} with request: {}", sessionId, request);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            java.util.List<String> logs = sillyTavernService.getContainerLogs(connection, request);

            Map<String, Object> logResponse = Map.of(
                    "logs", logs,
                    "totalLines", logs.size(),
                    "truncated", logs.size() >= request.getTailLines(),
                    "containerName", request.getContainerName()
            );

            sendSuccessMessage(sessionId, "logs", logResponse);

        } catch (Exception e) {
            log.error("Error getting logs for session {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "Log retrieval error: " + e.getMessage());
        }
    }

    /**
     * Handle configuration retrieval requests.
     */
    @MessageMapping("/sillytavern/get-config")
    public void handleGetConfiguration(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("Handling get configuration request for session: {}", sessionId);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            ConfigurationDto config = configurationService.readConfiguration(connection, "sillytavern");

            sendSuccessMessage(sessionId, "config", config);

        } catch (Exception e) {
            log.error("Error getting configuration for session {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "Configuration retrieval error: " + e.getMessage());
        }
    }

    /**
     * 处理配置更新请求
     */
    @MessageMapping("/sillytavern/update-config")
    public void handleUpdateConfiguration(
            @Valid ConfigurationDto request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("处理配置更新请求，会话: {} 请求: {}", sessionId, request);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("会话未找到SSH连接: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }

            // 验证配置
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

            // 使用增强的配置更新方法（包含自动重启）
            String containerName = request.getContainerName() != null ? request.getContainerName() : "sillytavern";
            boolean updated = configurationService.updateConfigurationWithRestart(connection, containerName, request);

            Map<String, Object> response = Map.of(
                    "success", updated,
                    "message", updated ? "配置更新成功，容器已自动重启" : "配置更新失败",
                    "requiresRestart", false // 已经自动重启了
            );

            messagingTemplate.convertAndSend("/queue/sillytavern/config-updated-user" + sessionId, response);

        } catch (Exception e) {
            log.error("更新配置失败，会话 {}: {}", sessionId, e.getMessage(), e);
            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "message", "配置更新失败",
                    "error", e.getMessage()
            );
            messagingTemplate.convertAndSend("/queue/sillytavern/config-updated-user" + sessionId, errorResponse);
        }
    }

    /**
     * Handle data export requests.
     */
    @MessageMapping("/sillytavern/export-data")
    public void handleDataExport(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("Handling data export request for session: {}", sessionId);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            // Start data export asynchronously with progress updates
            dataManagementService.exportData(connection, "sillytavern", (progress) -> {
                Map<String, Object> progressMessage = Map.of(
                        "type", "export-progress",
                        "message", progress
                );
                messagingTemplate.convertAndSend("/queue/sillytavern/export-progress-user" + sessionId, progressMessage);
            }).thenAccept(exportDto -> {
                sendSuccessMessage(sessionId, "export", exportDto);
            }).exceptionally(throwable -> {
                log.error("Data export failed for session {}: {}", sessionId, throwable.getMessage());
                sendErrorMessage(sessionId, "Data export failed: " + throwable.getMessage());
                return null;
            });

        } catch (Exception e) {
            log.error("Error starting data export for session {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "Data export error: " + e.getMessage());
        }
    }

    /**
     * Handle data import requests.
     */
    @MessageMapping("/sillytavern/import-data")
    public void handleDataImport(
            Map<String, String> request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        String uploadedFileName = request.get("uploadedFileName");
        log.debug("Handling data import request for session: {} with file: {}", sessionId, uploadedFileName);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            if (uploadedFileName == null || uploadedFileName.trim().isEmpty()) {
                sendErrorMessage(sessionId, "No uploaded file specified");
                return;
            }

            // Start data import asynchronously with progress updates
            dataManagementService.importData(connection, "sillytavern", uploadedFileName, (progress) -> {
                Map<String, Object> progressMessage = Map.of(
                        "type", "import-progress",
                        "message", progress
                );
                messagingTemplate.convertAndSend("/queue/sillytavern/import-progress-user" + sessionId, progressMessage);
            }).thenAccept(success -> {
                Map<String, Object> result = Map.of(
                        "success", success,
                        "message", success ? "Data imported successfully" : "Data import failed",
                        "requiresRestart", true // Usually requires restart after data import
                );
                messagingTemplate.convertAndSend("/queue/sillytavern/import-user" + sessionId, result);
            }).exceptionally(throwable -> {
                log.error("Data import failed for session {}: {}", sessionId, throwable.getMessage());
                Map<String, Object> result = Map.of(
                        "success", false,
                        "message", "Data import failed",
                        "error", throwable.getMessage()
                );
                messagingTemplate.convertAndSend("/queue/sillytavern/import-user" + sessionId, result);
                return null;
            });

        } catch (Exception e) {
            log.error("Error starting data import for session {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "Data import error: " + e.getMessage());
        }
    }

    /**
     * 处理版本信息查询请求
     */
    @MessageMapping("/sillytavern/get-version-info")
    public void handleGetVersionInfo(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("处理版本信息查询请求，会话: {}", sessionId);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("会话未找到SSH连接: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }

            String containerName = "sillytavern"; // 默认容器名
            VersionInfoDto versionInfo = dockerVersionService.getVersionInfo(connection, containerName);

            sendSuccessMessage(sessionId, "version-info", versionInfo);

        } catch (Exception e) {
            log.error("获取版本信息失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "获取版本信息失败: " + e.getMessage());
        }
    }

    /**
     * 处理版本升级请求
     */
    @MessageMapping("/sillytavern/upgrade-version")
    public void handleUpgradeVersion(
            Map<String, String> request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        String targetVersion = request.get("targetVersion");
        String containerName = request.getOrDefault("containerName", "sillytavern");

        log.debug("处理版本升级请求，会话: {} 目标版本: {}", sessionId, targetVersion);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("会话未找到SSH连接: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }

            if (targetVersion == null || targetVersion.trim().isEmpty()) {
                sendErrorMessage(sessionId, "目标版本不能为空");
                return;
            }

            // 开始异步版本升级，发送进度更新
            dockerVersionService.upgradeToVersion(connection, containerName, targetVersion, (progress) -> {
                Map<String, Object> progressMessage = Map.of(
                        "type", "version-upgrade-progress",
                        "message", progress
                );
                messagingTemplate.convertAndSend("/queue/sillytavern/version-upgrade-progress-user" + sessionId, progressMessage);
            }).thenRun(() -> {
                // 升级完成，发送成功消息
                Map<String, Object> result = Map.of(
                        "success", true,
                        "message", "版本升级完成: " + targetVersion,
                        "newVersion", targetVersion
                );
                messagingTemplate.convertAndSend("/queue/sillytavern/version-upgrade-user" + sessionId, result);
            }).exceptionally(throwable -> {
                // 升级失败，发送错误消息
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
            log.error("启动版本升级失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "版本升级启动失败: " + e.getMessage());
        }
    }

    /**
     * 处理清理未使用镜像请求
     */
    @MessageMapping("/sillytavern/cleanup-images")
    public void handleCleanupImages(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("处理镜像清理请求，会话: {}", sessionId);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("会话未找到SSH连接: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }

            // 异步执行镜像清理
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
            log.error("启动镜像清理失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "镜像清理启动失败: " + e.getMessage());
        }
    }

    /**
     * 处理开始实时日志流请求
     */
    @MessageMapping("/sillytavern/start-realtime-logs")
    public void handleStartRealtimeLogs(
            Map<String, Object> request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        String containerName = (String) request.getOrDefault("containerName", "sillytavern");
        Integer maxLines = (Integer) request.getOrDefault("maxLines", 1000);

        log.debug("处理开始实时日志流请求，会话: {} 容器: {} 最大行数: {}", sessionId, containerName, maxLines);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("会话未找到SSH连接: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }

            // 验证最大行数
            if (maxLines != null && (maxLines < 100 || maxLines > 5000)) {
                sendErrorMessage(sessionId, "最大行数必须在100-5000之间");
                return;
            }

            // 开始实时日志流
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
     * 处理停止实时日志流请求
     */
    @MessageMapping("/sillytavern/stop-realtime-logs")
    public void handleStopRealtimeLogs(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("处理停止实时日志流请求，会话: {}", sessionId);

        try {
            // 停止实时日志流
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
     * 处理获取历史日志请求
     */
    @MessageMapping("/sillytavern/get-history-logs")
    public void handleGetHistoryLogs(
            Map<String, Object> request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        String containerName = (String) request.getOrDefault("containerName", "sillytavern");
        Integer lines = (Integer) request.getOrDefault("lines", 500);
        String level = (String) request.getOrDefault("level", "all");

        log.debug("处理获取历史日志请求，会话: {} 容器: {} 行数: {} 级别: {}", sessionId, containerName, lines, level);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("会话未找到SSH连接: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }

            // 验证参数
            if (lines != null && (lines < 1 || lines > 3000)) {
                sendErrorMessage(sessionId, "日志行数必须在1-3000之间");
                return;
            }

            // 获取历史日志
            RealTimeLogDto historyLogs = realTimeLogService.getHistoryLogs(
                    connection, containerName, lines, level);

            sendSuccessMessage(sessionId, "history-logs", historyLogs);

        } catch (Exception e) {
            log.error("获取历史日志失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "获取历史日志失败: " + e.getMessage());
        }
    }

    /**
     * Send success message to session-specific queue
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
     * Send action result message
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
     * Send error message to session-specific queue
     */
    private void sendErrorMessage(String sessionId, String message) {
        sessionManager.sendErrorMessage(sessionId, message);
    }

    // ==================== 交互式部署功能端点 ====================

    /**
     * 处理交互式部署请求
     * 启动交互式SillyTavern部署流程，支持完全信任和分步确认两种模式
     */
    @MessageMapping("/sillytavern/interactive-deploy")
    public void handleInteractiveDeployment(
            @Valid InteractiveDeploymentDto.RequestDto request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("处理交互式部署请求，会话: {} 请求: {}", sessionId, request);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("会话未找到SSH连接: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }

            // 启动交互式部署流程
            interactiveDeploymentService.startInteractiveDeployment(
                sessionId,
                connection, 
                request
            );

        } catch (Exception e) {
            log.error("启动交互式部署失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "启动交互式部署失败: " + e.getMessage());
        }
    }

    /**
     * 处理部署确认请求
     * 用户对交互式部署过程中的确认步骤进行响应
     */
    @MessageMapping("/sillytavern/deployment-confirm")
    public void handleDeploymentConfirmation(
            Map<String, Object> request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        String stepId = (String) request.get("stepId");
        Boolean confirmed = (Boolean) request.getOrDefault("confirmed", false);
        Map<String, Object> userInput = (Map<String, Object>) request.getOrDefault("userInput", Map.of());

        log.debug("处理部署确认请求，会话: {} 步骤: {} 确认: {} 用户输入: {}", 
            sessionId, stepId, confirmed, userInput);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("会话未找到SSH连接: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }

            // 处理用户确认响应 - 创建确认对象
            InteractiveDeploymentDto.ConfirmationDto confirmation = InteractiveDeploymentDto.ConfirmationDto.builder()
                .stepId(stepId)
                .action(confirmed ? "confirm" : "cancel")
                .userChoice(userInput)
                .build();
                
            interactiveDeploymentService.handleUserConfirmation(sessionId, confirmation);
            
            // 发送响应
            Map<String, Object> response = Map.of(
                "success", true,
                "stepId", stepId,
                "message", confirmed ? "确认处理成功" : "操作取消"
            );
            messagingTemplate.convertAndSend(
                "/queue/sillytavern/deployment-confirm-user" + sessionId, 
                response);

        } catch (Exception e) {
            log.error("处理部署确认失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "处理部署确认失败: " + e.getMessage());
        }
    }

    /**
     * 处理部署跳过请求
     * 用户选择跳过某个交互式部署步骤
     */
    @MessageMapping("/sillytavern/deployment-skip")
    public void handleDeploymentSkip(
            Map<String, String> request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        String stepId = request.get("stepId");
        String reason = request.getOrDefault("reason", "用户选择跳过");

        log.debug("处理部署跳过请求，会话: {} 步骤: {} 原因: {}", sessionId, stepId, reason);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("会话未找到SSH连接: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }

            // 处理跳过请求 - 创建跳过确认对象
            InteractiveDeploymentDto.ConfirmationDto confirmation = InteractiveDeploymentDto.ConfirmationDto.builder()
                .stepId(stepId)
                .action("skip")
                .reason(reason)
                .build();
                
            interactiveDeploymentService.handleUserConfirmation(sessionId, confirmation);
            
            // 发送响应
            Map<String, Object> response = Map.of(
                "success", true,
                "stepId", stepId,
                "message", "步骤已跳过",
                "reason", reason
            );
            messagingTemplate.convertAndSend(
                "/queue/sillytavern/deployment-skip-user" + sessionId, 
                response);

        } catch (Exception e) {
            log.error("处理部署跳过失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "处理部署跳过失败: " + e.getMessage());
        }
    }

    /**
     * 处理部署取消请求
     * 用户取消整个交互式部署流程
     */
    @MessageMapping("/sillytavern/deployment-cancel")
    public void handleDeploymentCancel(
            Map<String, String> request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        String reason = request.getOrDefault("reason", "用户取消部署");

        log.debug("处理部署取消请求，会话: {} 原因: {}", sessionId, reason);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("会话未找到SSH连接: {}", sessionId);
                sendErrorMessage(sessionId, "SSH连接未建立");
                return;
            }

            // 处理取消请求
            interactiveDeploymentService.cancelDeployment(sessionId, reason);
            
            // 发送响应
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "部署已取消",
                "reason", reason
            );
            messagingTemplate.convertAndSend(
                "/queue/sillytavern/deployment-cancel-user" + sessionId, 
                response);

        } catch (Exception e) {
            log.error("处理部署取消失败，会话 {}: {}", sessionId, e.getMessage(), e);
            sendErrorMessage(sessionId, "处理部署取消失败: " + e.getMessage());
        }
    }
}







