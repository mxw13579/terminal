# 🔧 编译错误解决指南

## 当前报错分析

你遇到的编译错误表明以下包无法找到：
- `jakarta.validation` (验证注解相关)
- `org.springframework.boot.actuator.health` (健康检查相关)

## 解决方案步骤

### 第一步：确认依赖已添加 ✅
我已经在 `pom.xml` 中添加了必需的依赖：

```xml
<!-- Jakarta Validation for Spring Boot 3.x -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- Spring Boot Actuator for monitoring -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 第二步：更新Maven编译器插件 ✅
更新到支持JDK 17的版本：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
        <source>17</source>
        <target>17</target>
        <encoding>UTF-8</encoding>
        <release>17</release>
    </configuration>
</plugin>
```

## 🚀 立即执行的解决方案

### 方案1：使用提供的脚本 (推荐)
```bash
# 运行依赖修复脚本
.\fix-dependencies.bat
```

### 方案2：手动Maven命令
```bash
# 清理并重新下载依赖
mvn clean
mvn dependency:resolve
mvn compile
```

### 方案3：如果没有Maven，使用IDE
**IntelliJ IDEA:**
1. 右键点击 `pom.xml` → Maven → Reload Project
2. Build → Rebuild Project
3. 如果还有问题：File → Invalidate Caches and Restart

**Eclipse:**
1. 右键项目 → Maven → Reload Projects
2. Project → Clean → Clean all projects
3. Project → Build Project

**VS Code:**
1. 打开命令面板 (Ctrl+Shift+P)
2. 输入 "Java: Clean Workspace"
3. 重新打开项目

## 🔍 如果问题仍然存在

### 检查清单：
- [ ] 确认使用的是JDK 17
- [ ] 网络连接正常(Maven需要下载依赖)
- [ ] Maven本地仓库没有损坏
- [ ] IDE已刷新项目

### 强制重新下载依赖：
```bash
# 删除本地Maven仓库中的相关依赖
mvn dependency:purge-local-repository -DmanualInclude="org.springframework.boot:spring-boot-starter-validation,org.springframework.boot:spring-boot-starter-actuator"

# 重新下载
mvn dependency:resolve
```

## 📁 相关文件
修复后这些文件应该可以正常编译：
- `SafeSshCommand.java` - SSH命令验证注解
- `SafeString.java` - 字符串安全验证注解  
- `SafeStringValidator.java` - 验证器实现
- `SshConnectionHealthIndicator.java` - SSH连接健康检查
- `GlobalExceptionHandler.java` - 异常处理器

## ⚡ 快速测试
编译成功后，运行以下命令验证：
```bash
mvn clean compile -DskipTests
```

如果成功，你应该看到 "BUILD SUCCESS" 消息。