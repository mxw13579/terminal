package com.fufu.terminal.sillytavern;

import com.fufu.terminal.dto.sillytavern.ContainerStatusDto;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import com.fufu.terminal.service.sillytavern.DockerContainerService;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Docker container service operations.
 * Tests SSH command execution, Docker operations, and error handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Docker Container Service Tests")
class DockerContainerServiceTest {

    @Mock
    private SshCommandService sshCommandService;
    
    @Mock
    private SshConnection sshConnection;
    
    @Mock
    private Session jschSession;
    
    private DockerContainerService dockerContainerService;
    
    @BeforeEach
    void setUp() {
        dockerContainerService = new DockerContainerService(sshCommandService);
        when(sshConnection.getJschSession()).thenReturn(jschSession);
    }

    @Test
    @DisplayName("Should get running container status with all details")
    void testGetContainerStatusRunning() throws Exception {
        // Given
        String containerName = "test-sillytavern";
        
        // Mock container exists check
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("ps -a --filter name=" + containerName)))
                .thenReturn(new CommandResult(0, containerName, ""));
        
        // Mock container inspect
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("inspect " + containerName)))
                .thenReturn(new CommandResult(0, 
                        "true|running|ghcr.io/sillytavern/sillytavern:latest|abc123456789|2024-01-01T10:00:00", ""));
        
        // Mock port information
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("port " + containerName)))
                .thenReturn(new CommandResult(0, "8000/tcp -> 0.0.0.0:8000", ""));
        
        // Mock resource stats
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("stats --no-stream")))
                .thenReturn(new CommandResult(0, "256MiB / 2GiB|15.45%", ""));
        
        // When
        ContainerStatusDto result = dockerContainerService.getContainerStatus(sshConnection, containerName);
        
        // Then
        assertNotNull(result);
        assertTrue(result.getExists());
        assertTrue(result.getRunning());
        assertEquals(containerName, result.getContainerName());
        assertEquals("running", result.getStatus());
        assertEquals("ghcr.io/sillytavern/sillytavern:latest", result.getImage());
        assertEquals("abc123456789".substring(0, 12), result.getContainerId());
        assertEquals(8000, result.getPort());
        assertEquals(256L, result.getMemoryUsageMB());
        assertEquals(15.45, result.getCpuUsagePercent());
        assertNotNull(result.getLastUpdated());
        assertNotNull(result.getUptimeSeconds());
        
        verify(sshCommandService, times(4)).executeCommand(eq(jschSession), any());
    }

    @Test
    @DisplayName("Should return not exists status when container doesn't exist")
    void testGetContainerStatusNotExists() throws Exception {
        // Given
        String containerName = "non-existent-container";
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("ps -a --filter name=" + containerName)))
                .thenReturn(new CommandResult(0, "", ""));
        
        // When
        ContainerStatusDto result = dockerContainerService.getContainerStatus(sshConnection, containerName);
        
        // Then
        assertNotNull(result);
        assertFalse(result.getExists());
        assertFalse(result.getRunning());
        assertEquals("Container not found", result.getStatus());
        assertNotNull(result.getLastUpdated());
        
        verify(sshCommandService, times(1)).executeCommand(eq(jschSession), any());
    }

    @Test
    @DisplayName("Should get stopped container status")
    void testGetContainerStatusStopped() throws Exception {
        // Given
        String containerName = "stopped-container";
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("ps -a --filter name=" + containerName)))
                .thenReturn(new CommandResult(0, containerName, ""));
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("inspect " + containerName)))
                .thenReturn(new CommandResult(0, 
                        "false|exited|ghcr.io/sillytavern/sillytavern:latest|def987654321|2024-01-01T10:00:00", ""));
        
        // When
        ContainerStatusDto result = dockerContainerService.getContainerStatus(sshConnection, containerName);
        
        // Then
        assertNotNull(result);
        assertTrue(result.getExists());
        assertFalse(result.getRunning());
        assertEquals("exited", result.getStatus());
        assertEquals("ghcr.io/sillytavern/sillytavern:latest", result.getImage());
        assertEquals("def987654321".substring(0, 12), result.getContainerId());
        assertNull(result.getUptimeSeconds()); // No uptime for stopped container
        
        verify(sshCommandService, times(2)).executeCommand(eq(jschSession), any());
    }

    @Test
    @DisplayName("Should handle command execution errors gracefully")
    void testGetContainerStatusCommandError() throws Exception {
        // Given
        String containerName = "error-container";
        
        when(sshCommandService.executeCommand(eq(jschSession), any()))
                .thenReturn(new CommandResult(1, "", "Docker daemon not running"));
        
        // When
        ContainerStatusDto result = dockerContainerService.getContainerStatus(sshConnection, containerName);
        
        // Then
        assertNotNull(result);
        assertFalse(result.getExists());
        assertFalse(result.getRunning());
        assertTrue(result.getStatus().contains("Error"));
        assertTrue(result.getStatus().contains("Docker daemon not running"));
    }

    @Test
    @DisplayName("Should pull Docker image successfully with progress callback")
    void testPullImageSuccess() throws Exception {
        // Given
        String image = "ghcr.io/sillytavern/sillytavern:latest";
        java.util.List<String> progressMessages = new java.util.ArrayList<>();
        Consumer<String> progressCallback = progressMessages::add;
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("pull " + image)))
                .thenReturn(new CommandResult(0, "Successfully pulled image", ""));
        
        // When
        CompletableFuture<Void> pullFuture = dockerContainerService.pullImage(
                sshConnection, image, progressCallback);
        
        pullFuture.get(5, TimeUnit.SECONDS);
        
        // Then
        verify(sshCommandService).executeCommand(eq(jschSession), contains("pull " + image));
        assertFalse(progressMessages.isEmpty());
        assertTrue(progressMessages.stream().anyMatch(msg -> msg.contains("Pulling Docker image")));
        assertTrue(progressMessages.stream().anyMatch(msg -> msg.contains("Image pull completed")));
    }

    @Test
    @DisplayName("Should handle image pull failure")
    void testPullImageFailure() throws Exception {
        // Given
        String image = "invalid/image:tag";
        Consumer<String> progressCallback = msg -> {};
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("pull " + image)))
                .thenReturn(new CommandResult(1, "", "Image not found"));
        
        // When
        CompletableFuture<Void> pullFuture = dockerContainerService.pullImage(
                sshConnection, image, progressCallback);
        
        // Then
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> pullFuture.get(5, TimeUnit.SECONDS));
        
        assertTrue(exception.getCause().getMessage().contains("Failed to pull image"));
        verify(sshCommandService).executeCommand(eq(jschSession), contains("pull " + image));
    }

    @Test
    @DisplayName("Should create container successfully")
    void testCreateContainer() throws Exception {
        // Given
        String containerName = "new-sillytavern";
        String image = "ghcr.io/sillytavern/sillytavern:latest";
        int port = 8001;
        String dataPath = "/opt/sillytavern-data";
        String expectedContainerId = "abc123def456";
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("mkdir -p " + dataPath)))
                .thenReturn(new CommandResult(0, "", ""));
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("run -d --name " + containerName)))
                .thenReturn(new CommandResult(0, expectedContainerId, ""));
        
        // When
        String result = dockerContainerService.createContainer(
                sshConnection, containerName, image, port, dataPath);
        
        // Then
        assertEquals(expectedContainerId, result);
        verify(sshCommandService).executeCommand(eq(jschSession), contains("mkdir -p " + dataPath));
        verify(sshCommandService).executeCommand(eq(jschSession), 
                contains("run -d --name " + containerName + " -p " + port + ":8000"));
    }

    @Test
    @DisplayName("Should handle container creation failure")
    void testCreateContainerFailure() throws Exception {
        // Given
        String containerName = "failing-container";
        String image = "ghcr.io/sillytavern/sillytavern:latest";
        int port = 8001;
        String dataPath = "/opt/sillytavern-data";
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("mkdir -p " + dataPath)))
                .thenReturn(new CommandResult(0, "", ""));
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("run -d --name " + containerName)))
                .thenReturn(new CommandResult(1, "", "Port already in use"));
        
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> dockerContainerService.createContainer(
                        sshConnection, containerName, image, port, dataPath));
        
        assertTrue(exception.getMessage().contains("Port already in use"));
    }

    @Test
    @DisplayName("Should start container successfully")
    void testStartContainer() throws Exception {
        // Given
        String containerName = "stopped-container";
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("start " + containerName)))
                .thenReturn(new CommandResult(0, containerName, ""));
        
        // When
        dockerContainerService.startContainer(sshConnection, containerName);
        
        // Then
        verify(sshCommandService).executeCommand(eq(jschSession), 
                contains("start " + containerName));
    }

    @Test
    @DisplayName("Should stop container successfully")
    void testStopContainer() throws Exception {
        // Given
        String containerName = "running-container";
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("stop " + containerName)))
                .thenReturn(new CommandResult(0, containerName, ""));
        
        // When
        dockerContainerService.stopContainer(sshConnection, containerName);
        
        // Then
        verify(sshCommandService).executeCommand(eq(jschSession), 
                contains("stop " + containerName));
    }

    @Test
    @DisplayName("Should restart container successfully")
    void testRestartContainer() throws Exception {
        // Given
        String containerName = "running-container";
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("restart " + containerName)))
                .thenReturn(new CommandResult(0, containerName, ""));
        
        // When
        dockerContainerService.restartContainer(sshConnection, containerName);
        
        // Then
        verify(sshCommandService).executeCommand(eq(jschSession), 
                contains("restart " + containerName));
    }

    @Test
    @DisplayName("Should remove container successfully")
    void testRemoveContainer() throws Exception {
        // Given
        String containerName = "unwanted-container";
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("rm -f " + containerName)))
                .thenReturn(new CommandResult(0, containerName, ""));
        
        // When
        dockerContainerService.removeContainer(sshConnection, containerName, true);
        
        // Then
        verify(sshCommandService).executeCommand(eq(jschSession), 
                contains("rm -f " + containerName));
    }

    @Test
    @DisplayName("Should remove container without force flag")
    void testRemoveContainerNoForce() throws Exception {
        // Given
        String containerName = "stopped-container";
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                eq("sudo docker rm " + containerName)))
                .thenReturn(new CommandResult(0, containerName, ""));
        
        // When
        dockerContainerService.removeContainer(sshConnection, containerName, false);
        
        // Then
        verify(sshCommandService).executeCommand(eq(jschSession), 
                eq("sudo docker rm " + containerName));
    }

    @Test
    @DisplayName("Should get container logs successfully")
    void testGetContainerLogs() throws Exception {
        // Given
        String containerName = "logging-container";
        int tailLines = 100;
        int days = 3;
        
        String logOutput = "2024-01-01 10:00:01 [INFO] Starting application\n" +
                          "2024-01-01 10:00:02 [INFO] Database connected\n" +
                          "2024-01-01 10:00:03 [INFO] Server listening on port 8000";
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("logs --since 3d --tail 100 " + containerName)))
                .thenReturn(new CommandResult(0, logOutput, ""));
        
        // When
        List<String> result = dockerContainerService.getContainerLogs(
                sshConnection, containerName, tailLines, days);
        
        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.get(0).contains("Starting application"));
        assertTrue(result.get(1).contains("Database connected"));
        assertTrue(result.get(2).contains("Server listening on port 8000"));
        
        verify(sshCommandService).executeCommand(eq(jschSession), 
                contains("logs --since 3d --tail 100 " + containerName));
    }

    @Test
    @DisplayName("Should return empty logs when container has no logs")
    void testGetContainerLogsEmpty() throws Exception {
        // Given
        String containerName = "silent-container";
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("logs")))
                .thenReturn(new CommandResult(0, "", ""));
        
        // When
        List<String> result = dockerContainerService.getContainerLogs(
                sshConnection, containerName, 100, 1);
        
        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should handle logs command failure")
    void testGetContainerLogsFailure() throws Exception {
        // Given
        String containerName = "non-existent-container";
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("logs")))
                .thenReturn(new CommandResult(1, "", "No such container"));
        
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> dockerContainerService.getContainerLogs(
                        sshConnection, containerName, 100, 1));
        
        assertTrue(exception.getMessage().contains("No such container"));
    }

    @Test
    @DisplayName("Should handle interrupted command execution")
    void testInterruptedCommandHandling() throws Exception {
        // Given
        String containerName = "test-container";
        
        when(sshCommandService.executeCommand(eq(jschSession), any()))
                .thenThrow(new InterruptedException("Command interrupted"));
        
        // When & Then
        Exception exception = assertThrows(Exception.class, 
                () -> dockerContainerService.getContainerStatus(sshConnection, containerName));
        
        assertTrue(exception.getMessage().contains("Command execution was interrupted"));
    }

    @Test
    @DisplayName("Should parse resource usage correctly with GiB units")
    void testResourceUsageParsingGiB() throws Exception {
        // Given
        String containerName = "resource-container";
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("ps -a --filter name=" + containerName)))
                .thenReturn(new CommandResult(0, containerName, ""));
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("inspect " + containerName)))
                .thenReturn(new CommandResult(0, 
                        "true|running|test-image|abc123|2024-01-01T10:00:00", ""));
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("port " + containerName)))
                .thenReturn(new CommandResult(0, "", ""));
        
        // Mock stats with GiB memory usage
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("stats --no-stream")))
                .thenReturn(new CommandResult(0, "1.5GiB / 4GiB|25.75%", ""));
        
        // When
        ContainerStatusDto result = dockerContainerService.getContainerStatus(sshConnection, containerName);
        
        // Then
        assertEquals(1536L, result.getMemoryUsageMB()); // 1.5 GiB = 1536 MB
        assertEquals(25.75, result.getCpuUsagePercent());
    }

    @Test
    @DisplayName("Should handle malformed resource usage data gracefully")
    void testResourceUsageMalformedData() throws Exception {
        // Given
        String containerName = "malformed-container";
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("ps -a --filter name=" + containerName)))
                .thenReturn(new CommandResult(0, containerName, ""));
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("inspect " + containerName)))
                .thenReturn(new CommandResult(0, 
                        "true|running|test-image|abc123|2024-01-01T10:00:00", ""));
        
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("port " + containerName)))
                .thenReturn(new CommandResult(0, "", ""));
        
        // Mock malformed stats
        when(sshCommandService.executeCommand(eq(jschSession), 
                contains("stats --no-stream")))
                .thenReturn(new CommandResult(0, "invalid-format", ""));
        
        // When
        ContainerStatusDto result = dockerContainerService.getContainerStatus(sshConnection, containerName);
        
        // Then - should not crash, resource usage should be null
        assertNotNull(result);
        assertTrue(result.getExists());
        assertTrue(result.getRunning());
        assertNull(result.getMemoryUsageMB());
        assertNull(result.getCpuUsagePercent());
    }
}