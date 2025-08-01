@echo off
setlocal enabledelayedexpansion

echo ========================================
echo   SSH Terminal - 编译错误修复工具
echo ========================================
echo.

cd /d "%~dp0"

echo 🔍 正在诊断编译问题...
echo.

REM 检查JDK版本
echo 检查Java版本:
java -version 2>&1 | findstr "version" | findstr "17"
if %ERRORLEVEL% neq 0 (
    echo ⚠️  警告: 可能不是JDK 17，请确认你使用的是JDK 17
    java -version
    echo.
)

REM 尝试使用Maven Wrapper
echo 🚀 尝试使用Maven Wrapper编译...
if exist "mvnw.cmd" (
    echo 找到Maven Wrapper，开始编译...
    .\mvnw.cmd clean compile -DskipTests
    if !ERRORLEVEL! equ 0 (
        echo.
        echo ✅ 编译成功！所有错误已解决。
        goto success
    ) else (
        echo ❌ Maven Wrapper编译失败
    )
) else (
    echo ❌ Maven Wrapper不可用
)

echo.
echo 🔧 尝试使用系统Maven...
where mvn >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo 找到系统Maven，开始编译...
    mvn clean compile -DskipTests
    if !ERRORLEVEL! equ 0 (
        echo.
        echo ✅ 编译成功！所有错误已解决。
        goto success
    ) else (
        echo ❌ 系统Maven编译失败
    )
) else (
    echo ❌ 系统Maven不可用
)

echo.
echo 📋 手动解决方案:
echo.
echo 请按以下步骤操作:
echo.
echo 1. 确认使用JDK 17:
echo    set JAVA_HOME=你的JDK17路径
echo    set PATH=%%JAVA_HOME%%\bin;%%PATH%%
echo.
echo 2. 如果使用IDE (推荐):
echo    IntelliJ IDEA:
echo      - 右键pom.xml → Maven → Reload Project  
echo      - Build → Rebuild Project
echo.
echo    Eclipse:  
echo      - 右键项目 → Maven → Reload Projects
echo      - Project → Clean → 选择项目 → Clean
echo.
echo    VS Code:
echo      - Ctrl+Shift+P → Java: Clean Workspace
echo      - 重新打开项目
echo.
echo 3. 如果有Maven命令行:
echo    mvn dependency:resolve
echo    mvn clean compile
echo.
echo 4. 检查网络连接，确保可以下载Maven依赖
echo.
goto end

:success
echo.
echo 🎉 恭喜！编译错误已解决
echo.  
echo 现在这些文件应该可以正常编译:
echo   ✅ SafeSshCommand.java
echo   ✅ SafeString.java  
echo   ✅ SafeStringValidator.java
echo   ✅ SshConnectionHealthIndicator.java
echo   ✅ GlobalExceptionHandler.java
echo.
echo 你现在可以正常运行项目了！
echo.

:end
echo 按任意键退出...
pause >nul