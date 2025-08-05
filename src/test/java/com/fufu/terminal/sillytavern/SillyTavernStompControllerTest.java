package com.fufu.terminal.sillytavern;

import com.fufu.terminal.controller.SillyTavernStompController;
import com.fufu.terminal.dto.sillytavern.*;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.StompSessionManager;
import com.fufu.terminal.service.sillytavern.*;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SillyTavern STOMP控制器测试
 * 测试WebSocket消息处理和4个核心功能的集成
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SillyTavern STOMP控制器测试")
class SillyTavernStompControllerTest {

    @Mock
    private SillyTavernService sillyTavernService;
    
    @Mock
    private ConfigurationService configurationService;
    
    @Mock
    private DockerVersionService dockerVersionService;
    
    @Mock
    private RealTimeLogService realTimeLogService;
    
    @Mock
    private DataManagementService dataManagementService;
    
    @Mock
    private StompSessionManager sessionManager;
    
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    
    @Mock
    private SimpMessageHeaderAccessor headerAccessor;
    
    @Mock
    private SshConnection sshConnection;
    
    @Mock
    private Session jschSession;
    
    private SillyTavernStompController controller;
    
    private static final String TEST_SESSION_ID = "test-session-123";

    @BeforeEach
    void setUp() {
        controller = new SillyTavernStompController(
            sillyTavernService,
            configurationService,
            dockerVersionService,
            realTimeLogService,
            dataManagementService,
            sessionManager,
            messagingTemplate
        );
        
        when(headerAccessor.getSessionId()).thenReturn(TEST_SESSION_ID);
        when(sessionManager.getConnection(TEST_SESSION_ID)).thenReturn(sshConnection);
        when(sshConnection.getJschSession()).thenReturn(jschSession);
        when(jschSession.isConnected()).thenReturn(true);
    }

    // ===== 系统验证测试 =====

    @Test
    @DisplayName("应该处理系统验证请求")
    void testHandleSystemValidation() {
        // Given
        SystemInfoDto systemInfo = new SystemInfoDto();
        systemInfo.setMeetsRequirements(true);
        systemInfo.setDockerInstalled(true);
        systemInfo.setDockerRunning(true);
        systemInfo.setSufficientDiskSpace(true);
        systemInfo.setHasRootAccess(true);

        when(sillyTavernService.validateSystemRequirements(sshConnection))
                .thenReturn(systemInfo);

        // When
        controller.handleSystemValidation(headerAccessor);

        // Then
        verify(sessionManager).getConnection(TEST_SESSION_ID);
        verify(sillyTavernService).validateSystemRequirements(sshConnection);
        verify(messagingTemplate).convertAndSendToUser(
                eq(TEST_SESSION_ID),
                eq("/queue/sillytavern/system-validation"),
                any()
        );
    }

    @Test
    @DisplayName("应该处理系统验证失败的情况")
    void testHandleSystemValidationFailure() {
        // Given
        when(sillyTavernService.validateSystemRequirements(sshConnection))
                .thenThrow(new RuntimeException("Docker daemon not running"));

        // When
        controller.handleSystemValidation(headerAccessor);

        // Then
        verify(messagingTemplate).convertAndSendToUser(
                eq(TEST_SESSION_ID),
                eq("/queue/sillytavern/error"),
                argThat(message -> message.toString().contains("System validation error"))
        );
    }

    // ===== 容器状态测试 =====

    @Test
    @DisplayName("应该处理容器状态请求")
    void testHandleStatusRequest() {
        // Given
        ContainerStatusDto status = new ContainerStatusDto();
        status.setExists(true);
        status.setRunning(true);
        status.setContainerName("sillytavern");
        status.setPort(8000);
        status.setMemoryUsageMB(256L);
        status.setCpuUsagePercent(15.5);

        when(sillyTavernService.getContainerStatus(sshConnection))
                .thenReturn(status);

        // When
        controller.handleStatusRequest(headerAccessor);

        // Then
        verify(sillyTavernService).getContainerStatus(sshConnection);
        verify(messagingTemplate).convertAndSendToUser(
                eq(TEST_SESSION_ID),
                eq("/queue/sillytavern/status"),
                eq(status)
        );
    }

    // ===== 部署测试 =====

    @Test
    @DisplayName("应该处理部署请求")
    void testHandleDeployment() {
        // Given
        DeploymentRequestDto request = new DeploymentRequestDto();
        request.setContainerName("test-sillytavern");
        request.setDockerImage("ghcr.io/sillytavern/sillytavern:latest");
        request.setPort(8000);
        request.setDataPath("/opt/sillytavern-data");

        when(sillyTavernService.deployContainer(eq(sshConnection), eq(request), any()))
                .thenReturn(CompletableFuture.runAsync(() -> {
                    // 模拟部署进度
                }));

        // When
        controller.handleDeployment(request, headerAccessor);

        // Then
        verify(sillyTavernService).deployContainer(eq(sshConnection), eq(request), any());
    }

    // ===== 服务控制操作测试 =====

    @Test
    @DisplayName("应该处理启动容器请求")
    void testHandleStartContainer() throws Exception {
        // Given
        ServiceActionDto request = new ServiceActionDto();
        request.setAction("start");

        doNothing().when(sillyTavernService).startContainer(sshConnection);

        // When
        controller.handleServiceAction(request, headerAccessor);

        // Then
        verify(sillyTavernService).startContainer(sshConnection);
        verify(messagingTemplate).convertAndSendToUser(
                eq(TEST_SESSION_ID),
                eq("/queue/sillytavern/action-result"),
                argThat(result -> result.toString().contains("Container started successfully"))
        );
    }

    @Test
    @DisplayName("应该处理停止容器请求")
    void testHandleStopContainer() throws Exception {
        // Given
        ServiceActionDto request = new ServiceActionDto();
        request.setAction("stop");

        doNothing().when(sillyTavernService).stopContainer(sshConnection);

        // When
        controller.handleServiceAction(request, headerAccessor);

        // Then
        verify(sillyTavernService).stopContainer(sshConnection);
        verify(messagingTemplate).convertAndSendToUser(
                eq(TEST_SESSION_ID),
                eq("/queue/sillytavern/action-result"),
                argThat(result -> result.toString().contains("Container stopped successfully"))
        );
    }

    // ===== 错误处理测试 =====

    @Test
    @DisplayName("应该处理SSH连接不存在的情况")
    void testHandleNoSshConnection() {
        // Given
        when(sessionManager.getConnection(TEST_SESSION_ID)).thenReturn(null);

        // When
        controller.handleStatusRequest(headerAccessor);

        // Then
        verify(messagingTemplate).convertAndSendToUser(
                eq(TEST_SESSION_ID),
                eq("/queue/sillytavern/error"),
                argThat(message -> message.toString().contains("SSH connection not established"))
        );
    }

    @Test
    @DisplayName("应该处理服务异常")
    void testHandleServiceException() {
        // Given
        when(sillyTavernService.getContainerStatus(sshConnection))
                .thenThrow(new RuntimeException("Docker daemon is not running"));

        // When
        controller.handleStatusRequest(headerAccessor);

        // Then
        verify(messagingTemplate).convertAndSendToUser(
                eq(TEST_SESSION_ID),
                eq("/queue/sillytavern/error"),
                argThat(message -> message.toString().contains("Status check error"))
        );
    }

    // ===== 配置管理WebSocket消息处理测试 =====

    @Test
    @DisplayName("应该处理配置获取请求并返回完整配置信息")
    void testHandleGetConfiguration() {
        // Given
        ConfigurationDto expectedConfig = new ConfigurationDto();
        expectedConfig.setContainerName("sillytavern");
        expectedConfig.setUsername("admin_user");
        expectedConfig.setHasPassword(true);
        expectedConfig.setPort(8000);
        expectedConfig.setServerName("My SillyTavern");
        expectedConfig.setEnableExtensions(true);
        expectedConfig.setTheme("dark");
        expectedConfig.setLanguage("zh-CN");
        expectedConfig.setAutoConnect(false);

        when(configurationService.readConfiguration(sshConnection, "sillytavern"))
                .thenReturn(expectedConfig);

        // When
        controller.handleGetConfiguration(headerAccessor);

        // Then
        verify(configurationService).readConfiguration(sshConnection, "sillytavern");
        verify(messagingTemplate).convertAndSend(
                eq("/queue/sillytavern/config-user" + TEST_SESSION_ID),
                argThat(message -> {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> msg = (java.util.Map<String, Object>) message;
                    return msg.get("success").equals(true) && 
                           msg.get("type").equals("config");
                })
        );
    }

    @Test
    @DisplayName("应该处理配置更新请求并执行自动重启")
    void testHandleUpdateConfigurationWithRestart() {
        // Given
        ConfigurationDto updateRequest = new ConfigurationDto();
        updateRequest.setContainerName("sillytavern");
        updateRequest.setUsername("new_admin");
        updateRequest.setPassword("strongPassword123!");
        updateRequest.setPort(8080);
        updateRequest.setTheme("light");

        // 验证配置成功
        when(configurationService.validateConfiguration(updateRequest))
                .thenReturn(new java.util.HashMap<>());

        // 更新配置成功并自动重启
        when(configurationService.updateConfigurationWithRestart(sshConnection, "sillytavern", updateRequest))
                .thenReturn(true);

        // When
        controller.handleUpdateConfiguration(updateRequest, headerAccessor);

        // Then
        verify(configurationService).validateConfiguration(updateRequest);
        verify(configurationService).updateConfigurationWithRestart(sshConnection, "sillytavern", updateRequest);
        verify(messagingTemplate).convertAndSend(
                eq("/queue/sillytavern/config-updated-user" + TEST_SESSION_ID),
                argThat(response -> {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> resp = (java.util.Map<String, Object>) response;
                    return resp.get("success").equals(true) &&
                           resp.get("message").toString().contains("配置更新成功") &&
                           resp.get("requiresRestart").equals(false); // 已经自动重启
                })
        );
    }

    @Test
    @DisplayName("应该处理配置验证失败的情况")
    void testHandleUpdateConfigurationValidationFailure() {
        // Given
        ConfigurationDto invalidRequest = new ConfigurationDto();
        invalidRequest.setUsername("admin123"); // 包含数字的无效用户名
        invalidRequest.setPassword("weak"); // 弱密码

        java.util.Map<String, String> validationErrors = new java.util.HashMap<>();
        validationErrors.put("username", "用户名不能包含数字或特殊字符");
        validationErrors.put("password", "密码强度不足，至少需要6个字符");

        when(configurationService.validateConfiguration(invalidRequest))
                .thenReturn(validationErrors);

        // When
        controller.handleUpdateConfiguration(invalidRequest, headerAccessor);

        // Then
        verify(configurationService).validateConfiguration(invalidRequest);
        verify(configurationService, never()).updateConfigurationWithRestart(any(), any(), any());
        verify(messagingTemplate).convertAndSend(
                eq("/queue/sillytavern/config-updated-user" + TEST_SESSION_ID),
                argThat(response -> {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> resp = (java.util.Map<String, Object>) response;
                    return resp.get("success").equals(false) &&
                           resp.get("message").toString().contains("配置验证失败");
                })
        );
    }

    // ===== 实时日志WebSocket消息处理测试 =====

    @Test
    @DisplayName("应该处理开始实时日志流请求")
    void testHandleStartRealtimeLogs() {
        // Given
        java.util.Map<String, Object> request = new java.util.HashMap<>();
        request.put("containerName", "sillytavern");
        request.put("maxLines", 1000);

        doNothing().when(realTimeLogService).startLogStream(TEST_SESSION_ID, "sillytavern", 1000);

        // When
        controller.handleStartRealtimeLogs(request, headerAccessor);

        // Then
        verify(realTimeLogService).startLogStream(TEST_SESSION_ID, "sillytavern", 1000);
        verify(messagingTemplate).convertAndSend(
                eq("/queue/sillytavern/realtime-logs-started-user" + TEST_SESSION_ID),
                argThat(message -> {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> msg = (java.util.Map<String, Object>) message;
                    return msg.get("success").equals(true) && 
                           msg.get("type").equals("realtime-logs-started");
                })
        );
    }

    @Test
    @DisplayName("应该处理停止实时日志流请求")
    void testHandleStopRealtimeLogs() {
        // Given
        doNothing().when(realTimeLogService).stopLogStream(TEST_SESSION_ID);

        // When
        controller.handleStopRealtimeLogs(headerAccessor);

        // Then
        verify(realTimeLogService).stopLogStream(TEST_SESSION_ID);
        verify(messagingTemplate).convertAndSend(
                eq("/queue/sillytavern/realtime-logs-stopped-user" + TEST_SESSION_ID),
                argThat(message -> {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> msg = (java.util.Map<String, Object>) message;
                    return msg.get("success").equals(true) && 
                           msg.get("type").equals("realtime-logs-stopped");
                })
        );
    }

    @Test
    @DisplayName("应该处理历史日志获取请求")
    void testHandleGetHistoryLogs() {
        // Given
        java.util.Map<String, Object> request = new java.util.HashMap<>();
        request.put("containerName", "sillytavern");
        request.put("lines", 500);
        request.put("level", "all");

        RealTimeLogDto expectedLogs = new RealTimeLogDto();
        expectedLogs.setContainerName("sillytavern");
        expectedLogs.setLines(List.of(
                "[INFO] Application started",
                "[INFO] Server listening on port 8000",
                "[WARN] High memory usage detected"
        ));
        expectedLogs.setTotalLines(500);
        expectedLogs.setLogLevel("all");
        expectedLogs.setTimestamp(LocalDateTime.now());

        when(realTimeLogService.getHistoryLogs(sshConnection, "sillytavern", 500, "all"))
                .thenReturn(expectedLogs);

        // When
        controller.handleGetHistoryLogs(request, headerAccessor);

        // Then
        verify(realTimeLogService).getHistoryLogs(sshConnection, "sillytavern", 500, "all");
        verify(messagingTemplate).convertAndSend(
                eq("/queue/sillytavern/history-logs-user" + TEST_SESSION_ID),
                argThat(message -> {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> msg = (java.util.Map<String, Object>) message;
                    return msg.get("success").equals(true) && 
                           msg.get("type").equals("history-logs");
                })
        );
    }

    @Test
    @DisplayName("应该验证日志行数参数")
    void testHandleGetHistoryLogsInvalidLineCount() {
        // Given - 无效的行数（超出范围）
        java.util.Map<String, Object> request = new java.util.HashMap<>();
        request.put("containerName", "sillytavern");
        request.put("lines", 5000); // 超过最大值3000
        request.put("level", "all");

        // When
        controller.handleGetHistoryLogs(request, headerAccessor);

        // Then
        verify(realTimeLogService, never()).getHistoryLogs(any(), any(), anyInt(), any());
        verify(sessionManager).sendErrorMessage(TEST_SESSION_ID, "日志行数必须在1-3000之间");
    }

    // ===== 数据管理WebSocket消息处理测试 =====

    @Test
    @DisplayName("应该处理数据导出请求并提供进度更新")
    void testHandleDataExportWithProgress() {
        // Given
        DataExportDto expectedExport = new DataExportDto();
        expectedExport.setContainerName("sillytavern");
        expectedExport.setFileName("sillytavern_export_20240804.zip");
        expectedExport.setSizeBytes(10 * 1024 * 1024L); // 10MB
        expectedExport.setDownloadUrl("/download/temp/sillytavern_export_20240804.zip");

        when(dataManagementService.exportData(eq(sshConnection), eq("sillytavern"), any()))
                .thenAnswer(invocation -> {
                    Consumer<String> progressCallback = invocation.getArgument(2);
                    return CompletableFuture.supplyAsync(() -> {
                        // 模拟导出进度
                        progressCallback.accept("正在压缩数据目录...");
                        progressCallback.accept("正在创建ZIP文件...");
                        progressCallback.accept("导出完成");
                        return expectedExport;
                    });
                });

        // When
        controller.handleDataExport(headerAccessor);

        // Then
        verify(dataManagementService).exportData(eq(sshConnection), eq("sillytavern"), any());
        // 验证进度消息会被发送
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
                eq("/queue/sillytavern/export-progress-user" + TEST_SESSION_ID),
                any()
        );
    }

    @Test
    @DisplayName("应该处理数据导入请求并处理文件验证")
    void testHandleDataImportWithValidation() {
        // Given
        java.util.Map<String, String> request = new java.util.HashMap<>();
        request.put("uploadedFileName", "valid_backup.zip");

        when(dataManagementService.importData(eq(sshConnection), eq("sillytavern"), eq("valid_backup.zip"), any()))
                .thenAnswer(invocation -> {
                    Consumer<String> progressCallback = invocation.getArgument(3);
                    return CompletableFuture.runAsync(() -> {
                        // 模拟导入进度
                        progressCallback.accept("正在验证ZIP文件结构...");
                        progressCallback.accept("正在创建备份...");
                        progressCallback.accept("正在解压数据文件...");
                        progressCallback.accept("数据导入完成");
                    });
                });

        // When
        controller.handleDataImport(request, headerAccessor);

        // Then
        verify(dataManagementService).importData(eq(sshConnection), eq("sillytavern"), eq("valid_backup.zip"), any());
        // 验证进度消息会被发送
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
                eq("/queue/sillytavern/import-progress-user" + TEST_SESSION_ID),
                any()
        );
    }

    @Test
    @DisplayName("应该处理数据导入失败和回滚")
    void testHandleDataImportFailureWithRollback() {
        // Given
        java.util.Map<String, String> request = new java.util.HashMap<>();
        request.put("uploadedFileName", "corrupted_backup.zip");

        when(dataManagementService.importData(eq(sshConnection), eq("sillytavern"), eq("corrupted_backup.zip"), any()))
                .thenAnswer(invocation -> {
                    Consumer<String> progressCallback = invocation.getArgument(3);
                    return CompletableFuture.supplyAsync(() -> {
                        progressCallback.accept("正在验证ZIP文件结构...");
                        progressCallback.accept("检测到文件损坏，开始回滚...");
                        progressCallback.accept("回滚完成");
                        throw new RuntimeException("导入失败，已回滚到原始状态");
                    });
                });

        // When
        controller.handleDataImport(request, headerAccessor);

        // Then
        verify(dataManagementService).importData(eq(sshConnection), eq("sillytavern"), eq("corrupted_backup.zip"), any());
        // 验证失败消息会被发送
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
                eq("/queue/sillytavern/import-user" + TEST_SESSION_ID),
                argThat(response -> {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> resp = (java.util.Map<String, Object>) response;
                    return resp.get("success").equals(false);
                })
        );
    }

    // ===== 版本管理WebSocket消息处理测试 =====

    @Test
    @DisplayName("应该处理版本信息查询请求")
    void testHandleGetVersionInfo() {
        // Given
        VersionInfoDto expectedVersionInfo = new VersionInfoDto();
        expectedVersionInfo.setContainerName("sillytavern");
        expectedVersionInfo.setCurrentVersion("1.12.0");
        expectedVersionInfo.setLatestVersion("1.12.2");
        expectedVersionInfo.setHasUpdate(true);
        expectedVersionInfo.setAvailableVersions(List.of("1.12.2", "1.12.1", "1.12.0"));
        expectedVersionInfo.setLastChecked(LocalDateTime.now());

        when(dockerVersionService.getVersionInfo(sshConnection, "sillytavern"))
                .thenReturn(expectedVersionInfo);

        // When
        controller.handleGetVersionInfo(headerAccessor);

        // Then
        verify(dockerVersionService).getVersionInfo(sshConnection, "sillytavern");
        verify(messagingTemplate).convertAndSend(
                eq("/queue/sillytavern/version-info-user" + TEST_SESSION_ID),
                argThat(message -> {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> msg = (java.util.Map<String, Object>) message;
                    return msg.get("success").equals(true) && 
                           msg.get("type").equals("version-info");
                })
        );
    }

    @Test
    @DisplayName("应该处理版本升级请求")
    void testHandleUpgradeVersion() {
        // Given
        java.util.Map<String, String> request = new java.util.HashMap<>();
        request.put("targetVersion", "1.12.2");
        request.put("containerName", "sillytavern");

        when(dockerVersionService.upgradeToVersion(eq(sshConnection), eq("sillytavern"), eq("1.12.2"), any()))
                .thenAnswer(invocation -> {
                    Consumer<String> progressCallback = invocation.getArgument(3);
                    return CompletableFuture.runAsync(() -> {
                        // 模拟升级进度
                        progressCallback.accept("正在停止容器...");
                        progressCallback.accept("正在拉取新版本镜像...");
                        progressCallback.accept("正在创建新容器...");
                        progressCallback.accept("版本升级完成");
                    });
                });

        // When
        controller.handleUpgradeVersion(request, headerAccessor);

        // Then
        verify(dockerVersionService).upgradeToVersion(eq(sshConnection), eq("sillytavern"), eq("1.12.2"), any());
        // 验证进度消息会被发送
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
                eq("/queue/sillytavern/version-upgrade-progress-user" + TEST_SESSION_ID),
                any()
        );
    }

    @Test
    @DisplayName("应该处理镜像清理请求")
    void testHandleCleanupImages() {
        // Given
        doNothing().when(dockerVersionService).cleanupUnusedImages(sshConnection);

        // When
        controller.handleCleanupImages(headerAccessor);

        // Then
        verify(dockerVersionService).cleanupUnusedImages(sshConnection);
        verify(messagingTemplate).convertAndSend(
                eq("/queue/sillytavern/cleanup-images-user" + TEST_SESSION_ID),
                argThat(response -> {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> resp = (java.util.Map<String, Object>) response;
                    return resp.get("success").equals(true) &&
                           resp.get("message").toString().contains("镜像清理完成");
                })
        );
    }

    @Test
    @DisplayName("应该处理版本升级失败的情况")
    void testHandleUpgradeVersionFailure() {
        // Given
        java.util.Map<String, String> request = new java.util.HashMap<>();
        request.put("targetVersion", "invalid-version");
        request.put("containerName", "sillytavern");

        when(dockerVersionService.upgradeToVersion(eq(sshConnection), eq("sillytavern"), eq("invalid-version"), any()))
                .thenAnswer(invocation -> {
                    Consumer<String> progressCallback = invocation.getArgument(3);
                    return CompletableFuture.supplyAsync(() -> {
                        progressCallback.accept("正在验证目标版本...");
                        progressCallback.accept("版本验证失败");
                        throw new RuntimeException("版本升级失败：无效的版本号");
                    });
                });

        // When
        controller.handleUpgradeVersion(request, headerAccessor);

        // Then
        verify(dockerVersionService).upgradeToVersion(eq(sshConnection), eq("sillytavern"), eq("invalid-version"), any());
        // 验证失败消息会被发送
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
                eq("/queue/sillytavern/version-upgrade-user" + TEST_SESSION_ID),
                argThat(response -> {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> resp = (java.util.Map<String, Object>) response;
                    return resp.get("success").equals(false);
                })
        );
    }
}