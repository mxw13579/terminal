# Terminal Project – Pragmatic Security, Architecture, and UI/UX Improvement Plan

Date: 2025-08-08
Owner: Security & Architecture Working Group
Scope: Backend (Spring Boot) + Frontend (Vue 3 + Vite) + Infrastructure
Target: AI-Driven Implementation (precise, phase-gated, feature-flagged)

## 1. Executive Summary

Prioritize risk removal and stability, then unify architecture and UX, finally evolve capabilities. Immediate actions: remove plaintext credentials over STOMP, enforce SSH host key verification, restrict origins, replace base64 file transfer with streaming, standardize STOMP destinations, and introduce a unified command execution API with safety policies. Frontend increments to TypeScript/Pinia and UI consistency follow once safety is in place.

## 2. Critical Issues and Evidence

Frontend
- STOMP headers carry plaintext password (web/ssh-treminal-ui/src/composables/useTerminal.js:49-56)
- Fixed reconnectDelay without jitter (web/ssh-treminal-ui/src/composables/useTerminal.js:60)
- Base64 SFTP transfer path increases memory and CPU (web/ssh-treminal-ui/src/composables/useTerminal.js:236-240,425-443)

Backend
- StrictHostKeyChecking=no (src/main/java/com/fufu/terminal/config/StompAuthenticationInterceptor.java:101)
- Open WS origins (src/main/java/com/fufu/terminal/config/WebSocketStompConfig.java:41-46)
- Duplicated command wrappers; inconsistent policies (see IMPROVEMENT_PLAN.md:32-47)

Infrastructure
- Production logging is too verbose (src/main/resources/application.properties:13-19)

## 3. Non-Goals (Now)

- Micro-frontends, CQRS, full PWA offline command execution
- Replacing JSch immediately (evaluate after stability)
- WebAuthn passwordless (later phase)

## 4. Phased Roadmap (With Feature Flags)

### Phase 0: Hotfixes and Security Baseline (Critical)

Backend
- Replace credential-in-headers with short-lived session token
  - Add REST endpoint POST /api/session/token that validates payload and stores decrypted SSH credentials in an in-memory TTL “vault” keyed by token (no DB). Token TTL ≤ 2 min; single-use on CONNECT.
  - STOMP CONNECT must accept only Authorization: Bearer <token> and fetch credentials from the vault; reject password headers.
  - StompAuthenticationInterceptor.java:84-90 remove password reading; use token lookup
- Enforce SSH host key verification
  - StompAuthenticationInterceptor.java:101 set StrictHostKeyChecking=yes
  - Provide known_hosts (per-env mount) with first-use controlled only in dev
- Restrict WS origins by profile
  - WebSocketStompConfig.java:41-46 setAllowedOriginPatterns to a whitelist in prod, keep "*" only in dev/test
- Reduce sensitive logging
  - application.properties:13-19 set INFO in prod; sanitize user@host logs (StompAuthenticationInterceptor.java:127)

Frontend
- Stop sending password over STOMP
  - useTerminal.js:49-56 remove password; add Authorization header
- Remove URL/frame debug prints in production
  - useTerminal.js:65-69 guard by env/only dev

Flags and Backout
- flags.security.encryptedConnect (REST+token path) can be disabled to fall back to dev-only password headers (dev profile only)
- flags.ssh.strictHostKeyChecking toggles only in dev

Acceptance
- No plaintext credentials observable on the wire
- Unknown host keys rejected in prod
- Only whitelisted origins can connect
- No sensitive info in prod logs

### Phase 1: Transfer Safety and Memory Stability (High)

Backend
- HTTP streaming download endpoint for SFTP files/directories
  - Replace in-memory ByteArrayOutputStream with streaming ZipOutputStream/Flux<DataBuffer>
  - Enforce per-file and aggregate size caps; throttle throughput; limit concurrent transfers per session
- HTTP multipart chunked upload endpoint with server-side temp files + lifecycle cleanup
  - Validate MIME/extension; per-request and per-session caps; optional scanning hook

Frontend
- Download via HTTP stream + Blob instead of STOMP base64
  - useTerminal.js:236-240,425-443 switch to fetch/stream → Blob → save
- Upload via ArrayBuffer and multipart chunking (not FileReader+base64)
  - useTerminal.js:371-423 rework to binary chunks; progress; retry; cancel
- Terminal output back-pressure
  - Accumulate frames and write via requestAnimationFrame with an upper bound per tick

Acceptance
- Large uploads/downloads do not spike memory
- Throughput and responsiveness stable under load
- Terminal rendering remains smooth with high-volume output

### Phase 2: Architecture Unification (High)

- Standardize STOMP destinations and routing
  - Use convertAndSendToUser consistently; ensure front-end subscribes only /user/queue/*
  - Review StompSessionManager.java:112-115,167-171 and align
- Remove legacy WS path after 1-2 weeks of stable metrics
  - Delete LegacyWebSocketConfig/SshTerminalWebSocketHandler
- Unified command execution API with policies
  - Replace private executeCommand wrappers with SshCommandService policy methods (timeouts, stdout/stderr caps, deny-list, rate limits, audit redaction)

Acceptance
- No non-standard destinations; all user-scoped queues enforced
- No legacy WS in prod image
- One canonical SshCommandService API in use across services

### Phase 3: Frontend Modernization and UI/UX (High → Medium)

TypeScript & State
- Incremental TS migration with allowJs: tsconfig strict on new modules
- Introduce Pinia for connection/session/sftp/monitor state
  - Keep stores minimal; interop with existing composables

Design System and Accessibility
- Theming variables (colors, spacing, radii, shadows, typography), light/dark
- Base components: Button, Input, Select, Modal, Toast, Skeleton
- a11y: ARIA, keyboard navigation, focus management, contrast AA

Interaction Quality
- Consistent Loading/Error/Empty states with retry actions
- Connection form validation (host/port/user/token) with immediate feedback
- File transfer UX: combined progress (local/remote), speed, ETA, cancel/retry

Stability & Performance
- Reconnect strategy: exponential backoff + jitter (useTerminal.js:60)
- Resize/debounce for terminal resize events (useTerminal.js:309-314)
- Lazy-load xterm and enable WebGL; batch term.write via rAF

Acceptance
- Lighthouse a11y ≥ 90; consistent theming; clear error handling
- Measurably faster first-interaction; smooth terminal under load

### Phase 4: Observability and CI/CD (Medium)

- Micrometer/OpenTelemetry minimal tracing and metrics
  - Sessions, command latency P50/P95/P99, transfer throughput, WS stability, error rates
- GitHub Actions CI
  - Backend mvn test + dependency scan; Frontend unit tests for stores/services
  - Fail on critical vulnerabilities

Acceptance
- Dashboards live; alerting on error/latency thresholds
- CI gates critical issues

### Phase 5: Advanced (Optional/Later)

- WebAuthn for passwordless token issuance
- Apache MINA SSHD evaluation as JSch replacement
- PWA offline only for low-risk read views (no offline commands)
- CQRS/event bus and micro-frontend only when complexity/teams justify

## 5. Detailed Specifications

### 5.1 Encrypted Connect Flow (REST + Short-lived Token)

REST API
- POST /api/session/token
  - Request: { host, port, user, auth: "password"|"privateKey", password?, privateKey?, passphrase? }
  - Validation: basic schema; optional RSA-OAEP/AEAD in later iteration
  - Response: { token, expiresInSec }
- Backend vault
  - In-memory cache Map<token, EncryptedCredentials> with TTL≤120s, single-use
  - On STOMP CONNECT with Authorization: Bearer <token>, resolve credentials then wipe on success or after disconnect/failure
- Security
  - HTTPS/WSS in prod; restrict origins; rate-limit issuance; audit access without PII

Frontend
- Authenticate via REST, then pass Authorization in STOMP connectHeaders only
- Do not store credentials in localStorage/sessionStorage

Acceptance
- No password present in STOMP headers anywhere
- Token cannot be reused and expires quickly

### 5.2 SSH Host Key Management

- StrictHostKeyChecking=yes (prod)
- Provide known_hosts per environment via mounted file; deny unknown by default
- Dev profile may allow prompts/overrides

### 5.3 STOMP Destinations and Authorization

- Application prefix: /app
- User destinations: /user/queue/{terminal|sftp|monitor|errors}
- Server-side enforces that a session subscribes only to its own /user/queue/*
- accessor.setUser(sessionId) continues; ensure auth checks on SUBSCRIBE

### 5.4 Unified Command Service API (Policy Engine)

SshCommandService
- executeOrThrow(session, cmd, options): timeoutMs, stdout/stderr caps, workingDir, acceptedExitCodes
- execute(session, cmd, options): returns { exit, stdout(truncated?), stderr(truncated?) }
- stream(session, cmd, options, onStdout, onStderr): for long-running tasks
Policies
- Deny-list dangerous ops; optional allow-list mode for elevated flows
- Redact secrets in logs; rate-limit per session; audit structured logs

Migration
- Replace all listed wrappers per IMPROVEMENT_PLAN.md:32-47

### 5.5 File Transfer Architecture

Download (HTTP)
- GET /api/sftp/download?path=... or POST with batch list → server streams zip
- Flux<DataBuffer> with back-pressure and throughput limits
- Content-Disposition for filename; ETag or checksum optional

Upload (HTTP)
- POST /api/sftp/upload multipart with chunk index/total; server assembles to temp file then moves
- Validate size/type; enforce caps and concurrency; periodic cleanup job

Progress
- STOMP events for progress: sftp_remote_progress, sftp_upload_final_success, errors

### 5.6 Frontend State and Services

Pinia Stores
- terminal.store: connection state, token, ws lifecycle
- sftp.store: current path, files, transfers, progress
- monitor.store: metrics stream state

Services
- websocket.service: resilient connect with backoff+jitter; unified subscribe/publish; auto-resubscribe
- sftp.service: HTTP upload/download; STOMP for progress events
- auth.service: get token, renew on expiry

Terminal Rendering
- Batch writes via rAF; max characters per frame; WebGL enabled; lazy import xterm

### 5.7 UI/UX Design System

Theme Tokens
- Color scales (primary/success/warn/danger/neutral) light/dark
- Spacing, radii, shadow, typography, transitions

Components
- Base: Button, Input, Select, Checkbox, Switch, TextArea, Modal, Toast, Tooltip, Skeleton
- Patterns: ConnectionDialog, TransferPanel, TerminalPanel, AppShell

A11y
- ARIA roles/labels, focus ring, trap focus in modals, keyboard navigation, contrast AA

States
- Loading/Error/Empty with descriptions and retry; progressive validation for forms

## 6. Observability

Metrics
- Active sessions, command count/duration (p50/p95/p99), transfer bytes/s, ws disconnects/retries, error rates per destination
Tracing
- Connect flows, command execution spans, file transfers
Logging
- Correlation IDs per session; redaction middleware

## 7. CI/CD

GitHub Actions
- Backend: mvn -B -DskipITs=false test; OWASP/Snyk scan
- Frontend: unit tests for stores/services; lint/format; build
- Optional k6/Gatling for smoke perf on PRs touching hotspots

## 8. Testing Strategy

Security
- Ensure no plaintext credentials in any header; token TTL and single-use verified
- Origin restriction per profile; host key mismatch blocked in prod

Unit/Integration
- Unified command API behaviors (timeouts, truncation, exit handling)
- STOMP lifecycle and subscription authorization
- Transfer streaming integrity and cleanup

E2E
- Full connect → terminal IO → upload/download → disconnect workflow
- Failure/retry paths (network drop, token expiry, host key mismatch)

Performance
- Concurrent sessions+transfers; CPU/memory within budget; back-pressure verified

## 9. Success Metrics

Security
- Zero plaintext credentials in transit/logs
- Unknown host keys rejected in prod
- Origins restricted; no CSRF-able WS in prod

Performance
- Initial load < 2s; terminal under high throughput remains responsive
- Transfer throughput > 10MB/s stream (in LAN-like conditions)
- WS stability > 99.9%

Quality
- ≥ 80% coverage on critical paths; CI blocks high/sev vulns

UX
- Lighthouse a11y ≥ 90; consistent theming; clear error/empty/loading states

## 10. Rollout and Backout

Rollout
- Phase flags per feature; deploy Phase 0 first to staging; observe metrics 48h
- Progressive enablement of transfer streaming and command API
Backout
- Disable flags.security.encryptedConnect to dev-only fallback (never prod)
- Revert streaming to base implementation if instability detected (kept behind flag)
- Keep legacy WS only until Phase 2 complete; behind profile

## 11. File-by-File Remediation Map

Frontend
- web/ssh-treminal-ui/src/composables/useTerminal.js:49-56 — remove password header; add Authorization: Bearer
- web/ssh-treminal-ui/src/composables/useTerminal.js:60 — replace fixed reconnectDelay with backoff+jitter
- web/ssh-treminal-ui/src/composables/useTerminal.js:309-314 — add resize debounce
- web/ssh-treminal-ui/src/composables/useTerminal.js:236-240,425-443 — replace base64 download with HTTP stream+Blob
- web/ssh-treminal-ui/src/composables/useTerminal.js:371-423 — replace FileReader+base64 upload with ArrayBuffer/multipart
- web/ssh-treminal-ui/vite.config.js:22-31 — inject WS base URL per env; enable wss in prod; keep proxy only in dev

Backend
- src/main/java/com/fufu/terminal/config/StompAuthenticationInterceptor.java:84-90 — replace password headers with token lookup
- src/main/java/com/fufu/terminal/config/StompAuthenticationInterceptor.java:101 — StrictHostKeyChecking=yes
- src/main/java/com/fufu/terminal/config/WebSocketStompConfig.java:41-46 — restrict origins by profile
- src/main/resources/application.properties:13-19 — reduce prod log levels; keep DEBUG only in dev
- StompSessionManager.java:112-115,167-171 — unify convertAndSendToUser destinations
- SftpService.java:270-301,311-317 — replace in-memory buffers with streaming/temporary files and limits
- Remove legacy: LegacyWebSocketConfig.java; SshTerminalWebSocketHandler.java (after stabilization)

## 12. Open Questions

- Token issuance hardening (introduce AEAD/RSA-OAEP or libsodium in a later iteration)
- Shared known_hosts management across nodes (config repo/secret store)
- Spring Security integration scope/timing for REST and WS handshake protection

## 13. Appendix A – Sample Snippets

Frontend (resilient backoff)
```ts
function expBackoff(attempt) {
  const base = 1000, max = 30000
  const delay = Math.min(base * 2 ** attempt, max)
  return delay + Math.random() * 1000
}
```

Terminal batching
```ts
let buf=""; let scheduled=false
function writeChunk(s){ buf+=s; if(!scheduled){ scheduled=true; requestAnimationFrame(()=>{ term.write(buf); buf=""; scheduled=false })}}
```

Backend (download stream)
```java
@GetMapping(value="/api/sftp/download", produces=MediaType.APPLICATION_OCTET_STREAM_VALUE)
public Flux<DataBuffer> download(@RequestParam String path) { return sftpService.stream(path); }
```

## 14. Appendix B – Environments

- dev: origins="*", StrictHostKeyChecking=ask/override, DEBUG logs
- prod: origins=whitelist only, StrictHostKeyChecking=yes, INFO/ERROR logs, WSS required

---

This plan sequences “credentials safety → transfer/memory → architecture → UI/UX → observability/CI” with explicit file references, flags, and acceptance criteria to keep implementation unambiguous and guard against scope drift.
