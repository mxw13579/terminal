# SSH Terminal STOMP Migration Design

## 1. Overview

This document presents the system architecture design for migrating the SSH terminal application from native WebSocket to STOMP (Simple Text Oriented Messaging Protocol) over WebSocket. The design leverages Spring Boot's built-in STOMP support to provide a more standardized, maintainable, and scalable messaging infrastructure while preserving all existing functionality.

### 1.1 Migration Goals
- Replace custom WebSocket message routing with STOMP standardized messaging
- Utilize Spring Boot's @MessageMapping annotations for cleaner controller architecture
- Implement proper session management and security through Spring's STOMP framework
- Maintain backward compatibility with existing frontend implementation
- Improve error handling and logging through structured STOMP messaging

### 1.2 Key Design Principles
- **Minimal Frontend Impact**: Preserve existing message formats and communication patterns
- **Service Layer Preservation**: Maintain existing business logic in SftpService and SshMonitorService
- **Gradual Migration**: Support phased rollout with backward compatibility
- **Standard Compliance**: Follow Spring Boot messaging best practices
- **Security First**: Implement proper authentication and session management

## 2. Architecture

### 2.1 Current Architecture Analysis

**Current WebSocket Implementation:**
```
Client (Vue.js) → WebSocket → SshTerminalWebSocketHandler → MessageHandler Map → Services
```

**Current Message Flow:**
1. Native WebSocket connection with query parameter authentication
2. JSON message parsing in SshTerminalWebSocketHandler
3. Message type-based routing using Map<String, MessageHandler>
4. Direct service method invocation
5. Response sent through WebSocket.sendMessage()

**Current Session Management:**
- ConcurrentHashMap<String, SshConnection> for session storage
- WebSocket session ID as primary key
- Manual resource cleanup in connection close handlers

### 2.2 Target STOMP Architecture

**New STOMP Implementation:**
```
Client (Vue.js) → STOMP over WebSocket → Spring Message Broker → @MessageMapping Controllers → Services
```

**New Message Flow:**
1. STOMP connection with authentication through Spring Security
2. Message routing through Spring's message broker
3. @MessageMapping annotated controller methods
4. Service layer invocation (unchanged)
5. Response through @SendTo or SimpMessagingTemplate

### 2.3 Component Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                           Frontend Layer                            │
├─────────────────────────────────────────────────────────────────────┤
│  Vue.js Application                                                │
│  ├── useTerminal.js (STOMP Client)                                 │
│  ├── Terminal Components                                           │
│  └── SFTP/Monitor Components                                       │
└─────────────────────────────────────────────────────────────────────┘
                                     │
                              STOMP over WebSocket
                                     │
┌─────────────────────────────────────────────────────────────────────┐
│                        Spring Boot Backend                          │
├─────────────────────────────────────────────────────────────────────┤
│  STOMP Configuration Layer                                          │
│  ├── WebSocketStompConfig                                          │
│  ├── StompAuthenticationInterceptor                                │
│  └── StompSessionManager                                           │
├─────────────────────────────────────────────────────────────────────┤
│  Message Controller Layer                                           │
│  ├── SshTerminalStompController                                    │
│  ├── SftpStompController                                           │
│  └── MonitorStompController                                        │
├─────────────────────────────────────────────────────────────────────┤
│  Service Layer (Preserved)                                         │
│  ├── SshCommandService                                             │
│  ├── SftpService                                                   │
│  └── SshMonitorService                                             │
├─────────────────────────────────────────────────────────────────────┤
│  Infrastructure Layer                                              │
│  ├── SSH Session Management                                        │
│  ├── File Transfer Handlers                                        │
│  └── System Monitoring Tasks                                       │
└─────────────────────────────────────────────────────────────────────┘
```

## 3. Components and Interfaces

### 3.1 STOMP Configuration Components

#### 3.1.1 WebSocketStompConfig
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketStompConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/terminal")
                .setAllowedOrigins("*")
                .withSockJS();
    }
}
```

#### 3.1.2 STOMP Authentication Interceptor
```java
@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {
    
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Extract authentication from headers
            String sessionId = accessor.getSessionId();
            // Validate SSH connection parameters
            // Set up SSH session
        }
        return message;
    }
}
```

### 3.2 Message Controller Components

#### 3.2.1 SSH Terminal STOMP Controller
```java
@Controller
public class SshTerminalStompController {
    
    @MessageMapping("/terminal/data")
    public void handleTerminalData(
        @Payload TerminalDataMessage message,
        SimpMessageHeaderAccessor headerAccessor
    ) {
        String sessionId = headerAccessor.getSessionId();
        SshConnection connection = sessionManager.getConnection(sessionId);
        // Delegate to existing service logic
        sshCommandService.sendCommand(connection, message.getData());
    }
    
    @MessageMapping("/terminal/resize")
    public void handleTerminalResize(
        @Payload TerminalResizeMessage message,
        SimpMessageHeaderAccessor headerAccessor
    ) {
        String sessionId = headerAccessor.getSessionId();
        SshConnection connection = sessionManager.getConnection(sessionId);
        connection.getChannelShell().setPtySize(message.getCols(), message.getRows());
    }
}
```

#### 3.2.2 SFTP STOMP Controller
```java
@Controller
public class SftpStompController {
    
    @MessageMapping("/sftp/list")
    @SendToUser("/queue/sftp/list/response")
    public SftpListResponse handleSftpList(
        @Payload SftpListRequest request,
        SimpMessageHeaderAccessor headerAccessor
    ) {
        String sessionId = headerAccessor.getSessionId();
        SshConnection connection = sessionManager.getConnection(sessionId);
        return sftpService.listFiles(connection, request.getPath());
    }
    
    @MessageMapping("/sftp/upload/chunk")
    public void handleSftpUploadChunk(
        @Payload SftpUploadChunkMessage message,
        SimpMessageHeaderAccessor headerAccessor
    ) {
        String sessionId = headerAccessor.getSessionId();
        SshConnection connection = sessionManager.getConnection(sessionId);
        sftpService.handleUploadChunk(connection, message, sessionId);
    }
}
```

### 3.3 Session Management Components

#### 3.3.1 STOMP Session Manager
```java
@Component
public class StompSessionManager {
    
    private final ConcurrentHashMap<String, SshConnection> connections = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;
    
    public void registerConnection(String sessionId, SshConnection connection) {
        connections.put(sessionId, connection);
        startTerminalOutputForwarder(sessionId, connection);
    }
    
    private void startTerminalOutputForwarder(String sessionId, SshConnection connection) {
        executorService.submit(() -> {
            try (InputStream inputStream = connection.getInputStream()) {
                byte[] buffer = new byte[1024];
                int i;
                while ((i = inputStream.read(buffer)) != -1) {
                    String payload = new String(buffer, 0, i, StandardCharsets.UTF_8);
                    TerminalDataMessage message = new TerminalDataMessage("terminal_data", payload);
                    messagingTemplate.convertAndSendToUser(sessionId, "/queue/terminal/data", message);
                }
            } catch (IOException e) {
                handleConnectionError(sessionId, e);
            }
        });
    }
}
```

### 3.4 Message Models

#### 3.4.1 STOMP Message DTOs
```java
// Base message structure
@Data
public abstract class StompMessage {
    private String type;
    private long timestamp = System.currentTimeMillis();
}

// Terminal specific messages
@Data
@EqualsAndHashCode(callSuper = true)
public class TerminalDataMessage extends StompMessage {
    private String payload;
}

@Data
@EqualsAndHashCode(callSuper = true)
public class TerminalResizeMessage extends StompMessage {
    private int cols;
    private int rows;
}

// SFTP specific messages
@Data
@EqualsAndHashCode(callSuper = true)
public class SftpListRequest extends StompMessage {
    private String path;
}

@Data
@EqualsAndHashCode(callSuper = true)
public class SftpUploadChunkMessage extends StompMessage {
    private String path;
    private String filename;
    private int chunkIndex;
    private int totalChunks;
    private String content;
}
```

## 4. Data Models

### 4.1 Session Data Model
```java
@Data
public class StompSessionContext {
    private String sessionId;
    private String userId;
    private SshConnection sshConnection;
    private Map<String, Object> attributes;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivity;
}
```

### 4.2 Message Routing Model
```
STOMP Destination Patterns:

Incoming Messages (Client → Server):
/app/terminal/data          → Terminal input
/app/terminal/resize        → Terminal resize
/app/sftp/list             → File listing request
/app/sftp/upload/chunk     → File upload chunk
/app/sftp/download         → File download request
/app/monitor/start         → Start monitoring
/app/monitor/stop          → Stop monitoring

Outgoing Messages (Server → Client):
/user/queue/terminal/data   → Terminal output
/user/queue/sftp/list/response → File listing response
/user/queue/sftp/upload/progress → Upload progress
/user/queue/sftp/download/response → Download response
/user/queue/monitor/update  → Monitor data
/user/queue/error          → Error messages
```

## 5. Error Handling

### 5.1 STOMP Error Handling Strategy

#### 5.1.1 Global Exception Handler
```java
@ControllerAdvice
public class StompExceptionHandler {
    
    @MessageExceptionHandler
    @SendToUser("/queue/error")
    public ErrorMessage handleGenericException(Exception e) {
        log.error("STOMP message processing error", e);
        return new ErrorMessage("PROCESSING_ERROR", e.getMessage());
    }
    
    @MessageExceptionHandler(SshConnectionException.class)
    @SendToUser("/queue/error")
    public ErrorMessage handleSshException(SshConnectionException e) {
        log.error("SSH connection error", e);
        return new ErrorMessage("SSH_ERROR", e.getMessage());
    }
}
```

#### 5.1.2 Connection Error Handling
- Automatic session cleanup on disconnect
- Graceful SSH connection termination
- Resource cleanup through Spring's lifecycle management
- Error message propagation to frontend through dedicated error queues

### 5.2 Error Recovery Mechanisms
- Client-side reconnection logic with exponential backoff
- Server-side session restoration capabilities
- Partial upload resumption for large file transfers
- Monitoring session state persistence

## 6. Testing Strategy

### 6.1 Unit Testing Approach
- Controller method testing with @WebSocketTest
- Service layer testing (existing tests preserved)
- Message serialization/deserialization testing
- Session management testing with mock STOMP sessions

### 6.2 Integration Testing Approach
- End-to-end STOMP message flow testing
- Frontend-backend integration testing
- SSH connection lifecycle testing
- File transfer operation testing

### 6.3 Testing Infrastructure
```java
@SpringBootTest
@AutoConfigureTestDatabase
class StompIntegrationTest {
    
    @Autowired
    private TestStompSession stompSession;
    
    @Test
    void testTerminalDataFlow() {
        // Test complete terminal data flow
        stompSession.send("/app/terminal/data", new TerminalDataMessage("ls"));
        // Verify response in /user/queue/terminal/data
    }
}
```

## 7. Security Considerations

### 7.1 Authentication and Authorization
- STOMP connection authentication through Spring Security
- Session-based authorization for message endpoints
- SSH credential validation and secure storage
- Rate limiting for message processing

### 7.2 Message Security
- Input validation for all incoming STOMP messages
- Prevention of message injection attacks
- Secure handling of file upload content
- Audit logging for security-relevant operations

### 7.3 Session Security
- Secure session management with proper timeout handling
- Prevention of session hijacking
- Resource cleanup to prevent memory leaks
- Proper SSH connection termination

## 8. Performance Considerations

### 8.1 Message Throughput Optimization
- Efficient message serialization using Jackson
- Optimal buffer sizes for terminal data streaming
- Chunked file transfer with progress tracking
- Connection pooling for SSH sessions

### 8.2 Scalability Design
- Support for external message brokers (RabbitMQ, ActiveMQ)
- Horizontal scaling with session affinity
- Memory optimization for large file transfers
- Efficient resource cleanup mechanisms

### 8.3 Monitoring and Metrics
- STOMP message processing metrics
- SSH connection pool monitoring
- File transfer performance tracking
- Session lifecycle monitoring

## 9. Migration Strategy

### 9.1 Phased Migration Approach
1. **Phase 1**: Parallel STOMP implementation alongside existing WebSocket
2. **Phase 2**: Frontend adaptation to support both protocols
3. **Phase 3**: Gradual traffic migration to STOMP endpoints
4. **Phase 4**: Deprecation and removal of native WebSocket implementation

### 9.2 Backward Compatibility
- Maintain existing WebSocket endpoints during transition
- Message format compatibility layer
- Gradual frontend migration with feature flags
- Zero-downtime deployment strategy

### 9.3 Rollback Strategy
- Feature flags for protocol selection
- Database migration rollback procedures
- Configuration rollback mechanisms
- Emergency fallback to native WebSocket