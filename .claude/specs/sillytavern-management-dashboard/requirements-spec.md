# SillyTavern Management Dashboard - Technical Specification

## Problem Statement
- **Business Issue**: Beginner users cannot manage SillyTavern installations due to lack of Linux command line knowledge
- **Current State**: SSH terminal interface exists but requires technical expertise and manual Docker commands
- **Expected Outcome**: User-friendly web dashboard for complete SillyTavern lifecycle management (deploy, monitor, configure, manage) accessible to 20-40 concurrent non-technical users

## Solution Overview
- **Approach**: Transform existing SSH terminal into dual-page architecture with dedicated SillyTavern management dashboard that abstracts all Docker operations into Java backend services
- **Core Changes**: Add Vue Router for page navigation, implement Java-based SillyTavern deployment service, extend monitoring to Docker containers, add configuration and data management capabilities
- **Success Criteria**: Complete SillyTavern management without command line knowledge, support 20-40 concurrent users, sub-3-second response times for monitoring updates

## Technical Implementation

### Database Changes
- **No Database Required**: All data is ephemeral (Docker container states, configuration files, temporary uploads)
- **File-Based Storage**: Temporary ZIP files stored in `./temp/` directory with auto-cleanup after 1 hour
- **Configuration Management**: Direct YAML file manipulation for SillyTavern configuration

### Frontend Changes

#### New Files to Create
- **`web/ssh-treminal-ui/src/router/index.js`** - Vue Router configuration
- **`web/ssh-treminal-ui/src/views/Dashboard.vue`** - Main dashboard navigation page
- **`web/ssh-treminal-ui/src/views/SillyTavernConsole.vue`** - SillyTavern management interface
- **`web/ssh-treminal-ui/src/components/NavigationHeader.vue`** - Page navigation component
- **`web/ssh-treminal-ui/src/components/dashboard/WelcomeCard.vue`** - Dashboard card for SillyTavern
- **`web/ssh-treminal-ui/src/components/dashboard/TerminalCard.vue`** - Dashboard card for SSH Terminal
- **`web/ssh-treminal-ui/src/components/sillytavern/DeploymentWizard.vue`** - Multi-step deployment form
- **`web/ssh-treminal-ui/src/components/sillytavern/ContainerStatus.vue`** - Docker container monitoring widget
- **`web/ssh-treminal-ui/src/components/sillytavern/ServiceControls.vue`** - Start/stop/restart/delete controls
- **`web/ssh-treminal-ui/src/components/sillytavern/ConfigurationEditor.vue`** - Username/password modification form
- **`web/ssh-treminal-ui/src/components/sillytavern/LogViewer.vue`** - Container log display with auto-refresh
- **`web/ssh-treminal-ui/src/components/sillytavern/DataManager.vue`** - Data export/import interface
- **`web/ssh-treminal-ui/src/components/sillytavern/AccessInfo.vue`** - Display connection details with copy buttons
- **`web/ssh-treminal-ui/src/composables/useSillyTavern.js`** - SillyTavern state management composable
- **`web/ssh-treminal-ui/src/composables/useContainerMonitoring.js`** - Docker container monitoring composable

#### Files to Modify
- **`web/ssh-treminal-ui/src/main.js`** - Add Vue Router integration
- **`web/ssh-treminal-ui/src/App.vue`** - Replace direct Terminal with router-view and navigation
- **`web/ssh-treminal-ui/src/views/Terminal.vue`** - Add navigation header, keep existing functionality
- **`web/ssh-treminal-ui/package.json`** - Add vue-router dependency

#### Component Architecture
```
App.vue
├── NavigationHeader.vue (breadcrumb navigation)
└── router-view
    ├── Dashboard.vue (/)
    │   ├── WelcomeCard.vue → /sillytavern
    │   └── TerminalCard.vue → /terminal
    ├── SillyTavernConsole.vue (/sillytavern)
    │   ├── ContainerStatus.vue (top status banner)
    │   ├── DeploymentWizard.vue (if not deployed)
    │   ├── ServiceControls.vue (main action buttons)
    │   ├── ConfigurationEditor.vue (settings tab)
    │   ├── LogViewer.vue (logs tab)
    │   ├── DataManager.vue (data tab)
    │   └── AccessInfo.vue (connection info)
    └── Terminal.vue (/terminal)
        └── [existing components unchanged]
```

### Backend Changes

#### New Java Services to Create
- **`src/main/java/com/fufu/terminal/service/SillyTavernService.java`** - Core SillyTavern management service
- **`src/main/java/com/fufu/terminal/service/DockerContainerService.java`** - Docker container operations service
- **`src/main/java/com/fufu/terminal/service/SystemDetectionService.java`** - OS detection and system requirements validation
- **`src/main/java/com/fufu/terminal/service/ConfigurationService.java`** - SillyTavern configuration file management
- **`src/main/java/com/fufu/terminal/service/DataManagementService.java`** - Data export/import operations
- **`src/main/java/com/fufu/terminal/service/FileCleanupService.java`** - Temporary file cleanup service

#### New Controllers to Create
- **`src/main/java/com/fufu/terminal/controller/SillyTavernStompController.java`** - STOMP WebSocket endpoints for SillyTavern operations

#### New DTOs to Create
- **`src/main/java/com/fufu/terminal/dto/sillytavern/DeploymentRequestDto.java`** - Deployment configuration
- **`src/main/java/com/fufu/terminal/dto/sillytavern/DeploymentProgressDto.java`** - Deployment progress updates
- **`src/main/java/com/fufu/terminal/dto/sillytavern/ContainerStatusDto.java`** - Container status information
- **`src/main/java/com/fufu/terminal/dto/sillytavern/ServiceActionDto.java`** - Service control actions
- **`src/main/java/com/fufu/terminal/dto/sillytavern/ConfigurationDto.java`** - Configuration management
- **`src/main/java/com/fufu/terminal/dto/sillytavern/LogRequestDto.java`** - Log viewing parameters
- **`src/main/java/com/fufu/terminal/dto/sillytavern/DataExportDto.java`** - Data export requests
- **`src/main/java/com/fufu/terminal/dto/sillytavern/SystemInfoDto.java`** - System requirements and status

#### Service Implementation Details

##### SillyTavernService.java
```java
@Service
public class SillyTavernService {
    // Core methods:
    - validateSystemRequirements() -> SystemInfoDto
    - deployContainer(DeploymentRequestDto) -> void (async with progress updates)
    - getContainerStatus() -> ContainerStatusDto
    - startContainer() -> void
    - stopContainer() -> void
    - restartContainer() -> void
    - upgradeContainer() -> void (pull latest + restart)
    - deleteContainer() -> void
    - getContainerLogs(int days) -> List<String>
    - isContainerRunning() -> boolean
}
```

##### DockerContainerService.java
```java
@Service
public class DockerContainerService {
    // Docker command execution:
    - executeDockerCommand(String... args) -> CommandResult
    - pullImage(String image, ProgressCallback callback) -> void
    - createContainer(ContainerConfig config) -> String containerId
    - getContainerInfo(String containerName) -> ContainerInfo
    - getContainerLogs(String containerName, int tailLines) -> List<String>
}
```

##### ConfigurationService.java
```java
@Service
public class ConfigurationService {
    // YAML file management:
    - readConfiguration(String containerName) -> Map<String, Object>
    - updateCredentials(String containerName, String username, String password) -> void
    - validateConfiguration(Map<String, Object> config) -> ValidationResult
    - backupConfiguration(String containerName) -> String backupPath
}
```

### API Endpoints and Message Types

#### STOMP WebSocket Endpoints
All endpoints follow pattern: `/app/sillytavern/{action}` → `/queue/sillytavern/{response}-user{sessionId}`

**Deployment Operations:**
- **`/app/sillytavern/deploy`** - Start deployment process
  - Request: `DeploymentRequestDto{username, password, port, dataPath}`
  - Response: `/queue/sillytavern/deployment-user{sessionId}`
  - Progress: `DeploymentProgressDto{stage, progress, message, error?}`

**Container Management:**
- **`/app/sillytavern/status`** - Get container status
  - Request: `{}`
  - Response: `/queue/sillytavern/status-user{sessionId}`
  - Data: `ContainerStatusDto{running, uptime, memoryUsage, cpuUsage, port, lastUpdated}`

- **`/app/sillytavern/service-action`** - Container control actions
  - Request: `ServiceActionDto{action: "start"|"stop"|"restart"|"upgrade"|"delete"}`
  - Response: `/queue/sillytavern/action-result-user{sessionId}`
  - Data: `{success, message, error?}`

**Configuration Management:**
- **`/app/sillytavern/get-config`** - Retrieve current configuration
  - Request: `{}`
  - Response: `/queue/sillytavern/config-user{sessionId}`
  - Data: `ConfigurationDto{username, hasPassword, port, otherSettings}`

- **`/app/sillytavern/update-config`** - Update configuration
  - Request: `ConfigurationDto{username?, password?, port?}`
  - Response: `/queue/sillytavern/config-updated-user{sessionId}`
  - Data: `{success, message, requiresRestart?}`

**Log Management:**
- **`/app/sillytavern/get-logs`** - Retrieve container logs
  - Request: `LogRequestDto{days: 1|3|7, tailLines: 100|500|1000}`
  - Response: `/queue/sillytavern/logs-user{sessionId}`
  - Data: `{logs: String[], totalLines, truncated}`

**Data Management:**
- **`/app/sillytavern/export-data`** - Export data directory
  - Request: `{}`
  - Response: `/queue/sillytavern/export-user{sessionId}`
  - Data: `DataExportDto{downloadUrl, filename, sizeBytes, expiresAt}`

- **`/app/sillytavern/import-data`** - Import data from uploaded ZIP
  - Request: `{uploadedFileName}`
  - Response: `/queue/sillytavern/import-user{sessionId}`
  - Data: `{success, message, requiresRestart?, error?}`

#### Container Monitoring Integration
Extend existing monitoring service to include Docker container metrics:

**Enhanced MonitorUpdateDto:**
```java
public class MonitorUpdateDto {
    // Existing system metrics
    private Map<String, Object> systemMetrics;
    
    // New Docker container metrics
    private Map<String, ContainerStatusDto> containers;
    private DockerSystemInfo dockerInfo;
}
```

**Real-time Container Monitoring:**
- **Frequency**: 3-second updates for container status, 10-second updates for resource usage
- **Integration**: Extend `StompMonitoringService` to include Docker metrics alongside SSH monitoring
- **Endpoint**: Use existing `/queue/monitor/data-user{sessionId}` with enhanced payload

### File Structure Changes

#### Backend Directory Structure
```
src/main/java/com/fufu/terminal/
├── service/
│   ├── sillytavern/
│   │   ├── SillyTavernService.java
│   │   ├── DockerContainerService.java
│   │   ├── SystemDetectionService.java
│   │   ├── ConfigurationService.java
│   │   ├── DataManagementService.java
│   │   └── FileCleanupService.java
│   └── [existing services]
├── controller/
│   ├── SillyTavernStompController.java
│   └── [existing controllers]
├── dto/sillytavern/
│   ├── DeploymentRequestDto.java
│   ├── DeploymentProgressDto.java
│   ├── ContainerStatusDto.java
│   ├── ServiceActionDto.java
│   ├── ConfigurationDto.java
│   ├── LogRequestDto.java
│   ├── DataExportDto.java
│   └── SystemInfoDto.java
└── [existing packages]
```

#### Frontend Directory Structure
```
web/ssh-treminal-ui/src/
├── router/
│   └── index.js
├── views/
│   ├── Dashboard.vue
│   ├── SillyTavernConsole.vue
│   └── Terminal.vue [modified]
├── components/
│   ├── dashboard/
│   │   ├── WelcomeCard.vue
│   │   └── TerminalCard.vue
│   ├── sillytavern/
│   │   ├── DeploymentWizard.vue
│   │   ├── ContainerStatus.vue
│   │   ├── ServiceControls.vue
│   │   ├── ConfigurationEditor.vue
│   │   ├── LogViewer.vue
│   │   ├── DataManager.vue
│   │   └── AccessInfo.vue
│   ├── NavigationHeader.vue
│   └── [existing components]
├── composables/
│   ├── useSillyTavern.js
│   ├── useContainerMonitoring.js
│   └── useTerminal.js [existing]
└── [existing directories]
```

### Integration Patterns with Existing Code

#### WebSocket Infrastructure Integration
- **Reuse StompSessionManager**: Extend existing session management for SillyTavern operations
- **Extend StompMonitoringService**: Add Docker container monitoring alongside SSH monitoring
- **Message Pattern Consistency**: Follow existing `/app/{service}/{action}` → `/queue/{service}/{response}-user{sessionId}` pattern

#### Error Handling Integration
- **Reuse StompGlobalExceptionHandler**: Extend existing error handling for SillyTavern operations
- **Consistent Error Format**: Use existing `ErrorDto` structure for all SillyTavern errors
- **Logging Integration**: Use existing SLF4J logging patterns in all new services

#### Authentication Integration
- **StompAuthenticationInterceptor**: No changes needed, existing session-based auth applies to all new endpoints
- **Session Management**: Leverage existing `StompSessionManager` for SillyTavern session tracking

### Error Handling Specifications

#### System Validation Errors
- **Root Permission Check**: Validate `sudo` access before any Docker operations
- **Docker Installation**: Check Docker daemon availability and version compatibility
- **Disk Space Validation**: Ensure >500MB available before deployment
- **Port Conflict Detection**: Scan for port availability before container creation
- **Network Connectivity**: Validate internet access for Docker image pulls

#### Deployment Error Handling
- **Image Pull Failures**: Retry with exponential backoff (3 attempts), graceful failure with network troubleshooting tips
- **Container Creation Failures**: Validate configuration, port conflicts, insufficient resources
- **Permission Errors**: Clear instructions for sudo configuration
- **Resource Exhaustion**: Monitor disk space during deployment, abort if <100MB remaining

#### Runtime Error Handling
- **Container Crashed**: Auto-restart attempts (3 times), log analysis for common issues
- **Configuration Corruption**: Automatic backup restoration with user confirmation
- **Log Rotation Failures**: Graceful degradation with warning messages
- **File Upload Errors**: Validation, size limits (max 5GB), format verification

#### User Experience Error Messages
- **Technical Errors**: Translate Docker/system errors into user-friendly messages
- **Recovery Actions**: Provide specific next steps for each error type
- **Progress Interruption**: Allow cancellation with proper cleanup
- **Session Recovery**: Restore operation state after reconnection

### Performance Considerations for 20-40 Concurrent Users

#### Backend Optimization
- **Async Operations**: All Docker operations run asynchronously with progress callbacks
- **Connection Pooling**: Reuse Docker API connections across requests
- **Resource Monitoring**: Track CPU/memory usage, implement request throttling if needed
- **Caching Strategy**: Cache container status for 3 seconds, configuration for 30 seconds

#### File Management Optimization
- **Temporary File Limits**: Max 10 concurrent ZIP operations per user, 100 total system-wide
- **Cleanup Scheduling**: Scheduled cleanup every 15 minutes for files >1 hour old
- **Upload Streaming**: Stream large file uploads directly to disk without memory buffering
- **Compression Optimization**: Use native zip utilities for large data directories

#### WebSocket Scaling
- **Message Batching**: Batch monitoring updates to reduce WebSocket message frequency
- **Selective Updates**: Only send container status updates when values change >5%
- **Session Cleanup**: Aggressive cleanup of disconnected sessions within 30 seconds
- **Memory Management**: Limit stored messages per session to prevent memory leaks

#### Frontend Performance
- **Component Lazy Loading**: Lazy load SillyTavern components only when navigating to dashboard
- **Update Throttling**: Debounce user inputs (500ms), throttle monitoring updates (3s minimum)
- **Memory Optimization**: Properly cleanup WebSocket subscriptions on component unmount
- **Progress Optimization**: Use CSS animations for progress bars instead of frequent DOM updates

## Implementation Sequence

### Phase 1: Frontend Navigation Architecture
**Duration: 2-3 hours**
1. **Add Vue Router dependency** - Update `package.json`
2. **Create router configuration** - `src/router/index.js` with routes
3. **Modify App.vue** - Replace direct Terminal with router-view
4. **Create Dashboard.vue** - Basic navigation page with two cards
5. **Add NavigationHeader.vue** - Breadcrumb navigation component
6. **Update Terminal.vue** - Add navigation header, preserve existing functionality
7. **Test routing** - Verify navigation between Dashboard and Terminal works

**Validation**: Navigation works correctly, existing SSH terminal functionality unchanged

### Phase 2: SillyTavern Service Foundation
**Duration: 4-5 hours**
1. **Create core DTOs** - All sillytavern package DTOs
2. **Implement SystemDetectionService** - OS detection and requirements validation
3. **Implement DockerContainerService** - Basic Docker command execution
4. **Create SillyTavernStompController** - WebSocket endpoint structure
5. **Implement basic SillyTavernService** - Container status checking only
6. **Create SillyTavernConsole.vue** - Basic page structure with status display
7. **Create useSillyTavern composable** - WebSocket communication setup
8. **Test basic connectivity** - Verify container status detection

**Validation**: Can detect if SillyTavern container exists and is running

### Phase 3: Container Deployment System
**Duration: 6-8 hours**
1. **Implement deployment logic** - Complete `SillyTavernService.deployContainer()`
2. **Create DeploymentWizard.vue** - Multi-step form with validation
3. **Add progress tracking** - Real-time deployment progress updates
4. **Implement system validation** - Pre-deployment checks (space, Docker, permissions)
5. **Add error handling** - Comprehensive error messages and recovery
6. **Create Docker image pull logic** - With progress callbacks
7. **Implement container creation** - Configuration and startup
8. **Test full deployment** - End-to-end deployment process

**Validation**: Can successfully deploy SillyTavern from scratch with progress feedback

### Phase 4: Service Management Controls
**Duration: 3-4 hours**
1. **Implement service actions** - Start, stop, restart, upgrade, delete operations
2. **Create ServiceControls.vue** - Action buttons with confirmation dialogs
3. **Add upgrade logic** - Pull latest image and restart container
4. **Implement delete functionality** - Container and data removal options
5. **Add operation feedback** - Success/error messages and loading states
6. **Test all operations** - Verify each service action works correctly

**Validation**: All container management operations work with proper feedback

### Phase 5: Configuration Management
**Duration: 3-4 hours**
1. **Implement ConfigurationService** - YAML file reading/writing
2. **Create ConfigurationEditor.vue** - Form for username/password changes
3. **Add configuration validation** - Input validation and format checking
4. **Implement configuration backup** - Automatic backup before changes
5. **Add restart detection** - Notify when changes require container restart
6. **Test configuration changes** - Verify changes persist and work correctly

**Validation**: Can modify SillyTavern credentials and configuration online

### Phase 6: Enhanced Monitoring Integration
**Duration: 4-5 hours**
1. **Extend ContainerStatusDto** - Add resource usage metrics
2. **Modify StompMonitoringService** - Include Docker container monitoring
3. **Create ContainerStatus.vue** - Real-time status display widget
4. **Implement dual refresh rates** - 3s for status, 10s for resources
5. **Add useContainerMonitoring composable** - Separate monitoring logic
6. **Integrate with existing monitoring** - Combine SSH and container metrics
7. **Test monitoring accuracy** - Verify metrics are accurate and timely

**Validation**: Real-time container status updates with resource usage

### Phase 7: Log Management System
**Duration: 2-3 hours**
1. **Implement log retrieval** - Docker container log access
2. **Create LogViewer.vue** - Scrollable log display with auto-refresh
3. **Add log filtering** - Date range and line count options
4. **Implement log rotation** - 7-day automatic cleanup
5. **Add log export** - Download logs as text file
6. **Test log functionality** - Verify logs display correctly and update

**Validation**: Can view and manage container logs with proper cleanup

### Phase 8: Data Management Features
**Duration: 5-6 hours**
1. **Implement DataManagementService** - ZIP export/import operations
2. **Create DataManager.vue** - Export/import interface with progress
3. **Add file validation** - ZIP structure validation for imports
4. **Implement FileCleanupService** - Automatic temporary file cleanup
5. **Add upload progress** - Real-time file upload progress tracking
6. **Implement export streaming** - Efficient large file handling
7. **Test data operations** - Full export/import cycle with large files
8. **Add cleanup scheduling** - Scheduled task for temporary file cleanup

**Validation**: Can export and import data directories with progress tracking

### Phase 9: Access Information Display
**Duration: 1-2 hours**
1. **Create AccessInfo.vue** - Display connection details
2. **Implement clipboard integration** - One-click copy functionality
3. **Add connection testing** - Verify SillyTavern accessibility
4. **Test access information** - Verify all details are accurate and copyable

**Validation**: Displays correct access information with working copy buttons

### Phase 10: Performance Optimization and Testing
**Duration: 3-4 hours**
1. **Implement request throttling** - Prevent system overload
2. **Add memory optimization** - Proper component cleanup
3. **Test concurrent usage** - Verify 20-40 user support
4. **Performance monitoring** - Add logging for performance metrics
5. **Load testing** - Stress test with multiple concurrent operations
6. **Bug fixes and refinements** - Address any issues found during testing

**Validation**: System performs well under 20-40 concurrent user load

## Validation Plan

### Unit Tests
- **SillyTavernService**: Mock Docker commands, test all deployment scenarios
- **DockerContainerService**: Test command construction and result parsing
- **ConfigurationService**: Test YAML parsing, validation, and backup creation
- **DataManagementService**: Test ZIP operations with various file structures
- **SystemDetectionService**: Test OS detection and requirement validation

### Integration Tests
- **Deployment Workflow**: Full deployment process from start to completion
- **Configuration Changes**: Modify configuration and verify container restart
- **Data Export/Import**: Complete data cycle with validation
- **Service Management**: Test all container operations (start, stop, restart, delete)
- **Monitoring Integration**: Verify Docker metrics integrate with existing monitoring

### Business Logic Verification
- **Beginner User Experience**: Complete walkthrough without command line knowledge
- **Error Recovery**: Test all error scenarios with appropriate user guidance
- **Concurrent Operations**: Verify system stability under multiple user load
- **Data Integrity**: Ensure no data loss during operations
- **Permission Handling**: Verify proper sudo privilege management

### Performance Validation
- **Response Times**: All operations complete within acceptable timeframes
- **Memory Usage**: System memory remains stable under concurrent load
- **File Management**: Temporary files are properly cleaned up
- **WebSocket Performance**: Real-time updates remain responsive under load
- **Container Resource Usage**: Monitoring Docker container resource consumption

### Security Verification
- **Input Validation**: All user inputs are properly validated and sanitized
- **File Upload Security**: ZIP files are validated and safely extracted
- **Privilege Escalation**: Sudo operations are properly scoped and secure
- **Configuration Security**: Sensitive data is properly handled and stored
- **Session Management**: User sessions are properly isolated and secure