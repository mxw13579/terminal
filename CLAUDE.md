# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### Backend (Java/Spring Boot)

- **Build:** `mvn clean install`
- **Run:** `mvn spring-boot:run`
- **Tests:** `mvn test`
- **Format:** `mvn spotless:apply` (if available)

### Frontend (Vue.js)

- **Location:** `web/ssh-treminal-ui`
- **Install dependencies:** `npm install`
- **Run dev server:** `npm run dev`
- **Build for production:** `npm run build`
- **Format:** `npm run format`

### Development Workflow

- Backend runs on `localhost:8080`
- Frontend dev server runs on `localhost:5173` with proxy to backend
- Database: MySQL with auto-update schema (DDL auto: update)

## Architecture

This is a **SSH Terminal Management System** - a full-stack web application that provides script execution and SSH terminal management capabilities. The system allows users to manage, execute, and monitor scripts on remote servers through a web interface.

### Key Components

**Script Management System:**
- **Atomic Scripts**: Individual executable scripts (bash, python, etc.) with parameters
- **Aggregated Scripts**: Complex workflows combining multiple atomic scripts
- **Script Groups**: Organized collections for user interface management
- **Interactive Execution**: Scripts supporting user input during execution

**SSH Terminal Integration:**
- Web-based SSH terminals using xterm.js
- Real-time terminal sessions via WebSocket
- SFTP file transfer capabilities
- Multi-server connection management

### Backend Architecture (Java/Spring Boot 3.0.2)

Built with Java 17, using Spring Data JPA with MySQL, Sa-Token authentication, and WebSocket for real-time features.

**Key Packages:**
- `controller/`: Dual API structure with `/api/admin/*` and `/api/user/*` endpoints
- `service/`: Business logic including script execution and SSH management
- `entity/`: JPA entities with relationships between scripts, users, and execution logs
- `command/`: Extensible command execution framework for script processing
- `handler/`: WebSocket handlers for real-time terminal sessions

**Critical Services:**
- `AtomicScriptService`: Manages individual script definitions and parameters
- `InteractiveScriptExecutor`: Handles script execution with user interaction support
- SSH connection management with JSch library

### Frontend Architecture (Vue 3 + Vite)

Single-page application with dual interfaces (Admin/User) using Element Plus UI components.

**Key Technologies:**
- **Terminal**: xterm.js with fit addon for SSH terminal emulation
- **Real-time**: STOMP.js + SockJS for WebSocket communication
- **Drag & Drop**: Vue.draggable for script builder interface
- **HTTP**: Axios with interceptors for API communication

**Key Views:**
- `views/admin/`: Script management and user administration
- `views/user/`: Script execution and SSH terminal interface
- `components/InteractionModal.vue`: Handles interactive script execution

### Database Schema

MySQL database with key entities:
- **Users**: Role-based access (Admin/User) with Sa-Token authentication
- **AtomicScript**: Script definitions with parameters and metadata
- **AggregatedScript**: Complex script workflows with execution dependencies
- **ScriptGroup**: UI organization and access control
- **Execution Logs**: Comprehensive audit trail and monitoring

### WebSocket Integration

Real-time features implemented via STOMP protocol:
- Live terminal sessions with bidirectional communication
- Script execution progress monitoring
- Interactive parameter collection during script execution
