# Terminal Improvement Plan - Technical Specifications

## Problem Statement
- **Business Issue**: Critical security vulnerabilities in SSH terminal application including plaintext password transmission over STOMP headers, disabled SSH host key verification, and memory instability from base64 file transfers
- **Current State**: Spring Boot backend with Vue 3 frontend using WebSocket STOMP for terminal communication, JSch for SSH connections, and base64 encoding for file transfers
- **Expected Outcome**: Secure credential handling with RSA encryption, improved memory stability for file transfers, unified architecture, and enhanced user experience

## Solution Overview
- **Approach**: Implement 5-phase improvement plan starting with critical security baseline using RSA encryption for credential transmission, followed by transfer optimization, architecture unification, frontend modernization, and observability
- **Core Changes**: RSA public-key encryption on frontend with private-key decryption on backend, short-term token system, streaming file transfers, unified STOMP destinations, TypeScript migration, and comprehensive monitoring
- **Success Criteria**: Zero plaintext credentials in transit, stable memory usage under load, consistent UI/UX, >99.9% WebSocket stability, and comprehensive observability

## Technical Implementation

### Database Changes
**No database changes required** - Using in-memory token vault with TTL for credential security:
- **In-Memory Token Vault**: `ConcurrentHashMap<String, EncryptedCredentials>` with 2-minute TTL
- **RSA Key Pair**: Generated at server startup and stored in memory
- **Session Management**: Existing STOMP session handling with enhanced security

### Code Changes

#### Phase 0: Security Baseline (Critical Priority)

**Backend Changes:**

**File: `src/main/java/com/fufu/terminal/security/CryptoService.java`** (NEW)
```java
@Service
public class CryptoService {
    private KeyPair keyPair;
    private PublicKey publicKey;
    private PrivateKey privateKey;
    
    @PostConstruct
    public void generateKeys() {
        // Generate RSA-2048 key pair for credential encryption
    }
    
    public String getPublicKeyBase64();
    public String decryptCredentials(String encryptedData);
}
```

**File: `src/main/java/com/fufu/terminal/security/TokenVault.java`** (NEW)
```java
@Component
public class TokenVault {
    private final Map<String, VaultEntry> vault = new ConcurrentHashMap<>();
    
    public String storeCredentials(String host, String port, String user, String password);
    public VaultEntry retrieveAndRemove(String token);
    
    private static class VaultEntry {
        private final String host, port, user, password;
        private final long expiryTime;
    }
}
```

**File: `src/main/java/com/fufu/terminal/controller/SecurityController.java`** (NEW)
```java
@RestController
@RequestMapping("/api/security")
public class SecurityController {
    
    @GetMapping("/public-key")
    public PublicKeyResponse getPublicKey() {
        // Return RSA public key for frontend encryption
    }
    
    @PostMapping("/session/token")
    public TokenResponse createSessionToken(@RequestBody EncryptedCredentialsRequest request) {
        // Decrypt credentials, validate, store in vault, return token
    }
}
```

**File: `src/main/java/com/fufu/terminal/config/StompAuthenticationInterceptor.java`** (MODIFY)
- **Lines 84-90**: Remove password header reading, implement token-based authentication
- **Line 101**: Change `StrictHostKeyChecking` to "yes" 
- **Line 127**: Sanitize logging to remove sensitive information
- **Add**: Token validation and credential retrieval from vault

**File: `src/main/java/com/fufu/terminal/config/WebSocketStompConfig.java`** (MODIFY)
- **Lines 41-46**: Implement profile-based origin restrictions
```java
@Profile("prod")
setAllowedOriginPatterns(Arrays.asList(
    "https://your-domain.com",
    "https://staging.your-domain.com"
));

@Profile({"dev", "test"})
setAllowedOriginPatterns(Arrays.asList("*"));
```

**Frontend Changes:**

**File: `web/ssh-treminal-ui/src/services/crypto.js`** (NEW)
```javascript
export class CryptoService {
    static async getPublicKey() {
        // Fetch RSA public key from backend
    }
    
    static async encryptCredentials(credentials, publicKey) {
        // Encrypt using Web Crypto API RSA-OAEP
    }
}
```

**File: `web/ssh-treminal-ui/src/services/auth.js`** (NEW)
```javascript
export class AuthService {
    static async getSessionToken(encryptedCredentials) {
        // POST to /api/security/session/token
    }
    
    static isTokenValid(token) {
        // Check token expiry
    }
}
```

**File: `web/ssh-treminal-ui/src/composables/useTerminal.js`** (MODIFY)
- **Lines 49-56**: Remove password from connectHeaders, add Authorization bearer token
- **Lines 65-69**: Add environment guards for debug logging
- **Line 60**: Implement exponential backoff with jitter for reconnection

#### Phase 1: Transfer Safety and Memory Stability (High Priority)

**Backend Changes:**

**File: `src/main/java/com/fufu/terminal/controller/SftpController.java`** (NEW)
```java
@RestController
@RequestMapping("/api/sftp")
public class SftpController {
    
    @GetMapping(value = "/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Flux<DataBuffer> downloadFile(@RequestParam String path, @RequestParam String sessionId) {
        // Stream file download using Flux<DataBuffer>
    }
    
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
                                       @RequestParam String path,
                                       @RequestParam String sessionId) {
        // Handle chunked file upload with temporary files
    }
}
```

**File: `src/main/java/com/fufu/terminal/service/StreamingSftpService.java`** (NEW)
```java
@Service
public class StreamingSftpService {
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024; // 100MB
    private static final long MAX_CONCURRENT_TRANSFERS = 3;
    
    public Flux<DataBuffer> streamDownload(String path, String sessionId);
    public Mono<UploadResult> handleUpload(MultipartFile file, String path, String sessionId);
    public void cleanupTempFiles();
}
```

**File: `src/main/java/com/fufu/terminal/service/SftpService.java`** (MODIFY)
- **Lines 270-301**: Replace ByteArrayOutputStream with streaming approach
- **Lines 311-317**: Implement file size limits and memory optimization

**Frontend Changes:**

**File: `web/ssh-treminal-ui/src/services/sftp.js`** (NEW)
```javascript
export class SftpService {
    static async downloadFiles(paths, sessionId) {
        // Use fetch API for streaming download
        // Convert to Blob and trigger download
    }
    
    static async uploadFile(file, path, sessionId, onProgress) {
        // Upload using ArrayBuffer chunks via fetch API
        // Report progress via callback
    }
}
```

**File: `web/ssh-treminal-ui/src/composables/useTerminal.js`** (MODIFY)
- **Lines 236-240, 425-443**: Replace base64 download with HTTP streaming
- **Lines 371-423**: Replace FileReader+base64 upload with binary chunking
- **Add**: Terminal output buffering with requestAnimationFrame

#### Phase 2: Architecture Unification (High Priority)

**Backend Changes:**

**File: `src/main/java/com/fufu/terminal/service/UnifiedSshCommandService.java`** (NEW)
```java
@Service
public class UnifiedSshCommandService {
    
    public CommandResult executeOrThrow(Session session, String command, CommandOptions options);
    public CommandResult execute(Session session, String command, CommandOptions options);
    public void stream(Session session, String command, CommandOptions options, 
                      Consumer<String> onStdout, Consumer<String> onStderr);
    
    private boolean isDangerous(String command);
    private String redactSecrets(String command);
}
```

**File: `src/main/java/com/fufu/terminal/config/StompDestinationConfig.java`** (NEW)
```java
@Configuration
public class StompDestinationConfig {
    public static final String APP_PREFIX = "/app";
    public static final String USER_TERMINAL = "/user/queue/terminal";
    public static final String USER_SFTP = "/user/queue/sftp";
    public static final String USER_MONITOR = "/user/queue/monitor";
    public static final String USER_ERRORS = "/user/queue/errors";
}
```

**Removal:**
- Delete `src/main/java/com/fufu/terminal/config/LegacyWebSocketConfig.java` (after migration)
- Delete `src/main/java/com/fufu/terminal/handler/SshTerminalWebSocketHandler.java` (after migration)

#### Phase 3: Frontend Modernization (Medium Priority)

**Frontend Changes:**

**File: `web/ssh-treminal-ui/tsconfig.json`** (NEW)
```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "ESNext",
    "strict": true,
    "allowJs": true,
    "noEmit": true
  },
  "include": ["src/**/*"]
}
```

**File: `web/ssh-treminal-ui/src/stores/terminal.js`** (NEW)
```javascript
import { defineStore } from 'pinia'

export const useTerminalStore = defineStore('terminal', {
    state: () => ({
        isConnected: false,
        isConnecting: false,
        connectionDetails: null,
        sessionToken: null
    }),
    
    actions: {
        async connect(credentials) {
            // Encrypted connection flow
        },
        
        disconnect() {
            // Clean disconnect
        }
    }
})
```

**File: `web/ssh-treminal-ui/src/stores/sftp.js`** (NEW)
```javascript
export const useSftpStore = defineStore('sftp', {
    state: () => ({
        currentPath: '',
        files: [],
        transfers: new Map(),
        isVisible: false
    }),
    
    actions: {
        async fetchFiles(path = '.'),
        async uploadFile(file, onProgress),
        async downloadFiles(paths)
    }
})
```

**File: `web/ssh-treminal-ui/src/composables/useWebSocket.js`** (NEW)
```javascript
export function useWebSocket() {
    const reconnectAttempts = ref(0)
    const maxReconnectAttempts = 5
    
    const connect = (token) => {
        // Resilient connection with exponential backoff + jitter
    }
    
    const exponentialBackoff = (attempt) => {
        const base = 1000
        const max = 30000
        const delay = Math.min(base * Math.pow(2, attempt), max)
        return delay + Math.random() * 1000
    }
    
    return { connect, disconnect, isConnected }
}
```

**Design System Files:**
- `web/ssh-treminal-ui/src/components/base/Button.vue`
- `web/ssh-treminal-ui/src/components/base/Input.vue`
- `web/ssh-treminal-ui/src/components/base/Modal.vue`
- `web/ssh-treminal-ui/src/styles/tokens.css` (CSS custom properties for theming)

#### Phase 4: Observability and CI/CD (Medium Priority)

**Backend Changes:**

**File: `src/main/java/com/fufu/terminal/config/MetricsConfig.java`** (NEW)
```java
@Configuration
@EnableMetrics
public class MetricsConfig {
    
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
    
    @Bean
    public CounterService counterService(MeterRegistry registry) {
        return new CounterService(registry);
    }
}
```

**File: `src/main/java/com/fufu/terminal/metrics/TerminalMetrics.java`** (NEW)
```java
@Component
public class TerminalMetrics {
    private final Counter activeSessionsCounter;
    private final Timer commandExecutionTimer;
    private final Counter transferBytesCounter;
    
    @EventListener
    public void onSessionConnect(SessionConnectEvent event);
    
    @EventListener
    public void onCommandExecute(CommandExecuteEvent event);
}
```

**CI/CD Files:**
- `.github/workflows/backend.yml` (Maven test, dependency scan)
- `.github/workflows/frontend.yml` (npm test, lint, build)
- `.github/workflows/security.yml` (OWASP dependency check)

### API Changes

#### New Endpoints

**Security API:**
```
GET /api/security/public-key
Response: { publicKey: "base64-encoded-rsa-public-key" }

POST /api/security/session/token  
Request: { encryptedCredentials: "base64-encrypted-data" }
Response: { token: "uuid-token", expiresInSec: 120 }
```

**SFTP API:**
```
GET /api/sftp/download?path=<path>&sessionId=<sessionId>
Response: Binary stream (application/octet-stream)

POST /api/sftp/upload
Content-Type: multipart/form-data
Form fields: file, path, sessionId
Response: { success: true, filename: "uploaded-file.txt" }
```

**Metrics API:**
```
GET /actuator/metrics/terminal.sessions.active
GET /actuator/metrics/terminal.commands.duration
GET /actuator/metrics/terminal.transfers.bytes
```

### Configuration Changes

**File: `src/main/resources/application.properties`** (MODIFY)
```properties
# Environment-specific logging
logging.level.com.fufu.terminal=INFO
logging.level.com.fufu.terminal.config.StompAuthenticationInterceptor=WARN

# Profile-specific settings
---
spring.config.activate.on-profile=dev
logging.level.com.fufu.terminal=DEBUG
terminal.security.strict-host-checking=false

---
spring.config.activate.on-profile=prod  
logging.level.com.fufu.terminal=INFO
terminal.security.strict-host-checking=true
terminal.security.allowed-origins=https://your-domain.com,https://staging.your-domain.com
```

**File: `web/ssh-treminal-ui/vite.config.js`** (MODIFY)
```javascript
export default defineConfig({
  server: {
    proxy: {
      '/ws': 'ws://localhost:8080',
      '/api': 'http://localhost:8080'
    }
  },
  define: {
    __WS_BASE_URL__: process.env.NODE_ENV === 'production' 
      ? '"wss://your-domain.com"' 
      : '"ws://localhost:8080"'
  }
})
```

## Implementation Sequence

### Phase 0: Security Baseline (2-3 days)
1. **Backend Security Infrastructure**
   - Create `CryptoService` for RSA key generation and decryption
   - Create `TokenVault` for secure credential storage
   - Create `SecurityController` for public key and token endpoints
   - Modify `StompAuthenticationInterceptor` for token-based auth
   - Update `WebSocketStompConfig` for origin restrictions

2. **Frontend Security Integration**
   - Create `crypto.js` service for credential encryption
   - Create `auth.js` service for token management  
   - Update `useTerminal.js` to use token-based authentication
   - Remove password from STOMP headers

3. **SSH Configuration Hardening**
   - Set `StrictHostKeyChecking=yes` in production
   - Implement environment-specific known_hosts management
   - Add connection limits and anomaly detection

### Phase 1: Transfer Optimization (3-4 days)
1. **Backend Streaming Implementation**
   - Create `SftpController` for HTTP-based file operations
   - Create `StreamingSftpService` with memory-efficient streaming
   - Implement file size limits and concurrent transfer restrictions
   - Add temporary file cleanup mechanisms

2. **Frontend Transfer Modernization**
   - Create `sftp.js` service for HTTP-based transfers
   - Replace base64 encoding with binary streaming
   - Implement chunked upload with progress reporting
   - Add transfer cancellation and retry mechanisms

3. **Terminal Output Optimization**
   - Implement output buffering with requestAnimationFrame
   - Add back-pressure handling for high-volume output
   - Optimize xterm.js integration with WebGL acceleration

### Phase 2: Architecture Unification (2-3 days)
1. **Unified Command Service**
   - Create `UnifiedSshCommandService` with policy enforcement
   - Implement command sanitization and rate limiting
   - Add audit logging with secret redaction
   - Migrate existing command wrappers

2. **STOMP Standardization**
   - Create `StompDestinationConfig` for consistent routing
   - Standardize all destinations to `/user/queue/*` pattern
   - Implement subscription authorization checks
   - Remove legacy WebSocket handlers

3. **Session Management Enhancement**
   - Improve `StompSessionManager` reliability
   - Add session lifecycle monitoring
   - Implement graceful disconnection handling

### Phase 3: Frontend Modernization (5-7 days)
1. **TypeScript Migration**
   - Setup TypeScript configuration with allowJs
   - Migrate core services to TypeScript
   - Add type definitions for API responses
   - Implement strict typing for new components

2. **Pinia State Management**
   - Create stores for terminal, SFTP, and monitoring
   - Migrate from composition API state to Pinia
   - Implement persistent state for user preferences
   - Add state synchronization across components

3. **Design System Implementation**
   - Create base component library with consistent styling
   - Implement CSS custom properties for theming
   - Add light/dark theme support
   - Ensure WCAG AA accessibility compliance

4. **UX Improvements**
   - Implement consistent loading/error/empty states
   - Add form validation with real-time feedback
   - Improve file transfer UX with combined progress
   - Add connection retry with visual feedback

### Phase 4: Observability (3-4 days)
1. **Metrics Implementation**
   - Configure Micrometer with custom metrics
   - Add session, command, and transfer monitoring
   - Implement performance tracking (P50/P95/P99)
   - Create health check endpoints

2. **CI/CD Pipeline**
   - Setup GitHub Actions for backend testing
   - Add frontend unit test automation
   - Implement security vulnerability scanning
   - Add deployment quality gates

3. **Monitoring Dashboard**
   - Configure metrics collection and visualization
   - Setup alerting for critical thresholds
   - Add performance benchmarks and SLOs
   - Implement structured logging with correlation IDs

## Validation Plan

### Unit Tests
- **Security**: Token generation, encryption/decryption, authentication flow
- **Transfer**: Streaming download, chunked upload, memory usage validation
- **Commands**: Policy enforcement, sanitization, rate limiting
- **Frontend**: Store actions, service methods, component behavior

### Integration Tests
- **End-to-End Flow**: Connect → authenticate → terminal commands → file transfer → disconnect
- **Security**: Token expiry, origin restrictions, credential handling
- **Performance**: Memory usage under load, concurrent transfer limits
- **Error Handling**: Network failures, invalid credentials, timeout scenarios

### Business Logic Verification
- **Security Compliance**: No plaintext credentials in transit or logs
- **Memory Stability**: Large file transfers without memory spikes
- **User Experience**: Consistent UI/UX across all features
- **Performance**: Sub-2s initial load, responsive terminal under load

### Performance Testing
- **Load Testing**: 50 concurrent sessions with active terminal usage
- **Transfer Testing**: Multiple large file uploads/downloads simultaneously  
- **Memory Testing**: Long-running sessions with high-volume terminal output
- **WebSocket Stability**: Connection resilience under network instability

### Security Testing
- **Credential Protection**: Verify no passwords in headers, logs, or network traces
- **Token Security**: Validate TTL enforcement and single-use restrictions
- **Origin Validation**: Test cross-origin request blocking in production
- **Host Key Verification**: Confirm unknown hosts are rejected

### Acceptance Criteria
- **Zero Critical Vulnerabilities**: No plaintext credentials observable
- **Memory Efficiency**: <100MB heap usage per session under normal load
- **Performance**: Initial load <2s, terminal response <100ms, transfer >10MB/s LAN
- **Reliability**: >99.9% WebSocket uptime, successful reconnection after network issues
- **Accessibility**: Lighthouse accessibility score ≥90
- **Code Quality**: >80% test coverage on critical paths