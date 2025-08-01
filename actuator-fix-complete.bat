@echo off
echo ========================================
echo   修复 Spring Boot Actuator 编译错误
echo ========================================
echo.

echo ✅ 已完成临时修复:
echo.
echo 1. 已修改的文件:
echo    - SshConnectionHealthIndicator.java
echo    - ScriptExecutionHealthIndicator.java
echo.
echo 2. 修复内容:
echo    - 注释掉了 Actuator 相关的导入
echo    - 注释掉了 HealthIndicator 接口实现
echo    - 添加了临时的状态检查方法
echo.
echo 🔧 现在项目应该可以编译了！
echo.

echo 📋 永久解决方案:
echo.
echo 要恢复完整的健康检查功能，请:
echo.
echo 1. 运行以下命令重新下载 Actuator 依赖:
if exist "mvnw.cmd" (
    echo    .\mvnw.cmd dependency:resolve
    echo    .\mvnw.cmd clean compile
) else (
    echo    mvn dependency:resolve  
    echo    mvn clean compile
)
echo.
echo 2. 或使用 IDE 重新加载项目:
echo    IntelliJ IDEA: 右键 pom.xml → Maven → Reload Project
echo    Eclipse: 右键项目 → Maven → Reload Projects
echo.
echo 3. 依赖修复后，取消注释健康检查器中的代码:
echo    - 取消注释 import 语句
echo    - 取消注释 implements HealthIndicator
echo    - 取消注释 health() 方法
echo.

echo 🚀 测试编译:
echo.
if exist "mvnw.cmd" (
    echo 正在使用 Maven Wrapper 测试编译...
    .\mvnw.cmd compile -q
    if %ERRORLEVEL% equ 0 (
        echo ✅ 编译成功！Actuator 错误已修复
    ) else (
        echo ❌ 编译仍有问题，请检查其他错误
    )
) else (
    where mvn >nul 2>&1
    if %ERRORLEVEL% equ 0 (
        echo 正在使用系统 Maven 测试编译...
        mvn compile -q
        if %ERRORLEVEL% equ 0 (
            echo ✅ 编译成功！Actuator 错误已修复
        ) else (
            echo ❌ 编译仍有问题，请检查其他错误
        )
    ) else (
        echo ⚠️  无法找到 Maven，请使用 IDE 编译测试
    )
)

echo.
echo 📝 注意: 健康检查功能暂时不可用，但不影响主要功能运行
echo.
echo 按任意键退出...
pause >nul