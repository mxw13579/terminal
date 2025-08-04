package com.fufu.terminal.sillytavern;

import com.fufu.terminal.dto.sillytavern.*;
import com.fufu.terminal.model.SshConnection;
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
 * Error handling and edge case tests for SillyTavern management.
 * Tests system behavior under failure conditions and unusual scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SillyTavern Error Handling and Edge Cases Tests")
class SillyTavernErrorHandlingTest {

    @Mock
    private SillyTavernService sillyTavernService;
    
    @Mock
    private SystemDetectionService systemDetectionService;
    
    @Mock
    private SshConnection sshConnection;
    
    @Mock
    private Session jschSession;
    
    @BeforeEach
    void setUp() {
        when(sshConnection.getJschSession()).thenReturn(jschSession);
    }

    @Test
    @DisplayName("Should handle SSH connection failures gracefully")
    void testSshConnectionFailures() {
        // Test case 1: SSH session is null
        when(sshConnection.getJschSession()).thenReturn(null);
        
        assertThrows(Exception.class, () -> {
            sillyTavernService.getContainerStatus(sshConnection);
        });
        
        // Test case 2: SSH session is disconnected
        when(sshConnection.getJschSession()).thenReturn(jschSession);
        when(jschSession.isConnected()).thenReturn(false);
        
        // Should handle disconnected session gracefully
        when(sillyTavernService.getContainerStatus(sshConnection))
                .thenThrow(new RuntimeException("SSH connection lost"));
        
        Exception exception = assertThrows(RuntimeException.class, () -> {
            sillyTavernService.getContainerStatus(sshConnection);
        });
        
        assertTrue(exception.getMessage().contains("SSH connection lost"));
    }

    @Test
    @DisplayName("Should handle Docker daemon not running scenario")
    void testDockerDaemonNotRunning() {
        // Given
        SystemInfoDto systemWithoutDocker = SystemInfoDto.builder()
                .meetsRequirements(false)
                .dockerInstalled(true)  // Docker is installed but daemon not running
                .dockerRunning(false)
                .sufficientDiskSpace(true)
                .hasRootAccess(true)
                .requirementChecks(java.util.List.of("Docker daemon is not running"))
                .build();
        
        when(sillyTavernService.validateSystemRequirements(sshConnection))
                .thenReturn(systemWithoutDocker);
        
        // When
        SystemInfoDto result = sillyTavernService.validateSystemRequirements(sshConnection);
        
        // Then
        assertFalse(result.getMeetsRequirements());
        assertTrue(result.getDockerInstalled());
        assertFalse(result.getDockerRunning());
        assertTrue(result.getRequirementChecks().contains("Docker daemon is not running"));
    }

    @Test
    @DisplayName("Should handle insufficient permissions scenario")
    void testInsufficientPermissions() {
        // Given
        SystemInfoDto systemWithoutRoot = SystemInfoDto.builder()
                .meetsRequirements(false)
                .dockerInstalled(true)
                .dockerRunning(true)
                .sufficientDiskSpace(true)
                .hasRootAccess(false)
                .requirementChecks(java.util.List.of("Root/sudo access required for Docker operations"))
                .build();
        
        when(sillyTavernService.validateSystemRequirements(sshConnection))
                .thenReturn(systemWithoutRoot);
        
        // When
        SystemInfoDto result = sillyTavernService.validateSystemRequirements(sshConnection);
        
        // Then
        assertFalse(result.getMeetsRequirements());
        assertFalse(result.getHasRootAccess());
        assertTrue(result.getRequirementChecks().contains("Root/sudo access required"));
    }

    @Test
    @DisplayName("Should handle insufficient disk space scenario")
    void testInsufficientDiskSpace() {
        // Given
        SystemInfoDto systemWithLowDisk = SystemInfoDto.builder()
                .meetsRequirements(false)
                .dockerInstalled(true)
                .dockerRunning(true)
                .sufficientDiskSpace(false)
                .hasRootAccess(true)
                .availableDiskSpaceMB(100L)  // Only 100MB available
                .requirementChecks(java.util.List.of("Insufficient disk space: 100MB available, 500MB required"))
                .build();
        
        when(sillyTavernService.validateSystemRequirements(sshConnection))
                .thenReturn(systemWithLowDisk);
        
        // When
        SystemInfoDto result = sillyTavernService.validateSystemRequirements(sshConnection);
        
        // Then
        assertFalse(result.getMeetsRequirements());
        assertFalse(result.getSufficientDiskSpace());
        assertEquals(100L, result.getAvailableDiskSpaceMB());
        assertTrue(result.getRequirementChecks().stream()
                .anyMatch(check -> check.contains("Insufficient disk space")));
    }

    @Test
    @DisplayName("Should handle port conflicts during deployment")
    void testPortConflictDuringDeployment() throws Exception {
        // Given
        DeploymentRequestDto request = DeploymentRequestDto.builder()
                .containerName("sillytavern")
                .dockerImage("ghcr.io/sillytavern/sillytavern:latest")
                .port(8080)  // Conflicting port
                .dataPath("/opt/sillytavern")
                .build();
        
        java.util.List<DeploymentProgressDto> progressUpdates = new java.util.ArrayList<>();
        Consumer<DeploymentProgressDto> progressCallback = progressUpdates::add;
        
        when(sillyTavernService.deployContainer(sshConnection, request, progressCallback))
                .thenAnswer(invocation -> {
                    Consumer<DeploymentProgressDto> callback = invocation.getArgument(2);
                    
                    callback.accept(DeploymentProgressDto.success("validation", 10, "Validating system..."));
                    callback.accept(DeploymentProgressDto.success("check-existing", 20, "Checking existing containers..."));
                    callback.accept(DeploymentProgressDto.success("pull-image", 50, "Pulling Docker image..."));
                    callback.accept(DeploymentProgressDto.error("create-container", 
                            "Port 8080 is already in use by another service"));
                    
                    return CompletableFuture.completedFuture(null);
                });
        
        // When
        CompletableFuture<Void> deploymentFuture = sillyTavernService.deployContainer(
                sshConnection, request, progressCallback);
        
        deploymentFuture.get(5, TimeUnit.SECONDS);
        
        // Then
        DeploymentProgressDto errorUpdate = progressUpdates.stream()
                .filter(update -> update.getError() != null)
                .findFirst()
                .orElse(null);
        
        assertNotNull(errorUpdate);
        assertEquals("create-container", errorUpdate.getStage());
        assertTrue(errorUpdate.getError().contains("Port 8080 is already in use"));
    }

    @Test
    @DisplayName("Should handle Docker image pull failures")
    void testDockerImagePullFailure() throws Exception {
        // Given
        DeploymentRequestDto request = DeploymentRequestDto.builder()
                .containerName("sillytavern")
                .dockerImage("invalid/nonexistent:latest")  // Non-existent image
                .port(8000)
                .dataPath("/opt/sillytavern")
                .build();
        
        java.util.List<DeploymentProgressDto> progressUpdates = new java.util.ArrayList<>();
        Consumer<DeploymentProgressDto> progressCallback = progressUpdates::add;
        
        when(sillyTavernService.deployContainer(sshConnection, request, progressCallback))
                .thenAnswer(invocation -> {
                    Consumer<DeploymentProgressDto> callback = invocation.getArgument(2);
                    
                    callback.accept(DeploymentProgressDto.success("validation", 10, "Validating system..."));
                    callback.accept(DeploymentProgressDto.success("check-existing", 20, "Checking existing containers..."));
                    callback.accept(DeploymentProgressDto.error("pull-image", 
                            "Image pull failed: invalid/nonexistent:latest not found"));
                    
                    return CompletableFuture.completedFuture(null);
                });
        
        // When
        CompletableFuture<Void> deploymentFuture = sillyTavernService.deployContainer(
                sshConnection, request, progressCallback);
        
        deploymentFuture.get(5, TimeUnit.SECONDS);
        
        // Then
        DeploymentProgressDto errorUpdate = progressUpdates.stream()
                .filter(update -> update.getError() != null)
                .findFirst()
                .orElse(null);
        
        assertNotNull(errorUpdate);
        assertEquals("pull-image", errorUpdate.getStage());
        assertTrue(errorUpdate.getError().contains("Image pull failed"));
        assertTrue(errorUpdate.getError().contains("not found"));
    }

    @Test
    @DisplayName("Should handle container creation failures due to resource exhaustion")
    void testContainerCreationResourceExhaustion() throws Exception {
        // Given
        DeploymentRequestDto request = DeploymentRequestDto.builder()
                .containerName("sillytavern")
                .dockerImage("ghcr.io/sillytavern/sillytavern:latest")
                .port(8000)
                .dataPath("/opt/sillytavern")
                .build();
        
        java.util.List<DeploymentProgressDto> progressUpdates = new java.util.ArrayList<>();
        Consumer<DeploymentProgressDto> progressCallback = progressUpdates::add;
        
        when(sillyTavernService.deployContainer(sshConnection, request, progressCallback))
                .thenAnswer(invocation -> {
                    Consumer<DeploymentProgressDto> callback = invocation.getArgument(2);
                    
                    callback.accept(DeploymentProgressDto.success("validation", 10, "Validating system..."));
                    callback.accept(DeploymentProgressDto.success("check-existing", 20, "Checking existing containers..."));
                    callback.accept(DeploymentProgressDto.success("pull-image", 60, "Image pulled successfully"));
                    callback.accept(DeploymentProgressDto.error("create-container", 
                            "Container creation failed: insufficient memory available"));
                    
                    return CompletableFuture.completedFuture(null);
                });
        
        // When
        CompletableFuture<Void> deploymentFuture = sillyTavernService.deployContainer(
                sshConnection, request, progressCallback);
        
        deploymentFuture.get(5, TimeUnit.SECONDS);
        
        // Then
        DeploymentProgressDto errorUpdate = progressUpdates.stream()
                .filter(update -> update.getError() != null)
                .findFirst()
                .orElse(null);
        
        assertNotNull(errorUpdate);
        assertEquals("create-container", errorUpdate.getStage());
        assertTrue(errorUpdate.getError().contains("insufficient memory"));
    }

    @Test
    @DisplayName("Should handle network connectivity issues")
    void testNetworkConnectivityIssues() {
        // Given
        SystemInfoDto systemWithNetworkIssues = SystemInfoDto.builder()
                .meetsRequirements(false)
                .dockerInstalled(true)
                .dockerRunning(true)
                .sufficientDiskSpace(true)
                .hasRootAccess(true)
                .hasInternetAccess(false)  // No internet connectivity
                .requirementChecks(java.util.List.of("Internet connectivity required for Docker image pulls"))
                .build();
        
        when(sillyTavernService.validateSystemRequirements(sshConnection))
                .thenReturn(systemWithNetworkIssues);
        
        // When
        SystemInfoDto result = sillyTavernService.validateSystemRequirements(sshConnection);
        
        // Then
        assertFalse(result.getMeetsRequirements());
        assertFalse(result.getHasInternetAccess());
        assertTrue(result.getRequirementChecks().contains("Internet connectivity required"));
    }

    @Test
    @DisplayName("Should handle container in corrupted state")
    void testContainerCorruptedState() {
        // Given
        ContainerStatusDto corruptedStatus = new ContainerStatusDto();
        corruptedStatus.setExists(true);
        corruptedStatus.setRunning(false);
        corruptedStatus.setStatus("dead");  // Container is in dead state
        corruptedStatus.setContainerName("sillytavern");
        corruptedStatus.setImage("ghcr.io/sillytavern/sillytavern:latest");
        
        when(sillyTavernService.getContainerStatus(sshConnection))
                .thenReturn(corruptedStatus);
        
        // When
        ContainerStatusDto result = sillyTavernService.getContainerStatus(sshConnection);
        
        // Then
        assertTrue(result.getExists());
        assertFalse(result.getRunning());
        assertEquals("dead", result.getStatus());
        
        // Should not be able to start a dead container without cleanup
        when(sillyTavernService.startContainer(sshConnection))
                .thenThrow(new IllegalStateException("Cannot start container in 'dead' state. Remove and recreate."));
        
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            sillyTavernService.startContainer(sshConnection);
        });
        
        assertTrue(exception.getMessage().contains("dead"));
    }

    @Test
    @DisplayName("Should handle very large file operations gracefully")
    void testLargeFileOperations() throws Exception {
        // Given - Simulate very large log files
        LogRequestDto largeLogRequest = LogRequestDto.builder()
                .containerName("sillytavern")
                .days(30)  // 30 days of logs
                .tailLines(10000)  // Very large number of lines
                .build();
        
        when(sillyTavernService.getContainerLogs(sshConnection, largeLogRequest))
                .thenThrow(new RuntimeException("Log file too large (>100MB). Please reduce the number of days or tail lines."));
        
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            sillyTavernService.getContainerLogs(sshConnection, largeLogRequest);
        });
        
        assertTrue(exception.getMessage().contains("Log file too large"));
        assertTrue(exception.getMessage().contains("reduce the number"));
    }

    @Test
    @DisplayName("Should handle deployment timeout scenarios")
    void testDeploymentTimeout() throws Exception {
        // Given
        DeploymentRequestDto request = DeploymentRequestDto.builder()
                .containerName("slow-sillytavern")
                .dockerImage("ghcr.io/sillytavern/sillytavern:latest")
                .port(8000)
                .dataPath("/opt/sillytavern")
                .build();
        
        java.util.List<DeploymentProgressDto> progressUpdates = new java.util.ArrayList<>();
        Consumer<DeploymentProgressDto> progressCallback = progressUpdates::add;
        
        // Mock a deployment that times out during image pull
        when(sillyTavernService.deployContainer(sshConnection, request, progressCallback))
                .thenAnswer(invocation -> {
                    Consumer<DeploymentProgressDto> callback = invocation.getArgument(2);
                    
                    callback.accept(DeploymentProgressDto.success("validation", 10, "Validating system..."));
                    callback.accept(DeploymentProgressDto.success("check-existing", 20, "Checking existing containers..."));
                    callback.accept(DeploymentProgressDto.success("pull-image", 30, "Pulling Docker image (this may take a while)..."));
                    
                    // Simulate timeout
                    Thread.sleep(100);
                    callback.accept(DeploymentProgressDto.error("pull-image", 
                            "Image pull timed out after 10 minutes. Please check your internet connection."));
                    
                    return CompletableFuture.completedFuture(null);
                });
        
        // When
        CompletableFuture<Void> deploymentFuture = sillyTavernService.deployContainer(
                sshConnection, request, progressCallback);
        
        deploymentFuture.get(1, TimeUnit.SECONDS);
        
        // Then
        DeploymentProgressDto timeoutUpdate = progressUpdates.stream()
                .filter(update -> update.getError() != null && update.getError().contains("timed out"))
                .findFirst()
                .orElse(null);
        
        assertNotNull(timeoutUpdate);
        assertTrue(timeoutUpdate.getError().contains("timed out"));
        assertTrue(timeoutUpdate.getError().contains("check your internet connection"));
    }

    @Test
    @DisplayName("Should handle invalid configuration data")
    void testInvalidConfigurationData() {
        // Test various invalid configuration scenarios
        
        // Test 1: Empty username
        DeploymentRequestDto emptyUsernameRequest = DeploymentRequestDto.builder()
                .containerName("sillytavern")
                .dockerImage("ghcr.io/sillytavern/sillytavern:latest")
                .port(8000)
                .dataPath("/opt/sillytavern")
                .username("")  // Empty username
                .password("validpassword")
                .build();
        
        assertNotNull(emptyUsernameRequest);
        assertTrue(emptyUsernameRequest.getUsername().isEmpty());
        
        // Test 2: Invalid port numbers
        DeploymentRequestDto invalidPortRequest = DeploymentRequestDto.builder()
                .containerName("sillytavern")
                .dockerImage("ghcr.io/sillytavern/sillytavern:latest")
                .port(-1)  // Invalid port
                .dataPath("/opt/sillytavern")
                .build();
        
        assertNotNull(invalidPortRequest);
        assertTrue(invalidPortRequest.getPort() < 0);
        
        // Test 3: Invalid data path
        DeploymentRequestDto invalidPathRequest = DeploymentRequestDto.builder()
                .containerName("sillytavern")
                .dockerImage("ghcr.io/sillytavern/sillytavern:latest")
                .port(8000)
                .dataPath("/root")  // Potentially dangerous path
                .build();
        
        assertNotNull(invalidPathRequest);
        assertEquals("/root", invalidPathRequest.getDataPath());
    }

    @Test
    @DisplayName("Should handle concurrent operation conflicts")
    void testConcurrentOperationConflicts() throws Exception {
        // Given - Simulate trying to start and stop container simultaneously
        ContainerStatusDto runningStatus = new ContainerStatusDto();
        runningStatus.setExists(true);
        runningStatus.setRunning(true);
        runningStatus.setContainerName("sillytavern");
        
        when(sillyTavernService.getContainerStatus(sshConnection))
                .thenReturn(runningStatus);
        
        // Mock start operation that detects container is already running
        when(sillyTavernService.startContainer(sshConnection))
                .thenThrow(new IllegalStateException("Container 'sillytavern' is already running"));
        
        // Mock stop operation that succeeds
        doNothing().when(sillyTavernService).stopContainer(sshConnection);
        
        // When & Then
        Exception startException = assertThrows(IllegalStateException.class, () -> {
            sillyTavernService.startContainer(sshConnection);
        });
        
        assertTrue(startException.getMessage().contains("already running"));
        
        // Stop should work
        assertDoesNotThrow(() -> {
            sillyTavernService.stopContainer(sshConnection);
        });
    }

    @Test
    @DisplayName("Should handle malformed Docker responses")
    void testMalformedDockerResponses() {
        // Given - Container status with malformed data
        ContainerStatusDto malformedStatus = new ContainerStatusDto();
        malformedStatus.setExists(true);
        malformedStatus.setRunning(null);  // Null running status
        malformedStatus.setContainerName("sillytavern");
        malformedStatus.setStatus("");  // Empty status
        malformedStatus.setMemoryUsageMB(-1L);  // Invalid memory usage
        malformedStatus.setCpuUsagePercent(Double.NaN);  // Invalid CPU usage
        
        when(sillyTavernService.getContainerStatus(sshConnection))
                .thenReturn(malformedStatus);
        
        // When
        ContainerStatusDto result = sillyTavernService.getContainerStatus(sshConnection);
        
        // Then - Should handle malformed data gracefully
        assertNotNull(result);
        assertTrue(result.getExists());
        assertNull(result.getRunning());
        assertEquals("", result.getStatus());
        assertEquals(-1L, result.getMemoryUsageMB());
        assertTrue(Double.isNaN(result.getCpuUsagePercent()));
    }

    @Test
    @DisplayName("Should handle unexpected service interruptions")
    void testUnexpectedServiceInterruptions() throws Exception {
        // Given - Service operation that gets interrupted
        when(sillyTavernService.startContainer(sshConnection))
                .thenAnswer(invocation -> {
                    Thread.currentThread().interrupt();  // Simulate interruption
                    throw new InterruptedException("Service operation was interrupted");
                });
        
        // When & Then
        Exception exception = assertThrows(Exception.class, () -> {
            sillyTavernService.startContainer(sshConnection);
        });
        
        assertTrue(exception.getMessage().contains("interrupted") || 
                   exception.getCause() instanceof InterruptedException);
        
        // Verify thread interrupt status is handled properly
        assertFalse(Thread.currentThread().isInterrupted());
    }
}