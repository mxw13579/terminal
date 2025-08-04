package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.dto.sillytavern.ContainerStatusDto;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Service for managing Docker container operations.
 * Handles all Docker command execution and container lifecycle management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerContainerService {
    
    private final SshCommandService sshCommandService;
    
    private static final String DOCKER_COMMAND_PREFIX = "sudo docker";
    
    /**
     * Get detailed container status information
     */
    public ContainerStatusDto getContainerStatus(SshConnection connection, String containerName) {
        log.debug("Getting status for container: {}", containerName);
        
        try {
            // First check if Docker is available
            if (!isDockerAvailable(connection)) {
                log.warn("Docker is not available on the system");
                return ContainerStatusDto.dockerNotAvailable();
            }
            
            // Check if container exists
            String existsResult = executeCommand(connection, 
                DOCKER_COMMAND_PREFIX + " ps -a --filter name=" + containerName + " --format '{{.Names}}'");
            
            if (existsResult.trim().isEmpty()) {
                return ContainerStatusDto.notExists();
            }
            
            // Container exists, get detailed info
            ContainerStatusDto status = new ContainerStatusDto();
            status.setExists(true);
            status.setContainerName(containerName);
            status.setLastUpdated(LocalDateTime.now());
            
            // Get container info
            String containerInfo = executeCommand(connection,
                DOCKER_COMMAND_PREFIX + " inspect " + containerName + " --format " +
                "'{{.State.Running}}|{{.State.Status}}|{{.Config.Image}}|{{.Id}}|{{.State.StartedAt}}'");
            
            String[] infoParts = containerInfo.trim().split("\\|");
            if (infoParts.length >= 5) {
                status.setRunning(Boolean.parseBoolean(infoParts[0]));
                status.setStatus(infoParts[1]);
                status.setImage(infoParts[2]);
                status.setContainerId(infoParts[3].substring(0, Math.min(12, infoParts[3].length())));
                
                // Calculate uptime if running
                if (status.getRunning()) {
                    try {
                        LocalDateTime startTime = LocalDateTime.parse(infoParts[4].substring(0, 19), 
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                        long uptimeSeconds = java.time.Duration.between(startTime, LocalDateTime.now()).getSeconds();
                        status.setUptimeSeconds(uptimeSeconds);
                    } catch (Exception e) {
                        log.warn("Failed to parse container start time", e);
                    }
                }
            }
            
            // Get port information if running
            if (status.getRunning()) {
                try {
                    String portInfo = executeCommand(connection,
                        DOCKER_COMMAND_PREFIX + " port " + containerName);
                    // Parse port mapping (e.g., "8000/tcp -> 0.0.0.0:8000")
                    if (!portInfo.trim().isEmpty()) {
                        String[] portLines = portInfo.trim().split("\n");
                        for (String portLine : portLines) {
                            if (portLine.contains("->")) {
                                String[] portParts = portLine.split("->");
                                if (portParts.length > 1) {
                                    String hostPort = portParts[1].trim().split(":")[1];
                                    status.setPort(Integer.parseInt(hostPort));
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to get port information for container {}", containerName, e);
                }
                
                // Get resource usage
                getResourceUsage(connection, containerName, status);
            }
            
            return status;
            
        } catch (Exception e) {
            log.error("Error getting container status for {}", containerName, e);
            ContainerStatusDto errorStatus = ContainerStatusDto.notExists();
            errorStatus.setStatus("Error: " + e.getMessage());
            return errorStatus;
        }
    }
    
    /**
     * Pull Docker image with progress callback
     */
    public CompletableFuture<Void> pullImage(SshConnection connection, String image, 
                                           Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Pulling Docker image: {}", image);
                progressCallback.accept("Pulling Docker image: " + image);
                
                String result = executeCommand(connection, DOCKER_COMMAND_PREFIX + " pull " + image);
                
                progressCallback.accept("Image pull completed: " + image);
                log.info("Successfully pulled image: {}", image);
                
            } catch (Exception e) {
                log.error("Error pulling image: {}", image, e);
                throw new RuntimeException("Failed to pull image: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Create and start a new container
     */
    public String createContainer(SshConnection connection, String containerName, String image, 
                                 int port, String dataPath) throws Exception {
        log.info("Creating container: {} with image: {} on port: {}", containerName, image, port);
        
        // Create data directory if it doesn't exist
        executeCommand(connection, "mkdir -p " + dataPath);
        
        String createCommand = String.format(
            "%s run -d --name %s -p %d:8000 -v %s:/app/data %s",
            DOCKER_COMMAND_PREFIX, containerName, port, dataPath, image
        );
        
        String result = executeCommand(connection, createCommand);
        String containerId = result.trim();
        
        log.info("Container created with ID: {}", containerId);
        return containerId;
    }
    
    /**
     * Start an existing container
     */
    public void startContainer(SshConnection connection, String containerName) throws Exception {
        log.info("Starting container: {}", containerName);
        executeCommand(connection, DOCKER_COMMAND_PREFIX + " start " + containerName);
    }
    
    /**
     * Stop a container
     */
    public void stopContainer(SshConnection connection, String containerName) throws Exception {
        log.info("Stopping container: {}", containerName);
        executeCommand(connection, DOCKER_COMMAND_PREFIX + " stop " + containerName);
    }
    
    /**
     * Restart a container
     */
    public void restartContainer(SshConnection connection, String containerName) throws Exception {
        log.info("Restarting container: {}", containerName);
        executeCommand(connection, DOCKER_COMMAND_PREFIX + " restart " + containerName);
    }
    
    /**
     * Remove a container
     */
    public void removeContainer(SshConnection connection, String containerName, boolean force) throws Exception {
        log.info("Removing container: {} (force: {})", containerName, force);
        String command = DOCKER_COMMAND_PREFIX + " rm " + (force ? "-f " : "") + containerName;
        executeCommand(connection, command);
    }
    
    /**
     * Get container logs
     */
    public List<String> getContainerLogs(SshConnection connection, String containerName, 
                                       int tailLines, int days) throws Exception {
        log.debug("Getting logs for container: {} (tail: {}, days: {})", containerName, tailLines, days);
        
        String command = String.format("%s logs --since %dd --tail %d %s", 
            DOCKER_COMMAND_PREFIX, days, tailLines, containerName);
        
        String result = executeCommand(connection, command);
        
        List<String> logs = new ArrayList<>();
        if (!result.trim().isEmpty()) {
            String[] lines = result.split("\n");
            for (String line : lines) {
                logs.add(line);
            }
        }
        
        return logs;
    }
    
    /**
     * Get resource usage statistics
     */
    private void getResourceUsage(SshConnection connection, String containerName, ContainerStatusDto status) {
        try {
            String statsResult = executeCommand(connection,
                DOCKER_COMMAND_PREFIX + " stats --no-stream --format " +
                "'{{.MemUsage}}|{{.CPUPerc}}' " + containerName);
            
            String[] statsParts = statsResult.trim().split("\\|");
            if (statsParts.length >= 2) {
                // Parse memory usage (e.g., "123.4MiB / 2GiB")
                String memUsage = statsParts[0].trim();
                if (memUsage.contains("/")) {
                    String usedMem = memUsage.split("/")[0].trim();
                    if (usedMem.toLowerCase().contains("mib")) {
                        String memValue = usedMem.replaceAll("[^0-9.]", "");
                        status.setMemoryUsageMB(Math.round(Double.parseDouble(memValue)));
                    } else if (usedMem.toLowerCase().contains("gib")) {
                        String memValue = usedMem.replaceAll("[^0-9.]", "");
                        status.setMemoryUsageMB(Math.round(Double.parseDouble(memValue) * 1024));
                    }
                }
                
                // Parse CPU usage (e.g., "1.23%")
                String cpuUsage = statsParts[1].trim().replace("%", "");
                status.setCpuUsagePercent(Double.parseDouble(cpuUsage));
            }
        } catch (Exception e) {
            log.warn("Failed to get resource usage for container {}", containerName, e);
        }
    }
    
    /**
     * Execute a command via SSH connection
     */
    /**
     * Check if Docker is available on the system
     */
    private boolean isDockerAvailable(SshConnection connection) {
        try {
            // Try direct docker command first
            try {
                String result = executeCommand(connection, "docker --version");
                return result.toLowerCase().contains("docker version");
            } catch (Exception e) {
                // Try with sudo
                try {
                    String result = executeCommand(connection, "sudo docker --version");
                    return result.toLowerCase().contains("docker version");
                } catch (Exception e2) {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    private String executeCommand(SshConnection connection, String command) throws Exception {
        try {
            CommandResult result = sshCommandService.executeCommand(connection.getJschSession(), command);
            
            if (result.exitStatus() != 0) {
                String errorMsg = "Command failed with exit code " + result.exitStatus() + 
                               ": " + command;
                
                // Provide more helpful error messages for common Docker issues
                String stderr = result.stderr();
                if (stderr.contains("docker: command not found") || stderr.contains("docker: not found")) {
                    errorMsg = "Docker is not installed or not in PATH. Please install Docker first.";
                } else if (stderr.contains("Cannot connect to the Docker daemon")) {
                    errorMsg = "Docker daemon is not running. Please start Docker service.";
                } else if (stderr.contains("permission denied")) {
                    errorMsg = "Permission denied accessing Docker. User may need to be added to docker group or use sudo.";
                } else {
                    errorMsg += " - " + stderr;
                }
                
                log.warn("Command execution failed: {} - {}", command, errorMsg);
                throw new RuntimeException(errorMsg);
            }
            
            return result.stdout();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Command execution was interrupted: " + command, e);
        }
    }
}