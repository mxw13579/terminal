# Phase 0 Security Baseline Test Suite

## 概览

本测试套件为Terminal改进计划Phase 0安全基线实现提供全面的功能验证。测试覆盖了从RSA加密、令牌管理到STOMP认证的完整安全流程，确保87%质量分数的安全实现能够在生产环境中可靠运行。

## 测试架构

### 后端测试 (Java + Spring Boot + JUnit 5)

```
src/test/java/com/fufu/terminal/
├── security/
│   ├── CryptoServiceTest.java              # RSA加密服务测试
│   ├── TokenVaultTest.java                 # 令牌保险库测试
│   └── Phase0SecurityTestSuite.java        # 测试套件总览
├── controller/
│   └── SecurityControllerTest.java         # 安全控制器API测试
├── config/
│   └── StompAuthenticationInterceptorTest.java # STOMP认证拦截器测试
└── integration/
    └── Phase0SecurityIntegrationTest.java  # 端到端集成测试
```

### 前端测试 (JavaScript + Jest)

```
web/ssh-treminal-ui/tests/
├── crypto.test.js                          # 前端RSA加密服务测试
├── auth.test.js                           # 前端认证服务测试
├── package.json                           # 测试配置和依赖
└── setup.js                               # Jest测试环境设置
```

## 测试覆盖范围

### 1. RSA加密安全测试

**CryptoServiceTest.java** - 后端RSA加密服务
- ✅ 密钥生成和初始化
- ✅ 公钥获取和格式验证
- ✅ 凭据解密功能
- ✅ 加密解密往返一致性
- ✅ 并发安全性
- ✅ 错误处理和边界条件

**crypto.test.js** - 前端RSA加密服务
- ✅ 浏览器兼容性检查
- ✅ 公钥获取和缓存机制
- ✅ 凭据加密功能
- ✅ 输入验证和错误处理
- ✅ Base64编解码工具

### 2. 令牌管理测试

**TokenVaultTest.java** - 令牌保险库
- ✅ 凭据存储和检索
- ✅ TTL过期机制
- ✅ 一次性使用特性
- ✅ 令牌验证逻辑
- ✅ 自动清理机制
- ✅ 并发安全操作
- ✅ 存储容量管理

**auth.test.js** - 前端认证服务
- ✅ 会话令牌获取
- ✅ 令牌缓存和恢复
- ✅ 令牌验证和过期处理
- ✅ 连接重试机制
- ✅ 错误处理和用户友好提示

### 3. API安全测试

**SecurityControllerTest.java** - REST API端点
- ✅ 公钥获取API (`/api/security/public-key`)
- ✅ 令牌创建API (`/api/security/session/token`)
- ✅ 令牌验证API (`/api/security/token/validate`)
- ✅ 保险库统计API (`/api/security/vault/stats`)
- ✅ CORS跨域支持
- ✅ 输入验证和安全过滤
- ✅ 错误响应和状态码

### 4. WebSocket认证测试

**StompAuthenticationInterceptorTest.java** - STOMP拦截器
- ✅ Bearer令牌认证
- ✅ SSH连接管理
- ✅ 会话生命周期
- ✅ 连接清理机制
- ✅ 并发连接处理
- ✅ 环境配置支持

### 5. 端到端集成测试

**Phase0SecurityIntegrationTest.java** - 完整安全流程
- ✅ 公钥获取 → 凭据加密 → 令牌创建 → 验证流程
- ✅ 数据一致性验证
- ✅ 安全性验证（防数据泄露、时效性、一次性使用）
- ✅ 错误场景处理
- ✅ 并发性能测试
- ✅ 环境配置验证

## 关键测试场景

### 安全性验证
- **加密强度**: 验证RSA-2048+OAEP加密无法被轻易破解
- **数据保护**: 确认敏感凭据在传输和存储中得到充分保护
- **令牌安全**: 验证UUID v4令牌的唯一性和不可预测性
- **一次性使用**: 确认令牌使用后立即失效
- **TTL机制**: 验证120秒超时自动清理

### 错误处理
- **网络异常**: 模拟网络中断、超时等场景
- **输入验证**: 测试恶意输入、格式错误、长度超限
- **解密失败**: 验证错误密钥、损坏数据的处理
- **存储异常**: 测试令牌存储空间不足、IO错误

### 并发安全
- **多线程访问**: 验证ConcurrentHashMap的线程安全
- **并发加解密**: 测试RSA操作的并发安全性
- **令牌竞态**: 验证令牌创建、验证、清理的原子性

### 性能要求
- **加密性能**: RSA加密操作应在100ms内完成
- **令牌操作**: 令牌创建、验证应在10ms内完成
- **内存使用**: 令牌保险库内存占用应在合理范围内
- **并发处理**: 支持至少100个并发连接

## 测试执行

### 快速开始

```bash
# 执行完整测试套件
./run-security-tests.sh

# 仅运行后端测试
./run-security-tests.sh --backend-only

# 仅运行前端测试
./run-security-tests.sh --frontend-only

# 生成覆盖率报告
./run-security-tests.sh --coverage

# CI环境运行
./run-security-tests.sh --ci
```

### Maven执行（后端）

```bash
# 运行所有安全测试
mvn test -Dtest="CryptoServiceTest,TokenVaultTest,SecurityControllerTest,StompAuthenticationInterceptorTest,Phase0SecurityIntegrationTest"

# 运行特定测试类
mvn test -Dtest=CryptoServiceTest

# 生成覆盖率报告
mvn test jacoco:report
```

### Jest执行（前端）

```bash
cd web/ssh-treminal-ui/tests
npm install
npm test

# 监视模式
npm run test:watch

# 覆盖率报告
npm run test:coverage
```

## 覆盖率目标

### 后端覆盖率要求
- **CryptoService**: 95%+ (行覆盖率, 分支覆盖率)
- **TokenVault**: 95%+ (行覆盖率, 分支覆盖率)
- **SecurityController**: 90%+ (行覆盖率, 分支覆盖率)
- **StompAuthenticationInterceptor**: 85%+ (行覆盖率, 分支覆盖率)

### 前端覆盖率要求
- **crypto.js**: 95%+ (函数覆盖率, 分支覆盖率)
- **auth.js**: 95%+ (函数覆盖率, 分支覆盖率)

## 测试环境配置

### 后端环境
- Spring Profile: `test`
- 数据库: H2 内存数据库
- 日志级别: DEBUG
- 严格主机检查: false (测试环境)

### 前端环境
- 测试框架: Jest 29.5+
- 环境: jsdom
- Mock: Web Crypto API, fetch, sessionStorage
- Babel转换: ES6+ → CommonJS

## 持续集成

### GitHub Actions配置示例

```yaml
name: Security Tests
on: [push, pull_request]

jobs:
  security-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Setup JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Setup Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '18'
      - name: Run Security Tests
        run: ./run-security-tests.sh --ci --coverage
      - name: Upload Coverage
        uses: codecov/codecov-action@v3
```

## 故障排除

### 常见问题

1. **RSA密钥生成失败**
   - 检查Java版本和加密库支持
   - 验证系统熵源可用性

2. **前端加密测试失败**
   - 确认Node.js版本支持Web Crypto API mock
   - 检查Jest配置和babel转换

3. **STOMP连接测试失败**
   - 验证测试用SSH服务器可达性
   - 检查防火墙和网络配置

4. **并发测试不稳定**
   - 增加测试超时时间
   - 检查系统资源限制

### 性能调优

- **JVM参数**: `-Xmx2g -XX:+UseG1GC`
- **测试并行度**: `mvn test -T 4`
- **Jest工作进程**: `jest --maxWorkers=4`

## 安全合规

本测试套件验证以下安全标准的合规性：

- **OWASP Web应用安全**: 输入验证、加密传输、会话管理
- **NIST密码学标准**: RSA-2048, SHA-256, OAEP填充
- **企业安全基线**: 令牌管理、审计日志、错误处理

## 维护指南

### 测试更新
- 新增安全功能时，必须添加对应测试用例
- 安全配置变更时，更新测试环境配置
- 定期review测试覆盖率，确保关键路径被覆盖

### 版本兼容性
- Java 17+, Spring Boot 3.0+
- Node.js 18+, Jest 29+
- 浏览器: Chrome 90+, Firefox 88+, Safari 14+

---

**测试套件版本**: 1.0.0  
**最后更新**: 2025-01-08  
**维护者**: Terminal Security Team