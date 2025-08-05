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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SillyTavern端到端工作流集成测试
 * 测试完整用户工作流程，包括部署、管理和数据操作
 */
@ExtendWith(MockitoExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("SillyTavern端到端工作流集成测试")
class SillyTavernWorkflowIntegrationTest {

    @Mock
    private SillyTavernService sillyTavernService;
    
    @Mock
    private ConfigurationService configurationService;
    
    @Mock
    private RealTimeLogService realTimeLogService;
    
    @Mock
    private DataManagementService dataManagementService;
    
    @Mock
    private DockerVersionService dockerVersionService;

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
    
    private static final String TEST_SESSION_ID = "workflow-test-session";

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

    // ===== 完整部署工作流测试 =====

    @Test
    @DisplayName("应该完成完整的SillyTavern部署工作流")
    @Timeout(30)
    void testCompleteDeploymentWorkflow() throws Exception {
        // 阶段1：系统验证
        SystemInfoDto validSystem = new SystemInfoDto();
        validSystem.setMeetsRequirements(true);
        validSystem.setDockerInstalled(true);
        validSystem.setDockerRunning(true);
        validSystem.setSufficientDiskSpace(true);
        validSystem.setHasRootAccess(true);
        validSystem.setHasInternetAccess(true);
        validSystem.setAvailableDiskSpaceMB(10000L);

        when(sillyTavernService.validateSystemRequirements(sshConnection))
                .thenReturn(validSystem);

        // 阶段2：容器状态检查（初始不存在）
        ContainerStatusDto nonExistentStatus = new ContainerStatusDto();
        nonExistentStatus.setExists(false);
        nonExistentStatus.setRunning(false);

        ContainerStatusDto deployedStatus = new ContainerStatusDto();
        deployedStatus.setExists(true);
        deployedStatus.setRunning(true);
        deployedStatus.setContainerName("sillytavern");
        deployedStatus.setPort(8000);
        deployedStatus.setMemoryUsageMB(256L);
        deployedStatus.setCpuUsagePercent(15.5);

        when(sillyTavernService.getContainerStatus(sshConnection))
                .thenReturn(nonExistentStatus)  // 初始检查
                .thenReturn(deployedStatus);    // 部署后

        // 阶段3：部署请求
        DeploymentRequestDto deploymentRequest = new DeploymentRequestDto();
        deploymentRequest.setContainerName("sillytavern");
        deploymentRequest.setDockerImage("ghcr.io/sillytavern/sillytavern:latest");
        deploymentRequest.setPort(8000);
        deploymentRequest.setDataPath("/opt/sillytavern-data");

        when(sillyTavernService.deployContainer(eq(sshConnection), eq(deploymentRequest), any()))
                .thenAnswer(invocation -> {
                    Consumer<DeploymentProgressDto> callback = invocation.getArgument(2);
                    
                    // 模拟部署进度
                    DeploymentProgressDto progress1 = new DeploymentProgressDto();
                    progress1.setStage("validation");
                    progress1.setProgress(10);
                    progress1.setMessage("验证系统要求...");
                    callback.accept(progress1);
                    
                    DeploymentProgressDto progress2 = new DeploymentProgressDto();
                    progress2.setStage("pull-image");
                    progress2.setProgress(50);
                    progress2.setMessage("拉取Docker镜像...");
                    callback.accept(progress2);
                    
                    DeploymentProgressDto progress3 = new DeploymentProgressDto();
                    progress3.setStage("create-container");
                    progress3.setProgress(80);
                    progress3.setMessage("创建容器...");
                    callback.accept(progress3);
                    
                    DeploymentProgressDto completed = new DeploymentProgressDto();
                    completed.setStage("completed");
                    completed.setProgress(100);
                    completed.setMessage("部署完成！");
                    callback.accept(completed);
                    
                    return CompletableFuture.completedFuture(null);
                });

        // 执行工作流

        // 1. 验证系统
        controller.handleSystemValidation(headerAccessor);
        verify(sillyTavernService).validateSystemRequirements(sshConnection);

        // 2. 检查初始状态
        controller.handleStatusRequest(headerAccessor);

        // 3. 部署容器
        controller.handleDeployment(deploymentRequest, headerAccessor);
        verify(sillyTavernService).deployContainer(eq(sshConnection), eq(deploymentRequest), any());

        // 4. 检查最终状态
        controller.handleStatusRequest(headerAccessor);

        // 验证所有消息模板调用
        verify(messagingTemplate, atLeast(4)).convertAndSendToUser(anyString(), anyString(), any());
    }

    // ===== 配置管理工作流测试 =====

    @Test
    @DisplayName("应该完成配置管理工作流")
    @Timeout(20)
    void testConfigurationManagementWorkflow() throws Exception {
        // 1. 读取当前配置
        ConfigurationDto currentConfig = new ConfigurationDto();
        currentConfig.setContainerName("sillytavern");
        currentConfig.setUsername("admin");
        currentConfig.setHasPassword(true);
        currentConfig.setPort(8000);

        when(configurationService.readConfiguration(sshConnection, "sillytavern"))
                .thenReturn(currentConfig);

        controller.handleGetConfiguration(headerAccessor);
        verify(configurationService).readConfiguration(sshConnection, "sillytavern");

        // 2. 验证配置更新
        ConfigurationDto updateRequest = new ConfigurationDto();
        updateRequest.setContainerName("sillytavern");
        updateRequest.setUsername("newadmin");
        updateRequest.setPassword("strongpassword123");
        updateRequest.setPort(8001);

        when(configurationService.validateConfiguration(updateRequest))
                .thenReturn(Map.of()); // 无验证错误

        when(configurationService.updateConfigurationWithRestart(sshConnection, "sillytavern", updateRequest))
                .thenReturn(true);

        controller.handleUpdateConfiguration(updateRequest, headerAccessor);
        
        verify(configurationService).validateConfiguration(updateRequest);
        verify(configurationService).updateConfigurationWithRestart(sshConnection, "sillytavern", updateRequest);

        // 3. 验证配置已更新
        ConfigurationDto updatedConfig = new ConfigurationDto();
        updatedConfig.setContainerName("sillytavern");
        updatedConfig.setUsername("newadmin");
        updatedConfig.setHasPassword(true);
        updatedConfig.setPort(8001);

        when(configurationService.readConfiguration(sshConnection, "sillytavern"))
                .thenReturn(updatedConfig);

        controller.handleGetConfiguration(headerAccessor);

        // 验证配置管理消息
        verify(messagingTemplate, times(2)).convertAndSendToUser(
                eq(TEST_SESSION_ID), contains("config"), any());
        verify(messagingTemplate, times(1)).convertAndSend(
                contains("config-updated-user"), any());
    }

    // ===== 数据管理工作流测试 =====

    @Test
    @DisplayName("应该完成数据管理工作流")
    @Timeout(60)
    void testDataManagementWorkflow() throws Exception {
        // 1. 数据导出
        DataExportDto exportResult = new DataExportDto();
        exportResult.setContainerName("sillytavern");
        exportResult.setFileName("sillytavern-export-20240101.zip");
        exportResult.setSizeBytes(5120000L);
        exportResult.setDownloadUrl("/download/temp/sillytavern-export-20240101.zip");

        when(dataManagementService.exportData(eq(sshConnection), eq("sillytavern"), any()))
                .thenAnswer(invocation -> {
                    Consumer<String> callback = invocation.getArgument(2);
                    callback.accept("创建备份目录...");
                    callback.accept("复制数据文件...");
                    callback.accept("压缩归档文件...");
                    callback.accept("导出完成");
                    return CompletableFuture.completedFuture(exportResult);
                });

        controller.handleDataExport(headerAccessor);
        verify(dataManagementService).exportData(eq(sshConnection), eq("sillytavern"), any());

        // 2. 数据导入
        Map<String, String> importRequest = Map.of("uploadedFileName", "sillytavern-backup.zip");

        when(dataManagementService.importData(eq(sshConnection), eq("sillytavern"), 
                eq("sillytavern-backup.zip"), any()))
                .thenAnswer(invocation -> {
                    Consumer<String> callback = invocation.getArgument(3);
                    callback.accept("验证备份文件...");
                    callback.accept("停止容器...");
                    callback.accept("提取备份文件...");
                    callback.accept("恢复数据文件...");
                    callback.accept("启动容器...");
                    callback.accept("导入完成");
                    return CompletableFuture.completedFuture(null);
                });

        controller.handleDataImport(importRequest, headerAccessor);
        verify(dataManagementService).importData(eq(sshConnection), eq("sillytavern"),
                eq("sillytavern-backup.zip"), any());

        // 验证数据管理消息
        verify(messagingTemplate, atLeast(2)).convertAndSend(contains("progress-user"), any());
    }

    // ===== 版本管理工作流测试 =====

    @Test
    @DisplayName("应该完成版本管理工作流")
    @Timeout(45)
    void testVersionManagementWorkflow() throws Exception {
        // 1. 获取版本信息
        VersionInfoDto versionInfo = new VersionInfoDto();
        versionInfo.setContainerName("sillytavern");
        versionInfo.setCurrentVersion("1.12.0");
        versionInfo.setLatestVersion("1.12.2");
        versionInfo.setAvailableVersions(List.of("1.12.2", "1.12.1", "1.12.0"));
        versionInfo.setHasUpdate(true);
        versionInfo.setLastChecked(LocalDateTime.now());

        when(dockerVersionService.getVersionInfo(sshConnection, "sillytavern"))
                .thenReturn(versionInfo);

        controller.handleGetVersionInfo(headerAccessor);
        verify(dockerVersionService).getVersionInfo(sshConnection, "sillytavern");

        // 2. 版本升级
        Map<String, String> upgradeRequest = Map.of(
                "targetVersion", "1.12.2",
                "containerName", "sillytavern"
        );

        when(dockerVersionService.upgradeToVersion(eq(sshConnection), eq("sillytavern"), 
                eq("1.12.2"), any()))
                .thenAnswer(invocation -> {
                    Consumer<String> callback = invocation.getArgument(3);
                    callback.accept("停止容器...");
                    callback.accept("拉取新版本镜像...");
                    callback.accept("更新容器配置...");
                    callback.accept("启动新版本容器...");
                    callback.accept("清理旧镜像...");
                    callback.accept("版本升级完成");
                    return CompletableFuture.completedFuture(null);
                });

        controller.handleUpgradeVersion(upgradeRequest, headerAccessor);
        verify(dockerVersionService).upgradeToVersion(eq(sshConnection), eq("sillytavern"), 
                eq("1.12.2"), any());

        // 3. 清理未使用的镜像
        doNothing().when(dockerVersionService).cleanupUnusedImages(sshConnection);

        controller.handleCleanupImages(headerAccessor);
        verify(dockerVersionService).cleanupUnusedImages(sshConnection);

        // 验证版本管理消息
        verify(messagingTemplate).convertAndSendToUser(
                eq(TEST_SESSION_ID), contains("version-info"), any());
        verify(messagingTemplate, atLeast(1)).convertAndSend(
                contains("version-upgrade-progress-user"), any());
        verify(messagingTemplate, atLeast(1)).convertAndSend(
                contains("cleanup-images-user"), any());
    }

    // ===== 实时日志监控工作流测试 =====

    @Test
    @DisplayName("应该完成实时日志监控工作流")
    @Timeout(30)
    void testRealTimeLogMonitoringWorkflow() throws Exception {
        // 1. 开始实时日志流
        Map<String, Object> startLogRequest = Map.of(
                "containerName", "sillytavern",
                "maxLines", 1000
        );

        doNothing().when(realTimeLogService).startLogStream(TEST_SESSION_ID, "sillytavern", 1000);

        controller.handleStartRealtimeLogs(startLogRequest, headerAccessor);
        verify(realTimeLogService).startLogStream(TEST_SESSION_ID, "sillytavern", 1000);

        // 2. 获取历史日志
        Map<String, Object> historyLogRequest = Map.of(
                "containerName", "sillytavern",
                "lines", 500,
                "level", "all"
        );

        RealTimeLogDto historyLogs = new RealTimeLogDto();
        historyLogs.setContainerName("sillytavern");
        historyLogs.setLines(List.of(
                "2024-01-01 10:00:01 [INFO] SillyTavern启动中...",
                "2024-01-01 10:00:02 [INFO] 加载配置文件...",
                "2024-01-01 10:00:03 [INFO] 数据库连接成功",
                "2024-01-01 10:00:04 [INFO] 服务器监听端口 8000",
                "2024-01-01 10:00:05 [INFO] 准备接受连接"
        ));
        historyLogs.setTotalLines(500);
        historyLogs.setMaxLines(1000);

        when(realTimeLogService.getHistoryLogs(sshConnection, "sillytavern", 500, "all"))
                .thenReturn(historyLogs);

        controller.handleGetHistoryLogs(historyLogRequest, headerAccessor);
        verify(realTimeLogService).getHistoryLogs(sshConnection, "sillytavern", 500, "all");

        // 3. 停止实时日志流
        doNothing().when(realTimeLogService).stopLogStream(TEST_SESSION_ID);

        controller.handleStopRealtimeLogs(headerAccessor);
        verify(realTimeLogService).stopLogStream(TEST_SESSION_ID);

        // 验证日志监控消息
        verify(messagingTemplate).convertAndSendToUser(
                eq(TEST_SESSION_ID), eq("/queue/sillytavern/realtime-logs-started"), any());
        verify(messagingTemplate).convertAndSendToUser(
                eq(TEST_SESSION_ID), eq("/queue/sillytavern/history-logs"), any());
        verify(messagingTemplate).convertAndSendToUser(
                eq(TEST_SESSION_ID), eq("/queue/sillytavern/realtime-logs-stopped"), any());
    }

    // ===== 完整维护工作流测试 =====

    @Test
    @DisplayName("应该完成完整维护工作流")
    @Timeout(60)
    void testCompleteMaintenanceWorkflow() throws Exception {
        // 1. 容器状态检查
        ContainerStatusDto runningStatus = new ContainerStatusDto();
        runningStatus.setExists(true);
        runningStatus.setRunning(true);
        runningStatus.setContainerName("sillytavern");
        runningStatus.setMemoryUsageMB(512L);
        runningStatus.setCpuUsagePercent(25.0);

        when(sillyTavernService.getContainerStatus(sshConnection))
                .thenReturn(runningStatus);

        controller.handleStatusRequest(headerAccessor);

        // 2. 配置备份和更新
        ConfigurationDto config = new ConfigurationDto();
        config.setContainerName("sillytavern");
        config.setUsername("admin");
        config.setPassword("updatedpassword123");

        when(configurationService.validateConfiguration(config))
                .thenReturn(Map.of());
        when(configurationService.updateConfigurationWithRestart(sshConnection, "sillytavern", config))
                .thenReturn(true);

        controller.handleUpdateConfiguration(config, headerAccessor);

        // 3. 数据备份
        DataExportDto backupResult = new DataExportDto();
        backupResult.setFileName("maintenance-backup.zip");
        backupResult.setSizeBytes(2048000L);

        when(dataManagementService.exportData(eq(sshConnection), eq("sillytavern"), any()))
                .thenReturn(CompletableFuture.completedFuture(backupResult));

        controller.handleDataExport(headerAccessor);

        // 4. 版本检查和升级
        VersionInfoDto versionInfo = new VersionInfoDto();
        versionInfo.setHasUpdate(true);
        versionInfo.setLatestVersion("1.12.3");

        when(dockerVersionService.getVersionInfo(sshConnection, "sillytavern"))
                .thenReturn(versionInfo);

        controller.handleGetVersionInfo(headerAccessor);

        Map<String, String> upgradeRequest = Map.of("targetVersion", "1.12.3");
        when(dockerVersionService.upgradeToVersion(eq(sshConnection), eq("sillytavern"), 
                eq("1.12.3"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        controller.handleUpgradeVersion(upgradeRequest, headerAccessor);

        // 5. 清理和验证
        doNothing().when(dockerVersionService).cleanupUnusedImages(sshConnection);
        controller.handleCleanupImages(headerAccessor);

        ContainerStatusDto finalStatus = new ContainerStatusDto();
        finalStatus.setExists(true);
        finalStatus.setRunning(true);
        finalStatus.setContainerName("sillytavern");

        when(sillyTavernService.getContainerStatus(sshConnection))
                .thenReturn(finalStatus);

        controller.handleStatusRequest(headerAccessor);

        // 验证完整维护工作流
        verify(sillyTavernService, times(2)).getContainerStatus(sshConnection);
        verify(configurationService).updateConfigurationWithRestart(sshConnection, "sillytavern", config);
        verify(dataManagementService).exportData(eq(sshConnection), eq("sillytavern"), any());
        verify(dockerVersionService).upgradeToVersion(eq(sshConnection), eq("sillytavern"), eq("1.12.3"), any());
        verify(dockerVersionService).cleanupUnusedImages(sshConnection);

        // 验证发送了所有相关消息
        verify(messagingTemplate, atLeast(6)).convertAndSendToUser(anyString(), anyString(), any());
    }

    // ===== 错误检测和恢复工作流测试 =====

    @Test
    @DisplayName("应该处理错误检测和恢复工作流")
    @Timeout(45)
    void testErrorDetectionAndRecoveryWorkflow() throws Exception {
        // 1. 初始系统验证失败
        SystemInfoDto invalidSystem = new SystemInfoDto();
        invalidSystem.setMeetsRequirements(false);
        invalidSystem.setDockerInstalled(false);
        invalidSystem.setSufficientDiskSpace(true);
        invalidSystem.setHasRootAccess(true);
        invalidSystem.setRequirementChecks(List.of("Docker未安装"));

        when(sillyTavernService.validateSystemRequirements(sshConnection))
                .thenReturn(invalidSystem);

        controller.handleSystemValidation(headerAccessor);

        // 2. 尝试部署（应该失败）
        DeploymentRequestDto deploymentRequest = new DeploymentRequestDto();
        deploymentRequest.setContainerName("sillytavern");
        deploymentRequest.setDockerImage("ghcr.io/sillytavern/sillytavern:latest");

        when(sillyTavernService.deployContainer(eq(sshConnection), eq(deploymentRequest), any()))
                .thenThrow(new RuntimeException("部署失败：系统要求不满足"));

        // 验证部署失败被正确处理
        controller.handleDeployment(deploymentRequest, headerAccessor);

        // 3. 系统恢复（Docker安装完成）
        SystemInfoDto validSystem = new SystemInfoDto();
        validSystem.setMeetsRequirements(true);
        validSystem.setDockerInstalled(true);
        validSystem.setDockerRunning(true);
        validSystem.setSufficientDiskSpace(true);
        validSystem.setHasRootAccess(true);

        when(sillyTavernService.validateSystemRequirements(sshConnection))
                .thenReturn(validSystem);

        controller.handleSystemValidation(headerAccessor);

        // 4. 重试部署（应该成功）
        when(sillyTavernService.deployContainer(eq(sshConnection), eq(deploymentRequest), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        controller.handleDeployment(deploymentRequest, headerAccessor);

        // 验证错误恢复工作流
        verify(sillyTavernService, times(2)).validateSystemRequirements(sshConnection);
        verify(sillyTavernService, times(2)).deployContainer(eq(sshConnection), eq(deploymentRequest), any());

        // 应该有错误和成功消息
        verify(messagingTemplate, atLeast(4)).convertAndSendToUser(anyString(), anyString(), any());
    }

    // ===== 并发用户操作测试 =====

    @Test
    @DisplayName("应该正确处理并发用户操作")
    @Timeout(30)
    void testConcurrentUserOperations() throws Exception {
        final int CONCURRENT_USERS = 5;
        final CountDownLatch completionLatch = new CountDownLatch(CONCURRENT_USERS);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);

        try {
            for (int i = 0; i < CONCURRENT_USERS; i++) {
                final int userId = i;
                final String sessionId = "concurrent-session-" + userId;

                executor.submit(() -> {
                    try {
                        // 模拟不同用户的独立会话
                        SimpMessageHeaderAccessor userAccessor = mock(SimpMessageHeaderAccessor.class);
                        SshConnection userConnection = mock(SshConnection.class);

                        when(userAccessor.getSessionId()).thenReturn(sessionId);
                        when(sessionManager.getConnection(sessionId)).thenReturn(userConnection);

                        // 模拟不同的容器状态
                        ContainerStatusDto userStatus = new ContainerStatusDto();
                        userStatus.setExists(true);
                        userStatus.setRunning(userId % 2 == 0); // 奇偶用户不同状态
                        userStatus.setContainerName("sillytavern-user" + userId);

                        when(sillyTavernService.getContainerStatus(userConnection))
                                .thenReturn(userStatus);

                        // 执行状态检查
                        controller.handleStatusRequest(userAccessor);

                        // 验证独立处理
                        verify(sillyTavernService).getContainerStatus(userConnection);

                    } catch (Exception e) {
                        System.err.println("并发用户操作失败: " + e.getMessage());
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            // 等待所有操作完成
            assertTrue(completionLatch.await(20, TimeUnit.SECONDS),
                    "不是所有并发用户操作都在超时时间内完成");

            // 验证所有用户都得到了独立处理
            verify(messagingTemplate, times(CONCURRENT_USERS))
                    .convertAndSendToUser(anyString(), contains("status"), any());

        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}