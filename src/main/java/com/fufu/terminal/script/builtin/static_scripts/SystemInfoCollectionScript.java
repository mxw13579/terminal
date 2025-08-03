package com.fufu.terminal.script.builtin.static_scripts;

import com.fufu.terminal.entity.enums.ScriptType;
import com.fufu.terminal.entity.enums.VariableScope;
import com.fufu.terminal.script.ExecutableScript;
import com.fufu.terminal.script.context.ExecutionContext;
import com.fufu.terminal.script.model.ScriptParameter;
import com.fufu.terminal.script.model.ScriptResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * System Information Collection Script
 * Static Built-in Script that collects comprehensive system information
 * Outputs system variables for use by other scripts and decision making
 */
@Component
@Slf4j
public class SystemInfoCollectionScript implements ExecutableScript {
    
    @Override
    public String getId() {
        return "system-info-collection";
    }
    
    @Override
    public String getName() {
        return "System Information Collection";
    }
    
    @Override
    public String getDescription() {
        return "Collect comprehensive system information including OS details, hardware specs, installed software, and system status";
    }
    
    @Override
    public String getCategory() {
        return "System Information";
    }
    
    @Override
    public ScriptType getType() {
        return ScriptType.STATIC_BUILTIN;
    }
    
    @Override
    public List<ScriptParameter> getParameters() {
        return Collections.emptyList(); // Static scripts have no parameters
    }
    
    @Override
    public Set<String> getRequiredVariables() {
        return Collections.emptySet();
    }
    
    @Override
    public Set<String> getOutputVariables() {
        return Set.of("os_name", "os_version", "os_arch", "kernel_version", "hostname", 
                     "cpu_info", "memory_total", "memory_available", "disk_info", 
                     "network_interfaces", "docker_installed", "python_version", 
                     "java_version", "git_version", "package_manager");
    }
    
    @Override
    public Optional<Integer> getEstimatedExecutionTime() {
        return Optional.of(10); // 10 seconds
    }
    
    @Override
    public Set<String> getTags() {
        return Set.of("system", "hardware", "software", "os", "information");
    }
    
    @Override
    public CompletableFuture<ScriptResult> executeAsync(ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting system information collection for session: {}", context.getSessionId());
                
                long startTime = System.currentTimeMillis();
                Map<String, Object> systemInfo = new HashMap<>();
                StringBuilder report = new StringBuilder();
                
                // Collect OS information
                collectOsInfo(context, systemInfo, report);
                
                // Collect hardware information
                collectHardwareInfo(context, systemInfo, report);
                
                // Collect software information
                collectSoftwareInfo(context, systemInfo, report);
                
                // Collect network information
                collectNetworkInfo(context, systemInfo, report);
                
                // Set variables in session scope for use by other scripts
                context.setVariables(systemInfo, VariableScope.SESSION);
                
                long executionTime = System.currentTimeMillis() - startTime;
                
                String summary = String.format("System information collected: %s %s on %s", 
                    systemInfo.get("os_name"), systemInfo.get("os_version"), systemInfo.get("hostname"));
                
                return ScriptResult.builder()
                    .success(true)
                    .message(summary)
                    .outputVariables(systemInfo)
                    .executionTimeMs(executionTime)
                    .startTime(java.time.Instant.ofEpochMilli(startTime))
                    .endTime(java.time.Instant.now())
                    .sessionId(context.getSessionId())
                    .scriptId(getId())
                    .scriptVersion(getVersion())
                    .stdOut(report.toString())
                    .build();
                    
            } catch (Exception e) {
                log.error("Failed to collect system information for session: {}", context.getSessionId(), e);
                return ScriptResult.builder()
                    .success(false)
                    .errorMessage("System information collection failed: " + e.getMessage())
                    .errorCode("SYSTEM_INFO_ERROR")
                    .sessionId(context.getSessionId())
                    .scriptId(getId())
                    .build();
            }
        });
    }
    
    /**
     * Collect operating system information
     */
    private void collectOsInfo(ExecutionContext context, Map<String, Object> systemInfo, StringBuilder report) {
        report.append("=== Operating System Information ===\n");
        
        try {
            // OS Name and Version
            String osInfo = executeCommand(context, "cat /etc/os-release | grep PRETTY_NAME | cut -d'\"' -f2", "Unknown OS");
            systemInfo.put("os_name", extractOsName(osInfo));
            systemInfo.put("os_version", extractOsVersion(osInfo));
            report.append("OS: ").append(osInfo).append("\n");
            
            // Architecture
            String arch = executeCommand(context, "uname -m", "unknown");
            systemInfo.put("os_arch", arch);
            report.append("Architecture: ").append(arch).append("\n");
            
            // Kernel Version
            String kernel = executeCommand(context, "uname -r", "unknown");
            systemInfo.put("kernel_version", kernel);
            report.append("Kernel: ").append(kernel).append("\n");
            
            // Hostname
            String hostname = executeCommand(context, "hostname", "unknown");
            systemInfo.put("hostname", hostname);
            report.append("Hostname: ").append(hostname).append("\n");
            
            // Uptime
            String uptime = executeCommand(context, "uptime -p", "unknown");
            report.append("Uptime: ").append(uptime).append("\n");
            
            // Package Manager Detection
            String packageManager = detectPackageManager(context);
            systemInfo.put("package_manager", packageManager);
            report.append("Package Manager: ").append(packageManager).append("\n");
            
        } catch (Exception e) {
            log.warn("Failed to collect OS information", e);
            report.append("Failed to collect OS information: ").append(e.getMessage()).append("\n");
        }
        
        report.append("\n");
    }
    
    /**
     * Collect hardware information
     */
    private void collectHardwareInfo(ExecutionContext context, Map<String, Object> systemInfo, StringBuilder report) {
        report.append("=== Hardware Information ===\n");
        
        try {
            // CPU Information
            String cpuInfo = getCpuInfo(context);
            systemInfo.put("cpu_info", cpuInfo);
            report.append("CPU: ").append(cpuInfo).append("\n");
            
            // Memory Information
            Map<String, String> memoryInfo = getMemoryInfo(context);
            systemInfo.put("memory_total", memoryInfo.get("total"));
            systemInfo.put("memory_available", memoryInfo.get("available"));
            report.append("Memory Total: ").append(memoryInfo.get("total")).append("\n");
            report.append("Memory Available: ").append(memoryInfo.get("available")).append("\n");
            
            // Disk Information
            String diskInfo = getDiskInfo(context);
            systemInfo.put("disk_info", diskInfo);
            report.append("Disk Usage:\n").append(diskInfo).append("\n");
            
        } catch (Exception e) {
            log.warn("Failed to collect hardware information", e);
            report.append("Failed to collect hardware information: ").append(e.getMessage()).append("\n");
        }
        
        report.append("\n");
    }
    
    /**
     * Collect software information
     */
    private void collectSoftwareInfo(ExecutionContext context, Map<String, Object> systemInfo, StringBuilder report) {
        report.append("=== Software Information ===\n");
        
        try {
            // Docker
            boolean dockerInstalled = checkSoftwareInstalled(context, "docker --version");
            systemInfo.put("docker_installed", dockerInstalled);
            if (dockerInstalled) {
                String dockerVersion = executeCommand(context, "docker --version", "unknown");
                report.append("Docker: ").append(dockerVersion).append("\n");
            } else {
                report.append("Docker: Not installed\n");
            }
            
            // Python
            String pythonVersion = executeCommand(context, "python3 --version 2>/dev/null || python --version", "Not installed");
            systemInfo.put("python_version", pythonVersion);
            report.append("Python: ").append(pythonVersion).append("\n");
            
            // Java
            String javaVersion = executeCommand(context, "java -version 2>&1 | head -n 1", "Not installed");
            systemInfo.put("java_version", javaVersion);
            report.append("Java: ").append(javaVersion).append("\n");
            
            // Git
            String gitVersion = executeCommand(context, "git --version", "Not installed");
            systemInfo.put("git_version", gitVersion);
            report.append("Git: ").append(gitVersion).append("\n");
            
            // Node.js
            String nodeVersion = executeCommand(context, "node --version", "Not installed");
            report.append("Node.js: ").append(nodeVersion).append("\n");
            
            // npm
            String npmVersion = executeCommand(context, "npm --version", "Not installed");
            report.append("npm: ").append(npmVersion).append("\n");
            
        } catch (Exception e) {
            log.warn("Failed to collect software information", e);
            report.append("Failed to collect software information: ").append(e.getMessage()).append("\n");
        }
        
        report.append("\n");
    }
    
    /**
     * Collect network information
     */
    private void collectNetworkInfo(ExecutionContext context, Map<String, Object> systemInfo, StringBuilder report) {
        report.append("=== Network Information ===\n");
        
        try {
            // Network Interfaces
            String interfaces = getNetworkInterfaces(context);
            systemInfo.put("network_interfaces", interfaces);
            report.append("Network Interfaces:\n").append(interfaces).append("\n");
            
            // Internet Connectivity
            boolean internetConnected = testInternetConnectivity(context);
            report.append("Internet Connectivity: ").append(internetConnected ? "Available" : "Not available").append("\n");
            
            // DNS Resolution
            boolean dnsWorking = testDnsResolution(context);
            report.append("DNS Resolution: ").append(dnsWorking ? "Working" : "Failed").append("\n");
            
        } catch (Exception e) {
            log.warn("Failed to collect network information", e);
            report.append("Failed to collect network information: ").append(e.getMessage()).append("\n");
        }
    }
    
    /**
     * Execute command with fallback value
     */
    private String executeCommand(ExecutionContext context, String command, String fallback) {
        try {
            ExecutionContext.CommandResult result = context.executeCommand(command, Duration.ofSeconds(10));
            if (result.getExitCode() == 0 && !result.getOutput().trim().isEmpty()) {
                return result.getOutput().trim();
            }
        } catch (Exception e) {
            log.debug("Command failed: {}", command, e);
        }
        return fallback;
    }
    
    /**
     * Check if software is installed
     */
    private boolean checkSoftwareInstalled(ExecutionContext context, String command) {
        try {
            ExecutionContext.CommandResult result = context.executeCommand(command, Duration.ofSeconds(5));
            return result.getExitCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get CPU information
     */
    private String getCpuInfo(ExecutionContext context) {
        try {
            String cpuModel = executeCommand(context, 
                "cat /proc/cpuinfo | grep 'model name' | head -1 | cut -d':' -f2 | xargs", 
                "Unknown CPU");
            String cpuCores = executeCommand(context, "nproc", "unknown");
            return cpuModel + " (" + cpuCores + " cores)";
        } catch (Exception e) {
            return "Unknown CPU";
        }
    }
    
    /**
     * Get memory information
     */
    private Map<String, String> getMemoryInfo(ExecutionContext context) {
        Map<String, String> memInfo = new HashMap<>();
        
        try {
            String memTotal = executeCommand(context, 
                "free -h | grep Mem | awk '{print $2}'", "unknown");
            String memAvailable = executeCommand(context, 
                "free -h | grep Mem | awk '{print $7}'", "unknown");
            
            memInfo.put("total", memTotal);
            memInfo.put("available", memAvailable);
        } catch (Exception e) {
            memInfo.put("total", "unknown");
            memInfo.put("available", "unknown");
        }
        
        return memInfo;
    }
    
    /**
     * Get disk information
     */
    private String getDiskInfo(ExecutionContext context) {
        try {
            return executeCommand(context, "df -h | grep -E '^/dev/'", "No disk information available");
        } catch (Exception e) {
            return "Failed to get disk information";
        }
    }
    
    /**
     * Get network interfaces
     */
    private String getNetworkInterfaces(ExecutionContext context) {
        try {
            return executeCommand(context, "ip addr show | grep -E '^[0-9]+:' | cut -d':' -f2 | xargs", 
                "No network interfaces found");
        } catch (Exception e) {
            return "Failed to get network interfaces";
        }
    }
    
    /**
     * Test internet connectivity
     */
    private boolean testInternetConnectivity(ExecutionContext context) {
        try {
            ExecutionContext.CommandResult result = context.executeCommand(
                "ping -c 1 -W 5 8.8.8.8 > /dev/null 2>&1", Duration.ofSeconds(10));
            return result.getExitCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Test DNS resolution
     */
    private boolean testDnsResolution(ExecutionContext context) {
        try {
            ExecutionContext.CommandResult result = context.executeCommand(
                "nslookup google.com > /dev/null 2>&1", Duration.ofSeconds(10));
            return result.getExitCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Detect package manager
     */
    private String detectPackageManager(ExecutionContext context) {
        String[] packageManagers = {
            "apt:which apt",
            "yum:which yum", 
            "dnf:which dnf",
            "pacman:which pacman",
            "zypper:which zypper",
            "apk:which apk"
        };
        
        for (String pm : packageManagers) {
            String[] parts = pm.split(":");
            if (checkSoftwareInstalled(context, parts[1])) {
                return parts[0];
            }
        }
        
        return "unknown";
    }
    
    /**
     * Extract OS name from OS info string
     */
    private String extractOsName(String osInfo) {
        if (osInfo.toLowerCase().contains("ubuntu")) return "Ubuntu";
        if (osInfo.toLowerCase().contains("debian")) return "Debian";
        if (osInfo.toLowerCase().contains("centos")) return "CentOS";
        if (osInfo.toLowerCase().contains("rhel") || osInfo.toLowerCase().contains("red hat")) return "RHEL";
        if (osInfo.toLowerCase().contains("fedora")) return "Fedora";
        if (osInfo.toLowerCase().contains("alpine")) return "Alpine";
        if (osInfo.toLowerCase().contains("arch")) return "Arch Linux";
        return "Linux";
    }
    
    /**
     * Extract OS version from OS info string
     */
    private String extractOsVersion(String osInfo) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+\\.\\d+");
        java.util.regex.Matcher matcher = pattern.matcher(osInfo);
        if (matcher.find()) {
            return matcher.group();
        }
        return "unknown";
    }
}