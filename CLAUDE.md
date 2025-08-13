# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Backend (Spring Boot)
- **Build**: `mvn clean package` - Compiles and packages the Spring Boot application
- **Run**: `mvn spring-boot:run` - Starts the development server on port 8080
- **Test**: `mvn test` - Runs unit tests
- **Clean**: `mvn clean` - Removes target directory and compiled artifacts

### Frontend (Vue 3 + Vite + TypeScript)
Navigate to `web/ssh-treminal-ui/` directory:
- **Install dependencies**: `npm install`
- **Development server**: `npm run dev` - Starts Vite dev server with hot reload
- **Build**: `npm run build` - Creates production build in TypeScript
- **Build (dev)**: `npm run build:dev` - Development mode build
- **Preview**: `npm run preview` - Preview production build locally
- **Format code**: `npm run format` - Formats source code with Prettier
- **Lint**: `npm run lint` / `npm run lint:fix` - ESLint checking and fixing
- **Type check**: `npm run type-check` - TypeScript type checking with vue-tsc
- **Test**: `npm run test:unit` - Run unit tests with Vitest
- **E2E Test**: `npm run test:e2e` - Run end-to-end tests with Playwright

## Architecture Overview

This is an **advanced dual-component web application** with sophisticated enterprise features:

### Backend: Spring Boot Multi-Service Architecture
- **Main application**: `TerminalApplication.java` - Entry point with scheduling enabled
- **Core STOMP Controllers**:
  - `SshTerminalStompController.java` - Terminal operations via STOMP protocol
  - `SftpStompController.java` - File transfer operations
  - `MonitorStompController.java` - System monitoring
  - `SillyTavernStompController.java` - SillyTavern Docker deployment management
- **HTTP Controllers**:
  - `TrueStreamingController.java` - HTTP streaming file transfers with `StreamingResponseBody`
  - `StreamingFileController.java` - Alternative HTTP file operations
  - `SecurityController.java` - RSA-encrypted credential authentication
  - `SessionController.java` - Session management
- **Advanced Services**:
  - `StreamingFileService.java` & `TrueStreamingFileService.java` - Memory-efficient large file transfers
  - `SshCommandService.java`, `SftpService.java`, `SshMonitorService.java` - Core SSH operations
  - `StompSessionManager.java` - Centralized STOMP session management with security
  - **SillyTavern Management Suite**: Full Docker-based AI deployment management
- **Security & Configuration**:
  - `WebSocketStompConfig.java` - STOMP WebSocket configuration with CORS policies
  - `StompAuthenticationInterceptor.java` - RSA-based credential authentication
  - `CryptoService.java` & `TokenVault.java` - Security token management
  - `FileUploadConfig.java` - Multi-gigabyte file transfer configuration

### Frontend: Vue 3 + TypeScript Enterprise SPA
- **Core Architecture**:
  - TypeScript-first development with full type safety
  - Pinia state management stores (`stores/`)
  - Composition API with advanced composables
  - Multiple routing with Vue Router (`views/`)
- **Key Composables**:
  - `useTerminal.js` - SSH terminal state and STOMP communication
  - `useSillyTavern.js` & `useSillyTavernExtended.js` - Docker deployment management
  - `useConnectionManager.js` - SSH connection lifecycle
  - `useValidation.js` - Form validation utilities
- **Primary Views & Components**:
  - `Terminal.vue` - Main SSH terminal interface
  - `Dashboard.vue` - Multi-service dashboard
  - `SillyTavernConsole.vue` - AI deployment management interface
  - **UI Component System**: Reusable base components (`components/ui/`)
  - **SillyTavern Suite**: Complete Docker container management UI
- **Services**:
  - `streamingFile.js` - HTTP streaming file transfers with progress tracking
  - `auth.js` & `crypto.js` - RSA encryption and secure authentication
  - Advanced performance utilities and accessibility helpers

## Advanced Technical Patterns

### STOMP WebSocket Communication Protocol
The application uses enterprise-grade STOMP over WebSocket with comprehensive message routing:
- **Terminal Operations**: `/app/terminal/*` - Real-time terminal I/O, resizing
- **File Transfer**: `/app/sftp/*` - SFTP operations with progress tracking
- **System Monitoring**: `/app/monitor/*` - Real-time system metrics
- **SillyTavern Management**: `/app/sillytavern/*` - Complete Docker lifecycle management
- **Queue Subscriptions**: `/user/queue/*` - User-specific message queues

### Dual File Transfer Architecture
**1. STOMP-based Transfer** (Legacy/Compatibility):
- WebSocket message-based for smaller files
- Base64 encoding with chunked transfer
- Real-time progress via STOMP messages

**2. HTTP Streaming Transfer** (Primary):
- `StreamingResponseBody` for memory-efficient large file handling
- Direct binary streaming with progress tracking
- Configurable throttling and concurrent transfer limits
- Support for multi-gigabyte files (configurable up to 2GB per file)

### Security & Authentication Architecture
- **RSA Encryption**: Client-side public key encryption of SSH credentials
- **Secure Token Exchange**: JWT-style tokens for session authentication
- **STOMP Authentication Interceptor**: Custom authentication for WebSocket connections
- **Environment-based Security**: Different security profiles for dev/test/production
- **CORS Configuration**: Environment-specific cross-origin policies

### State Management Patterns
- **Frontend**: Pinia stores with TypeScript typing + Vue 3 Composition API
- **Backend**: `ConcurrentHashMap`-based session management with thread-safe operations
- **File Upload State**: Atomic progress tracking with cancellation support
- **Connection Lifecycle**: Sophisticated connection management with automatic recovery

## SillyTavern Deployment Management

This application includes a complete Docker-based SillyTavern deployment and management system:

### Features
- **Automated Deployment**: One-click Docker container deployment
- **System Requirements Validation**: Pre-deployment environment checking
- **Interactive Deployment Wizard**: Step-by-step deployment with user confirmations
- **Container Lifecycle Management**: Start, stop, restart, upgrade, delete operations
- **Version Management**: Docker Hub integration for version updates
- **Configuration Management**: Dynamic config file editing with validation
- **Data Management**: Export/import functionality with progress tracking
- **Real-time Logging**: Live log streaming with historical log access
- **System Configuration**: Automatic mirror configuration for Chinese users
- **Docker Installation**: Automated Docker installation with system detection

### Technical Implementation
- Geolocation-based mirror selection for optimal performance
- Comprehensive system detection (Ubuntu, CentOS, etc.)
- Advanced error handling and recovery mechanisms
- Real-time progress updates via STOMP messaging
- Background task management with proper resource cleanup

## Development Workflow

### Standard Development
1. **Start backend**: `mvn spring-boot:run` from root directory
2. **Start frontend**: `npm run dev` from `web/ssh-treminal-ui/` directory  
3. **Access application**: Navigate to `http://localhost:5173` (Vite dev server with auto-port fallback)
4. **Backend APIs**: 
   - WebSocket STOMP: `ws://localhost:8080/ws/terminal`
   - HTTP Streaming: `http://localhost:8080/api/streaming/*`
   - Regular HTTP: `http://localhost:8080/api/*`

### Production Deployment
- **Backend**: Standard Spring Boot JAR deployment
- **Frontend**: Static assets served via web server with proxy configuration
- **Configuration**: Environment-specific `application.properties` profiles
- **Security**: Production CORS policies and strict host key checking

## Key Configuration Files

### Backend Configuration
- `application.properties` - Multi-environment configuration (dev/test/prod profiles)
  - File transfer limits (up to 2GB configurable)
  - STOMP WebSocket settings with heartbeat configuration
  - Security policies per environment
  - Rate limiting and throttling settings
- `WebSocketStompConfig.java` - STOMP endpoint configuration with security
- `FileUploadConfig.java` - Multipart file handling configuration

### Frontend Configuration  
- `vite.config.js` - Development proxy configuration with streaming support
  - Critical streaming upload fixes with `selfHandleRequest: true`
  - Proper proxy pipe configuration for large file uploads
- `package.json` - Dependencies with TypeScript and testing frameworks
- `tsconfig.json` - TypeScript configuration with strict type checking

## Dependencies & Technology Stack

### Backend (Maven)
- **Core**: Spring Boot 3.0.2 with Web, WebSocket, WebFlux, and Messaging starters
- **SSH**: JSch 0.1.55 for SSH/SFTP operations
- **Security**: Spring Boot Validation and custom RSA encryption
- **Observability**: Spring Boot Actuator, Micrometer Prometheus registry
- **Logging**: Logback with JSON structured logging support
- **Utilities**: Lombok for boilerplate reduction
- **Security**: OWASP Dependency Check Maven plugin

### Frontend (npm)
- **Core Framework**: Vue 3.5.17 with Composition API and TypeScript support
- **Build System**: Vite 7.0.0 with Vue DevTools integration
- **State Management**: Pinia 3.0.3 for TypeScript-first state management  
- **Communication**: @stomp/stompjs 7.0.0 + SockJS for WebSocket connections
- **Terminal**: xterm.js 5.3.0 with fit addon for terminal emulation
- **Development**: 
  - TypeScript 5.9.2 with Vue TSC for type checking
  - ESLint + Prettier for code quality
  - Vitest for unit testing  
  - Playwright for E2E testing
  - Webpack Bundle Analyzer for performance optimization

## Performance & Scalability Features

- **Memory Management**: Streaming file transfers prevent memory overflow
- **Concurrency Control**: Configurable concurrent transfer limits  
- **Rate Limiting**: Bandwidth throttling for file operations
- **Progress Tracking**: Real-time progress updates with speed calculations
- **Resource Cleanup**: Automatic temporary file cleanup and connection management
- **Background Processing**: Asynchronous operations with proper thread pool management
- **Caching**: Service-level caching for frequently accessed data

## Security Considerations

- **Credential Security**: RSA encryption prevents plaintext credential transmission
- **Token Management**: Secure token generation and validation
- **Environment Isolation**: Different security profiles for development vs production
- **Connection Security**: SSH strict host key checking (configurable per environment)
- **File Transfer Security**: Size limits and path validation to prevent abuse
- **CORS Policies**: Environment-specific cross-origin configurations