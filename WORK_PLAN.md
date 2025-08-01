# SSH 终端脚本管理系统 - 工作计划与决策日志

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
*   [▶️] **P1: 上下文与条件执行** `(正在进行)`
*   [🔲] **P2: 数据模型重构 (脚本与分组)**
*   [🔲] **P3: 聚合脚本构建器 (支持参数化)**
*   [🔲] **P4: 用户端首页重构与体验优化**

---

## 3. 详细工作与决策日志 (Action & Decision Log)

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

**代码快照 (Git Commit):** `(将在下一步生成)`

---

## 4. 当前状态

*   **当前阶段:** P1 - 上下文与条件执行
*   **下一步行动:** 增强 `EnhancedScriptContext` 使其支持在聚合脚本的完整生命周期内持久化，确保一个原子脚本写入的变量，可以被后续的脚本读取和使用。
