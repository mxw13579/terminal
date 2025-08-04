package com.fufu.terminal.sillytavern;

import com.fufu.terminal.dto.sillytavern.*;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.sillytavern.DockerContainerService;
import com.fufu.terminal.service.sillytavern.SillyTavernService;
import com.fufu.terminal.service.sillytavern.SystemDetectionService;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for SillyTavern service core functionality.
 * Tests all business logic, deployment workflows, and container management operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SillyTavern Service Core Functionality Tests")
class SillyTavernServiceTest {

    @Mock
    private DockerContainerService dockerService;
    
    @Mock
    private SystemDetectionService systemDetectionService;
    
    @Mock
    private SshConnection sshConnection;
    
    @Mock
    private Session jschSession;
    
    private SillyTavernService sillyTavernService;
    
    @BeforeEach
    void setUp() {
        sillyTavernService = new SillyTavernService(dockerService, systemDetectionService);
        when(sshConnection.getJschSession()).thenReturn(jschSession);
    }

    @Test
    @DisplayName("Should validate system requirements successfully")
    void testValidateSystemRequirements() {
        // Given
        SystemInfoDto expectedSystemInfo = SystemInfoDto.builder()
                .meetsRequirements(true)
                .dockerInstalled(true)
                .sufficientDiskSpace(true)
                .build();
        
        when(systemDetectionService.validateSystemRequirements(sshConnection))
                .thenReturn(expectedSystemInfo);
        
        // When
        SystemInfoDto result = sillyTavernService.validateSystemRequirements(sshConnection);
        
        // Then
        assertNotNull(result);
        assertTrue(result.getMeetsRequirements());
        assertTrue(result.getDockerInstalled());
        assertTrue(result.getSufficientDiskSpace());
        verify(systemDetectionService).validateSystemRequirements(sshConnection);
    }

    @Test
    @DisplayName("Should get container status when container exists and is running")
    void testGetContainerStatusRunning() {
        // Given
        ContainerStatusDto expectedStatus = new ContainerStatusDto();
        expectedStatus.setExists(true);
        expectedStatus.setRunning(true);
        expectedStatus.setContainerName("sillytavern");
        expectedStatus.setPort(8000);
        expectedStatus.setMemoryUsageMB(256L);
        expectedStatus.setCpuUsagePercent(15.5);
        
        when(dockerService.getContainerStatus(sshConnection, "sillytavern"))
                .thenReturn(expectedStatus);
        
        // When
        ContainerStatusDto result = sillyTavernService.getContainerStatus(sshConnection);
        
        // Then
        assertNotNull(result);
        assertTrue(result.getExists());
        assertTrue(result.getRunning());
        assertEquals("sillytavern", result.getContainerName());
        assertEquals(8000, result.getPort());
        assertEquals(256L, result.getMemoryUsageMB());
        assertEquals(15.5, result.getCpuUsagePercent());
        verify(dockerService).getContainerStatus(sshConnection, "sillytavern");
    }

    @Test
    @DisplayName("Should return not exists status when container doesn't exist")
    void testGetContainerStatusNotExists() {
        // Given
        ContainerStatusDto expectedStatus = ContainerStatusDto.notExists();
        when(dockerService.getContainerStatus(sshConnection, "sillytavern"))
                .thenReturn(expectedStatus);
        
        // When
        ContainerStatusDto result = sillyTavernService.getContainerStatus(sshConnection);
        
        // Then
        assertNotNull(result);
        assertFalse(result.getExists());
        assertFalse(result.getRunning());
        assertEquals("Container not found", result.getStatus());
    }

    @Test
    @DisplayName("Should check if container is running correctly")
    void testIsContainerRunning() {
        // Given - container exists and is running
        ContainerStatusDto runningStatus = new ContainerStatusDto();
        runningStatus.setExists(true);
        runningStatus.setRunning(true);
        
        when(dockerService.getContainerStatus(sshConnection, "sillytavern"))
                .thenReturn(runningStatus);
        
        // When
        boolean isRunning = sillyTavernService.isContainerRunning(sshConnection);
        
        // Then
        assertTrue(isRunning);
        
        // Given - container exists but is stopped
        ContainerStatusDto stoppedStatus = new ContainerStatusDto();
        stoppedStatus.setExists(true);
        stoppedStatus.setRunning(false);
        
        when(dockerService.getContainerStatus(sshConnection, "sillytavern"))
                .thenReturn(stoppedStatus);
        
        // When
        isRunning = sillyTavernService.isContainerRunning(sshConnection);
        
        // Then
        assertFalse(isRunning);
    }

    @Test
    @DisplayName("Should deploy container successfully with progress updates")
    void testDeployContainerSuccess() throws Exception {
        // Given
        DeploymentRequestDto request = DeploymentRequestDto.builder()
                .containerName("test-sillytavern")
                .dockerImage("ghcr.io/sillytavern/sillytavern:latest")
                .port(8001)
                .dataPath("/opt/sillytavern-data")
                .build();
        
        SystemInfoDto validSystemInfo = SystemInfoDto.builder()
                .meetsRequirements(true)
                .dockerInstalled(true)
                .sufficientDiskSpace(true)
                .build();
        
        ContainerStatusDto nonExistentStatus = ContainerStatusDto.notExists();
        ContainerStatusDto deployedStatus = new ContainerStatusDto();
        deployedStatus.setExists(true);
        deployedStatus.setRunning(true);
        deployedStatus.setContainerName("test-sillytavern");
        
        when(systemDetectionService.validateSystemRequirements(sshConnection))
                .thenReturn(validSystemInfo);
        when(dockerService.getContainerStatus(sshConnection, "test-sillytavern"))
                .thenReturn(nonExistentStatus)  // First call - doesn't exist
                .thenReturn(deployedStatus);    // Second call - deployed and running
        when(dockerService.pullImage(eq(sshConnection), eq(request.getDockerImage()), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(dockerService.createContainer(sshConnection, "test-sillytavern", 
                request.getDockerImage(), 8001, "/opt/sillytavern-data"))
                .thenReturn("container-id-123");
        
        // Track progress updates
        java.util.List<DeploymentProgressDto> progressUpdates = new java.util.ArrayList<>();
        Consumer<DeploymentProgressDto> progressCallback = progressUpdates::add;
        
        // When
        CompletableFuture<Void> deploymentFuture = sillyTavernService.deployContainer(
                sshConnection, request, progressCallback);
        
        // Wait for deployment to complete
        deploymentFuture.get(10, TimeUnit.SECONDS);
        
        // Then
        verify(systemDetectionService).validateSystemRequirements(sshConnection);
        verify(dockerService).pullImage(eq(sshConnection), eq(request.getDockerImage()), any());
        verify(dockerService).createContainer(sshConnection, "test-sillytavern", 
                request.getDockerImage(), 8001, "/opt/sillytavern-data");
        
        // Verify progress updates
        assertFalse(progressUpdates.isEmpty());
        assertTrue(progressUpdates.stream().anyMatch(p -> "validation".equals(p.getStage())));
        assertTrue(progressUpdates.stream().anyMatch(p -> "check-existing".equals(p.getStage())));
        assertTrue(progressUpdates.stream().anyMatch(p -> "pull-image".equals(p.getStage())));
        assertTrue(progressUpdates.stream().anyMatch(p -> "create-container".equals(p.getStage())));
        assertTrue(progressUpdates.stream().anyMatch(p -> "verify".equals(p.getStage())));
        
        // Verify successful completion
        DeploymentProgressDto lastUpdate = progressUpdates.get(progressUpdates.size() - 1);
        assertTrue(lastUpdate.getCompleted());
        assertTrue(lastUpdate.getSuccess());
        assertNull(lastUpdate.getError());
    }

    @Test
    @DisplayName("Should fail deployment when system requirements not met")
    void testDeployContainerSystemValidationFailure() throws Exception {
        // Given
        DeploymentRequestDto request = DeploymentRequestDto.builder()
                .containerName("test-sillytavern")
                .dockerImage("ghcr.io/sillytavern/sillytavern:latest")
                .port(8001)
                .dataPath("/opt/sillytavern-data")
                .build();
        
        SystemInfoDto invalidSystemInfo = SystemInfoDto.builder()
                .meetsRequirements(false)
                .dockerInstalled(false)
                .sufficientDiskSpace(true)
                .requirementChecks(java.util.List.of("Docker not installed"))
                .build();
        
        when(systemDetectionService.validateSystemRequirements(sshConnection))
                .thenReturn(invalidSystemInfo);
        
        java.util.List<DeploymentProgressDto> progressUpdates = new java.util.ArrayList<>();
        Consumer<DeploymentProgressDto> progressCallback = progressUpdates::add;
        
        // When
        CompletableFuture<Void> deploymentFuture = sillyTavernService.deployContainer(
                sshConnection, request, progressCallback);
        
        deploymentFuture.get(5, TimeUnit.SECONDS);
        
        // Then
        verify(systemDetectionService).validateSystemRequirements(sshConnection);
        verify(dockerService, never()).pullImage(any(), any(), any());
        verify(dockerService, never()).createContainer(any(), any(), any(), anyInt(), any());
        
        // Verify error is reported
        DeploymentProgressDto errorUpdate = progressUpdates.stream()
                .filter(p -> p.getError() != null)
                .findFirst()
                .orElse(null);
        
        assertNotNull(errorUpdate);
        assertEquals("validation", errorUpdate.getStage());
        assertNotNull(errorUpdate.getError());
        assertTrue(errorUpdate.getError().contains("系统不满足部署要求"));
    }

    @Test
    @DisplayName("Should fail deployment when container already exists")
    void testDeployContainerAlreadyExists() throws Exception {
        // Given
        DeploymentRequestDto request = DeploymentRequestDto.builder()
                .containerName("existing-container")
                .dockerImage("ghcr.io/sillytavern/sillytavern:latest")
                .port(8001)
                .dataPath("/opt/sillytavern-data")
                .build();
        
        SystemInfoDto validSystemInfo = SystemInfoDto.builder()
                .meetsRequirements(true)
                .dockerInstalled(true)
                .sufficientDiskSpace(true)
                .build();
        
        ContainerStatusDto existingStatus = new ContainerStatusDto();
        existingStatus.setExists(true);
        existingStatus.setContainerName("existing-container");
        
        when(systemDetectionService.validateSystemRequirements(sshConnection))
                .thenReturn(validSystemInfo);
        when(dockerService.getContainerStatus(sshConnection, "existing-container"))
                .thenReturn(existingStatus);
        
        java.util.List<DeploymentProgressDto> progressUpdates = new java.util.ArrayList<>();
        Consumer<DeploymentProgressDto> progressCallback = progressUpdates::add;
        
        // When
        CompletableFuture<Void> deploymentFuture = sillyTavernService.deployContainer(
                sshConnection, request, progressCallback);
        
        deploymentFuture.get(5, TimeUnit.SECONDS);
        
        // Then
        verify(dockerService, never()).pullImage(any(), any(), any());
        verify(dockerService, never()).createContainer(any(), any(), any(), anyInt(), any());
        
        // Verify error is reported
        DeploymentProgressDto errorUpdate = progressUpdates.stream()
                .filter(p -> p.getError() != null)
                .findFirst()
                .orElse(null);
        
        assertNotNull(errorUpdate);
        assertEquals("check-existing", errorUpdate.getStage());
        assertTrue(errorUpdate.getError().contains("已存在"));
    }

    @Test
    @DisplayName("Should start container successfully")
    void testStartContainer() throws Exception {
        // Given
        ContainerStatusDto existingStatus = ContainerStatusDto.stopped("sillytavern");
        when(dockerService.getContainerStatus(sshConnection, "sillytavern"))
                .thenReturn(existingStatus);
        
        // When
        sillyTavernService.startContainer(sshConnection);
        
        // Then
        verify(dockerService).getContainerStatus(sshConnection, "sillytavern");
        verify(dockerService).startContainer(sshConnection, "sillytavern");
    }

    @Test
    @DisplayName("Should throw exception when trying to start non-existent container")
    void testStartContainerNotExists() {
        // Given
        ContainerStatusDto nonExistentStatus = ContainerStatusDto.notExists();
        when(dockerService.getContainerStatus(sshConnection, "sillytavern"))
                .thenReturn(nonExistentStatus);
        
        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
                () -> sillyTavernService.startContainer(sshConnection));
        
        assertTrue(exception.getMessage().contains("does not exist"));
        verify(dockerService, never()).startContainer(any(), any());
    }

    @Test
    @DisplayName("Should throw exception when trying to start already running container")
    void testStartContainerAlreadyRunning() {
        // Given
        ContainerStatusDto runningStatus = new ContainerStatusDto();
        runningStatus.setExists(true);
        runningStatus.setRunning(true);
        runningStatus.setContainerName("sillytavern");
        
        when(dockerService.getContainerStatus(sshConnection, "sillytavern"))
                .thenReturn(runningStatus);
        
        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
                () -> sillyTavernService.startContainer(sshConnection));
        
        assertTrue(exception.getMessage().contains("is already running"));
        verify(dockerService, never()).startContainer(any(), any());
    }

    @Test
    @DisplayName("Should stop container successfully")
    void testStopContainer() throws Exception {
        // Given
        ContainerStatusDto runningStatus = new ContainerStatusDto();
        runningStatus.setExists(true);
        runningStatus.setRunning(true);
        runningStatus.setContainerName("sillytavern");
        
        when(dockerService.getContainerStatus(sshConnection, "sillytavern"))
                .thenReturn(runningStatus);
        
        // When
        sillyTavernService.stopContainer(sshConnection);
        
        // Then
        verify(dockerService).getContainerStatus(sshConnection, "sillytavern");
        verify(dockerService).stopContainer(sshConnection, "sillytavern");
    }

    @Test
    @DisplayName("Should throw exception when trying to stop non-running container")
    void testStopContainerNotRunning() {
        // Given
        ContainerStatusDto stoppedStatus = ContainerStatusDto.stopped("sillytavern");
        when(dockerService.getContainerStatus(sshConnection, "sillytavern"))
                .thenReturn(stoppedStatus);
        
        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
                () -> sillyTavernService.stopContainer(sshConnection));
        
        assertTrue(exception.getMessage().contains("is not running"));
        verify(dockerService, never()).stopContainer(any(), any());
    }

    @Test
    @DisplayName("Should restart container successfully")
    void testRestartContainer() throws Exception {
        // Given
        ContainerStatusDto existingStatus = new ContainerStatusDto();
        existingStatus.setExists(true);
        existingStatus.setContainerName("sillytavern");
        
        when(dockerService.getContainerStatus(sshConnection, "sillytavern"))
                .thenReturn(existingStatus);
        
        // When
        sillyTavernService.restartContainer(sshConnection);
        
        // Then
        verify(dockerService).getContainerStatus(sshConnection, "sillytavern");
        verify(dockerService).restartContainer(sshConnection, "sillytavern");
    }

    @Test
    @DisplayName("Should upgrade container successfully with progress updates")
    void testUpgradeContainer() throws Exception {
        // Given
        ContainerStatusDto existingStatus = new ContainerStatusDto();
        existingStatus.setExists(true);
        existingStatus.setRunning(true);
        existingStatus.setContainerName("sillytavern");
        existingStatus.setImage("ghcr.io/sillytavern/sillytavern:latest");
        
        when(dockerService.getContainerStatus(sshConnection, "sillytavern"))
                .thenReturn(existingStatus);
        when(dockerService.pullImage(eq(sshConnection), eq("ghcr.io/sillytavern/sillytavern:latest"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        
        java.util.List<String> progressMessages = new java.util.ArrayList<>();
        Consumer<String> progressCallback = progressMessages::add;
        
        // When
        CompletableFuture<Void> upgradeFuture = sillyTavernService.upgradeContainer(
                sshConnection, "sillytavern", progressCallback);
        
        upgradeFuture.get(10, TimeUnit.SECONDS);
        
        // Then
        verify(dockerService).stopContainer(sshConnection, "sillytavern");
        verify(dockerService).pullImage(eq(sshConnection), eq("ghcr.io/sillytavern/sillytavern:latest"), any());
        verify(dockerService).startContainer(sshConnection, "sillytavern");
        
        // Verify progress messages
        assertFalse(progressMessages.isEmpty());
        assertTrue(progressMessages.stream().anyMatch(msg -> msg.contains("Stopping container")));
        assertTrue(progressMessages.stream().anyMatch(msg -> msg.contains("Pulling latest image")));
        assertTrue(progressMessages.stream().anyMatch(msg -> msg.contains("Starting container")));
        assertTrue(progressMessages.stream().anyMatch(msg -> msg.contains("Upgrade completed successfully")));
    }

    @Test
    @DisplayName("Should delete container successfully without data removal")
    void testDeleteContainerWithoutData() throws Exception {
        // Given
        ContainerStatusDto runningStatus = new ContainerStatusDto();
        runningStatus.setExists(true);
        runningStatus.setRunning(true);
        runningStatus.setContainerName("sillytavern");
        
        when(dockerService.getContainerStatus(sshConnection, "sillytavern"))
                .thenReturn(runningStatus);
        
        // When
        sillyTavernService.deleteContainer(sshConnection, false);
        
        // Then
        verify(dockerService).getContainerStatus(sshConnection, "sillytavern");
        verify(dockerService).stopContainer(sshConnection, "sillytavern");
        verify(dockerService).removeContainer(sshConnection, "sillytavern", true);
    }

    @Test
    @DisplayName("Should delete container successfully with data removal")
    void testDeleteContainerWithData() throws Exception {
        // Given
        ContainerStatusDto stoppedStatus = ContainerStatusDto.stopped("sillytavern");
        when(dockerService.getContainerStatus(sshConnection, "sillytavern"))
                .thenReturn(stoppedStatus);
        
        // When
        sillyTavernService.deleteContainer(sshConnection, true);
        
        // Then
        verify(dockerService).getContainerStatus(sshConnection, "sillytavern");
        verify(dockerService, never()).stopContainer(any(), any()); // Container was already stopped
        verify(dockerService).removeContainer(sshConnection, "sillytavern", true);
        // Note: Data removal implementation is not yet completed in the service
    }

    @Test
    @DisplayName("Should get container logs successfully")
    void testGetContainerLogs() throws Exception {
        // Given
        LogRequestDto logRequest = LogRequestDto.builder()
                .containerName("sillytavern")
                .days(3)
                .tailLines(500)
                .build();
        
        java.util.List<String> expectedLogs = java.util.List.of(
                "2024-01-01 10:00:01 [INFO] SillyTavern starting...",
                "2024-01-01 10:00:02 [INFO] Server listening on port 8000",
                "2024-01-01 10:00:03 [INFO] Ready to accept connections"
        );
        
        when(dockerService.getContainerLogs(sshConnection, "sillytavern", 500, 3))
                .thenReturn(expectedLogs);
        
        // When
        java.util.List<String> result = sillyTavernService.getContainerLogs(sshConnection, logRequest);
        
        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(expectedLogs, result);
        verify(dockerService).getContainerLogs(sshConnection, "sillytavern", 500, 3);
    }

    @Test
    @DisplayName("Should handle container logs retrieval failure gracefully")
    void testGetContainerLogsFailure() throws Exception {
        // Given
        LogRequestDto logRequest = LogRequestDto.builder()
                .containerName("non-existent")
                .days(1)
                .tailLines(100)
                .build();
        
        when(dockerService.getContainerLogs(sshConnection, "non-existent", 100, 1))
                .thenThrow(new RuntimeException("Container not found"));
        
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> sillyTavernService.getContainerLogs(sshConnection, logRequest));
        
        assertTrue(exception.getMessage().contains("Container not found"));
        verify(dockerService).getContainerLogs(sshConnection, "non-existent", 100, 1);
    }
}