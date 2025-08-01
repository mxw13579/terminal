# SSH 终端脚本管理系统 - 工作计划与决策日志
[WORK_PLAN.md](
**文档版本**: v1.0
**创建时间**: 2025-08-01
**目标**: 作为开发过程的中央计划和决策记录，确保开发过程不偏离核心需求，并为问题排查提供精准的回溯路径。

---

## 1. 核心需求与设计原则

*   **脚本模型**: 内置脚本必须区分为“带参模板”（如 Docker 部署，需要配置文件）和“无参功能”（如系统检测），以实现最大程度的复用和灵活性。
*   **分组维度**: 脚本分组必须支持“项目维度”（围绕一个具体项目，如“我的博客”）和“配置驱动维度”（通用的、可被任何项目使用的功能，如“通用Docker项目部署”）。
*   **核心体验**: 系统必须实现基于 WebSocket 的实时交互执行流程。脚本执行过程中可以暂停，向用户请求确认（是/否）或输入（文本/密码），并根据用户的实时反馈继续执行。

---

## 2. 总体开发计划 (Roadmap)

*   [✅] **P0: 交互式执行机制 (后端+前端)** `(已完成)`
*   [✅] **P1: 上下文与条件执行** `(已完成)`
*   [✅] **P2: 数据模型重构 (脚本与分组)** `(已完成)`
*   [✅] **P3: 聚合脚本构建器 (支持参数化)** `(已完成)`
*   [✅] **P4: 用户端首页重构与体验优化** `(已完成)`

---

## 3. 详细工作与决策日志 (Action & Decision Log)

`[2025-08-01]`
#### **完成 (P4): 用户端首页重构与体验优化**

**思考与决策:**
*   **目标:** 全面提升核心用户界面的性能、健壮性和功能完整性，使其与后端强大的功能对齐。主要涉及`Home.vue`和`ScriptExecution.vue`两个视图。
*   **决策路径:**
    1.  **优化数据加载:** 我首先分析了`Home.vue`的加载机制，发现了一个严重的N+1查询性能问题（为获取脚本数量，对每个分组都进行一次API调用）。我立即重构了前端逻辑，使其能够利用已有的高效后端API (`/api/user/script-groups`)，仅通过一次网络请求就获取所有需要的数据，显著提升了首页加载速度。
    2.  **重构执行页面:** 对于`ScriptExecution.vue`，我同样发现其数据加载逻辑是低效且错误的（调用了两个独立的API，其中一个还是管理员接口）。我通过在后端`ScriptGroupService`和`UserScriptGroupController`中增加一个新的、专用的API端点 (`/api/user/script-groups/{groupId}`) 来解决此问题，该端点一次性返回了脚本执行页面所需的所有数据。这不仅提高了性能，还修复了权限bug。
    3.  **移除技术债务:** 在重构过程中，我移除了`UserScriptExecution.vue`中所有用于模拟执行的硬编码逻辑 (`simulateScriptExecution`)。这些代码是过时的技术债务，移除后使组件更简洁、更易于维护，且完全依赖于真实的后端WebSocket通信。
    4.  **实现交互式UI:** 最后，我完成了交互式执行的闭环。我创建了一个新的可复用组件`InteractionModal.vue`来处理所有类型的用户交互（确认、文本输入、密码输入）。然后，我将此组件集成到`UserScriptExecution.vue`中，并添加了新的WebSocket订阅逻辑来监听和显示交互请求，并通过`handleInteractionResponse`方法将用户的响应安全地提交回后端。
*   **影响:**
    *   **后端:** `ScriptGroupService.java`, `UserScriptGroupController.java`, `ScriptGroupRepository.java`
    *   **前端:** `views/user/Home.vue`, `views/user/ScriptExecution.vue`, `components/InteractionModal.vue` (新文件)

**代码快照 (Git Commit):** `68905b2ccb468c50579641a47e53ae2b08aa62ad`


`[2025-08-01]`
#### **完成 (P3): 实现聚合脚本构建器 (端到端)**

**思考与决策:**
*   **目标:** 提供一个端到端的功能，允许用户通过可视化界面创建、配置和保存由多个原子脚本组成的复杂工作流。这是应用的核心价值所在。
*   **决策路径:**
    1.  **DTO先行:** 我首先定义了一个 `AggregatedScriptCreateRequest` DTO。这个决策至关重要，因为它创建了一个清晰、稳定的数据契约，解耦了前端的视图模型和后端的持久化实体。这使得前后端可以并行开发，并且降低了因后端实体变化而破坏前端的风险。
    2.  **服务层逻辑:** 接下来，我重构了 `AggregatedScriptService`，用一个新的、更智能的 `createAggregatedScript` 方法取代了旧的、简单的CRUD方法。该方法负责接收DTO，并将其转换为 `AggregatedScript` 实体及其所有关联的 `AggregateAtomicRelation` 子实体，封装了所有的业务逻辑和持久化操作。
    3.  **暴露API端点:** 我更新了 `AdminAggregatedScriptController`，使其能够接收新的DTO请求并调用我们刚刚创建的服务方法。这完成了后端的闭环。
    4.  **前端实现:** 最后，我重构了 `ScriptBuilder.vue` 组件。我添加了用于输入元数据（名称、描述）的表单，并实现了核心的 `saveScript` 方法。该方法负责收集用户在UI上的所有输入（包括拖拽的命令顺序和配置的条件），构建与后端DTO完全匹配的JSON载荷，并将其发送到我们创建的API端点。
*   **影响:**
    *   **后端:** `AggregatedScriptService.java`, `AdminAggregatedScriptController.java`, `dto/AggregatedScriptCreateRequest.java`
    *   **前端:** `views/ScriptBuilder.vue`

**代码快照 (Git Commit):** `68905b2ccb468c50579641a47e53ae2b08aa62ad`


`[2025-08-01]`
#### **完成 (P2): 完成核心实体的数据模型重构**

**思考与决策:**
*   **目标:** 使Java实体类与数据库Schema完全对齐，并用现代JPA实践（如关联关系映射）替换掉旧的、基于JSON的变通方法。一个干净、类型安全的数据模型是后续所有业务功能开发的基础。
*   **决策路径:**
    1.  **自下而上:** 我遵循了从 `AtomicScript` -> `AggregatedScript` -> `ScriptGroup` 的自下而上的重构顺序。这种方法是有效的，因为它确保了在构建更高级别的关系（如 `AggregatedScript` 对 `AtomicScript` 的 `OneToMany`）之前，其依赖的实体（`AtomicScript`）已经被清理和稳定下来。
    2.  **清理代替修补:** 对于 `AtomicScript`，我选择了直接移除冗余字段（如 `inputParams`）而不是试图去兼容它们。这虽然改动较大，但从长远来看，它消除了代码中的歧义和未来的维护成本。
    3.  **用对象关系代替ID引用:** 在所有关联实体中（如 `AggregateAtomicRelation`），我用标准的 `@ManyToOne` 对象引用替换了原始的 `Long` 类型ID字段。这是JPA的核心实践，它极大地简化了数据查询（例如，可以从一个`AggregateAtomicRelation`对象直接通过 `getAtomicScript()` 获取脚本，而无需再次查询数据库），并使代码更具可读性和面向对象。
*   **影响:**
    *   **后端:** `AtomicScript.java`, `AggregatedScript.java`, `ScriptGroup.java`, `AggregateAtomicRelation.java`, `GroupAggregateRelation.java`

**代码快照 (Git Commit):** `0a01f9e67afd9bb68b54545949f04f6a0cdfb260`

`[2025-08-01]`
#### **完成 (P1): 实现上下文持久化与条件执行**

**思考与决策:**
*   **目标:** 解决上下文的线程安全问题，并实现变量在脚本各步骤间的持久化传递，这是实现智能化脚本流（如“检测到系统为Debian后才执行Debian专属命令”）的基础。
*   **决策路径:**
    1.  **重构上下文对象:** 首先，将 `EnhancedScriptContext` 从一个单例 Spring Bean (`@Component`) 改为普通的Java对象 (POJO)。这是解决问题的关键，它消除了多线程执行时的数据污染风险，保证了每个执行会话都有一个隔离的、干净的上下文环境。
    2.  **实现序列化:** 为 `EnhancedScriptContext` 添加了 `toJson()` 和 `fromJson()` 方法，使其具备了将自身状态（核心变量）与数据库中的JSON字段相互转换的能力。
    3.  **整合执行器:** 最后，修改 `InteractiveScriptExecutor` 来管理上下文的完整生命周期。执行器现在会在会话开始时创建上下文实例，从数据库加载其先前状态，并在**每一步执行后**立即将其最新状态序列化并存回数据库。这个“步后即存”的策略是确保变量能被下一步骤读取的核心机制。
    4.  **引入条件判断:** 在执行器中添加了 `shouldExecuteStep` 方法，该方法利用上下文的 `resolveVariables` 功能来判断脚本的执行条件是否满足。
*   **影响:**
    *   **后端:** `EnhancedScriptContext`, `InteractiveScriptExecutor`

**代码快照 (Git Commit):** `d76f43499fbfc644edb635eec12024550f37d5a7`


`[2025-08-01]`
#### **完成 (P0): 实现端到端的持久化交互执行机制**

**思考与决策:**
*   **目标:** 构建一个健壮的、端到端的交互式执行流程，这是整个项目的核心功能。该流程必须能够处理执行过程中的暂停、用户输入和恢复，并且交互状态必须持久化，以防止服务器重启导致执行流程丢失。
*   **决策路径:**
    1.  **架构先行:** 首先更新 `ARCHITECTURE_ANALYSIS.md`，明确了交互式执行的整体设计，包括数据库模型、后端状态机和前后端通信协议。这是为了确保所有后续开发都有一个清晰的蓝图。
    2.  **服务层抽象:** 创建 `ScriptInteractionService`，将交互状态的数据库操作（创建、完成）封装起来。此举遵循单一职责原则，让 `InteractiveScriptExecutor` 更专注于执行流程的编排，而不是数据存取的细节。
    3.  **重构执行器:** 修改 `InteractiveScriptExecutor`，将原有的内存中交互模型 (`ConcurrentHashMap`) 替换为依赖 `ScriptInteractionService` 的持久化模型。在请求用户输入时，现在会先在数据库中创建一条 `PENDING` 状态的记录。
    4.  **打通响应链路:** 修改 `InteractiveScriptExecutionController`，提供一个 `/api/user/interactive-execution/respond` 端点。这是用户响应的入口，它会调用执行器中的 `handleUserResponse` 方法，通过 `CompletableFuture` 唤醒被阻塞的执行线程。
    5.  **前端适配:** 最后，更新前端组件 `InteractiveExecutionPanel.vue`，使其能够正确处理来自 WebSocket 的交互请求，提取持久化的 `interactionId`，并调用新的 `/respond` API 发送用户响应。
*   **影响:**
    *   **后端:** `ScriptInteractionService`, `InteractiveScriptExecutor`, `InteractiveScriptExecutionController`
    *   **前端:** `InteractiveExecutionPanel.vue`
    *   **文档:** `ARCHITECTURE_ANALYSIS.md`

**代码快照 (Git Commit):** `e70e38b1cd8d5ab786ede765e6e47281ffa614a3`

---

## 4. 当前状态

*   **当前阶段:** 全部完成
*   **下一步行动:** 项目核心功能已全部完成。可以进行最终的集成测试，准备部署。
