# SillyTavern Web Deployment Wizard - Technical Specification

## Problem Statement

- **Business Issue**: Current SillyTavern web interface fails when Docker is missing, showing error messages instead of guiding users through installation
- **Current State**: Existing system has a complete 530+ line shell script (`linux-silly-tavern-docker-deploy.sh`) with comprehensive deployment logic, but the web interface only performs basic Docker detection and fails immediately when Docker is missing
- **Expected Outcome**: Transform shell script functionality into interactive web-based deployment wizard that automatically installs Docker and guides non-technical users through complete SillyTavern deployment

## Solution Overview

- **Approach**: Extend existing Spring Boot + Vue 3 architecture to provide interactive web-based deployment wizard that transforms all shell script functionality into user-friendly web interface
- **Core Changes**: Create new backend services for Docker installation and system configuration, add Vue 3 wizard components with dual interaction modes, extend WebSocket messaging protocol for real-time deployment progress
- **Success Criteria**: Users can deploy SillyTavern from zero (no Docker) to fully running service through web interface clicks without any command line interaction

## Technical Implementation

### Database Changes

No database schema changes required - all deployment operations are stateless and use existing SSH connections.

### Code Changes

#### Backend Service Extensions

**File: `src/main/java/com/fufu/terminal/service/sillytavern/InteractiveDeploymentService.java`** - NEW FILE
```java
@Service
public class InteractiveDeploymentService {
    
    // Core deployment orchestration methods
    public CompletableFuture<InteractiveDeploymentResult> startInteractiveDeployment(
        SshConnection connection, 
        InteractiveDeploymentConfig config,
        Consumer<InteractiveDeploymentProgress> progressCallback
    );
    
    public void confirmDeploymentStep(String sessionId, String stepId, boolean confirmed, Map<String, Object> userInput);
    public void skipDeploymentStep(String sessionId, String stepId, String reason);
    public void cancelDeployment(String sessionId, String reason);
    
    // Step execution methods (private)
    private void executeGeoDetection(DeploymentSession session);
    private void executeSystemDetection(DeploymentSession session);
    private void executePackageManagerConfiguration(DeploymentSession session);
    private void executeDockerInstallation(DeploymentSession session);
    private void executeDockerMirrorConfiguration(DeploymentSession session);
    private void executeSillyTavernDeployment(DeploymentSession session);
    private void executeExternalAccessConfiguration(DeploymentSession session);
    private void executeServiceValidation(DeploymentSession session);
}
```

**File: `src/main/java/com/fufu/terminal/service/sillytavern/DockerInstallationService.java`** - NEW FILE
```java
@Service
public class DockerInstallationService {
    
    public CompletableFuture<DockerInstallationResult> installDocker(
        SshConnection connection, 
        String osType, 
        boolean useChineseMirror,
        Consumer<String> progressCallback
    );
    
    public DockerInstallationStatus checkDockerStatus(SshConnection connection);
    public void configureDockerMirror(SshConnection connection, boolean useChineseMirror);
    public void installDockerCompose(SshConnection connection, String osType, boolean useChineseMirror);
    
    // OS-specific installation methods
    private void installDockerDebian(SshConnection connection, String osName, boolean useChineseMirror);
    private void installDockerRedhat(SshConnection connection, String osType, boolean useChineseMirror);
    private void installDockerArch(SshConnection connection);
    private void installDockerAlpine(SshConnection connection);
    private void installDockerSuse(SshConnection connection);
}
```

**File: `src/main/java/com/fufu/terminal/service/sillytavern/SystemConfigurationService.java`** - NEW FILE
```java
@Service
public class SystemConfigurationService {
    
    public GeoDetectionResult detectGeolocation(SshConnection connection);
    public SystemDetectionResult detectSystemInfo(SshConnection connection);
    public void configureSystemMirrors(SshConnection connection, String osType, boolean useChineseMirror);
    
    // System detection methods
    private String detectOperatingSystem(SshConnection connection);
    private String detectOSVersion(SshConnection connection);
    private boolean checkSudoPermissions(SshConnection connection);
    
    // Mirror configuration methods
    private void configureDebianMirrors(SshConnection connection);
    private void configureRedhatMirrors(SshConnection connection);
    private void configureArchMirrors(SshConnection connection);
    private void configureAlpineMirrors(SshConnection connection);
}
```

**File Modifications:**

**Extend: `src/main/java/com/fufu/terminal/controller/SillyTavernStompController.java`**
```java
// Add new WebSocket endpoints
@MessageMapping("/sillytavern/interactive-deploy")
@SendToUser("/queue/sillytavern/interactive-deploy-progress")
public void startInteractiveDeployment(@Payload InteractiveDeploymentRequest request, Principal principal);

@MessageMapping("/sillytavern/deployment-confirm")
@SendToUser("/queue/sillytavern/deployment-confirm")
public void confirmDeploymentStep(@Payload DeploymentConfirmRequest request, Principal principal);

@MessageMapping("/sillytavern/deployment-skip")
@SendToUser("/queue/sillytavern/deployment-skip")
public void skipDeploymentStep(@Payload DeploymentSkipRequest request, Principal principal);

@MessageMapping("/sillytavern/deployment-cancel")
@SendToUser("/queue/sillytavern/deployment-cancel")
public void cancelDeployment(@Payload DeploymentCancelRequest request, Principal principal);
```

**Extend: `src/main/java/com/fufu/terminal/service/sillytavern/SystemDetectionService.java`**
```java
// Add methods to support comprehensive system detection
public boolean checkDockerInstallation(SshConnection connection);
public String detectLinuxDistribution(SshConnection connection);
public boolean checkInternetConnectivity(SshConnection connection);
public boolean checkPortAvailability(SshConnection connection, int port);
public Map<String, String> getSystemResources(SshConnection connection);
```

#### Frontend Component Architecture

**File: `web/ssh-treminal-ui/src/components/sillytavern/DeploymentWizard.vue`** - MODIFY EXISTING
```vue
<template>
  <div class="deployment-wizard">
    <!-- Mode Selection -->
    <div v-if="!deploymentStarted" class="mode-selection">
      <div class="mode-card" @click="startDeployment('trusted')">
        <div class="mode-icon">🤖</div>
        <h3>完全信任模式</h3>
        <p>自动执行所有步骤，使用推荐配置</p>
      </div>
      <div class="mode-card" @click="startDeployment('interactive')">
        <div class="mode-icon">👤</div>
        <h3>分步确认模式</h3>
        <p>每个重要步骤都需要用户确认</p>
      </div>
    </div>

    <!-- Interactive Deployment Progress -->
    <div v-else class="deployment-progress">
      <DeploymentProgressBar 
        :steps="deploymentSteps" 
        :current-step="currentStep"
        :overall-progress="overallProgress"
      />
      
      <div class="deployment-main">
        <!-- Left: Real-time Logs -->
        <div class="deployment-logs">
          <DeploymentLogs 
            :logs="currentStepLogs"
            :is-streaming="isStepRunning"
          />
        </div>
        
        <!-- Right: Step Interaction -->
        <div class="step-interaction">
          <DeploymentStepCard
            v-if="currentStepData"
            :step="currentStepData"
            :mode="deploymentMode"
            @confirm="handleStepConfirm"
            @skip="handleStepSkip"
            @cancel="handleCancel"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// Props and emits
const props = defineProps({
  connection: Object,
  systemInfo: Object,
  isSystemValid: Boolean,
  systemChecking: Boolean
});

const emit = defineEmits(['deployment-started', 'deployment-completed']);

// State management
const deploymentStarted = ref(false);
const deploymentMode = ref(null);
const deploymentSteps = ref([]);
const currentStep = ref(null);
const currentStepData = computed(() => {
  return deploymentSteps.value.find(step => step.id === currentStep.value);
});

// Composable integration
const { 
  interactiveDeployment,
  startInteractiveDeployment,
  confirmDeploymentStep,
  skipDeploymentStep,
  cancelInteractiveDeployment
} = useSillyTavern();

// Methods
const startDeployment = (mode) => {
  deploymentMode.value = mode;
  deploymentStarted.value = true;
  
  const config = {
    mode: mode,
    enableExternalAccess: true,
    port: '8000',
    autoSelectMirrors: mode === 'trusted'
  };
  
  startInteractiveDeployment(config);
  emit('deployment-started', config);
};
</script>
```

**File: `web/ssh-treminal-ui/src/components/sillytavern/DeploymentStepCard.vue`** - NEW FILE
```vue
<template>
  <div class="step-card" :class="stepStatusClass">
    <div class="step-header">
      <div class="step-icon">{{ stepIcon }}</div>
      <div class="step-info">
        <h3 class="step-title">{{ step.title }}</h3>
        <p class="step-description">{{ step.description }}</p>
      </div>
      <div class="step-status">
        <span class="status-badge" :class="step.status">
          {{ statusText }}
        </span>
      </div>
    </div>

    <div class="step-content">
      <!-- Progress bar for running steps -->
      <div v-if="step.status === 'running'" class="step-progress">
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: `${step.progress}%` }"></div>
        </div>
        <span class="progress-text">{{ step.progress }}%</span>
      </div>

      <!-- Confirmation UI for interactive mode -->
      <div v-if="step.requiresConfirmation && step.status === 'waiting'" class="confirmation-ui">
        <div class="confirmation-message">
          {{ step.confirmationMessage }}
        </div>
        
        <!-- Step-specific configuration forms -->
        <component
          v-if="step.configComponent"
          :is="step.configComponent"
          v-model="stepConfig"
          :step-data="step.stepData"
        />
        
        <div class="confirmation-actions">
          <button @click="confirmStep" class="btn btn-primary">
            <i class="fas fa-check"></i>
            确认执行
          </button>
          <button @click="skipStep" class="btn btn-secondary">
            <i class="fas fa-forward"></i>
            跳过此步骤
          </button>
          <button @click="cancelDeployment" class="btn btn-danger">
            <i class="fas fa-times"></i>
            取消部署
          </button>
        </div>
      </div>

      <!-- Completion status -->
      <div v-if="step.status === 'completed'" class="completion-info">
        <div class="success-message">
          <i class="fas fa-check-circle"></i>
          {{ step.completionMessage || '步骤执行成功' }}
        </div>
        <div v-if="step.resultData" class="result-data">
          <pre>{{ JSON.stringify(step.resultData, null, 2) }}</pre>
        </div>
      </div>

      <!-- Error handling -->
      <div v-if="step.status === 'error'" class="error-info">
        <div class="error-message">
          <i class="fas fa-exclamation-triangle"></i>
          {{ step.errorMessage }}
        </div>
        <div class="error-actions">
          <button @click="retryStep" class="btn btn-warning">
            <i class="fas fa-redo"></i>
            重试
          </button>
          <button @click="skipStep" class="btn btn-secondary">
            <i class="fas fa-forward"></i>
            跳过并继续
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
```

**File: `web/ssh-treminal-ui/src/components/sillytavern/DeploymentProgressBar.vue`** - NEW FILE
```vue
<template>
  <div class="progress-container">
    <div class="progress-header">
      <h2>SillyTavern 部署向导</h2>
      <div class="overall-progress">
        <span>总体进度: {{ overallProgress }}%</span>
        <div class="overall-progress-bar">
          <div class="overall-progress-fill" :style="{ width: `${overallProgress}%` }"></div>
        </div>
      </div>
    </div>
    
    <div class="steps-timeline">
      <div 
        v-for="(step, index) in steps" 
        :key="step.id"
        class="timeline-step"
        :class="{
          'step-completed': step.status === 'completed',
          'step-current': step.id === currentStep,
          'step-running': step.status === 'running',
          'step-waiting': step.status === 'waiting',
          'step-error': step.status === 'error',
          'step-skipped': step.status === 'skipped'
        }"
      >
        <div class="step-connector" v-if="index < steps.length - 1"></div>
        <div class="step-marker">
          <i v-if="step.status === 'completed'" class="fas fa-check"></i>
          <i v-else-if="step.status === 'running'" class="fas fa-spinner fa-spin"></i>
          <i v-else-if="step.status === 'error'" class="fas fa-times"></i>
          <i v-else-if="step.status === 'skipped'" class="fas fa-forward"></i>
          <i v-else-if="step.status === 'waiting'" class="fas fa-pause"></i>
          <span v-else class="step-number">{{ index + 1 }}</span>
        </div>
        <div class="step-label">
          {{ step.title }}
          <div v-if="step.status === 'running'" class="step-progress-mini">
            {{ step.progress }}%
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
```

#### WebSocket Message Protocol Extensions

**New Message Types:**
```typescript
// Deployment step progress
interface InteractiveDeploymentProgress {
  stepId: string;
  stepTitle: string;
  status: 'pending' | 'running' | 'waiting' | 'completed' | 'error' | 'skipped';
  progress: number; // 0-100
  message: string;
  requiresConfirmation: boolean;
  confirmationMessage?: string;
  stepData?: any; // Step-specific data for UI
  resultData?: any; // Step completion results
  errorMessage?: string;
  logs: LogEntry[];
}

// Deployment configuration
interface InteractiveDeploymentConfig {
  mode: 'trusted' | 'interactive';
  enableExternalAccess: boolean;
  port: string;
  autoSelectMirrors: boolean;
  customMirrors?: MirrorConfig;
  accessCredentials?: {
    username: string;
    password: string;
    generateRandom: boolean;
  };
}

// Step confirmation
interface DeploymentConfirmRequest {
  stepId: string;
  confirmed: boolean;
  userInput: Record<string, any>;
}

// Step skip request
interface DeploymentSkipRequest {
  stepId: string;
  reason: string;
}

// Deployment cancellation
interface DeploymentCancelRequest {
  reason: string;
}
```

### API Changes

**New WebSocket Endpoints:**
- `/app/sillytavern/interactive-deploy` - Start interactive deployment
- `/app/sillytavern/deployment-confirm` - Confirm deployment step
- `/app/sillytavern/deployment-skip` - Skip deployment step  
- `/app/sillytavern/deployment-cancel` - Cancel deployment

**New Response Queues:**
- `/user/queue/sillytavern/interactive-deploy-progress` - Step-by-step progress updates
- `/user/queue/sillytavern/deployment-confirm` - Confirmation responses
- `/user/queue/sillytavern/deployment-skip` - Skip responses
- `/user/queue/sillytavern/deployment-cancel` - Cancellation responses

**Request/Response Structures:**

**Interactive Deployment Request:**
```json
{
  "mode": "trusted|interactive",
  "enableExternalAccess": true,
  "port": "8000",
  "autoSelectMirrors": true,
  "accessCredentials": {
    "username": "admin",
    "password": "random123",
    "generateRandom": false
  }
}
```

**Deployment Progress Response:**
```json
{
  "success": true,
  "payload": {
    "stepId": "docker-installation",
    "stepTitle": "Docker安装",
    "status": "running",
    "progress": 65,
    "message": "正在安装Docker CE...",
    "requiresConfirmation": false,
    "logs": [
      {
        "timestamp": "2025-01-20T10:30:00Z",
        "level": "INFO",
        "message": "开始安装Docker..."
      }
    ]
  }
}
```

### Configuration Changes

**No new configuration parameters required** - all deployment settings are passed dynamically through WebSocket messages.

## Implementation Sequence

### Phase 1: Backend Core Services
- Create `InteractiveDeploymentService` with session management
- Create `DockerInstallationService` with OS-specific installation methods
- Create `SystemConfigurationService` for geo-detection and mirror configuration
- Extend existing `SystemDetectionService` with comprehensive checks
- Add new WebSocket endpoints to `SillyTavernStompController`

### Phase 2: Shell Script Integration
- Transform each shell script function into corresponding Java service method:
  - `configure_system_mirrors()` → `SystemConfigurationService.configureSystemMirrors()`
  - `install_docker_debian_based()` → `DockerInstallationService.installDockerDebian()`
  - `install_docker_redhat_based()` → `DockerInstallationService.installDockerRedhat()`
  - `configure_docker_mirror()` → `DockerInstallationService.configureDockerMirror()`
  - `setup_docker_compose()` → `DockerInstallationService.installDockerCompose()`
- Implement geo-detection logic from shell script lines 18-27
- Implement OS detection logic from shell script lines 32-50

### Phase 3: Frontend Wizard Components
- Modify existing `DeploymentWizard.vue` to support dual-mode selection
- Create `DeploymentStepCard.vue` for individual step interaction
- Create `DeploymentProgressBar.vue` for overall progress visualization
- Create `DeploymentLogs.vue` for real-time log streaming
- Extend `useSillyTavern.js` composable with interactive deployment methods

### Phase 4: Docker Installation Flow Implementation
- Implement critical Docker installation gap fix:
  - When Docker is missing, automatically start installation instead of showing error
  - Support all Linux distributions from shell script (Ubuntu, Debian, CentOS, RHEL, Fedora, Arch, Alpine, SUSE)
  - Handle permission elevation through SSH sudo commands
  - Provide real-time installation progress feedback
- Test Docker installation on multiple Linux distributions

### Phase 5: Integration and Testing
- Integrate all components into existing `SillyTavernConsole.vue`
- Add comprehensive error handling and recovery mechanisms
- Implement deployment state persistence across WebSocket reconnections
- Add deployment cancellation and rollback capabilities

## Validation Plan

### Unit Tests
- `InteractiveDeploymentServiceTest.java` - Test deployment orchestration logic
- `DockerInstallationServiceTest.java` - Test OS-specific Docker installation methods
- `SystemConfigurationServiceTest.java` - Test geo-detection and system configuration
- `DeploymentWizard.test.js` - Test Vue component interaction flows
- `useSillyTavern.test.js` - Test composable state management

### Integration Tests
- End-to-end deployment flow from "no Docker" to "running SillyTavern"
- Multi-user concurrent deployment scenarios
- Network interruption and recovery testing
- Permission elevation and sudo handling
- WebSocket message protocol validation

### Business Logic Verification
- **Docker Installation Gap Fix**: Verify that missing Docker triggers installation wizard instead of error
- **Shell Script Parity**: Verify all 530+ lines of shell script functionality work through web interface
- **Dual Mode Operation**: Verify both "trusted" and "interactive" modes work correctly
- **Cross-Platform Support**: Test deployment on Ubuntu, Debian, CentOS, Fedora, Arch Linux
- **User Experience**: Verify non-technical users can complete deployment without command line access

## Key Implementation Details

### Docker Installation Gap Fix
The critical issue identified in requirements is that the current web interface fails when Docker is missing. The technical solution:

1. **Detection Phase**: `SystemDetectionService.checkDockerInstallation()` returns false
2. **Installation Trigger**: Instead of showing error, automatically start Docker installation wizard
3. **OS-Specific Installation**: Route to appropriate installation method based on detected Linux distribution
4. **Progress Feedback**: Stream real-time installation progress through WebSocket
5. **Verification**: Confirm Docker installation success before proceeding to SillyTavern deployment

### Shell Script to Service Method Mapping
Direct transformation of shell script functions to Java service methods:

| Shell Script Function | Java Service Method | Purpose |
|----------------------|-------------------|---------|
| `configure_system_mirrors()` | `SystemConfigurationService.configureSystemMirrors()` | Configure package manager mirrors |
| `install_docker_debian_based()` | `DockerInstallationService.installDockerDebian()` | Install Docker on Debian/Ubuntu |
| `install_docker_redhat_based()` | `DockerInstallationService.installDockerRedhat()` | Install Docker on RHEL/CentOS/Fedora |
| `install_docker_arch()` | `DockerInstallationService.installDockerArch()` | Install Docker on Arch Linux |
| `configure_docker_mirror()` | `DockerInstallationService.configureDockerMirror()` | Configure Docker registry mirrors |
| `setup_docker_compose()` | `DockerInstallationService.installDockerCompose()` | Install Docker Compose |

### Deployment Session Management
Each interactive deployment creates a session with:
- **Session ID**: Unique identifier for tracking deployment across WebSocket messages
- **Step State**: Current progress through 9-step deployment process
- **User Preferences**: Mode selection and configuration choices
- **Error Recovery**: Ability to retry failed steps or rollback changes
- **Real-time Communication**: Bidirectional WebSocket messaging for progress and confirmations

This specification provides the complete technical blueprint for transforming the SillyTavern shell script into an interactive web deployment wizard, directly addressing the Docker installation gap and providing a beginner-friendly deployment experience.