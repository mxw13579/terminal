# SSH Terminal Management System Refactoring - Requirements

## Introduction

This specification defines the requirements for refactoring the SSH Terminal Management System from an over-engineered enterprise architecture to a simplified personal project scale. The refactoring implements a 4-type atomic script classification system with intelligent execution, variable passing, real-time interaction, and geographic-based decision making while maintaining the KISS (Keep It Simple, Stupid) and YAGNI (You Aren't Gonna Need It) design principles outlined in the simplified architecture.

The system maintains the existing Vue 3 + Spring Boot technology stack while significantly reducing architectural complexity, consolidating redundant controllers, and introducing enhanced script management capabilities with real-time user interaction support.

## Requirements

### 1. Four-Type Atomic Script Classification System
**User Story:** As a system administrator, I want scripts classified into four distinct types with specific behaviors and execution patterns, so that the system can handle different script scenarios intelligently while maintaining code-based management for built-in scripts.

**Acceptance Criteria:**
1.1. When scripts are registered, then they SHALL be classified as one of four types: Static Built-in, Configurable Built-in, Interactive Built-in, or User Scripts
1.2. When Static Built-in scripts execute, then they SHALL run without parameters and provide immediate results (e.g., server location detection, system info collection)
1.3. When Configurable Built-in scripts execute, then they SHALL accept parameters and make intelligent decisions based on context (e.g., geographic-based mirror selection)
1.4. When Interactive Built-in scripts execute, then they SHALL support real-time user interaction during execution via WebSocket communication
1.5. When User Scripts execute, then they SHALL be admin-configurable through the management interface and stored in database
1.6. When built-in scripts are managed, then they SHALL be implemented in Java code under `com.fufu.terminal.command.impl.builtin` package and never stored in database
1.7. When script types are displayed in the UI, then each type SHALL have distinct visual indicators, behavior descriptions, and execution patterns

### 2. Variable Passing and Context Management Between Scripts
**User Story:** As a user, I want output from one script to be automatically available as input to subsequent scripts with proper type validation, so that complex workflows can be executed seamlessly without manual data transfer.

**Acceptance Criteria:**
2.1. When scripts execute in sequence, then output variables from completed scripts SHALL be available to subsequent scripts within the same session
2.2. When variables are passed between scripts, then they SHALL be strongly typed (String, Integer, Boolean, JSON) and validated before use
2.3. When variable dependencies exist, then the system SHALL enforce execution order to ensure dependencies are resolved before script execution
2.4. When variables are missing or invalid, then the system SHALL provide clear error messages and halt execution with recovery suggestions
2.5. When variable transformations are needed, then the system SHALL support data type conversions and formatting between compatible types
2.6. When execution context is maintained, then variables SHALL persist throughout the entire script workflow session with proper scope management (SESSION, SCRIPT, GLOBAL)
2.7. When variable conflicts occur, then the system SHALL use scope precedence rules (SCRIPT > SESSION > GLOBAL) to resolve conflicts

### 3. Intelligent Geographic-Based Decision Making
**User Story:** As a user, I want the system to automatically detect server location and select optimal mirror sources, so that installations complete faster without manual configuration while supporting both Chinese and international environments.

**Acceptance Criteria:**
3.1. When server location is detected, then the system SHALL automatically determine the optimal mirror source region using built-in detection scripts
3.2. When Chinese servers are detected (based on IP geolocation or network tests), then the system SHALL default to Chinese mirror sources (Aliyun, Tsinghua, USTC, 163)
3.3. When non-Chinese servers are detected, then the system SHALL use official international mirror sources (Ubuntu, Docker Hub, NPM Registry)
3.4. When location detection fails or times out, then the system SHALL prompt user for manual mirror selection with recommended defaults
3.5. When mirror selection occurs, then the decision logic SHALL be logged with reasoning for debugging and user transparency
3.6. When multiple mirrors are available for a region, then the system SHALL select based on predefined priority rankings and optional network speed tests
3.7. When mirror configurations are applied, then they SHALL be automatically injected into configurable built-in scripts as parameters

### 4. Real-Time User Interaction During Script Execution
**User Story:** As a user, I want to provide input during script execution when prompted, so that interactive installations can proceed without pre-configuration of all parameters while maintaining session isolation.

**Acceptance Criteria:**
4.1. When interactive scripts require user input, then the system SHALL pause execution and prompt the user via WebSocket with input specifications (type, validation, default)
4.2. When user input is provided, then validation SHALL occur immediately with feedback before continuing execution, supporting text, number, boolean, and choice inputs
4.3. When user input timeouts occur (default 5 minutes), then the system SHALL use default values or gracefully terminate with clear messages and recovery options
4.4. When multiple users are executing scripts, then interaction sessions SHALL be properly isolated per user session with unique session identifiers
4.5. When network connectivity is lost during interaction, then the system SHALL maintain execution state and allow reconnection to resume interaction
4.6. When interaction is complete, then the system SHALL resume script execution with the provided input values and log the interaction for audit purposes

### 5. Controller Architecture Consolidation
**User Story:** As a developer, I want the three redundant script execution controllers (ScriptController, UserScriptExecutionController, UnifiedScriptExecutionController) consolidated into a single unified interface, so that the codebase is maintainable and API surface is simplified.

**Acceptance Criteria:**
5.1. When controllers are refactored, then ScriptController, UserScriptExecutionController, and UnifiedScriptExecutionController SHALL be merged into a single ScriptExecutionController
5.2. When API endpoints are consolidated, then all script execution SHALL use consistent endpoint patterns under `/api/scripts/` with type-based routing
5.3. When execution strategies are unified, then all script types SHALL use the same execution pipeline with type-specific handlers for parameter processing
5.4. When error handling is centralized, then all controllers SHALL use consistent error response formats (ErrorResponse class) and structured logging
5.5. When API documentation is updated, then OpenAPI specifications SHALL reflect the simplified controller structure with clear endpoint descriptions
5.6. When backward compatibility is required during migration, then deprecated endpoints SHALL include clear migration guidance and sunset timelines

### 6. Service Layer Simplification and Consolidation
**User Story:** As a developer, I want over-abstracted service layers simplified while maintaining all functionality, so that the code is easier to understand, test, and maintain following KISS principles.

**Acceptance Criteria:**
6.1. When service layers are refactored, then AtomicScriptService, ScriptEngineService, and TaskExecutionService SHALL be consolidated into ScriptExecutionService and BuiltinScriptRegistry
6.2. When business logic is simplified, then unnecessary abstraction layers SHALL be removed without losing functionality or introducing code duplication
6.3. When service interfaces are cleaned up, then only essential methods SHALL remain in public interfaces with clear single responsibilities
6.4. When dependency injection is optimized, then circular dependencies SHALL be eliminated and all dependencies SHALL be clearly defined and testable
6.5. When service responsibilities are clarified, then each service SHALL have a single, well-defined purpose aligned with the simplified architecture
6.6. When legacy services are removed, then migration SHALL ensure no functionality is lost and all existing features remain operational

### 7. Database Schema Optimization for Personal Scale
**User Story:** As a system administrator, I want the database schema simplified to match the new script classification system and personal project scale, so that data storage is efficient, queries are performant, and maintenance is minimal.

**Acceptance Criteria:**
7.1. When schema is refactored, then script type enumeration SHALL reflect the four-type classification system (STATIC_BUILTIN, CONFIGURABLE_BUILTIN, INTERACTIVE_BUILTIN, USER_SCRIPT)
7.2. When built-in scripts are removed from database, then they SHALL be managed entirely in Java code with Spring component registration
7.3. When user scripts remain in database, then schema SHALL be optimized for the new execution model with simplified relationships
7.4. When variable passing is implemented, then execution context storage SHALL support variable persistence with JSON format for flexibility
7.5. When migration scripts are created, then they SHALL safely migrate existing data to the new schema without data loss
7.6. When database performance is optimized, then indexes SHALL be created for common query patterns (user_id + script_type, session_id, created_at ranges)
7.7. When atomic_scripts and aggregated_scripts tables are removed, then their functionality SHALL be replaced by code-based built-in script management

### 8. Enhanced WebSocket Integration for Real-Time Communication
**User Story:** As a user, I want real-time progress updates, interaction capabilities, and status notifications during script execution, so that I can monitor progress, provide input when needed, and receive immediate feedback.

**Acceptance Criteria:**
8.1. When scripts execute, then progress updates SHALL be sent via WebSocket with execution stage, current step, percentage completion, and estimated time remaining
8.2. When user interaction is required, then interaction prompts SHALL be sent via WebSocket with input field specifications (type, validation rules, help text, default value)
8.3. When users provide input, then responses SHALL be validated immediately and acknowledgments sent via WebSocket before script execution resumes
8.4. When errors occur during execution, then error details SHALL be immediately communicated via WebSocket with error type, user-friendly message, and suggested actions
8.5. When execution completes, then final results SHALL be sent via WebSocket with execution summary, output data, and performance metrics
8.6. When WebSocket connections are lost, then the system SHALL maintain execution state and allow session recovery with full history replay
8.7. When multiple executions run concurrently, then WebSocket messages SHALL be properly routed to correct user sessions with unique identifiers

### 9. Frontend Vue.js Interface Modernization
**User Story:** As a user, I want an intuitive and responsive interface that clearly shows script types, execution progress, and interaction prompts, so that I can efficiently manage and execute scripts without confusion.

**Acceptance Criteria:**
9.1. When script lists are displayed, then each script type SHALL have distinct visual styling (icons, colors, badges) and behavior indicators with clear descriptions
9.2. When script execution begins, then a progress interface SHALL show real-time updates with step details, progress bars, and estimated completion times
9.3. When user interaction is required, then modal dialogs SHALL capture input with appropriate validation, help text, and default values
9.4. When execution completes, then results SHALL be displayed in a readable format with options for viewing detailed logs, copying outputs, and downloading results
9.5. When errors occur, then error messages SHALL be displayed with context, stack traces (for developers), and suggested resolution steps
9.6. When multiple executions run concurrently, then the interface SHALL manage multiple progress tracking sessions with tabbed or list views
9.7. When script types are browsed, then the interface SHALL group scripts by type with filtering and search capabilities

### 10. Enhanced Admin Management Interface
**User Story:** As an administrator, I want flexible management of user scripts, script groups, and system monitoring, so that I can customize the system for specific organizational needs while maintaining oversight.

**Acceptance Criteria:**
10.1. When user scripts are managed, then the interface SHALL provide CRUD operations with parameter definition, validation rules, and testing capabilities
10.2. When script groups are organized, then drag-and-drop functionality SHALL allow flexible grouping and ordering with visual hierarchy
10.3. When script templates are created, then the interface SHALL support parameter placeholders, default values, and conditional logic
10.4. When execution history is reviewed, then detailed logs SHALL be available with filtering by user, script type, date range, and execution status
10.5. When system health is monitored, then dashboard SHALL show execution statistics, error rates, system performance, and SSH connection metrics
10.6. When user permissions are managed, then role-based access SHALL control script visibility, execution rights, and administrative functions
10.7. When built-in scripts are viewed, then the interface SHALL display read-only information about available built-in scripts with documentation

### 11. SSH Connection Management Optimization
**User Story:** As a user, I want SSH connections managed efficiently across all script types with proper connection pooling, so that connections are reused appropriately and resource usage is optimized.

**Acceptance Criteria:**
11.1. When SSH connections are established, then connection pooling SHALL prevent redundant connections for the same host with user session isolation
11.2. When scripts execute sequentially, then SSH connections SHALL be reused when targeting the same server within the same user session
11.3. When connection failures occur, then retry logic SHALL attempt reconnection with exponential backoff (1s, 2s, 4s, 8s, 16s max)
11.4. When connections idle timeout (default 10 minutes), then they SHALL be cleanly closed to prevent resource leaks
11.5. When multiple users connect to the same server, then connection sharing SHALL be managed securely per user session without credential mixing
11.6. When connection parameters change, then new connections SHALL be established and old ones properly closed with graceful cleanup
11.7. When connection limits are reached, then new requests SHALL be queued or rejected with clear error messages

### 12. Comprehensive Error Handling and Logging Enhancement
**User Story:** As a developer and system administrator, I want comprehensive error handling and structured logging, so that issues can be quickly diagnosed, resolved, and prevented from recurring.

**Acceptance Criteria:**
12.1. When errors occur during script execution, then they SHALL be logged with correlation IDs, user context, script type, and full stack traces
12.2. When user errors occur, then error messages SHALL be user-friendly with actionable resolution steps and contact information
12.3. When system errors occur, then they SHALL be logged for developer investigation without exposing sensitive information to users
12.4. When performance issues arise, then metrics SHALL be logged for analysis including execution time, memory usage, and SSH connection counts
12.5. When audit trails are required, then all script executions SHALL be logged with user identity, script details, parameters, and outcomes
12.6. When log retention is configured, then logs SHALL be rotated based on size (100MB) and age limits (30 days) with compression
12.7. When error patterns are detected, then the system SHALL provide alerts and diagnostic information for preventive maintenance

### 13. Performance and Scalability Optimization for Personal Scale
**User Story:** As a system administrator, I want the system to handle concurrent script executions efficiently within personal project limits, so that multiple scripts can execute simultaneously without performance degradation.

**Acceptance Criteria:**
13.1. When multiple scripts execute concurrently, then resource allocation SHALL prevent any single execution from consuming excessive system resources (CPU, memory)
13.2. When script execution queues build up, then prioritization SHALL ensure interactive scripts receive higher priority than batch scripts
13.3. When memory usage grows during execution, then garbage collection SHALL be managed to prevent out-of-memory errors with monitoring
13.4. When database connections are used, then connection pooling SHALL optimize database access across all operations with HikariCP
13.5. When system resources are limited, then graceful degradation SHALL maintain core functionality while limiting resource-intensive operations
13.6. When load testing is performed, then the system SHALL handle at least 10 concurrent script executions without significant performance degradation
13.7. When resource limits are reached, then the system SHALL provide clear feedback and queue management for additional requests

### 14. Comprehensive Testing Framework Implementation
**User Story:** As a developer, I want comprehensive unit, integration, and end-to-end tests, so that refactoring can be completed safely without introducing regressions or breaking existing functionality.

**Acceptance Criteria:**
14.1. When controller consolidation occurs, then unit tests SHALL verify all endpoints function correctly with proper error handling and response formats
14.2. When service layer refactoring is done, then integration tests SHALL verify business logic operates correctly across all script types
14.3. When database schema changes, then migration tests SHALL verify data integrity, query performance, and successful data migration
14.4. When WebSocket functionality is enhanced, then real-time communication tests SHALL verify message delivery, session management, and error handling
14.5. When frontend components are updated, then component tests SHALL verify user interface behavior, error handling, and user experience flows
14.6. When end-to-end workflows are tested, then automated tests SHALL verify complete script execution workflows for all script types with realistic scenarios
14.7. When performance testing is conducted, then load tests SHALL verify system behavior under concurrent execution scenarios

### 15. Security and Authorization Enhancement
**User Story:** As a security administrator, I want enhanced security controls for script execution and data protection, so that the system prevents unauthorized access, protects sensitive information, and maintains audit compliance.

**Acceptance Criteria:**
15.1. When user authentication occurs, then JWT tokens SHALL be validated for all script execution requests with proper expiration handling
15.2. When authorization is checked, then users SHALL only access scripts and groups assigned to their role with proper permission validation
15.3. When script parameters are processed, then input validation SHALL prevent command injection, path traversal, and other security vulnerabilities
15.4. When SSH credentials are managed, then they SHALL be encrypted at rest, transmitted securely, and never logged in plain text
15.5. When audit logs are created, then they SHALL include sufficient detail for compliance requirements without exposing sensitive data (passwords, keys)
15.6. When rate limiting is applied, then it SHALL prevent abuse (max 10 concurrent executions per user) while allowing legitimate usage patterns
15.7. When sensitive data is handled, then it SHALL be properly masked in logs, responses, and error messages to prevent information disclosure