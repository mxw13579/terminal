package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for container status information.
 * Provides detailed information about the SillyTavern container state.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContainerStatusDto {
    
    /**
     * Whether the container is currently running
     */
    private Boolean running = false;
    
    /**
     * Container uptime in seconds (null if not running)
     */
    private Long uptimeSeconds;
    
    /**
     * Memory usage in MB
     */
    private Long memoryUsageMB;
    
    /**
     * CPU usage percentage
     */
    private Double cpuUsagePercent;
    
    /**
     * Port the container is exposed on
     */
    private Integer port;
    
    /**
     * Container status string from Docker
     */
    private String status;
    
    /**
     * Container health status
     */
    private String health;
    
    /**
     * When the status was last updated
     */
    private LocalDateTime lastUpdated;
    
    /**
     * Container ID
     */
    private String containerId;
    
    /**
     * Container name
     */
    private String containerName;
    
    /**
     * Docker image being used
     */
    private String image;
    
    /**
     * Whether the container exists (but may not be running)
     */
    private Boolean exists = false;
    
    /**
     * Error message if there was a problem getting container status
     */
    private String error;
    
    public static ContainerStatusDto notExists() {
        ContainerStatusDto status = new ContainerStatusDto();
        status.setExists(false);
        status.setRunning(false);
        status.setStatus("Container not found");
        status.setLastUpdated(LocalDateTime.now());
        return status;
    }
    
    public static ContainerStatusDto dockerNotAvailable() {
        ContainerStatusDto status = new ContainerStatusDto();
        status.setExists(false);
        status.setRunning(false);
        status.setStatus("Docker not available");
        status.setError("Docker is not installed or not accessible. Please install Docker first.");
        status.setLastUpdated(LocalDateTime.now());
        return status;
    }
    
    public static ContainerStatusDto stopped(String containerName) {
        ContainerStatusDto status = new ContainerStatusDto();
        status.setExists(true);
        status.setRunning(false);
        status.setContainerName(containerName);
        status.setStatus("Stopped");
        status.setLastUpdated(LocalDateTime.now());
        return status;
    }
}