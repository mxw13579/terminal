package com.fufu.terminal.dto.sillytavern;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * DTO for system information and requirements validation.
 * Used to check if the system can run SillyTavern containers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemInfoDto {
    
    /**
     * Operating system type
     */
    private String osType;
    
    /**
     * Whether Docker is installed and accessible
     */
    private Boolean dockerInstalled = false;
    
    /**
     * Whether Docker daemon is running
     */
    private Boolean dockerRunning = false;
    
    /**
     * Docker version if available
     */
    private String dockerVersion;
    
    /**
     * Whether current user has sudo privileges
     */
    private Boolean hasRootAccess = false;
    
    /**
     * Whether sufficient disk space is available
     */
    private Boolean sufficientDiskSpace = false;
    
    /**
     * Whether internet connectivity is available
     */
    private Boolean hasInternetAccess = true;
    
    /**
     * Available port range for containers
     */
    private String availablePortRange;
    
    /**
     * Available disk space in MB
     */
    private Long availableDiskSpaceMB;
    
    /**
     * Total system memory in MB
     */
    private Long totalMemoryMB;
    
    /**
     * Available memory in MB
     */
    private Long availableMemoryMB;
    
    /**
     * Number of CPU cores
     */
    private Integer cpuCores;
    
    /**
     * Whether system meets minimum requirements
     */
    private Boolean meetsRequirements = false;
    
    /**
     * List of requirement check results
     */
    private java.util.List<String> requirementChecks;
    
    /**
     * Any warnings about system configuration
     */
    private java.util.List<String> warnings;
    
    /**
     * Port availability check results
     */
    private java.util.Map<Integer, Boolean> portAvailability;
    
    public static SystemInfoDto requirementsNotMet(java.util.List<String> checks) {
        SystemInfoDto info = new SystemInfoDto();
        info.setMeetsRequirements(false);
        info.setRequirementChecks(checks);
        return info;
    }
}