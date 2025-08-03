# SSH Terminal Management System Refactoring - Design Document

## Overview

This design document outlines the architectural refactoring strategy for the SSH Terminal Management System, transforming it from an over-engineered enterprise architecture to a simplified personal project scale while implementing a 4-type atomic script classification system. The refactoring maintains core functionality while significantly reducing complexity and improving maintainability.

## Architecture

### Current Architecture Issues

1. **Controller Redundancy**: Multiple controllers (`ScriptController`, `UserScriptExecutionController`, `UnifiedScriptExecutionController`) with overlapping responsibilities
2. **Service Layer Over-abstraction**: Excessive abstraction layers (`AtomicScriptService`, `ScriptEngineService`, `TaskExecutionService`) creating unnecessary complexity
3. **Database Over-normalization**: Complex schema with unnecessary relationships for a personal project scale
4. **Mixed Script Management**: Built-in scripts stored in database alongside user scripts, violating separation of concerns
5. **Inconsistent Execution Patterns**: Different execution paths for similar script types

### Target Architecture Principles

1. **KISS (Keep It Simple, Stupid)**: Reduce abstraction layers while maintaining functionality
2. **Clear Separation of Concerns**: Built-in scripts in code, user scripts in database
3. **Unified Execution Pipeline**: Single execution path with type-specific handlers
4. **Real-time Communication**: WebSocket-based progress reporting and user interaction
5. **Variable Passing**: Context-based variable sharing between scripts

## Components and Interfaces

### 1. Unified Script Execution Controller

**Purpose**: Single entry point for all script execution requests

```java
@RestController
@RequestMapping("/api/scripts")
public class ScriptExecutionController {
    
    // Consolidated endpoints
    @PostMapping("/execute/{scriptId}")
    ResponseEntity<String> executeScript(@PathVariable String scriptId, @RequestBody ExecutionRequest request);
    
    @GetMapping("/progress/{sessionId}")
    ResponseEntity<ExecutionProgress> getProgress(@PathVariable String sessionId);
    
    @PostMapping("/interact/{sessionId}")
    ResponseEntity<String> handleInteraction(@PathVariable String sessionId, @RequestBody InteractionResponse response);
    
    @GetMapping("/types")
    ResponseEntity<List<ScriptTypeInfo>> getScriptTypes();
    
    @GetMapping("/list/{type}")
    ResponseEntity<List<ScriptInfo>> getScriptsByType(@PathVariable ScriptType type);
}
```

**Key Features**:
- Replaces 3 existing controllers with unified interface
- Type-agnostic execution with internal routing based on script type
- Consistent error handling and response format
- WebSocket integration for real-time updates

### 2. Four-Type Script Classification System

**Script Type Enumeration**:
```java
public enum ScriptType {
    STATIC_BUILTIN,      // No parameters, immediate execution (e.g., location detection)
    CONFIGURABLE_BUILTIN, // Parameters with intelligent decision making (e.g., mirror selection)
    INTERACTIVE_BUILTIN,  // Real-time user interaction during execution
    USER_SCRIPT          // Admin-configurable scripts stored in database
}
```

**Script Interface**:
```java
public interface ExecutableScript {
    String getId();
    String getName();
    String getDescription();
    ScriptType getType();
    List<ScriptParameter> getParameters();
    boolean shouldExecute(ExecutionContext context);
    ScriptResult execute(ExecutionContext context);
    boolean supportsInteraction();
}
```

### 3. Script Registry and Management

**Built-in Script Registry**:
```java
@Component
public class BuiltinScriptRegistry {
    
    private final Map<String, ExecutableScript> staticScripts = new HashMap<>();
    private final Map<String, ExecutableScript> configurableScripts = new HashMap<>();
    private final Map<String, ExecutableScript> interactiveScripts = new HashMap<>();
    
    @PostConstruct
    public void initializeBuiltinScripts() {
        // Register static scripts
        registerScript(new ServerLocationDetectionScript());
        registerScript(new SystemInfoCollectionScript());
        
        // Register configurable scripts
        registerScript(new MirrorConfigurationScript());
        registerScript(new SoftwareInstallationScript());
        
        // Register interactive scripts
        registerScript(new CustomInstallationScript());
        registerScript(new ConfigurationWizardScript());
    }
}
```

**User Script Service**:
```java
@Service
public class UserScriptService {
    
    @Autowired
    private ScriptRepository scriptRepository;
    
    public List<ExecutableScript> getUserScripts(Long userId);
    public ExecutableScript createUserScript(UserScriptDefinition definition);
    public void updateUserScript(String scriptId, UserScriptDefinition definition);
    public void deleteUserScript(String scriptId);
}
```

### 4. Unified Execution Engine

**Execution Service**:
```java
@Service
public class ScriptExecutionService {
    
    @Autowired
    private BuiltinScriptRegistry builtinRegistry;
    
    @Autowired
    private UserScriptService userScriptService;
    
    @Autowired
    private ExecutionContextManager contextManager;
    
    @Autowired
    private WebSocketProgressReporter progressReporter;
    
    public String executeScript(String scriptId, ExecutionRequest request) {
        // 1. Resolve script by ID and type
        ExecutableScript script = resolveScript(scriptId);
        
        // 2. Create execution context with SSH connection
        ExecutionContext context = contextManager.createContext(request.getSshConfig());
        
        // 3. Validate parameters if script is configurable
        if (script.getType() == ScriptType.CONFIGURABLE_BUILTIN) {
            validateParameters(script, request.getParameters());
        }
        
        // 4. Start async execution
        String sessionId = UUID.randomUUID().toString();
        CompletableFuture.runAsync(() -> executeAsync(sessionId, script, context));
        
        return sessionId;
    }
    
    private void executeAsync(String sessionId, ExecutableScript script, ExecutionContext context) {
        try {
            progressReporter.reportStart(sessionId, script.getName());
            
            // Execute with type-specific handling
            ScriptResult result = script.execute(context);
            
            progressReporter.reportCompletion(sessionId, result);
        } catch (Exception e) {
            progressReporter.reportError(sessionId, e);
        }
    }
}
```

### 5. Variable Passing and Context Management

**Execution Context**:
```java
public class ExecutionContext {
    
    private final SshConnection sshConnection;
    private final String sessionId;
    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    private final UserInteractionHandler interactionHandler;
    
    // Variable management
    public void setVariable(String name, Object value, VariableScope scope);
    public <T> T getVariable(String name, Class<T> type);
    public boolean hasVariable(String name);
    
    // SSH command execution
    public CommandResult executeCommand(String command);
    
    // User interaction
    public String promptUser(String message, InputType type);
    public boolean confirmAction(String message);
}
```

**Variable Scope Types**:
```java
public enum VariableScope {
    SESSION,     // Available throughout entire session
    SCRIPT,      // Available only within current script
    GLOBAL       // Available across all executions for user
}
```

### 6. Intelligent Decision Making System

**Geographic Mirror Selection**:
```java
@Component
public class GeographicMirrorSelector {
    
    public MirrorConfiguration selectOptimalMirror(String detectedLocation) {
        if (isChineseLocation(detectedLocation)) {
            return selectChineseMirror();
        } else {
            return selectInternationalMirror();
        }
    }
    
    private MirrorConfiguration selectChineseMirror() {
        // Priority: Aliyun > Tsinghua > USTC > 163
        return MirrorConfiguration.builder()
            .aptMirror("https://mirrors.aliyun.com/ubuntu/")
            .dockerMirror("https://mirror.baidubce.com")
            .npmMirror("https://registry.npmmirror.com")
            .build();
    }
}
```

### 7. Real-time User Interaction System

**WebSocket Interaction Handler**:
```java
@Component
public class UserInteractionHandler {
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    private final Map<String, CompletableFuture<String>> pendingInteractions = new ConcurrentHashMap<>();
    
    public String promptUser(String sessionId, InteractionPrompt prompt) {
        CompletableFuture<String> responseFuture = new CompletableFuture<>();
        pendingInteractions.put(sessionId, responseFuture);
        
        // Send prompt via WebSocket
        messagingTemplate.convertAndSend("/topic/interaction/" + sessionId, prompt);
        
        try {
            // Wait for user response with timeout
            return responseFuture.get(5, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            return prompt.getDefaultValue();
        }
    }
    
    public void receiveUserResponse(String sessionId, String response) {
        CompletableFuture<String> future = pendingInteractions.remove(sessionId);
        if (future != null) {
            future.complete(response);
        }
    }
}
```

## Data Models

### 1. Simplified Database Schema

**User Scripts Table**:
```sql
CREATE TABLE user_scripts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    script_content TEXT NOT NULL,
    parameters JSON,
    created_by BIGINT NOT NULL,
    group_id BIGINT,
    status ENUM('ACTIVE', 'INACTIVE') DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (created_by) REFERENCES users(id),
    FOREIGN KEY (group_id) REFERENCES script_groups(id)
);
```

**Script Groups Table** (Simplified):
```sql
CREATE TABLE script_groups (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    icon VARCHAR(50),
    sort_order INT DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (created_by) REFERENCES users(id)
);
```

**Execution History Table**:
```sql
CREATE TABLE execution_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(36) NOT NULL,
    script_id VARCHAR(100) NOT NULL,
    script_type ENUM('STATIC_BUILTIN', 'CONFIGURABLE_BUILTIN', 'INTERACTIVE_BUILTIN', 'USER_SCRIPT') NOT NULL,
    user_id BIGINT NOT NULL,
    status ENUM('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED') NOT NULL,
    parameters JSON,
    result_data JSON,
    error_message TEXT,
    execution_time_ms INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_session_id (session_id),
    INDEX idx_user_script (user_id, script_type, created_at)
);
```

### 2. Built-in Script Definitions (Code-based)

**Static Built-in Scripts**:
- `server-location-detection`: Detect server geographic location
- `system-info-collection`: Collect system information and specifications
- `network-connectivity-test`: Test network connectivity and speed
- `security-status-check`: Check system security status

**Configurable Built-in Scripts**:
- `mirror-configuration`: Configure package manager mirrors based on location
- `docker-installation`: Install Docker with optimal mirror sources
- `node-installation`: Install Node.js with geographic-optimized npm registry
- `development-environment-setup`: Set up development environment with optimal configurations

**Interactive Built-in Scripts**:
- `custom-software-installation`: Interactive software installation with user choices
- `system-configuration-wizard`: Step-by-step system configuration
- `database-setup-wizard`: Interactive database installation and configuration

## Error Handling

### 1. Hierarchical Error Handling

**Error Classification**:
```java
public enum ErrorType {
    VALIDATION_ERROR,    // Parameter validation failures
    SSH_CONNECTION_ERROR, // SSH connectivity issues
    SCRIPT_EXECUTION_ERROR, // Script runtime errors
    USER_INTERACTION_TIMEOUT, // User input timeout
    SYSTEM_RESOURCE_ERROR, // System resource limitations
    CONFIGURATION_ERROR   // Configuration or setup errors
}
```

**Error Response Format**:
```java
public class ErrorResponse {
    private String errorCode;
    private String message;
    private String userMessage;
    private List<String> suggestions;
    private Map<String, Object> context;
    private long timestamp;
}
```

### 2. Recovery Mechanisms

**SSH Connection Recovery**:
- Automatic reconnection with exponential backoff
- Connection pool management to prevent resource leaks
- Graceful degradation when SSH is unavailable

**Script Execution Recovery**:
- Partial execution state preservation
- Rollback mechanisms for failed installations
- Resume capability for interrupted operations

## Testing Strategy

### 1. Unit Testing Framework

**Controller Testing**:
```java
@WebMvcTest(ScriptExecutionController.class)
class ScriptExecutionControllerTest {
    
    @Test
    void shouldExecuteStaticBuiltinScript() {
        // Test static script execution without parameters
    }
    
    @Test
    void shouldValidateParametersForConfigurableScript() {
        // Test parameter validation for configurable scripts
    }
    
    @Test
    void shouldHandleUserInteractionForInteractiveScript() {
        // Test WebSocket interaction handling
    }
}
```

**Service Layer Testing**:
```java
@SpringBootTest
class ScriptExecutionServiceTest {
    
    @Test
    void shouldPassVariablesBetweenScripts() {
        // Test variable passing mechanism
    }
    
    @Test
    void shouldSelectOptimalMirrorBasedOnLocation() {
        // Test geographic decision making
    }
}
```

### 2. Integration Testing

**End-to-End Workflow Testing**:
- Complete script execution workflows for each script type
- WebSocket communication testing
- Database integration testing
- SSH connection management testing

**Performance Testing**:
- Concurrent execution testing (10+ simultaneous scripts)
- Memory usage monitoring during long-running scripts
- SSH connection pool efficiency testing

## Implementation Migration Plan

### Phase 1: Controller Consolidation
1. Create new `ScriptExecutionController` with unified endpoints
2. Implement type-based routing to existing services
3. Update frontend to use new endpoints
4. Deprecate old controllers with migration warnings

### Phase 2: Script Type System Implementation
1. Create `ScriptType` enumeration and interfaces
2. Implement built-in script registry
3. Create execution context and variable passing system
4. Update database schema to support new type system

### Phase 3: Service Layer Simplification
1. Merge redundant service classes
2. Remove unnecessary abstraction layers
3. Simplify dependency injection structure
4. Update unit tests for simplified services

### Phase 4: Frontend Enhancement
1. Update Vue.js components for new script type system
2. Implement WebSocket-based progress reporting
3. Create user interaction modal components
4. Update admin interface for user script management

### Phase 5: Database Migration and Cleanup
1. Create migration scripts for schema changes
2. Remove built-in scripts from database
3. Optimize remaining database structure
4. Update data access patterns

This design provides a clear path to simplify the architecture while implementing the requested 4-type script classification system with variable passing and intelligent decision-making capabilities.