package com.fufu.terminal.sillytavern;

import com.fufu.terminal.controller.SillyTavernStompController;
import com.fufu.terminal.dto.sillytavern.*;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.StompSessionManager;
import com.fufu.terminal.service.sillytavern.ConfigurationService;
import com.fufu.terminal.service.sillytavern.DataManagementService;
import com.fufu.terminal.service.sillytavern.SillyTavernService;
import com.fufu.terminal.service.sillytavern.SystemDetectionService;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end integration tests for SillyTavern management workflow.
 * Tests complete user workflows including deployment, management, and data operations.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("SillyTavern End-to-End Workflow Tests")
class SillyTavernWorkflowIntegrationTest {

    @Autowired
    private SillyTavernStompController controller;
    
    @MockBean
    private SillyTavernService sillyTavernService;
    
    @MockBean
    private SystemDetectionService systemDetectionService;
    
    @MockBean
    private ConfigurationService configurationService;
    
    @MockBean
    private DataManagementService dataManagementService;
    
    @MockBean
    private StompSessionManager sessionManager;
    
    @MockBean
    private SimpMessagingTemplate messagingTemplate;
    
    @MockBean
    private SshConnection sshConnection;
    
    @MockBean
    private Session jschSession;
    
    private SimpMessageHeaderAccessor headerAccessor;
    
    private static final String TEST_SESSION_ID = "integration-test-session";
    
    @BeforeEach
    void setUp() throws JSchException {
        headerAccessor = mock(SimpMessageHeaderAccessor.class);
        when(headerAccessor.getSessionId()).thenReturn(TEST_SESSION_ID);
        when(sessionManager.getConnection(TEST_SESSION_ID)).thenReturn(sshConnection);
        when(sshConnection.getJschSession()).thenReturn(jschSession);
        when(jschSession.isConnected()).thenReturn(true);
    }

    @Test
    @DisplayName("Complete SillyTavern deployment workflow should work end-to-end")
    void testCompleteDeploymentWorkflow() throws Exception {
        // Stage 1: System validation
        SystemInfoDto validSystem = SystemInfoDto.builder()
                .meetsRequirements(true)
                .dockerInstalled(true)
                .sufficientDiskSpace(true)
                .hasRootAccess(true)
                .availablePortRange("8000-9000")
                .build();
        
        when(sillyTavernService.validateSystemRequirements(sshConnection))
                .thenReturn(validSystem);
        
        // Stage 2: Container status check (should not exist initially)
        ContainerStatusDto nonExistentStatus = ContainerStatusDto.notExists();
        when(sillyTavernService.getContainerStatus(sshConnection))
                .thenReturn(nonExistentStatus);
        
        // Stage 3: Deployment request
        DeploymentRequestDto deploymentRequest = DeploymentRequestDto.builder()
                .containerName("sillytavern")
                .dockerImage("ghcr.io/sillytavern/sillytavern:latest")
                .port(8000)
                .dataPath("/opt/sillytavern")
                .username("admin")
                .password("secure123")
                .build();
        
        // Mock successful deployment
        when(sillyTavernService.deployContainer(eq(sshConnection), eq(deploymentRequest), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Consumer<DeploymentProgressDto> callback = invocation.getArgument(2);
                    
                    // Simulate deployment progress
                    callback.accept(DeploymentProgressDto.success("validation", 10, "Validating system..."));
                    callback.accept(DeploymentProgressDto.success("pull-image", 40, "Pulling Docker image..."));
                    callback.accept(DeploymentProgressDto.success("create-container", 80, "Creating container..."));
                    callback.accept(DeploymentProgressDto.completed("Deployment completed successfully!"));
                    
                    return CompletableFuture.completedFuture(null);
                });
        
        // Stage 4: Post-deployment status check
        ContainerStatusDto deployedStatus = new ContainerStatusDto();
        deployedStatus.setExists(true);
        deployedStatus.setRunning(true);
        deployedStatus.setContainerName("sillytavern");
        deployedStatus.setPort(8000);
        deployedStatus.setMemoryUsageMB(256L);
        deployedStatus.setCpuUsagePercent(12.5);
        
        when(sillyTavernService.getContainerStatus(sshConnection))
                .thenReturn(nonExistentStatus)  // Initial check
                .thenReturn(deployedStatus);    // After deployment
        
        // Execute workflow
        
        // 1. Validate system
        controller.handleSystemValidation(headerAccessor);
        verify(sillyTavernService).validateSystemRequirements(sshConnection);
        
        // 2. Check initial status
        controller.handleStatusRequest(headerAccessor);
        
        // 3. Deploy container
        controller.handleDeployment(deploymentRequest, headerAccessor);
        verify(sillyTavernService).deployContainer(eq(sshConnection), eq(deploymentRequest), any());
        
        // 4. Check final status
        controller.handleStatusRequest(headerAccessor);
        
        // Verify all messaging template calls were made
        verify(messagingTemplate, atLeast(4)).convertAndSend(anyString(), any());
    }

    @Test
    @DisplayName("Complete container lifecycle management workflow should work end-to-end")
    void testContainerLifecycleWorkflow() throws Exception {
        // Setup: Container exists and is running
        ContainerStatusDto runningStatus = new ContainerStatusDto();
        runningStatus.setExists(true);
        runningStatus.setRunning(true);
        runningStatus.setContainerName("sillytavern");
        runningStatus.setPort(8000);
        
        ContainerStatusDto stoppedStatus = new ContainerStatusDto();
        stoppedStatus.setExists(true);
        stoppedStatus.setRunning(false);
        stoppedStatus.setContainerName("sillytavern");
        
        when(sillyTavernService.getContainerStatus(sshConnection))
                .thenReturn(runningStatus)   // Initial state: running
                .thenReturn(stoppedStatus)   // After stop
                .thenReturn(runningStatus)   // After start
                .thenReturn(runningStatus);  // After restart
        
        // Test container lifecycle operations
        
        // 1. Stop container
        ServiceActionDto stopAction = new ServiceActionDto();
        stopAction.setAction("stop");
        
        controller.handleServiceAction(stopAction, headerAccessor);
        verify(sillyTavernService).stopContainer(sshConnection);
        
        // 2. Start container
        ServiceActionDto startAction = new ServiceActionDto();
        startAction.setAction("start");
        
        controller.handleServiceAction(startAction, headerAccessor);
        verify(sillyTavernService).startContainer(sshConnection);
        
        // 3. Restart container
        ServiceActionDto restartAction = new ServiceActionDto();
        restartAction.setAction("restart");
        
        controller.handleServiceAction(restartAction, headerAccessor);
        verify(sillyTavernService).restartContainer(sshConnection);
        
        // 4. Upgrade container
        ServiceActionDto upgradeAction = new ServiceActionDto();
        upgradeAction.setAction("upgrade");
        
        when(sillyTavernService.upgradeContainer(eq(sshConnection), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Consumer<String> callback = invocation.getArgument(1);
                    callback.accept("Stopping container...");
                    callback.accept("Pulling latest image...");
                    callback.accept("Starting container...");
                    callback.accept("Upgrade completed successfully");
                    return CompletableFuture.completedFuture(null);
                });
        
        controller.handleServiceAction(upgradeAction, headerAccessor);
        verify(sillyTavernService).upgradeContainer(eq(sshConnection), any());
        
        // Verify all actions generated appropriate responses
        verify(messagingTemplate, atLeast(4)).convertAndSend(
                contains("action-result"), any());
    }

    @Test
    @DisplayName("Configuration management workflow should work end-to-end")
    void testConfigurationManagementWorkflow() throws Exception {
        // Setup: Initial configuration
        ConfigurationDto initialConfig = ConfigurationDto.builder()
                .username("admin")
                .hasPassword(true)
                .port(8000)
                .containerName("sillytavern")
                .build();
        
        when(configurationService.readConfiguration(sshConnection, "sillytavern"))
                .thenReturn(initialConfig);
        
        // 1. Get current configuration
        controller.handleGetConfiguration(headerAccessor);
        verify(configurationService).readConfiguration(sshConnection, "sillytavern");
        
        // 2. Update configuration
        ConfigurationDto updateRequest = ConfigurationDto.builder()
                .username("newuser")
                .password("newpassword123")
                .port(8001)
                .containerName("sillytavern")
                .build();
        
        when(configurationService.validateConfiguration(updateRequest))
                .thenReturn(java.util.Map.of()); // No validation errors
        
        when(configurationService.updateConfiguration(sshConnection, "sillytavern", updateRequest))
                .thenReturn(true);
        
        controller.handleUpdateConfiguration(updateRequest, headerAccessor);
        verify(configurationService).validateConfiguration(updateRequest);
        verify(configurationService).updateConfiguration(sshConnection, "sillytavern", updateRequest);
        
        // 3. Get updated configuration
        ConfigurationDto updatedConfig = ConfigurationDto.builder()
                .username("newuser")
                .hasPassword(true)
                .port(8001)
                .containerName("sillytavern")
                .build();
        
        when(configurationService.readConfiguration(sshConnection, "sillytavern"))
                .thenReturn(updatedConfig);
        
        controller.handleGetConfiguration(headerAccessor);
        
        // Verify configuration workflow responses
        verify(messagingTemplate, times(2)).convertAndSend(
                contains("config-user"), any());
        verify(messagingTemplate).convertAndSend(
                contains("config-updated-user"), any());
    }

    @Test
    @DisplayName("Data management workflow should work end-to-end")
    void testDataManagementWorkflow() throws Exception {
        // 1. Export data
        DataExportDto exportResult = DataExportDto.builder()
                .downloadUrl("/download/sillytavern-export-20240101.zip")
                .filename("sillytavern-export-20240101.zip")
                .sizeBytes(5120000L)
                .expiresAt(java.time.LocalDateTime.now().plusHours(1))
                .build();
        
        when(dataManagementService.exportData(eq(sshConnection), eq("sillytavern"), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Consumer<String> callback = invocation.getArgument(2);
                    callback.accept("Creating backup directory...");
                    callback.accept("Copying data files...");
                    callback.accept("Compressing archive...");
                    callback.accept("Export completed successfully");
                    return CompletableFuture.completedFuture(exportResult);
                });
        
        controller.handleDataExport(headerAccessor);
        verify(dataManagementService).exportData(eq(sshConnection), eq("sillytavern"), any());
        
        // 2. Import data
        java.util.Map<String, String> importRequest = java.util.Map.of(
                "uploadedFileName", "sillytavern-backup.zip");
        
        when(dataManagementService.importData(eq(sshConnection), eq("sillytavern"), 
                eq("sillytavern-backup.zip"), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Consumer<String> callback = invocation.getArgument(3);
                    callback.accept("Validating backup file...");
                    callback.accept("Stopping container...");
                    callback.accept("Extracting backup...");
                    callback.accept("Restoring data files...");
                    callback.accept("Starting container...");
                    callback.accept("Import completed successfully");
                    return CompletableFuture.completedFuture(true);
                });
        
        controller.handleDataImport(importRequest, headerAccessor);
        verify(dataManagementService).importData(eq(sshConnection), eq("sillytavern"), 
                eq("sillytavern-backup.zip"), any());
        
        // Verify data management responses
        verify(messagingTemplate).convertAndSend(
                contains("export-user"), any());
        verify(messagingTemplate).convertAndSend(
                contains("import-user"), any());
        verify(messagingTemplate, atLeast(2)).convertAndSend(
                contains("progress-user"), any());
    }

    @Test
    @DisplayName("Log management workflow should work end-to-end")
    void testLogManagementWorkflow() throws Exception {
        // Setup: Container logs
        java.util.List<String> containerLogs = java.util.List.of(
                "2024-01-01 10:00:01 [INFO] SillyTavern starting up...",
                "2024-01-01 10:00:02 [INFO] Loading configuration...",
                "2024-01-01 10:00:03 [INFO] Database connected",
                "2024-01-01 10:00:04 [INFO] Server listening on port 8000",
                "2024-01-01 10:00:05 [INFO] Ready to accept connections"
        );
        
        // Test different log request scenarios
        
        // 1. Get recent logs (default)
        LogRequestDto recentLogsRequest = LogRequestDto.builder()
                .containerName("sillytavern")
                .days(1)
                .tailLines(100)
                .build();
        
        when(sillyTavernService.getContainerLogs(sshConnection, recentLogsRequest))
                .thenReturn(containerLogs);
        
        controller.handleLogRequest(recentLogsRequest, headerAccessor);
        verify(sillyTavernService).getContainerLogs(sshConnection, recentLogsRequest);
        
        // 2. Get extended logs (7 days, more lines)
        LogRequestDto extendedLogsRequest = LogRequestDto.builder()
                .containerName("sillytavern")
                .days(7)
                .tailLines(500)
                .build();
        
        java.util.List<String> extendedLogs = java.util.ArrayList<>(containerLogs);
        extendedLogs.addAll(java.util.List.of(
                "2024-01-01 11:00:01 [INFO] Processing request...",
                "2024-01-01 12:00:01 [INFO] Daily maintenance started",
                "2024-01-01 13:00:01 [WARN] High memory usage detected"
        ));
        
        when(sillyTavernService.getContainerLogs(sshConnection, extendedLogsRequest))
                .thenReturn(extendedLogs);
        
        controller.handleLogRequest(extendedLogsRequest, headerAccessor);
        verify(sillyTavernService).getContainerLogs(sshConnection, extendedLogsRequest);
        
        // Verify log responses
        verify(messagingTemplate, times(2)).convertAndSend(
                contains("logs-user"), any());
    }

    @Test
    @DisplayName("Error recovery workflow should handle failures gracefully")
    void testErrorRecoveryWorkflow() throws Exception {
        // Test scenario: Deployment fails, then system recovers
        
        // 1. System validation fails initially
        SystemInfoDto invalidSystem = SystemInfoDto.builder()
                .meetsRequirements(false)
                .dockerInstalled(false)
                .sufficientDiskSpace(true)
                .requirementChecks(java.util.List.of("Docker not installed"))
                .build();
        
        when(sillyTavernService.validateSystemRequirements(sshConnection))
                .thenReturn(invalidSystem);
        
        controller.handleSystemValidation(headerAccessor);
        
        // 2. Try deployment (should fail)
        DeploymentRequestDto deploymentRequest = DeploymentRequestDto.builder()
                .containerName("sillytavern")
                .dockerImage("ghcr.io/sillytavern/sillytavern:latest")
                .port(8000)
                .dataPath("/opt/sillytavern")
                .build();
        
        when(sillyTavernService.deployContainer(eq(sshConnection), eq(deploymentRequest), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Consumer<DeploymentProgressDto> callback = invocation.getArgument(2);
                    callback.accept(DeploymentProgressDto.error("validation", 
                            "System requirements not met: Docker not installed"));
                    return CompletableFuture.completedFuture(null);
                });
        
        controller.handleDeployment(deploymentRequest, headerAccessor);
        
        // 3. System recovers (Docker gets installed)
        SystemInfoDto validSystem = SystemInfoDto.builder()
                .meetsRequirements(true)
                .dockerInstalled(true)
                .sufficientDiskSpace(true)
                .hasRootAccess(true)
                .build();
        
        when(sillyTavernService.validateSystemRequirements(sshConnection))
                .thenReturn(validSystem);
        
        controller.handleSystemValidation(headerAccessor);
        
        // 4. Retry deployment (should succeed)
        when(sillyTavernService.deployContainer(eq(sshConnection), eq(deploymentRequest), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Consumer<DeploymentProgressDto> callback = invocation.getArgument(2);
                    callback.accept(DeploymentProgressDto.success("validation", 20, "System validated"));
                    callback.accept(DeploymentProgressDto.success("pull-image", 60, "Image pulled"));
                    callback.accept(DeploymentProgressDto.success("create-container", 90, "Container created"));
                    callback.accept(DeploymentProgressDto.completed("Deployment successful"));
                    return CompletableFuture.completedFuture(null);
                });
        
        controller.handleDeployment(deploymentRequest, headerAccessor);
        
        // Verify error recovery workflow
        verify(sillyTavernService, times(2)).validateSystemRequirements(sshConnection);
        verify(sillyTavernService, times(2)).deployContainer(eq(sshConnection), eq(deploymentRequest), any());
        
        // Should have both error and success messages
        verify(messagingTemplate, atLeast(4)).convertAndSend(anyString(), any());
    }

    @Test
    @DisplayName("Concurrent user operations should be handled correctly")
    void testConcurrentUserOperations() throws Exception {
        // Simulate multiple concurrent operations
        String session1 = "session-1";
        String session2 = "session-2";
        
        SimpMessageHeaderAccessor accessor1 = mock(SimpMessageHeaderAccessor.class);
        SimpMessageHeaderAccessor accessor2 = mock(SimpMessageHeaderAccessor.class);
        
        when(accessor1.getSessionId()).thenReturn(session1);
        when(accessor2.getSessionId()).thenReturn(session2);
        
        SshConnection connection1 = mock(SshConnection.class);
        SshConnection connection2 = mock(SshConnection.class);
        
        when(sessionManager.getConnection(session1)).thenReturn(connection1);
        when(sessionManager.getConnection(session2)).thenReturn(connection2);
        
        // Mock different container states for each session
        ContainerStatusDto status1 = new ContainerStatusDto();
        status1.setExists(true);
        status1.setRunning(true);
        status1.setContainerName("sillytavern-user1");
        
        ContainerStatusDto status2 = new ContainerStatusDto();
        status2.setExists(true);
        status2.setRunning(false);
        status2.setContainerName("sillytavern-user2");
        
        when(sillyTavernService.getContainerStatus(connection1)).thenReturn(status1);
        when(sillyTavernService.getContainerStatus(connection2)).thenReturn(status2);
        
        // Execute concurrent operations
        controller.handleStatusRequest(accessor1);
        controller.handleStatusRequest(accessor2);
        
        // Verify both operations were handled independently
        verify(sillyTavernService).getContainerStatus(connection1);
        verify(sillyTavernService).getContainerStatus(connection2);
        
        verify(messagingTemplate).convertAndSend(
                eq("/queue/sillytavern/status-user" + session1), any());
        verify(messagingTemplate).convertAndSend(
                eq("/queue/sillytavern/status-user" + session2), any());
    }
}