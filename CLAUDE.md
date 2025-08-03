# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Backend (Spring Boot)
- **Build**: `mvn clean package` - Compiles and packages the Spring Boot application
- **Run**: `mvn spring-boot:run` - Starts the development server on port 8080
- **Test**: `mvn test` - Runs unit tests
- **Clean**: `mvn clean` - Removes target directory and compiled artifacts

### Frontend (Vue 3 + Vite)
Navigate to `web/ssh-treminal-ui/` directory:
- **Install dependencies**: `npm install`
- **Development server**: `npm run dev` - Starts Vite dev server with hot reload
- **Build**: `npm run build` - Creates production build
- **Preview**: `npm run preview` - Preview production build locally
- **Format code**: `npm run format` - Formats source code with Prettier

## Architecture Overview

This is a **dual-component web application** consisting of:

### Backend: Spring Boot SSH Terminal Service
- **Main application**: `TerminalApplication.java` - Entry point
- **WebSocket handler**: `SshTerminalWebSocketHandler.java` - Manages WebSocket connections and SSH sessions
- **Core services**:
  - `SshCommandService.java` - Executes SSH commands on remote hosts
  - `SftpService.java` - Handles file transfer operations
  - `SshMonitorService.java` - Provides system monitoring capabilities
- **Configuration**: 
  - `WebSocketConfig.java` - WebSocket endpoint configuration
  - `ExecutorConfig.java` - Thread pool configuration for async operations

### Frontend: Vue 3 Single Page Application
- **Entry point**: `src/main.js` and `src/App.vue`
- **Main view**: `src/views/Terminal.vue` - Primary terminal interface
- **Core composable**: `src/composables/useTerminal.js` - Central state management and WebSocket communication
- **Key components**:
  - `SshConsole.vue` - Terminal display using xterm.js
  - `ConnectionForm.vue` - SSH connection configuration
  - `SftpPanel.vue` - File transfer interface
  - `MonitorPanel.vue` - System monitoring display

## Key Technical Patterns

### WebSocket Communication Protocol
The application uses a message-based protocol over WebSocket with message types:
- `data` - Terminal input/output
- `resize` - Terminal window resize
- `sftp_*` - File transfer operations (list, upload, download)
- `monitor_*` - System monitoring start/stop

### State Management
- Frontend uses Vue 3 Composition API with a centralized `useTerminal` composable
- Backend maintains SSH connections in a `ConcurrentHashMap` keyed by WebSocket session ID
- File upload uses chunked transfer with progress tracking

### Security Considerations
- SSH connections use JSch library with disabled strict host key checking
- Credentials are passed via WebSocket query parameters (consider security implications)
- No persistent storage of credentials

## Development Workflow

1. **Start backend**: Run `mvn spring-boot:run` from root directory
2. **Start frontend**: Run `npm run dev` from `web/ssh-treminal-ui/` directory
3. **Access application**: Navigate to `http://localhost:5173` (Vite dev server)
4. **Backend API**: Available at `ws://localhost:8080/ws/terminal` for WebSocket connections

## Dependencies

### Backend (Maven)
- Spring Boot 3.0.2 with Web and WebSocket starters
- JSch 0.1.55 for SSH connectivity
- Lombok for boilerplate reduction

### Frontend (npm)
- Vue 3.5.17 with Composition API
- Vite 7.0.0 for build tooling
- xterm.js 5.3.0 for terminal emulation
- Prettier for code formatting