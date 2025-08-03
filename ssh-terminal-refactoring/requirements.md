# SSH Terminal Management System Refactoring - Requirements

## Introduction

This specification defines the requirements for refactoring the SSH Terminal Management System from an over-engineered enterprise architecture to a simplified personal project scale while implementing a 4-type atomic script classification system. The refactoring addresses architectural complexity, multiple redundant controllers, and implements intelligent script execution with variable passing and decision-making capabilities.

The system will maintain existing Vue 3 + Spring Boot technology stack while significantly simplifying the architecture and introducing enhanced script management capabilities with real-time user interaction support.

## Requirements

### 1. Four-Type Atomic Script Classification System
**User Story:** As a system administrator, I want scripts classified into four distinct types with specific behaviors, so that the system can handle different script execution patterns intelligently and efficiently.

**Acceptance Criteria:**
1.1. When scripts are registered, then they SHALL be classified as one of four types: Static Built-in, Configurable Built-in, Interactive Built-in, or User Scripts
1.2. When Static Built-in scripts execute, then they SHALL run without parameters and provide immediate results (e.g., server location detection)
1.3. When Configurable Built-in scripts execute, then they SHALL accept parameters and make intelligent decisions based on context (e.g., geographic-based mirror selection)
1.4. When Interactive Built-in scripts execute, then they SHALL support real-time user interaction during execution
1.5. When User Scripts execute, then they SHALL be admin-configurable through the management interface
1.6. When script types are displayed in the UI, then each type SHALL have distinct visual indicators and behavior descriptions

### 2. Variable Passing Between Scripts
**User Story:** As a user, I want output from one script to be automatically available as input to subsequent scripts, so that complex workflows can be executed seamlessly without manual data transfer.

**Acceptance Criteria:**
2.1. When scripts execute in sequence, then output variables from completed scripts SHALL be available to subsequent scripts
2.2. When variables are passed between scripts, then they SHALL be strongly typed and validated before use
2.3. When variable dependencies exist, then the system SHALL enforce execution order to ensure dependencies are resolved
2.4. When variables are missing or invalid, then the system SHALL provide clear error messages and halt execution
2.5. When variable transformations are needed, then the system SHALL support data type conversions and formatting
2.6. When execution context is maintained, then variables SHALL persist throughout the entire script workflow session

### 3. Intelligent Geographic-Based Decision Making
**User Story:** As a user, I want the system to automatically select optimal mirror sources based on server geographic location, so that installations complete faster without manual configuration.

**Acceptance Criteria:**
3.1. When server location is detected, then the system SHALL automatically determine the optimal mirror source region
3.2. When Chinese servers are detected, then the system SHALL default to Chinese mirror sources (Aliyun, Tsinghua, etc.)
3.3. When non-Chinese servers are detected, then the system SHALL use official international mirror sources
3.4. When location detection fails, then the system SHALL prompt user for manual mirror selection or use safe defaults
3.5. When mirror selection occurs, then the decision logic SHALL be logged for debugging and user transparency
3.6. When multiple mirrors are available for a region, then the system SHALL select based on predefined priority rankings

### 4. Real-Time User Interaction During Script Execution
**User Story:** As a user, I want to provide input during script execution when prompted, so that interactive installations can proceed without pre-configuration of all parameters.

**Acceptance Criteria:**
4.1. When interactive scripts require user input, then the system SHALL pause execution and prompt the user via WebSocket
4.2. When user input is provided, then validation SHALL occur immediately with feedback before continuing execution
4.3. When user input timeouts occur, then the system SHALL use default values or gracefully terminate with clear messages
4.4. When multiple users are executing scripts, then interaction sessions SHALL be properly isolated per user session
4.5. When network connectivity is lost during interaction, then the system SHALL maintain state and allow reconnection
4.6. When interaction is complete, then the system SHALL resume script execution with the provided input values

### 5. Controller Architecture Simplification
**User Story:** As a developer, I want multiple redundant script execution controllers consolidated into a single unified interface, so that the codebase is maintainable and API surface is simplified.

**Acceptance Criteria:**
5.1. When controllers are refactored, then ScriptController, UserScriptExecutionController, and UnifiedScriptExecutionController SHALL be merged into a single ScriptExecutionController
5.2. When API endpoints are consolidated, then all script execution SHALL use consistent endpoint patterns (/api/scripts/execute)
5.3. When execution strategies are unified, then all script types SHALL use the same execution pipeline with type-specific handlers
5.4. When error handling is centralized, then all controllers SHALL use consistent error response formats and logging
5.5. When documentation is updated, then API documentation SHALL reflect the simplified controller structure
5.6. When backward compatibility is required, then deprecated endpoints SHALL include clear migration guidance

### 6. Service Layer Simplification
**User Story:** As a developer, I want over-abstracted service layers simplified while maintaining functionality, so that the code is easier to understand and maintain.

**Acceptance Criteria:**
6.1. When service layers are refactored, then AtomicScriptService, ScriptEngineService, and TaskExecutionService SHALL be consolidated where appropriate
6.2. When business logic is simplified, then unnecessary abstraction layers SHALL be removed without losing functionality
6.3. When service interfaces are cleaned up, then only essential methods SHALL remain in public interfaces
6.4. When dependency injection is optimized, then circular dependencies SHALL be eliminated and dependencies SHALL be clearly defined
6.5. When service responsibilities are clarified, then each service SHALL have a single, well-defined purpose
6.6. When legacy services are removed, then migration path SHALL ensure no functionality is lost

### 7. Database Schema Optimization
**User Story:** As a system administrator, I want the database schema simplified to match the new script classification system, so that data storage is efficient and queries are performant.

**Acceptance Criteria:**
7.1. When schema is refactored, then script type enumeration SHALL reflect the four-type classification system
7.2. When built-in scripts are removed from database, then they SHALL be managed entirely in code
7.3. When user scripts remain in database, then schema SHALL be optimized for the new execution model
7.4. When variable passing is implemented, then execution context storage SHALL support variable persistence
7.5. When migration scripts are created, then they SHALL safely migrate existing data to the new schema
7.6. When database performance is optimized, then indexes SHALL be created for common query patterns

### 8. WebSocket Integration Enhancement
**User Story:** As a user, I want real-time progress updates and interaction capabilities during script execution, so that I can monitor progress and provide input when needed.

**Acceptance Criteria:**
8.1. When scripts execute, then progress updates SHALL be sent via WebSocket with execution stage, current step, and estimated completion
8.2. When user interaction is required, then interaction prompts SHALL be sent via WebSocket with input field specifications
8.3. When users provide input, then responses SHALL be validated and acknowledgments sent via WebSocket
8.4. When errors occur during execution, then error details SHALL be immediately communicated via WebSocket
8.5. When execution completes, then final results SHALL be sent via WebSocket with execution summary and output data
8.6. When WebSocket connections are lost, then the system SHALL maintain execution state and allow session recovery

### 9. Frontend Vue.js Interface Modernization
**User Story:** As a user, I want an intuitive and responsive interface that clearly shows script types and execution progress, so that I can efficiently manage and execute scripts.

**Acceptance Criteria:**
9.1. When script lists are displayed, then each script type SHALL have distinct visual styling and behavior indicators
9.2. When script execution begins, then a progress interface SHALL show real-time updates with step details and estimated completion
9.3. When user interaction is required, then modal dialogs SHALL capture input with appropriate validation and help text
9.4. When execution completes, then results SHALL be displayed in a readable format with options for viewing logs and outputs
9.5. When errors occur, then error messages SHALL be displayed with context and suggested resolution steps
9.6. When multiple executions run concurrently, then the interface SHALL manage multiple progress tracking sessions

### 10. Admin Management Interface Enhancement
**User Story:** As an administrator, I want flexible management of user scripts and script groups, so that I can customize the system for specific organizational needs.

**Acceptance Criteria:**
10.1. When user scripts are managed, then the interface SHALL provide CRUD operations with parameter definition and validation
10.2. When script groups are organized, then drag-and-drop functionality SHALL allow flexible grouping and ordering
10.3. When script templates are created, then the interface SHALL support parameter placeholders and default values
10.4. When execution history is reviewed, then detailed logs SHALL be available with filtering and search capabilities
10.5. When system health is monitored, then dashboard SHALL show execution statistics, error rates, and system performance
10.6. When user permissions are managed, then role-based access SHALL control script visibility and execution rights

### 11. SSH Connection Management Optimization
**User Story:** As a user, I want SSH connections managed efficiently across all script types, so that connections are reused appropriately and resource usage is optimized.

**Acceptance Criteria:**
11.1. When SSH connections are established, then connection pooling SHALL prevent redundant connections for the same host
11.2. When scripts execute sequentially, then SSH connections SHALL be reused when targeting the same server
11.3. When connection failures occur, then retry logic SHALL attempt reconnection with exponential backoff
11.4. When connections idle timeout, then they SHALL be cleanly closed to prevent resource leaks
11.5. When multiple users connect to the same server, then connection sharing SHALL be managed securely per user session
11.6. When connection parameters change, then new connections SHALL be established and old ones properly closed

### 12. Error Handling and Logging Enhancement
**User Story:** As a developer and system administrator, I want comprehensive error handling and logging, so that issues can be quickly diagnosed and resolved.

**Acceptance Criteria:**
12.1. When errors occur during script execution, then they SHALL be logged with correlation IDs, user context, and full stack traces
12.2. When user errors occur, then error messages SHALL be user-friendly with actionable resolution steps
12.3. When system errors occur, then they SHALL be logged for developer investigation without exposing sensitive information to users
12.4. When performance issues arise, then metrics SHALL be logged for analysis and optimization
12.5. When audit trails are required, then all script executions SHALL be logged with user identity, script details, and outcomes
12.6. When log retention is configured, then logs SHALL be rotated based on size and age limits

### 13. Performance and Scalability Optimization
**User Story:** As a system administrator, I want the system to handle concurrent script executions efficiently, so that multiple users can execute scripts simultaneously without performance degradation.

**Acceptance Criteria:**
13.1. When multiple scripts execute concurrently, then resource allocation SHALL prevent any single execution from consuming excessive system resources
13.2. When script execution queues build up, then prioritization SHALL ensure interactive scripts receive higher priority than batch scripts
13.3. When memory usage grows during execution, then garbage collection SHALL be managed to prevent out-of-memory errors
13.4. When database connections are used, then connection pooling SHALL optimize database access across all operations
13.5. When system resources are limited, then graceful degradation SHALL maintain core functionality while limiting resource-intensive operations
13.6. When load testing is performed, then the system SHALL handle at least 10 concurrent script executions without significant performance degradation

### 14. Testing Framework Implementation
**User Story:** As a developer, I want comprehensive unit and integration tests, so that refactoring can be completed safely without introducing regressions.

**Acceptance Criteria:**
14.1. When controller consolidation occurs, then unit tests SHALL verify all endpoints function correctly with proper error handling
14.2. When service layer refactoring is done, then integration tests SHALL verify business logic operates correctly across all script types
14.3. When database schema changes, then migration tests SHALL verify data integrity and query performance
14.4. When WebSocket functionality is enhanced, then real-time communication tests SHALL verify message delivery and session management
14.5. When frontend components are updated, then component tests SHALL verify user interface behavior and error handling
14.6. When end-to-end workflows are tested, then automated tests SHALL verify complete script execution workflows for all script types

### 15. Security and Authorization Enhancement
**User Story:** As a security administrator, I want enhanced security controls for script execution, so that the system prevents unauthorized access and maintains audit compliance.

**Acceptance Criteria:**
15.1. When user authentication occurs, then JWT tokens SHALL be validated for all script execution requests
15.2. When authorization is checked, then users SHALL only access scripts and groups assigned to their role
15.3. When script parameters are processed, then input validation SHALL prevent command injection and other security vulnerabilities
15.4. When SSH credentials are managed, then they SHALL be encrypted at rest and never logged in plain text
15.5. When audit logs are created, then they SHALL include sufficient detail for compliance requirements without exposing sensitive data
15.6. When rate limiting is applied, then it SHALL prevent abuse while allowing legitimate usage patterns