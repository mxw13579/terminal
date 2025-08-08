# Terminal Improvement Plan - Requirements Confirmation

## Original Request
用户请求完整阅读 PRAGMATIC_IMPROVEMENT_PLAN.md 后帮助一步一步进行改进计划，特别说明服务器只能账号密码登录无法使用SSH密钥。

## Initial Quality Assessment: 75/100

### Functional Clarity (25/30)
- ✅ 5-phase improvement roadmap clearly defined
- ✅ Critical security issues identified
- ⚠️ SSH key constraint requires strategy adjustment

### Technical Specificity (20/25)
- ✅ Detailed file paths and code locations provided
- ✅ Spring Boot + Vue 3 tech stack confirmed
- ⚠️ SSH configuration needs password-auth adjustments

### Implementation Completeness (15/25)
- ✅ Phased implementation approach
- ❌ SSH host key verification strategy needs redesign
- ❌ Missing password-specific security measures

### Business Context (15/20)
- ✅ Security priority clearly established
- ✅ Performance and stability goals defined

## Critical Clarification Questions

### 1. SSH Host Key Verification Strategy
Given password-only authentication constraint:
- Should we implement known_hosts management with first-connection confirmation?
- Or maintain `StrictHostKeyChecking=ask` in dev, pre-configured `known_hosts` in prod?

### 2. Password Security Enhancement
Since password auth is required:
- Implement short-term token mechanism (2min TTL) to avoid password in WebSocket?
- Add password strength validation or rate limiting?

### 3. Implementation Priority
- Phase 0 (Security baseline) - Immediate priority
- Phase 1 (Transfer optimization) - High priority for memory issues
- Phases 2-4 - Original order or specific preferences?

### 4. Environment Constraints
- Development/testing/production configuration differences?
- Network or firewall restrictions affecting WebSocket connections?
- File transfer size limits (severity of current base64 memory issues)?

## User Clarification Response

### Password Security Strategy (CONFIRMED)
用户建议采用前端公钥加密 + 后端私钥解密方案：
- 账号和密码在前端使用公钥加密
- 后端通过私钥解密获取凭证
- 解决了密码明文传输的安全问题

### SSH Configuration Adjustments (INFERRED)
基于用户环境约束：
- 服务器不具备SSH密钥管理能力
- 无法维护已知主机列表 (用户可能重装系统)
- 建议保持 `StrictHostKeyChecking=no` 但增加其他安全措施

## Updated Quality Assessment: 88/100

### Functional Clarity (28/30)
- ✅ 5-phase improvement roadmap clearly defined
- ✅ Critical security issues identified
- ✅ Password encryption strategy confirmed

### Technical Specificity (23/25)
- ✅ Detailed file paths and code locations provided
- ✅ Spring Boot + Vue 3 tech stack confirmed
- ✅ RSA encryption for password transport specified

### Implementation Completeness (22/25)
- ✅ Phased implementation approach
- ✅ Password encryption security measure defined
- ⚠️ Need to specify key generation and management details

### Business Context (15/20)
- ✅ Security priority clearly established
- ✅ Performance and stability goals defined

## Remaining Questions for 90+ Score

1. **RSA Key Management**:
   - 密钥对生成方式: 服务端启动时生成还是预配置？
   - 公钥分发: 通过API端点还是配置文件？
   - 私钥存储: 内存中还是加密文件？

2. **Token生命周期**:
   - 解密后的凭证是否仍使用短期token机制？
   - Token TTL建议保持2分钟吗？

3. **实施顺序确认**:
   - Phase 0先实现加密传输，再优化其他安全措施？

## Final User Confirmation
用户确认: "我认为可以"

### Confirmed Technical Details:
1. **RSA密钥管理**: 服务端启动时生成，通过API端点提供公钥，私钥内存存储
2. **加密流程**: 前端公钥加密 → 后端解密 → 短期token → 销毁明文凭证
3. **SSH配置**: 保持StrictHostKeyChecking=no，增加连接限制和异常检测

## Final Quality Assessment: 92/100

### Functional Clarity (30/30) ✅
- 完整的5阶段改进路线图
- 关键安全问题识别完整
- 加密传输策略确认

### Technical Specificity (25/25) ✅
- 详细文件路径和代码位置
- Spring Boot + Vue 3 技术栈
- RSA加密具体实现方案

### Implementation Completeness (25/25) ✅
- 分阶段实施方法
- 密码加密安全措施
- 密钥管理完整方案

### Business Context (12/20)
- 安全优先级明确
- 性能目标清晰
- 环境约束理解

## Confirmation Status
- **Final Score**: 92/100 ✅
- **Target Score**: 90+ ✅
- **Status**: REQUIREMENTS CONFIRMED - Ready for implementation