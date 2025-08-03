package com.fufu.terminal.script.builtin.configurable;

import com.fufu.terminal.entity.enums.ScriptType;
import com.fufu.terminal.entity.enums.VariableScope;
import com.fufu.terminal.script.ExecutableScript;
import com.fufu.terminal.script.context.ExecutionContext;
import com.fufu.terminal.script.geographic.GeographicMirrorSelector;
import com.fufu.terminal.script.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Docker Installation Script
 * Configurable Built-in Script with intelligent mirror selection and cross-platform support
 * Demonstrates geographic awareness and parameter-based configuration
 */
@Component
@Slf4j
public class DockerInstallationScript implements ExecutableScript {
    
    @Autowired
    private GeographicMirrorSelector mirrorSelector;
    
    @Override
    public String getId() {
        return "docker-installation";
    }
    
    @Override
    public String getName() {
        return "Docker Installation";
    }
    
    @Override
    public String getDescription() {
        return "Install Docker Engine with intelligent mirror selection, Docker Compose, and post-installation configuration";
    }
    
    @Override
    public String getCategory() {
        return "Container Platform";
    }
    
    @Override
    public ScriptType getType() {
        return ScriptType.CONFIGURABLE_BUILTIN;
    }
    
    @Override
    public List<ScriptParameter> getParameters() {
        return Arrays.asList(
            ScriptParameter.builder()
                .name("docker_mirror")
                .displayName("Docker Mirror")
                .type(ParameterType.STRING)
                .required(false)
                .description("Docker registry mirror (auto-selected based on location if not provided)")
                .placeholder("https://registry-1.docker.io")
                .helpText("Leave empty for automatic mirror selection based on server location")
                .build(),
                
            ScriptParameter.builder()
                .name("install_compose")
                .displayName("Install Docker Compose")
                .type(ParameterType.BOOLEAN)
                .required(false)
                .defaultValue(true)
                .description("Whether to install Docker Compose alongside Docker")
                .helpText("Docker Compose is recommended for multi-container applications")
                .build(),
                
            ScriptParameter.builder()
                .name("enable_user_group")
                .displayName("Add User to Docker Group")
                .type(ParameterType.BOOLEAN)
                .required(false)
                .defaultValue(true)
                .description("Add current user to docker group for non-root access")
                .helpText("Allows running Docker commands without sudo")
                .build(),
                
            ScriptParameter.builder()
                .name("start_service")
                .displayName("Start Docker Service")
                .type(ParameterType.BOOLEAN)
                .required(false)
                .defaultValue(true)
                .description("Start and enable Docker service after installation")
                .build(),
                
            ScriptParameter.builder()
                .name("docker_version")
                .displayName("Docker Version")
                .type(ParameterType.STRING)
                .required(false)
                .description("Specific Docker version to install (latest if not specified)")
                .placeholder("24.0.7")
                .helpText("Leave empty to install the latest stable version")
                .build()
        );
    }
    
    @Override
    public Set<String> getRequiredVariables() {
        return Set.of("os_name", "package_manager"); // From SystemInfoCollectionScript
    }
    
    @Override
    public Set<String> getOutputVariables() {
        return Set.of("docker_version_installed", "docker_compose_version", "docker_mirror_used", 
                     "docker_service_status", "docker_group_added");
    }
    
    @Override
    public Optional<Integer> getEstimatedExecutionTime() {
        return Optional.of(180); // 3 minutes
    }
    
    @Override
    public Set<String> getTags() {
        return Set.of("docker", "container", "installation", "devops", "platform");
    }
    
    @Override
    public boolean shouldExecute(ExecutionContext context) {
        // Check if Docker is already installed
        try {
            ExecutionContext.CommandResult result = context.executeCommand("docker --version", Duration.ofSeconds(5));
            if (result.getExitCode() == 0) {
                log.info("Docker is already installed: {}", result.getOutput());
                return false;
            }
        } catch (Exception e) {
            log.debug("Docker not found, proceeding with installation");
        }
        
        return true;
    }
    
    @Override
    public CompletableFuture<ScriptResult> executeAsync(ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting Docker installation for session: {}", context.getSessionId());
                
                long startTime = System.currentTimeMillis();
                Map<String, Object> outputs = new HashMap<>();
                StringBuilder report = new StringBuilder();
                
                // Step 1: Gather system information and parameters
                String osName = context.getVariable("os_name", String.class);
                String packageManager = context.getVariable("package_manager", String.class);
                String serverLocation = context.getVariable("server_location", String.class);
                
                if (osName == null || packageManager == null) {
                    throw new RuntimeException("Required system information not available. Run system-info-collection first.");
                }
                
                report.append("=== Docker Installation Report ===\n");
                report.append("Target OS: ").append(osName).append("\n");
                report.append("Package Manager: ").append(packageManager).append("\n");
                report.append("Server Location: ").append(serverLocation != null ? serverLocation : "Unknown").append("\n\n");
                
                // Step 2: Apply intelligent mirror selection
                String dockerMirror = applyIntelligentMirrorSelection(context, serverLocation, outputs, report);
                
                // Step 3: Get configuration parameters
                boolean installCompose = context.getVariableOrDefault("install_compose", Boolean.class, true);
                boolean enableUserGroup = context.getVariableOrDefault("enable_user_group", Boolean.class, true);
                boolean startService = context.getVariableOrDefault("start_service", Boolean.class, true);
                String dockerVersion = context.getVariable("docker_version", String.class);
                
                // Step 4: Execute installation based on OS and package manager
                installDocker(context, osName, packageManager, dockerVersion, dockerMirror, outputs, report);
                
                // Step 5: Install Docker Compose if requested
                if (installCompose) {
                    installDockerCompose(context, outputs, report);
                }
                
                // Step 6: Configure user group if requested
                if (enableUserGroup) {
                    configureUserGroup(context, outputs, report);
                }
                
                // Step 7: Start and enable service if requested
                if (startService) {
                    startDockerService(context, outputs, report);
                }
                
                // Step 8: Verify installation
                verifyDockerInstallation(context, outputs, report);
                
                // Set output variables in session scope
                context.setVariables(outputs, VariableScope.SESSION);
                
                long executionTime = System.currentTimeMillis() - startTime;
                
                String summary = String.format("Docker installation completed successfully. Version: %s, Mirror: %s", 
                    outputs.get("docker_version_installed"), outputs.get("docker_mirror_used"));
                
                return ScriptResult.builder()
                    .success(true)
                    .message(summary)
                    .outputVariables(outputs)
                    .executionTimeMs(executionTime)
                    .startTime(java.time.Instant.ofEpochMilli(startTime))
                    .endTime(java.time.Instant.now())
                    .sessionId(context.getSessionId())
                    .scriptId(getId())
                    .scriptVersion(getVersion())
                    .stdOut(report.toString())
                    .build();
                    
            } catch (Exception e) {
                log.error("Docker installation failed for session: {}", context.getSessionId(), e);
                return ScriptResult.builder()
                    .success(false)
                    .errorMessage("Docker installation failed: " + e.getMessage())
                    .errorCode("DOCKER_INSTALLATION_ERROR")
                    .sessionId(context.getSessionId())
                    .scriptId(getId())
                    .build();
            }
        });
    }
    
    /**
     * Apply intelligent mirror selection based on geographic location
     */
    private String applyIntelligentMirrorSelection(ExecutionContext context, String serverLocation, 
                                                 Map<String, Object> outputs, StringBuilder report) {
        report.append("=== Mirror Selection ===\n");
        
        // Check if user provided a specific mirror
        String userMirror = context.getVariable("docker_mirror", String.class);
        if (userMirror != null && !userMirror.trim().isEmpty()) {
            report.append("Using user-specified mirror: ").append(userMirror).append("\n");
            outputs.put("docker_mirror_used", userMirror);
            return userMirror;
        }
        
        // Use intelligent mirror selection
        try {
            var mirrorConfig = mirrorSelector.selectOptimalMirror(serverLocation, Set.of("docker"));
            String selectedMirror = mirrorConfig.getDockerMirror();
            
            report.append("Auto-selected mirror based on location: ").append(selectedMirror).append("\n");
            report.append("Region: ").append(mirrorConfig.getRegion()).append("\n");
            
            if (!mirrorConfig.getDockerBackupMirrors().isEmpty()) {
                report.append("Backup mirrors available: ").append(String.join(", ", mirrorConfig.getDockerBackupMirrors())).append("\n");
            }
            
            // Apply mirror configuration to context for other scripts
            context.setVariables(mirrorConfig.toParameterMap(), VariableScope.SESSION);
            outputs.put("docker_mirror_used", selectedMirror);
            
            return selectedMirror;
            
        } catch (Exception e) {
            log.warn("Failed to select mirror automatically, using default", e);
            String defaultMirror = "https://registry-1.docker.io";
            report.append("Failed to auto-select mirror, using default: ").append(defaultMirror).append("\n");
            outputs.put("docker_mirror_used", defaultMirror);
            return defaultMirror;
        } finally {
            report.append("\n");
        }
    }
    
    /**
     * Install Docker based on OS and package manager
     */
    private void installDocker(ExecutionContext context, String osName, String packageManager, 
                             String dockerVersion, String dockerMirror, 
                             Map<String, Object> outputs, StringBuilder report) {
        report.append("=== Docker Installation ===\n");
        
        try {
            switch (packageManager.toLowerCase()) {
                case "apt":
                    installDockerApt(context, dockerVersion, dockerMirror, outputs, report);
                    break;
                case "yum":
                case "dnf":
                    installDockerYum(context, packageManager, dockerVersion, outputs, report);
                    break;
                case "pacman":
                    installDockerPacman(context, dockerVersion, outputs, report);
                    break;
                default:
                    throw new RuntimeException("Unsupported package manager: " + packageManager);
            }
        } catch (Exception e) {
            report.append("Docker installation failed: ").append(e.getMessage()).append("\n");
            throw e;
        }
        
        report.append("\n");
    }
    
    /**
     * Install Docker using APT (Ubuntu/Debian)
     */
    private void installDockerApt(ExecutionContext context, String dockerVersion, String dockerMirror,
                                Map<String, Object> outputs, StringBuilder report) {
        report.append("Installing Docker using APT...\n");
        
        // Update package index
        executeCommand(context, "sudo apt-get update", "Failed to update package index");
        report.append("Package index updated\n");
        
        // Install prerequisites
        executeCommand(context, 
            "sudo apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release",
            "Failed to install prerequisites");
        report.append("Prerequisites installed\n");
        
        // Add Docker GPG key
        executeCommand(context,
            "curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg",
            "Failed to add Docker GPG key");
        report.append("Docker GPG key added\n");
        
        // Add Docker repository
        executeCommand(context,
            "echo \"deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable\" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null",
            "Failed to add Docker repository");
        report.append("Docker repository added\n");
        
        // Update package index again
        executeCommand(context, "sudo apt-get update", "Failed to update package index after adding repository");
        
        // Install Docker
        String installCommand = dockerVersion != null && !dockerVersion.trim().isEmpty()
            ? "sudo apt-get install -y docker-ce=" + dockerVersion + " docker-ce-cli=" + dockerVersion + " containerd.io"
            : "sudo apt-get install -y docker-ce docker-ce-cli containerd.io";
            
        executeCommand(context, installCommand, "Failed to install Docker");
        report.append("Docker installed successfully\n");
    }
    
    /**
     * Install Docker using YUM/DNF (RHEL/CentOS/Fedora)
     */
    private void installDockerYum(ExecutionContext context, String packageManager, String dockerVersion,
                                Map<String, Object> outputs, StringBuilder report) {
        report.append("Installing Docker using ").append(packageManager.toUpperCase()).append("...\n");
        
        // Install prerequisites
        executeCommand(context, 
            "sudo " + packageManager + " install -y yum-utils",
            "Failed to install prerequisites");
        report.append("Prerequisites installed\n");
        
        // Add Docker repository
        executeCommand(context,
            "sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo",
            "Failed to add Docker repository");
        report.append("Docker repository added\n");
        
        // Install Docker
        String installCommand = dockerVersion != null && !dockerVersion.trim().isEmpty()
            ? "sudo " + packageManager + " install -y docker-ce-" + dockerVersion + " docker-ce-cli-" + dockerVersion + " containerd.io"
            : "sudo " + packageManager + " install -y docker-ce docker-ce-cli containerd.io";
            
        executeCommand(context, installCommand, "Failed to install Docker");
        report.append("Docker installed successfully\n");
    }
    
    /**
     * Install Docker using Pacman (Arch Linux)
     */
    private void installDockerPacman(ExecutionContext context, String dockerVersion,
                                   Map<String, Object> outputs, StringBuilder report) {
        report.append("Installing Docker using Pacman...\n");
        
        // Update package database
        executeCommand(context, "sudo pacman -Sy", "Failed to update package database");
        report.append("Package database updated\n");
        
        // Install Docker
        executeCommand(context, "sudo pacman -S --noconfirm docker", "Failed to install Docker");
        report.append("Docker installed successfully\n");
    }
    
    /**
     * Install Docker Compose
     */
    private void installDockerCompose(ExecutionContext context, Map<String, Object> outputs, StringBuilder report) {
        report.append("=== Docker Compose Installation ===\n");
        
        try {
            // Get latest version or use specified version
            String composeVersion = getLatestComposeVersion(context);
            
            // Download and install Docker Compose
            executeCommand(context,
                "sudo curl -L \"https://github.com/docker/compose/releases/download/" + composeVersion + "/docker-compose-$(uname -s)-$(uname -m)\" -o /usr/local/bin/docker-compose",
                "Failed to download Docker Compose");
            
            executeCommand(context,
                "sudo chmod +x /usr/local/bin/docker-compose",
                "Failed to make Docker Compose executable");
            
            // Verify installation
            ExecutionContext.CommandResult result = context.executeCommand("docker-compose --version", Duration.ofSeconds(10));
            if (result.getExitCode() == 0) {
                outputs.put("docker_compose_version", result.getOutput().trim());
                report.append("Docker Compose installed: ").append(result.getOutput().trim()).append("\n");
            } else {
                throw new RuntimeException("Failed to verify Docker Compose installation");
            }
            
        } catch (Exception e) {
            report.append("Docker Compose installation failed: ").append(e.getMessage()).append("\n");
            outputs.put("docker_compose_version", "Installation failed");
        }
        
        report.append("\n");
    }
    
    /**
     * Configure user group for non-root Docker access
     */
    private void configureUserGroup(ExecutionContext context, Map<String, Object> outputs, StringBuilder report) {
        report.append("=== User Group Configuration ===\n");
        
        try {
            // Get current user
            String currentUser = executeCommand(context, "whoami", "Failed to get current user").trim();
            
            // Add user to docker group
            executeCommand(context, 
                "sudo usermod -aG docker " + currentUser,
                "Failed to add user to docker group");
            
            outputs.put("docker_group_added", true);
            report.append("User ").append(currentUser).append(" added to docker group\n");
            report.append("Note: You may need to log out and back in for group changes to take effect\n");
            
        } catch (Exception e) {
            outputs.put("docker_group_added", false);
            report.append("Failed to configure user group: ").append(e.getMessage()).append("\n");
        }
        
        report.append("\n");
    }
    
    /**
     * Start and enable Docker service
     */
    private void startDockerService(ExecutionContext context, Map<String, Object> outputs, StringBuilder report) {
        report.append("=== Docker Service Configuration ===\n");
        
        try {
            // Start Docker service
            executeCommand(context, "sudo systemctl start docker", "Failed to start Docker service");
            report.append("Docker service started\n");
            
            // Enable Docker service to start on boot
            executeCommand(context, "sudo systemctl enable docker", "Failed to enable Docker service");
            report.append("Docker service enabled for auto-start\n");
            
            // Check service status
            ExecutionContext.CommandResult statusResult = context.executeCommand("sudo systemctl is-active docker", Duration.ofSeconds(5));
            outputs.put("docker_service_status", statusResult.getOutput().trim());
            report.append("Docker service status: ").append(statusResult.getOutput().trim()).append("\n");
            
        } catch (Exception e) {
            outputs.put("docker_service_status", "failed");
            report.append("Failed to configure Docker service: ").append(e.getMessage()).append("\n");
        }
        
        report.append("\n");
    }
    
    /**
     * Verify Docker installation
     */
    private void verifyDockerInstallation(ExecutionContext context, Map<String, Object> outputs, StringBuilder report) {
        report.append("=== Installation Verification ===\n");
        
        try {
            // Check Docker version
            ExecutionContext.CommandResult versionResult = context.executeCommand("docker --version", Duration.ofSeconds(10));
            if (versionResult.getExitCode() == 0) {
                outputs.put("docker_version_installed", versionResult.getOutput().trim());
                report.append("Docker version: ").append(versionResult.getOutput().trim()).append("\n");
            } else {
                throw new RuntimeException("Docker not found after installation");
            }
            
            // Test Docker with hello-world (if service is running)
            try {
                ExecutionContext.CommandResult helloResult = context.executeCommand("sudo docker run hello-world", Duration.ofSeconds(30));
                if (helloResult.getExitCode() == 0) {
                    report.append("Docker hello-world test: PASSED\n");
                } else {
                    report.append("Docker hello-world test: FAILED (but Docker is installed)\n");
                }
            } catch (Exception e) {
                report.append("Docker hello-world test: SKIPPED (").append(e.getMessage()).append(")\n");
            }
            
        } catch (Exception e) {
            report.append("Installation verification failed: ").append(e.getMessage()).append("\n");
            throw new RuntimeException("Docker installation verification failed", e);
        }
    }
    
    /**
     * Execute command with error handling
     */
    private String executeCommand(ExecutionContext context, String command, String errorMessage) {
        try {
            ExecutionContext.CommandResult result = context.executeCommand(command, Duration.ofSeconds(60));
            if (result.getExitCode() != 0) {
                throw new RuntimeException(errorMessage + ": " + result.getErrorOutput());
            }
            return result.getOutput();
        } catch (Exception e) {
            log.error("Command failed: {}", command, e);
            throw new RuntimeException(errorMessage, e);
        }
    }
    
    /**
     * Get latest Docker Compose version
     */
    private String getLatestComposeVersion(ExecutionContext context) {
        try {
            // Try to get latest version from GitHub API
            ExecutionContext.CommandResult result = context.executeCommand(
                "curl -s https://api.github.com/repos/docker/compose/releases/latest | grep '\"tag_name\":' | sed -E 's/.*\"([^\"]+)\".*/\\1/'", 
                Duration.ofSeconds(10));
            
            if (result.getExitCode() == 0 && !result.getOutput().trim().isEmpty()) {
                return result.getOutput().trim();
            }
        } catch (Exception e) {
            log.debug("Failed to get latest Compose version from API", e);
        }
        
        // Fallback to a known stable version
        return "v2.21.0";
    }
}