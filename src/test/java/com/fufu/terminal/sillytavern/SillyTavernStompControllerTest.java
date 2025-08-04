package com.fufu.terminal.sillytavern;

import com.fufu.terminal.controller.SillyTavernStompController;
import com.fufu.terminal.dto.sillytavern.*;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.StompSessionManager;
import com.fufu.terminal.service.sillytavern.ConfigurationService;
import com.fufu.terminal.service.sillytavern.DataManagementService;
import com.fufu.terminal.service.sillytavern.SillyTavernService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for SillyTavern STOMP controller.
 * Tests WebSocket message handling, session management, and response routing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SillyTavern STOMP Controller Integration Tests")
class SillyTavernStompControllerTest {

    @Mock
    private SillyTavernService sillyTavernService;
    
    @Mock
    private ConfigurationService configurationService;
    
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
    
    private SillyTavernStompController controller;
    
    private static final String TEST_SESSION_ID = "test-session-123";
    
    @BeforeEach
    void setUp() {
        controller = new SillyTavernStompController(
                sillyTavernService, configurationService, dataManagementService,
                sessionManager, messagingTemplate);
        
        when(headerAccessor.getSessionId()).thenReturn(TEST_SESSION_ID);
        when(sessionManager.getConnection(TEST_SESSION_ID)).thenReturn(sshConnection);
    }

    @Test
    @DisplayName("Should handle system validation request successfully")
    void testHandleSystemValidationSuccess() {
        // Given
        SystemInfoDto systemInfo = SystemInfoDto.builder()
                .meetsRequirements(true)
                .dockerInstalled(true)
                .sufficientDiskSpace(true)
                .build();
        
        when(sillyTavernService.validateSystemRequirements(sshConnection))
                .thenReturn(systemInfo);
        
        // When
        controller.handleSystemValidation(headerAccessor);
        
        // Then
        verify(sillyTavernService).validateSystemRequirements(sshConnection);
        
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> messageCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());
        
        assertEquals("/queue/sillytavern/system-validation-user" + TEST_SESSION_ID, destinationCaptor.getValue());
        Map<String, Object> message = messageCaptor.getValue();
        assertEquals("system-validation", message.get("type"));
        assertTrue((Boolean) message.get("success"));
        assertEquals(systemInfo, message.get("payload"));
    }

    @Test
    @DisplayName("Should handle system validation with no SSH connection")
    void testHandleSystemValidationNoConnection() {
        // Given
        when(sessionManager.getConnection(TEST_SESSION_ID)).thenReturn(null);
        
        // When
        controller.handleSystemValidation(headerAccessor);
        
        // Then
        verify(sillyTavernService, never()).validateSystemRequirements(any());
        verify(sessionManager).sendErrorMessage(TEST_SESSION_ID, "SSH connection not established");
    }

    @Test
    @DisplayName("Should handle container status request successfully")
    void testHandleStatusRequestSuccess() {
        // Given
        ContainerStatusDto containerStatus = new ContainerStatusDto();
        containerStatus.setExists(true);
        containerStatus.setRunning(true);
        containerStatus.setContainerName("sillytavern");
        containerStatus.setPort(8000);
        
        when(sillyTavernService.getContainerStatus(sshConnection))
                .thenReturn(containerStatus);
        
        // When
        controller.handleStatusRequest(headerAccessor);
        
        // Then
        verify(sillyTavernService).getContainerStatus(sshConnection);
        
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> messageCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());
        
        assertEquals("/queue/sillytavern/status-user" + TEST_SESSION_ID, destinationCaptor.getValue());
        Map<String, Object> message = messageCaptor.getValue();
        assertEquals("status", message.get("type"));
        assertTrue((Boolean) message.get("success"));
        assertEquals(containerStatus, message.get("payload"));
    }

    @Test
    @DisplayName("Should handle deployment request with progress updates")
    void testHandleDeploymentWithProgress() {
        // Given
        DeploymentRequestDto deploymentRequest = DeploymentRequestDto.builder()
                .containerName("test-sillytavern")
                .dockerImage("ghcr.io/sillytavern/sillytavern:latest")
                .port(8001)
                .dataPath("/opt/sillytavern-data")
                .build();
        
        when(sillyTavernService.deployContainer(eq(sshConnection), eq(deploymentRequest), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        
        // When
        controller.handleDeployment(deploymentRequest, headerAccessor);
        
        // Then
        verify(sillyTavernService).deployContainer(eq(sshConnection), eq(deploymentRequest), any());
        
        // Verify that progress callback is properly set up
        ArgumentCaptor<java.util.function.Consumer> callbackCaptor = 
                ArgumentCaptor.forClass(java.util.function.Consumer.class);
        
        verify(sillyTavernService).deployContainer(eq(sshConnection), eq(deploymentRequest), 
                callbackCaptor.capture());
        
        // Test the progress callback
        java.util.function.Consumer<DeploymentProgressDto> progressCallback = callbackCaptor.getValue();
        DeploymentProgressDto testProgress = DeploymentProgressDto.success("test-stage", 50, "Testing...");
        
        progressCallback.accept(testProgress);
        
        verify(messagingTemplate).convertAndSend(
                eq("/queue/sillytavern/deployment-progress-user" + TEST_SESSION_ID),
                argThat(map -> {
                    Map<String, Object> msg = (Map<String, Object>) map;
                    return "deployment-progress".equals(msg.get("type")) &&
                           Boolean.TRUE.equals(msg.get("success")) &&
                           testProgress.equals(msg.get("payload"));
                })
        );
    }

    @Test
    @DisplayName("Should handle service action start successfully")
    void testHandleServiceActionStart() throws Exception {
        // Given
        ServiceActionDto actionRequest = new ServiceActionDto();
        actionRequest.setAction("start");
        
        // When
        controller.handleServiceAction(actionRequest, headerAccessor);
        
        // Then
        verify(sillyTavernService).startContainer(sshConnection);
        
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> messageCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());
        
        assertEquals("/queue/sillytavern/action-result-user" + TEST_SESSION_ID, destinationCaptor.getValue());
        Map<String, Object> message = messageCaptor.getValue();
        assertTrue((Boolean) message.get("success"));
        assertEquals("Container started successfully", message.get("message"));
        assertEquals("", message.get("error"));
    }

    @Test
    @DisplayName("Should handle service action stop successfully")
    void testHandleServiceActionStop() throws Exception {
        // Given
        ServiceActionDto actionRequest = new ServiceActionDto();
        actionRequest.setAction("stop");
        
        // When
        controller.handleServiceAction(actionRequest, headerAccessor);
        
        // Then
        verify(sillyTavernService).stopContainer(sshConnection);
        
        ArgumentCaptor<Map> messageCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(anyString(), messageCaptor.capture());
        
        Map<String, Object> message = messageCaptor.getValue();
        assertTrue((Boolean) message.get("success"));
        assertEquals("Container stopped successfully", message.get("message"));
    }

    @Test
    @DisplayName("Should handle service action restart successfully")
    void testHandleServiceActionRestart() throws Exception {
        // Given
        ServiceActionDto actionRequest = new ServiceActionDto();
        actionRequest.setAction("restart");
        
        // When
        controller.handleServiceAction(actionRequest, headerAccessor);
        
        // Then
        verify(sillyTavernService).restartContainer(sshConnection);
        
        ArgumentCaptor<Map> messageCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(anyString(), messageCaptor.capture());
        
        Map<String, Object> message = messageCaptor.getValue();
        assertTrue((Boolean) message.get("success"));
        assertEquals("Container restarted successfully", message.get("message"));
    }

    @Test
    @DisplayName("Should handle service action upgrade with progress")
    void testHandleServiceActionUpgrade() throws Exception {
        // Given
        ServiceActionDto actionRequest = new ServiceActionDto();
        actionRequest.setAction("upgrade");
        
        CompletableFuture<Void> upgradeFuture = CompletableFuture.completedFuture(null);
        when(sillyTavernService.upgradeContainer(eq(sshConnection), any()))
                .thenReturn(upgradeFuture);
        
        // When
        controller.handleServiceAction(actionRequest, headerAccessor);
        
        // Then
        verify(sillyTavernService).upgradeContainer(eq(sshConnection), any());
        
        // Verify upgrade progress callback is set up
        ArgumentCaptor<java.util.function.Consumer> callbackCaptor = 
                ArgumentCaptor.forClass(java.util.function.Consumer.class);
        
        verify(sillyTavernService).upgradeContainer(eq(sshConnection), callbackCaptor.capture());
        
        // Test the progress callback
        java.util.function.Consumer<String> progressCallback = callbackCaptor.getValue();
        progressCallback.accept("Pulling latest image...");
        
        verify(messagingTemplate).convertAndSend(
                eq("/queue/sillytavern/upgrade-progress-user" + TEST_SESSION_ID),
                argThat(map -> {
                    Map<String, Object> msg = (Map<String, Object>) map;
                    return "upgrade-progress".equals(msg.get("type")) &&
                           Boolean.TRUE.equals(msg.get("success")) &&
                           ((Map<String, String>) msg.get("payload")).get("message").equals("Pulling latest image...");
                })
        );
    }

    @Test
    @DisplayName("Should handle service action delete successfully")
    void testHandleServiceActionDelete() throws Exception {
        // Given
        ServiceActionDto actionRequest = new ServiceActionDto();
        actionRequest.setAction("delete");
        actionRequest.setRemoveData(true);
        
        // When
        controller.handleServiceAction(actionRequest, headerAccessor);
        
        // Then
        verify(sillyTavernService).deleteContainer(sshConnection, true);
        
        ArgumentCaptor<Map> messageCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(anyString(), messageCaptor.capture());
        
        Map<String, Object> message = messageCaptor.getValue();
        assertTrue((Boolean) message.get("success"));
        assertEquals("Container deleted successfully", message.get("message"));
    }

    @Test
    @DisplayName("Should handle unknown service action")
    void testHandleServiceActionUnknown() {
        // Given
        ServiceActionDto actionRequest = new ServiceActionDto();
        actionRequest.setAction("unknown-action");
        
        // When
        controller.handleServiceAction(actionRequest, headerAccessor);
        
        // Then
        verifyNoInteractions(sillyTavernService);
        
        ArgumentCaptor<Map> messageCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(anyString(), messageCaptor.capture());
        
        Map<String, Object> message = messageCaptor.getValue();
        assertFalse((Boolean) message.get("success"));
        assertEquals("Unknown action", message.get("message"));
        assertEquals("Unsupported action: unknown-action", message.get("error"));
    }

    @Test
    @DisplayName("Should handle service action exception")
    void testHandleServiceActionException() throws Exception {
        // Given
        ServiceActionDto actionRequest = new ServiceActionDto();
        actionRequest.setAction("start");
        
        when(sillyTavernService.startContainer(sshConnection))
                .thenThrow(new RuntimeException("Container start failed"));
        
        // When
        controller.handleServiceAction(actionRequest, headerAccessor);
        
        // Then
        ArgumentCaptor<Map> messageCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(anyString(), messageCaptor.capture());
        
        Map<String, Object> message = messageCaptor.getValue();
        assertFalse((Boolean) message.get("success"));
        assertEquals("Action failed", message.get("message"));
        assertEquals("Container start failed", message.get("error"));
    }

    @Test
    @DisplayName("Should handle log request successfully")
    void testHandleLogRequestSuccess() throws Exception {
        // Given
        LogRequestDto logRequest = LogRequestDto.builder()
                .containerName("sillytavern")
                .days(3)
                .tailLines(100)
                .build();
        
        List<String> expectedLogs = List.of(
                "2024-01-01 10:00:01 [INFO] Starting SillyTavern",
                "2024-01-01 10:00:02 [INFO] Server ready",
                "2024-01-01 10:00:03 [INFO] Listening on port 8000"
        );
        
        when(sillyTavernService.getContainerLogs(sshConnection, logRequest))
                .thenReturn(expectedLogs);
        
        // When
        controller.handleLogRequest(logRequest, headerAccessor);
        
        // Then
        verify(sillyTavernService).getContainerLogs(sshConnection, logRequest);
        
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> messageCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());
        
        assertEquals("/queue/sillytavern/logs-user" + TEST_SESSION_ID, destinationCaptor.getValue());
        Map<String, Object> message = messageCaptor.getValue();
        assertEquals("logs", message.get("type"));
        assertTrue((Boolean) message.get("success"));
        
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        assertEquals(expectedLogs, payload.get("logs"));
        assertEquals(3, payload.get("totalLines"));
        assertTrue((Boolean) payload.get("truncated"));
        assertEquals("sillytavern", payload.get("containerName"));
    }

    @Test
    @DisplayName("Should handle configuration retrieval successfully")
    void testHandleGetConfigurationSuccess() throws Exception {
        // Given
        ConfigurationDto expectedConfig = ConfigurationDto.builder()
                .username("admin")
                .hasPassword(true)
                .port(8000)
                .containerName("sillytavern")
                .build();
        
        when(configurationService.readConfiguration(sshConnection, "sillytavern"))
                .thenReturn(expectedConfig);
        
        // When
        controller.handleGetConfiguration(headerAccessor);
        
        // Then
        verify(configurationService).readConfiguration(sshConnection, "sillytavern");
        
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> messageCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());
        
        assertEquals("/queue/sillytavern/config-user" + TEST_SESSION_ID, destinationCaptor.getValue());
        Map<String, Object> message = messageCaptor.getValue();
        assertEquals("config", message.get("type"));
        assertTrue((Boolean) message.get("success"));
        assertEquals(expectedConfig, message.get("payload"));
    }

    @Test
    @DisplayName("Should handle configuration update successfully")
    void testHandleUpdateConfigurationSuccess() throws Exception {
        // Given
        ConfigurationDto configRequest = ConfigurationDto.builder()
                .username("newuser")
                .password("newpassword")
                .port(8001)
                .containerName("sillytavern")
                .build();
        
        when(configurationService.validateConfiguration(configRequest))
                .thenReturn(Map.of()); // No validation errors
        
        when(configurationService.updateConfiguration(sshConnection, "sillytavern", configRequest))
                .thenReturn(true);
        
        // When
        controller.handleUpdateConfiguration(configRequest, headerAccessor);
        
        // Then
        verify(configurationService).validateConfiguration(configRequest);
        verify(configurationService).updateConfiguration(sshConnection, "sillytavern", configRequest);
        
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> messageCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());
        
        assertEquals("/queue/sillytavern/config-updated-user" + TEST_SESSION_ID, destinationCaptor.getValue());
        Map<String, Object> message = messageCaptor.getValue();
        assertTrue((Boolean) message.get("success"));
        assertEquals("Configuration updated successfully", message.get("message"));
        assertTrue((Boolean) message.get("requiresRestart"));
    }

    @Test
    @DisplayName("Should handle configuration validation errors")
    void testHandleUpdateConfigurationValidationError() throws Exception {
        // Given
        ConfigurationDto configRequest = ConfigurationDto.builder()
                .username("")
                .password("short")
                .port(-1)
                .build();
        
        Map<String, String> validationErrors = Map.of(
                "username", "Username cannot be empty",
                "password", "Password too short",
                "port", "Invalid port number"
        );
        
        when(configurationService.validateConfiguration(configRequest))
                .thenReturn(validationErrors);
        
        // When
        controller.handleUpdateConfiguration(configRequest, headerAccessor);
        
        // Then
        verify(configurationService).validateConfiguration(configRequest);
        verify(configurationService, never()).updateConfiguration(any(), any(), any());
        
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> messageCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());
        
        assertEquals("/queue/sillytavern/config-updated-user" + TEST_SESSION_ID, destinationCaptor.getValue());
        Map<String, Object> message = messageCaptor.getValue();
        assertFalse((Boolean) message.get("success"));
        assertEquals("Configuration validation failed", message.get("message"));
        assertEquals(validationErrors, message.get("errors"));
    }

    @Test
    @DisplayName("Should handle data export request successfully")
    void testHandleDataExportSuccess() throws Exception {
        // Given
        DataExportDto exportResult = DataExportDto.builder()
                .downloadUrl("/download/sillytavern-data-20240101.zip")
                .filename("sillytavern-data-20240101.zip")
                .sizeBytes(1024000L)
                .expiresAt(java.time.LocalDateTime.now().plusHours(1))
                .build();
        
        CompletableFuture<DataExportDto> exportFuture = CompletableFuture.completedFuture(exportResult);
        when(dataManagementService.exportData(eq(sshConnection), eq("sillytavern"), any()))
                .thenReturn(exportFuture);
        
        // When
        controller.handleDataExport(headerAccessor);
        
        // Then
        verify(dataManagementService).exportData(eq(sshConnection), eq("sillytavern"), any());
        
        // Verify export progress callback
        ArgumentCaptor<java.util.function.Consumer> callbackCaptor = 
                ArgumentCaptor.forClass(java.util.function.Consumer.class);
        
        verify(dataManagementService).exportData(eq(sshConnection), eq("sillytavern"), 
                callbackCaptor.capture());
        
        // Test the progress callback
        java.util.function.Consumer<String> progressCallback = callbackCaptor.getValue();
        progressCallback.accept("Compressing data...");
        
        verify(messagingTemplate).convertAndSend(
                eq("/queue/sillytavern/export-progress-user" + TEST_SESSION_ID),
                argThat(map -> {
                    Map<String, Object> msg = (Map<String, Object>) map;
                    return "export-progress".equals(msg.get("type")) &&
                           "Compressing data...".equals(msg.get("message"));
                })
        );
    }

    @Test
    @DisplayName("Should handle data import request successfully")
    void testHandleDataImportSuccess() throws Exception {
        // Given
        Map<String, String> importRequest = Map.of("uploadedFileName", "sillytavern-backup.zip");
        
        CompletableFuture<Boolean> importFuture = CompletableFuture.completedFuture(true);
        when(dataManagementService.importData(eq(sshConnection), eq("sillytavern"), 
                eq("sillytavern-backup.zip"), any()))
                .thenReturn(importFuture);
        
        // When
        controller.handleDataImport(importRequest, headerAccessor);
        
        // Then
        verify(dataManagementService).importData(eq(sshConnection), eq("sillytavern"), 
                eq("sillytavern-backup.zip"), any());
        
        // Verify import progress callback
        ArgumentCaptor<java.util.function.Consumer> callbackCaptor = 
                ArgumentCaptor.forClass(java.util.function.Consumer.class);
        
        verify(dataManagementService).importData(eq(sshConnection), eq("sillytavern"), 
                eq("sillytavern-backup.zip"), callbackCaptor.capture());
        
        // Test the progress callback  
        java.util.function.Consumer<String> progressCallback = callbackCaptor.getValue();
        progressCallback.accept("Extracting files...");
        
        verify(messagingTemplate).convertAndSend(
                eq("/queue/sillytavern/import-progress-user" + TEST_SESSION_ID),
                argThat(map -> {
                    Map<String, Object> msg = (Map<String, Object>) map;
                    return "import-progress".equals(msg.get("type")) &&
                           "Extracting files...".equals(msg.get("message"));
                })
        );
    }

    @Test
    @DisplayName("Should handle data import with missing filename")
    void testHandleDataImportMissingFilename() {
        // Given
        Map<String, String> importRequest = Map.of();
        
        // When
        controller.handleDataImport(importRequest, headerAccessor);
        
        // Then
        verify(dataManagementService, never()).importData(any(), any(), any(), any());
        verify(sessionManager).sendErrorMessage(TEST_SESSION_ID, "No uploaded file specified");
    }

    @Test
    @DisplayName("Should handle all operations with no SSH connection gracefully")
    void testHandleOperationsWithNoConnection() {
        // Given
        when(sessionManager.getConnection(TEST_SESSION_ID)).thenReturn(null);
        
        // When & Then - test all operations
        controller.handleStatusRequest(headerAccessor);
        controller.handleDeployment(new DeploymentRequestDto(), headerAccessor);
        controller.handleServiceAction(new ServiceActionDto(), headerAccessor);
        controller.handleLogRequest(new LogRequestDto(), headerAccessor);
        controller.handleGetConfiguration(headerAccessor);
        controller.handleUpdateConfiguration(new ConfigurationDto(), headerAccessor);
        controller.handleDataExport(headerAccessor);
        controller.handleDataImport(Map.of("uploadedFileName", "test.zip"), headerAccessor);
        
        // Then - all should result in error messages
        verify(sessionManager, times(8)).sendErrorMessage(TEST_SESSION_ID, "SSH connection not established");
        
        // No service methods should be called
        verifyNoInteractions(sillyTavernService);
        verifyNoInteractions(configurationService);
        verifyNoInteractions(dataManagementService);
    }
}