package com.fufu.terminal.service.sillytavern;

import com.fufu.terminal.dto.sillytavern.SystemInfoDto;
import com.fufu.terminal.model.CommandResult;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.SshCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for detecting system requirements and capabilities.
 * Checks if the system can run SillyTavern containers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemDetectionService {
    
    private final SshCommandService sshCommandService;
    
    private static final long MIN_DISK_SPACE_MB = 500;
    private static final long MIN_MEMORY_MB = 512;
    
    /**
     * Validate system requirements for SillyTavern deployment
     */
    public SystemInfoDto validateSystemRequirements(SshConnection connection) {
        log.info("Validating system requirements for SillyTavern");
        
        SystemInfoDto systemInfo = new SystemInfoDto();
        List<String> checks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        try {
            // Detect OS type
            systemInfo.setOsType(detectOsType(connection));
            checks.add("✓ Operating system detected: " + systemInfo.getOsType());
            
            // Check Docker availability with better error messages
            if (checkDockerAvailability(connection)) {
                systemInfo.setDockerInstalled(true);
                systemInfo.setDockerVersion(getDockerVersion(connection));
                checks.add("✓ Docker is available: " + systemInfo.getDockerVersion());
            } else {
                systemInfo.setDockerInstalled(false);
                // Provide installation guidance
                checks.add("✗ Docker is not available");
                warnings.add("Docker需要安装。请运行以下命令安装Docker:");
                warnings.add("curl -fsSL https://get.docker.com -o get-docker.sh");
                warnings.add("sudo sh get-docker.sh");
                warnings.add("或者访问 https://docs.docker.com/engine/install/ 查看详细安装指南");
            }
            
            // Check sudo access
            if (checkSudoAccess(connection)) {
                systemInfo.setHasRootAccess(true);
                checks.add("✓ Sudo access is available");
            } else {
                systemInfo.setHasRootAccess(false);
                checks.add("✗ Sudo access is not available");
            }
            
            // Check disk space
            Long diskSpace = getAvailableDiskSpace(connection);
            systemInfo.setAvailableDiskSpaceMB(diskSpace);
            if (diskSpace != null && diskSpace >= MIN_DISK_SPACE_MB) {
                checks.add("✓ Sufficient disk space: " + diskSpace + " MB available");
            } else {
                checks.add("✗ Insufficient disk space: " + (diskSpace != null ? diskSpace : "unknown") + " MB available, need at least " + MIN_DISK_SPACE_MB + " MB");
            }
            
            // Check memory
            Map<String, Long> memoryInfo = getMemoryInfo(connection);
            if (memoryInfo != null) {
                systemInfo.setTotalMemoryMB(memoryInfo.get("total"));
                systemInfo.setAvailableMemoryMB(memoryInfo.get("available"));
                
                if (memoryInfo.get("available") >= MIN_MEMORY_MB) {
                    checks.add("✓ Sufficient memory: " + memoryInfo.get("available") + " MB available");
                } else {
                    checks.add("✗ Insufficient memory: " + memoryInfo.get("available") + " MB available, need at least " + MIN_MEMORY_MB + " MB");
                }
            } else {
                checks.add("? Unable to determine memory information");
            }
            
            // Check CPU info
            Integer cpuCores = getCpuCoreCount(connection);
            systemInfo.setCpuCores(cpuCores);
            if (cpuCores != null && cpuCores > 0) {
                checks.add("✓ CPU cores: " + cpuCores);
            } else {
                checks.add("? Unable to determine CPU information");
            }
            
            // Determine if requirements are met
            boolean meetsRequirements = systemInfo.getDockerInstalled() && 
                                      systemInfo.getHasRootAccess() &&
                                      (diskSpace != null && diskSpace >= MIN_DISK_SPACE_MB);
            
            systemInfo.setMeetsRequirements(meetsRequirements);
            systemInfo.setRequirementChecks(checks);
            systemInfo.setWarnings(warnings);
            
            log.info("System requirements validation completed. Meets requirements: {}", meetsRequirements);
            
        } catch (Exception e) {
            log.error("Error validating system requirements", e);
            checks.add("✗ Error during system validation: " + e.getMessage());
            systemInfo.setMeetsRequirements(false);
            systemInfo.setRequirementChecks(checks);
        }
        
        return systemInfo;
    }
    
    /**
     * Check if specific ports are available
     */
    public Map<Integer, Boolean> checkPortAvailability(SshConnection connection, List<Integer> ports) {
        Map<Integer, Boolean> availability = new HashMap<>();
        
        for (Integer port : ports) {
            try {
                // Try to connect to the port to see if it's in use
                boolean available = executeCommand(connection, "netstat -ln | grep -q ':" + port + " ' && echo 'used' || echo 'available'")
                    .contains("available");
                availability.put(port, available);
            } catch (Exception e) {
                log.warn("Unable to check port {} availability: {}", port, e.getMessage());
                availability.put(port, false);
            }
        }
        
        return availability;
    }
    
    private String detectOsType(SshConnection connection) {
        try {
            String result = executeCommand(connection, "uname -s");
            if (result.toLowerCase().contains("linux")) {
                return "Linux";
            } else if (result.toLowerCase().contains("darwin")) {
                return "macOS";
            } else {
                return result.trim();
            }
        } catch (Exception e) {
            log.warn("Unable to detect OS type", e);
            return "Unknown";
        }
    }
    
    private boolean checkDockerAvailability(SshConnection connection) {
        try {
            // First try direct docker command (for non-root users with docker group)
            try {
                String result = executeCommand(connection, "docker --version");
                if (result.toLowerCase().contains("docker version")) {
                    log.debug("Docker available without sudo");
                    return true;
                }
            } catch (Exception e) {
                log.debug("Docker not available without sudo: {}", e.getMessage());
            }
            
            // Then try with sudo
            try {
                String result = executeCommand(connection, "sudo docker --version");
                if (result.toLowerCase().contains("docker version")) {
                    log.debug("Docker available with sudo");
                    return true;
                }
            } catch (Exception e) {
                log.debug("Docker not available with sudo: {}", e.getMessage());
            }
            
            // Check if docker is installed but not in PATH
            try {
                String result = executeCommand(connection, "which docker || whereis docker");
                if (!result.trim().isEmpty() && !result.contains("not found")) {
                    log.info("Docker found at: {}", result.trim());
                    return false; // Found but not accessible
                }
            } catch (Exception e) {
                log.debug("Docker location check failed: {}", e.getMessage());
            }
            
            return false;
        } catch (Exception e) {
            log.debug("Docker availability check failed: {}", e.getMessage());
            return false;
        }
    }
    
    private String getDockerVersion(SshConnection connection) {
        try {
            // Try without sudo first
            try {
                String result = executeCommand(connection, "docker --version");
                if (result.toLowerCase().contains("docker version")) {
                    return result.trim();
                }
            } catch (Exception e) {
                log.debug("Docker version check without sudo failed: {}", e.getMessage());
            }
            
            // Try with sudo
            return executeCommand(connection, "sudo docker --version").trim();
        } catch (Exception e) {
            log.debug("Docker version check failed: {}", e.getMessage());
            return "Unknown";
        }
    }
    
    private boolean checkSudoAccess(SshConnection connection) {
        try {
            String result = executeCommand(connection, "sudo -n true 2>&1");
            return !result.toLowerCase().contains("password") && !result.toLowerCase().contains("sorry");
        } catch (Exception e) {
            log.debug("Sudo access check failed: {}", e.getMessage());
            return false;
        }
    }
    
    private Long getAvailableDiskSpace(SshConnection connection) {
        try {
            // Try different approaches to get disk space
            String result;
            
            // First try: df -m . (current directory in MB)
            try {
                result = executeCommand(connection, "df -m . | tail -1 | awk '{print $4}'");
                if (!result.trim().isEmpty() && result.trim().matches("\\d+")) {
                    return Long.parseLong(result.trim());
                }
            } catch (Exception e) {
                log.debug("First disk space check failed: {}", e.getMessage());
            }
            
            // Second try: df -BM (force MB units)
            try {
                result = executeCommand(connection, "df -BM . | tail -1 | awk '{print $4}' | sed 's/M//'");
                if (!result.trim().isEmpty() && result.trim().matches("\\d+")) {
                    return Long.parseLong(result.trim());
                }
            } catch (Exception e) {
                log.debug("Second disk space check failed: {}", e.getMessage());
            }
            
            // Third try: df in blocks and convert
            try {
                result = executeCommand(connection, "df . | tail -1 | awk '{print $4}'");
                if (!result.trim().isEmpty() && result.trim().matches("\\d+")) {
                    // Convert from 1K blocks to MB
                    return Long.parseLong(result.trim()) / 1024;
                }
            } catch (Exception e) {
                log.debug("Third disk space check failed: {}", e.getMessage());
            }
            
            // Fourth try: get the full df output for debugging
            try {
                result = executeCommand(connection, "df -h .");
                log.info("Full df output for debugging: {}", result);
            } catch (Exception e) {
                log.debug("Debug df command failed: {}", e.getMessage());
            }
            
            return null;
        } catch (Exception e) {
            log.warn("Unable to determine disk space", e);
            return null;
        }
    }
    
    private Map<String, Long> getMemoryInfo(SshConnection connection) {
        try {
            String result = executeCommand(connection, "free -m | grep '^Mem:' | awk '{print $2 \" \" $7}'");
            String[] parts = result.trim().split("\\s+");
            if (parts.length >= 2) {
                Map<String, Long> memInfo = new HashMap<>();
                memInfo.put("total", Long.parseLong(parts[0]));
                memInfo.put("available", Long.parseLong(parts[1]));
                return memInfo;
            }
        } catch (Exception e) {
            log.warn("Unable to determine memory information", e);
        }
        return null;
    }
    
    private Integer getCpuCoreCount(SshConnection connection) {
        try {
            String result = executeCommand(connection, "nproc");
            return Integer.parseInt(result.trim());
        } catch (Exception e) {
            log.warn("Unable to determine CPU core count", e);
            return null;
        }
    }
    
    private String executeCommand(SshConnection connection, String command) throws Exception {
        try {
            CommandResult result = sshCommandService.executeCommand(connection.getJschSession(), command);
            
            if (result.exitStatus() != 0) {
                // For system detection, some commands may fail normally (e.g., when checking if something exists)
                // Log as debug rather than warn for expected failures
                log.debug("Command returned non-zero exit code {}: {} - {}", 
                    result.exitStatus(), command, result.stderr());
                return result.stdout(); // Still return stdout as it may contain useful info
            }
            
            return result.stdout();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Command execution was interrupted: " + command, e);
        }
    }
}