# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## High-level code architecture and structure

This is a web-based SSH and SFTP client.

- **Backend:** A Java Spring Boot application located in the root directory.
  - The main application entry point is `src/main/java/com/fufu/terminal/TerminalApplication.java`.
  - It uses WebSockets for communication with the frontend. The core WebSocket logic is in `src/main/java/com/fufu/terminal/handler/SshTerminalWebSocketHandler.java`.
  - SSH and SFTP functionality is implemented using the JSch library (`com.jcraft:jsch`).
  - `SshTerminalWebSocketHandler.java` handles SSH shell connections and dispatches messages to services like `SftpService.java` and `SshMonitorService.java`.
  - The backend is configured via `pom.xml`.

- **Frontend:** A Vue.js single-page application located in `web/ssh-treminal-ui/`.
  - The main UI component is `src/views/Terminal.vue`.
  - It uses `xterm.js` for the terminal interface.
  - Client-side WebSocket and terminal logic is likely within `src/composables/useTerminal.js`.
  - The frontend is built with Vite and managed by `package.json`.

## Commands

### Backend (Java/Maven)

- **Run the application:**
  ```bash
  mvn spring-boot:run
  ```
- **Build the application:**
  ```bash
  mvn package
  ```
- **Run tests:**
  ```bash
  mvn test
  ```

### Frontend (Vue/npm)

- **Navigate to the frontend directory:**
  ```bash
  cd web/ssh-treminal-ui
  ```
- **Install dependencies:**
  ```bash
  npm install
  ```
- **Run in development mode (with hot-reload):**
  ```bash
  npm run dev
  ```
- **Compile and build for production:**
  ```bash
  npm run build
  ```
