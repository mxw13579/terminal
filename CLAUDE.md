# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 🎯 CRITICAL: READ ARCHITECTURE FIRST

**Before writing any code, you MUST read and understand: `SIMPLIFIED_SSH_TERMINAL_ARCHITECTURE.md`**

This architecture document defines:
- Core design principles (KISS, YAGNI)
- Simplified data model
- Internal script management approach
- Development patterns and constraints

## Commands

### Backend (Java/Spring Boot)

- **Build:** `mvn clean install`
- **Run:** `mvn spring-boot:run` or use `run-app.bat`
- **Tests:** `mvn test` or use `run-tests.bat`
- **Compile Check:** Use `compile-check.bat`

### Frontend (Vue.js)

- **Location:** `web/ssh-treminal-ui`
- **Install dependencies:** `npm install`
- **Run dev server:** `npm run dev`
- **Build for production:** `npm run build`
- **Format:** `npm run format`

### Database Setup

- **Initialize Database:** Use `database/simplified_init_schema.sql`
- **Important:** This is a complete initialization script, use on empty database only

### Development Workflow

- Backend runs on `localhost:8080`
- Frontend dev server runs on `localhost:5173` with proxy to backend
- Database: MySQL (initialized with `simplified_init_schema.sql`)

## Simplified Architecture Overview

This is a **SSH Terminal Management System** with simplified design for personal use.

### Core Principles ⚠️

1. **Built-in Scripts = Code Only**: Internal scripts live in `com.fufu.terminal.command.impl.builtin` package, NOT in database
2. **Project-Based Grouping**: Users work with project templates (MySQL Management, Redis Management, etc.)
3. **KISS Design**: Avoid over-engineering, focus on user experience
4. **Personal Scale**: Designed for individual users, not enterprise complexity

### Key Components (Simplified)

**Project Templates:**
- Docker-based projects (MySQL, Redis, Nginx)
- Native installation projects
- Pre-defined operation sets (Install, Start, Stop, Logs)

**Built-in Script System:**
- Code-managed scripts with automatic OS detection
- Static scripts (no parameters) vs Dynamic scripts (with parameters)
- Auto-registration via Spring components

**User Interface:**
- User portal: Project selection → SSH config → Execute
- Admin portal: Project template management
- Real-time execution logs via WebSocket

### Database Design (Simplified)

**Core Tables:**
- `users` - Basic user management
- `project_groups` - Project templates and configurations  
- `script_executions` - Execution history and status
- `execution_logs` - Real-time execution logs

**Removed Complexity:**
- No atomic_scripts table (code-managed)
- No aggregated_scripts table (replaced by project templates)
- No complex relationship tables
- No over-engineered configuration tables

### Built-in Scripts Location

```
src/main/java/com/fufu/terminal/command/impl/builtin/
├── SystemInfoCommand.java        # Static script
├── DockerInstallCommand.java     # Dynamic script  
├── MySQLInstallCommand.java      # Dynamic script
└── RedisInstallCommand.java      # Dynamic script
```

### Development Guidelines

1. **New Built-in Scripts**: Create Java components, never database records
2. **Project Templates**: Use `project_groups.config_template` JSON field
3. **Database**: Only store execution history and user data
4. **Frontend**: Focus on project-based workflow, not script-level complexity
5. **Testing**: Use simplified test data, avoid complex setup

### Important Notes

- ❌ Don't create database records for built-in scripts
- ❌ Don't over-engineer for enterprise scale
- ✅ Focus on user experience and simplicity  
- ✅ Use project templates for common workflows
- ✅ Leverage Java code for cross-platform compatibility

## File Cleanup Status

**Removed Files:**
- All legacy architecture documents (keeping only `SIMPLIFIED_SSH_TERMINAL_ARCHITECTURE.md`)
- Unused bat files (keeping only essential ones: `run-app.bat`, `run-tests.bat`, `compile-check.bat`)
- Old SQL migration files (using single `simplified_init_schema.sql`)

**Current Valid Files:**
- `SIMPLIFIED_SSH_TERMINAL_ARCHITECTURE.md` - Main architecture guide
- `database/simplified_init_schema.sql` - Complete database initialization
- Essential development scripts only

