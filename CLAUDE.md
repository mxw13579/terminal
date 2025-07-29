# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a web-based SSH terminal application with the following architecture:
- **Backend**: Spring Boot 3.0.2 Java application providing REST APIs and WebSocket services
- **Frontend**: Vue 3 + Vite application with terminal emulation capabilities
- **Database**: MySQL for user management and script storage
- **Authentication**: Sa-Token for session management

## Project Structure

### Backend (Java/Spring Boot)
- **Main Application**: `src/main/java/com/fufu/terminal/TerminalApplication.java`
- **Command System**: Extensible command chain pattern in `src/main/java/com/fufu/terminal/command/`
  - Base commands for environment checking, preprocessing, and enhancements
  - Command registry and factory pattern for dynamic command execution
- **Controllers**: Separated into admin and user endpoints in `src/main/java/com/fufu/terminal/controller/`
- **Services**: Core business logic for script execution, SSH connections, and user management
- **Entities**: JPA entities for database operations (User, Script, ScriptGroup, etc.)
- **WebSocket Handlers**: Real-time terminal communication and SFTP progress monitoring

### Frontend (Vue 3)
- **Location**: `web/ssh-treminal-ui/`
- **Main Components**: Terminal interface, connection forms, admin panels
- **Terminal**: Uses xterm.js for terminal emulation
- **WebSocket**: Real-time communication with backend via STOMP protocol

## Development Commands

### Backend (Maven)
```bash
# Compile and run Spring Boot application
mvn spring-boot:run

# Run tests
mvn test

# Build JAR package
mvn clean package
```

### Frontend (npm)
```bash
# Install dependencies
cd web/ssh-treminal-ui
npm install

# Development server
npm run dev

# Build for production
npm run build

# Format code
npm run format
```

## Key Features

1. **SSH Terminal Emulation**: Real-time terminal access via WebSocket
2. **Script Management**: CRUD operations for atomic and aggregated scripts
3. **User Management**: Admin and user role separation
4. **Command Chain System**: Extensible preprocessing, environment checking, and enhancement commands
5. **SFTP File Transfer**: File upload/download with progress monitoring
6. **Script Execution**: Unified script execution with progress tracking

## Database Configuration

The application uses MySQL with the following key entities:
- `User`: User authentication and roles
- `Script`/`AtomicScript`: Individual script definitions
- `AggregatedScript`: Collections of related scripts
- `ScriptGroup`: Organizational script grouping
- `ExecutionLog`: Script execution tracking

## Authentication

Uses Sa-Token for authentication with BCrypt password hashing. Admin and user roles are supported with different permission levels.

## WebSocket Endpoints

- SSH terminal sessions via custom WebSocket handlers
- Real-time progress updates for script execution
- SFTP file transfer progress monitoring

## Testing

Backend tests are located in `src/test/java/` with comprehensive coverage of:
- Command chain functionality
- Environment detection and OS-specific operations
- Script configuration and execution