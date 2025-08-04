package com.fufu.terminal.controller;

import com.fufu.terminal.dto.sillytavern.*;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.StompSessionManager;
import com.fufu.terminal.service.sillytavern.SillyTavernService;
import com.fufu.terminal.service.sillytavern.ConfigurationService;
import com.fufu.terminal.service.sillytavern.DataManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import jakarta.validation.Valid;
import java.util.Map;

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
    private final DataManagementService dataManagementService;
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
     * Handle configuration update requests.
     */
    @MessageMapping("/sillytavern/update-config")
    public void handleUpdateConfiguration(
            @Valid ConfigurationDto request,
            SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        log.debug("Handling update configuration request for session: {} with request: {}", sessionId, request);

        try {
            SshConnection connection = sessionManager.getConnection(sessionId);
            if (connection == null) {
                log.warn("No SSH connection found for session: {}", sessionId);
                sendErrorMessage(sessionId, "SSH connection not established");
                return;
            }

            // Validate configuration
            Map<String, String> validationErrors = configurationService.validateConfiguration(request);
            if (!validationErrors.isEmpty()) {
                Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "message", "Configuration validation failed",
                    "errors", validationErrors
                );
                messagingTemplate.convertAndSend("/queue/sillytavern/config-updated-user" + sessionId, errorResponse);
                return;
            }

            // Update configuration
            String containerName = request.getContainerName() != null ? request.getContainerName() : "sillytavern";
            boolean updated = configurationService.updateConfiguration(connection, containerName, request);
            
            Map<String, Object> response = Map.of(
                "success", updated,
                "message", updated ? "Configuration updated successfully" : "Configuration update failed",
                "requiresRestart", true // SillyTavern typically requires restart for config changes
            );
            
            messagingTemplate.convertAndSend("/queue/sillytavern/config-updated-user" + sessionId, response);

        } catch (Exception e) {
            log.error("Error updating configuration for session {}: {}", sessionId, e.getMessage(), e);
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "message", "Configuration update failed",
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
}