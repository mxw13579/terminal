# Phase 2 架构统一 - 遗留系统清理计划

## 📋 清理概览

随着 Phase 2 架构统一的完成，系统已全面迁移到标准化的 STOMP 消息架构。此文档制定了遗留 WebSocket 组件的清理计划和迁移验证步骤。

## 🗂️ 待清理的遗留组件

### 1. LegacyWebSocketConfig.java
- **文件路径**: `src/main/java/com/fufu/terminal/config/LegacyWebSocketConfig.java`
- **当前状态**: 已标记 `@Deprecated`，仅在 `legacy-websocket` 和 `test` profile 下激活
- **依赖组件**: `SshTerminalWebSocketHandler.java`
- **清理计划**: 在稳定运行 2 周后移除

### 2. SshTerminalWebSocketHandler.java  
- **文件路径**: `src/main/java/com/fufu/terminal/handler/SshTerminalWebSocketHandler.java`
- **当前状态**: 被 `LegacyWebSocketConfig` 引用
- **功能**: 原始 WebSocket 处理逻辑
- **清理计划**: 与 `LegacyWebSocketConfig` 同步移除

## ✅ 已完成的架构统一

### STOMP 目标地址标准化
所有服务已迁移到 `StompDestinationConfig` 定义的统一地址：

| 服务类型 | 旧地址 | 新地址 | 迁移状态 |
|---------|--------|--------|----------|
| 终端输出 | `/queue/terminal/output-user{sessionId}` | `StompDestinationConfig.USER_TERMINAL` | ✅ 完成 |
| 错误消息 | `/queue/errors` | `StompDestinationConfig.USER_ERRORS` | ✅ 完成 |
| 系统监控 | `/queue/monitor` | `StompDestinationConfig.USER_MONITOR` | ✅ 完成 |  
| SillyTavern | `/queue/sillytavern/*` | `StompDestinationConfig.USER_SILLYTAVERN` | ✅ 完成 |

### 统一命令执行 API
`SshCommandService` 已提供三种标准化 API 模式：

1. **executeOrThrow()** - 异常抛出模式，用于快速失败场景
2. **execute()** - 完整结果模式，返回退出码和完整输出
3. **stream()** - 流式模式，支持实时回调（待实现）

**政策引擎功能**:
- ✅ 超时控制 (默认 30 秒)
- ✅ 输出限制 (stdout 1MB, stderr 256KB)
- ✅ 危险命令拦截
- ✅ 频率限制 (100ms 间隔)
- ✅ 审计日志和敏感信息脱敏

### 已迁移的服务
- ✅ `StompSessionManager.java` - 终端输出和错误消息
- ✅ `StompMonitoringService.java` - 系统监控数据推送  
- ✅ `InteractiveDeploymentService.java` - SillyTavern 部署状态

## 🔄 清理执行计划

### Phase 2.1: 监控和验证期 (当前 - 2 周)
1. **监控遗留组件使用情况**
   - 确认生产环境不激活 `legacy-websocket` profile
   - 监控是否有客户端尝试连接旧 WebSocket 端点
   - 收集新 STOMP 架构的稳定性数据

2. **功能验证检查清单**
   - ✅ 终端连接和输入输出正常
   - ✅ SFTP 文件传输功能正常  
   - ✅ 系统监控数据推送正常
   - ✅ 错误处理和用户隔离正常
   - ✅ SillyTavern 部署功能正常

### Phase 2.2: 遗留组件移除 (2 周后)
1. **移除遗留配置类**
   ```bash
   git rm src/main/java/com/fufu/terminal/config/LegacyWebSocketConfig.java
   ```

2. **移除遗留处理器**  
   ```bash
   git rm src/main/java/com/fufu/terminal/handler/SshTerminalWebSocketHandler.java
   ```

3. **清理相关测试**
   - 移除依赖遗留组件的测试用例
   - 确保所有测试使用新的 STOMP 架构

4. **更新文档**
   - 更新 README.md 中的架构说明
   - 移除遗留 WebSocket 相关的配置文档

## 🛡️ 回滚计划

如果在监控期发现重大问题，可以通过以下方式快速回滚：

1. **激活遗留 profile**
   ```properties
   spring.profiles.active=legacy-websocket
   ```

2. **前端客户端回滚**
   - 恢复原始 WebSocket 连接代码
   - 临时禁用 STOMP 客户端

3. **问题修复后重新迁移**
   - 分析问题根因
   - 修复 STOMP 架构问题  
   - 重新启动清理计划

## 📊 成功指标

Phase 2 架构统一被认为成功完成当：

1. **稳定性指标**
   - STOMP 连接成功率 > 99.9%
   - 消息传输无丢失或重复
   - 用户会话隔离 100% 有效

2. **性能指标**  
   - 消息传输延迟 < 50ms (P95)
   - 并发连接数支持与原架构相同
   - 内存使用无显著增长

3. **兼容性指标**
   - 所有现有功能正常工作
   - 前端客户端无需额外修改
   - 第三方集成无中断

## 🔍 监控检查点

### 每日检查
- [ ] 确认没有客户端连接旧 WebSocket 端点
- [ ] 检查 STOMP 连接错误日志
- [ ] 验证用户会话隔离正常

### 每周检查  
- [ ] 性能指标对比分析
- [ ] 用户反馈收集和问题分析
- [ ] 系统稳定性综合评估

### 清理前最终检查
- [ ] 所有功能测试通过
- [ ] 性能测试满足基线要求
- [ ] 安全测试确认用户隔离有效
- [ ] 团队成员确认可以执行清理

---

**清理负责人**: 开发团队  
**计划执行时间**: 2025年8月 - 2025年9月  
**风险评级**: 低 (已有完整回滚计划)

此计划确保遗留系统清理的安全性和可控性，同时保持系统功能的连续性和稳定性。