# Terminal Project – Architecture, Security, and Maintainability Improvement Plan

Date: 2025-08-08
Owner: Security & Architecture Review
Scope: Backend (Spring Boot) + Frontend (Vue 3)

## 1. Executive Summary
This document proposes a prioritized plan to harden security, remove duplication, and standardize architecture. Key actions:
- Replace STOMP header credentials with session-scoped tokens; enable host key verification
- Remove legacy WebSocket path; consolidate on STOMP + session manager
- Unify command execution via a single policy-driven API (timeouts, output caps, allow/deny lists)
- Stream SFTP/ZIP transfers, add size/concurrency limits and cleanup
- Tighten CORS/WS origins, move debug-only settings to dev profile, standardize STOMP destinations
- Upgrade dependencies and add automated security checks

## 2. Current State Overview
- Dual WebSocket paths: STOMP-based path and a legacy handler. STOMP is primary; legacy guarded by profile but still present
- SSH credentials passed in STOMP headers; multiple places set StrictHostKeyChecking=no
- Repeated executeCommand wrappers across services; inconsistent error handling
- File transfers build large byte arrays in memory; potential OOM
- CORS and WS origins open to "*"; production risks

Key references:
- STOMP headers carry user/password and disable host key checking: src/main/java/com/fufu/terminal/config/StompAuthenticationInterceptor.java:87-105
- Legacy handler uses query params and disables host key checking: src/main/java/com/fufu/terminal/handler/SshTerminalWebSocketHandler.java:168-176,171
- Open origins: src/main/java/com/fufu/terminal/config/LegacyWebSocketConfig.java:45-46; src/main/java/com/fufu/terminal/config/WebSocketStompConfig.java:41-46; src/main/java/com/fufu/terminal/config/CorsConfig.java:44
- Mixed message destinations: src/main/java/com/fufu/terminal/service/StompSessionManager.java:112-115,167-171
- In-memory ZIP/base64: src/main/java/com/fufu/terminal/service/SftpService.java:270-301,311-317
- Large upload API (5GB): src/main/java/com/fufu/terminal/controller/FileUploadController.java:43,52-103
- Test with plaintext password and host key off: src/test/java/com/fufu/terminal/TerminalApplicationTests.java:36,47

## 3. Duplication: executeCommand Wrappers
One-off wrappers around SshCommandService exist with small behavior differences. Consolidate to a single policy-capable API.

Wrappers (duplicate intent):
- src/main/java/com/fufu/terminal/service/sillytavern/DockerContainerService.java:370
- src/main/java/com/fufu/terminal/service/sillytavern/ConfigurationService.java:498
- src/main/java/com/fufu/terminal/service/sillytavern/SystemDetectionService.java:497
- src/main/java/com/fufu/terminal/service/sillytavern/DockerVersionService.java:429
- src/main/java/com/fufu/terminal/service/sillytavern/RealTimeLogService.java:422
- src/main/java/com/fufu/terminal/service/sillytavern/DataManagementService.java:536

Canonical implementation:
- src/main/java/com/fufu/terminal/service/SshCommandService.java:49

Recommendation:
- Extend SshCommandService with policy-based methods (see Section 6). Replace all private executeCommand methods with calls to the unified API

## 4. Security Hardening
4.1 Encrypted Credential Exchange (No DB)
- Use server-published public key (JWK/PEM). Front-end fetches key and encrypts a payload using RSA-OAEP (SHA-256) or libsodium sealed box (X25519/XSalsa20-Poly1305)
- Payload schema (JSON, then encrypt+base64):
  {
    "host":"<ip/hostname>",
    "port":22,
    "user":"<username>",
    "pass":"<password>|<optional passphrase>",
    "auth":"password|privateKey",
    "privateKey":"<PEM if auth=privateKey, optional>",
    "nonce":"<random 96-bit>",
    "ts": "<epoch ms>",
    "sessionId":"<client session id>"
  }
- Client sends { alg, kid, enc, ciphertext } in STOMP connect headers (or initial REST bootstrap). Server uses private key to decrypt, validates ts (±60s) and nonce (single-use per session, in-memory LRU)
- Server stores decrypted credentials only in memory with TTL (e.g., 2 minutes) keyed by sessionId; immediately wipes on use/disconnect
- Prefer SSH key auth: client encrypts {privateKey, passphrase} same way; password becomes optional
- Add AEAD (e.g., RSA-OAEP + AES-GCM envelope) or sealed box to ensure integrity; reject tampered ciphertext

4.2 SSH Trust & Host Keys
- Enable StrictHostKeyChecking=yes. Maintain known_hosts (mounted file or centralized KV); first-time trust via admin confirmation or pinned fingerprints
- Deny unknown host or mismatch by default in prod; allow dev profile to prompt/override

4.3 Transport & Origins
- Require HTTPS/WSS in production; terminate TLS at proxy or app server
- Restrict allowed origins to trusted domains per environment; move current "*" to dev only

4.4 Session Isolation & Authorization
- Keep accessor.setUser(sessionId); enforce that clients can only SUBSCRIBE to /user/queue/* for their own session
- Add rate limiting and idle timeouts for sessions and command execution

4.5 File Upload/Download Safety
- Add authentication/authorization to FileUploadController endpoints; validate MIME and extension; add size and concurrency caps; integrate malware scanning where feasible
- Store uploads in a dedicated non-executable temp dir; configure periodic cleanup; ensure disk quotas/alerts

References: StompAuthenticationInterceptor.java:87-105; SshTerminalWebSocketHandler.java:168-176; WebSocketStompConfig.java:41-46; CorsConfig.java:44-53

4.2 Transport & Origins
- Require HTTPS/WSS in production; terminate TLS at proxy or app server
- Restrict allowed origins to trusted domains per environment; move current "*" to dev only

References: WebSocketStompConfig.java:41-46; LegacyWebSocketConfig.java:45-46; CorsConfig.java:44-53

4.3 Session Isolation & Authorization
- Continue using accessor.setUser(sessionId) but enforce subscription authorization: ensure clients can only subscribe to /user/queue/* for their own session
- Add rate limiting and idle timeouts for sessions and command execution

4.4 File Upload/Download Safety
- Add authentication/authorization to FileUploadController endpoints; validate MIME and extension; add size and concurrency caps; integrate malware scanning where feasible
- Store uploads in a dedicated non-executable temp dir; configure periodic cleanup; ensure disk quotas/alerts

References: FileUploadController.java:43,52-103

## 5. Architecture Consolidation
5.1 Remove Legacy WebSocket
- Remove LegacyWebSocketConfig and SshTerminalWebSocketHandler once STOMP path is verified; this eliminates duplicated lifecycles and surface area

References: LegacyWebSocketConfig.java; SshTerminalWebSocketHandler.java

5.2 Standardize STOMP Destinations
- Use convertAndSendToUser exclusively with consistent /user/queue/... destinations. Avoid custom "-user{sessionId}" endpoints
- Validate front-end subscriptions align to the standardized destinations

References: StompSessionManager.java:112-115,167-171

5.3 Session Lifecycle Ownership
- Keep SSH lifecycle in StompAuthenticationInterceptor + StompSessionManager. Document ownership boundaries and cleanup responsibilities

## 6. Unified Command Execution API
Goals: consistent timeouts, output caps, error semantics, and safety.

Proposed SshCommandService additions:
- executeOrThrow(Session, cmd, options): returns stdout or throws on non-zero exit; options include:
  - timeoutMs (overall), connectTimeoutMs
  - stdoutLimitBytes, stderrLimitBytes
  - allowSudo (boolean)
  - workingDir (optional)
  - acceptedExitCodes (set)
- execute(Session, cmd, options): returns {exit, stdout, stderr} with truncation indicators
- stream(Session, cmd, options, onStdoutChunk, onStderrChunk): for long-running streams (logs)
- policy:
  - Deny-list patterns (e.g., destructive root ops). Optional allow-list mode for high-risk contexts
  - Mandatory quoting helpers; avoid ad-hoc String.format("sh -c ...") shells unless necessary
  - Automatic redaction of secrets in logs

Migration:
- Replace private executeCommand wrappers in listed services with the unified API
- Add integration tests to capture different semantics (e.g., SystemDetectionService allows non-zero exit)

## 7. SFTP and File Transfer Improvements
- Switch from ByteArrayOutputStream accumulation to streaming/zip-on-the-fly
  - For directory downloads, build ZipOutputStream directly to WebSocket chunks or HTTP stream; avoid full in-memory buffers
- Add limits: per-file and aggregate size caps; throttle transfer rate; bound concurrent transfers per session
- Use server-side temp files with lifecycle management and periodic cleanup
- Compress/decompress in a bounded worker pool; surface progress events via STOMP

References: SftpService.java:270-301,311-317

## 8. Logging & Observability
- Sanitize logs to remove credentials and sensitive paths
- Reduce noisy debug logs in production (front-end and back-end)
- Add metrics: active sessions, command durations, transfer throughput, errors per destination; export to existing monitoring

## 9. Dependency & Platform Upgrades
- Spring Boot 3.0.2 → modern LTS 3.3.x series
- jsch 0.1.55 → maintained fork (e.g., com.github.mwiede:jsch) or evaluate Apache MINA SSHD for long-term
- Add OWASP Dependency-Check / Snyk to CI; fail on critical vulns

POM reference: pom.xml:14,62-91

## 10. Testing Strategy
- Crypto flow tests:
  - Fetch JWK, encrypt payload with kid=X, server decrypt validates alg/enc, ts window, nonce uniqueness
  - Expired/clock-skewed ts rejected; replayed nonce rejected; wrong kid rejected
  - SSH password and privateKey variants both succeed end-to-end; secrets wiped post-use
- Unit tests for unified command API (timeouts, truncation, exit-code handling)
- Integration tests for STOMP session lifecycle, subscription authorization, and destination routing
- Transfer tests: large file streaming boundaries, zip integrity, cleanup behaviors
- Security tests: CORS/WS origin restrictions per profile; ensure credentials never logged; known_hosts verification path
- Load tests: concurrent sessions and transfers to validate thread pools and limits

## 11. Rollout Plan
1) Publish server JWK set (GET /.well-known/terminal-jwks or /api/crypto/public-key). Implement private-key decrypt path on server; add nonce cache and ts window checks
2) Front-end: fetch key, encrypt payload, send encrypted headers; fall back to password-less (key-based) auth when available
3) Introduce unified command API behind feature flag; migrate services incrementally
4) Add origin restrictions and dev/prod profiles; deploy to staging
5) Switch front-end to standardized /user/queue/...; validate subscriptions; deprecate direct queue names
6) Remove legacy WebSocket; monitor errors/metrics
7) Enable strict host key checking with managed known_hosts; provide admin process for first trust
8) Turn on transfer streaming and limits; monitor memory and throughput

Backout: flags to disable encrypted header path or revert command API if issues arise

## 12. Acceptance Criteria Checklist
- Encrypted connect payload: client encrypts with latest server kid; server decrypts and validates ts±60s and nonce uniqueness
- No plaintext credentials present on the wire or in logs; decrypted secrets live only in memory with TTL and are wiped on use/disconnect
- StrictHostKeyChecking enabled; connections succeed with managed known_hosts; unknown hosts rejected in prod
- All services use unified SshCommandService API; no private executeCommand remain
- STOMP destinations standardized; front-end aligned; authorization verified
- SFTP/ZIP operations stream without OOM; enforced per-file and aggregate caps; concurrency throttled
- CORS/WS origins restricted in prod; dev allows “*” only
- CI shows zero critical dependency vulnerabilities; tests cover crypto flow, command API policies, transfers, and origins

## 13. File-by-File Remediation Map
- StompAuthenticationInterceptor.java:87-105 — remove password headers; switch to token lookup; enable host key check
- SshTerminalWebSocketHandler.java:168-176 — delete with legacy removal
- WebSocketStompConfig.java:41-46; CorsConfig.java:44 — restrict origins by profile
- StompSessionManager.java:112-115,167-171 — use convertAndSendToUser exclusively; unify destinations
- SftpService.java:270-301,311-317 — replace in-memory buffers with streaming/temporary files and limits
- DockerContainerService.java:370; ConfigurationService.java:498; SystemDetectionService.java:497; DockerVersionService.java:429; RealTimeLogService.java:422; DataManagementService.java:536 — remove private executeCommand and use unified API
- FileUploadController.java:43,52-103 — add authn/z, rate limits, MIME+extension validation, scanning, quotas
- TerminalApplicationTests.java:36,47 — remove plaintext password; avoid disabling host key in tests or guard by profile

## 14. Open Questions
- Token issuance and lifetime policy for SSH connect
- Strategy for managing known_hosts across multiple backends (shared store vs per-node)
- Whether to introduce Spring Security for endpoint protection immediately or in a later phase

## 15. Appendix – Default Command Policy Suggestions
- Defaults: timeout 30s; stdout/stderr cap 1MB; acceptedExitCodes {0}
- Deny-list samples: destructive filesystem ops on /, unrestricted rm -rf, arbitrary dd to block devices
- For diagnostics: a separate policy allowing certain non-zero exits and larger output, still capped
