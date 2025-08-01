@echo off
echo 🔧 修复 Spring Boot Actuator 依赖错误
echo ==========================================
echo.

cd /d "%~dp0"

echo 第1步: 检查当前问题...
echo 错误: org.springframework.boot.actuator.health 包不存在
echo.

echo 第2步: 强制重新下载 Actuator 依赖...
echo.

REM 尝试使用 Maven Wrapper
if exist "mvnw.cmd" (
    echo 使用 Maven Wrapper...
    echo 清理本地 Actuator 依赖:
    .\mvnw.cmd dependency:purge-local-repository -DmanualInclude="org.springframework.boot:spring-boot-starter-actuator" -q
    
    echo 重新下载依赖:
    .\mvnw.cmd dependency:resolve -q
    
    echo 编译项目:
    .\mvnw.cmd clean compile -DskipTests
    
    if %ERRORLEVEL% equ 0 (
        echo.
        echo ✅ 成功！Actuator 错误已修复
        goto success
    )
)

REM 尝试使用系统 Maven
where mvn >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo 使用系统 Maven...
    echo 清理本地 Actuator 依赖:
    mvn dependency:purge-local-repository -DmanualInclude="org.springframework.boot:spring-boot-starter-actuator" -q
    
    echo 重新下载依赖:
    mvn dependency:resolve -q
    
    echo 编译项目:
    mvn clean compile -DskipTests
    
    if %ERRORLEVEL% equ 0 (
        echo.
        echo ✅ 成功！Actuator 错误已修复
        goto success
    )
)

echo.
echo ❌ 自动修复失败，请手动执行以下步骤:
echo.
echo 1. 使用 IDE 重新加载项目:
echo    IntelliJ IDEA: 右键 pom.xml → Maven → Reload Project
echo    Eclipse: 右键项目 → Maven → Reload Projects
echo.
echo 2. 或手动执行 Maven 命令:
echo    mvn dependency:resolve
echo    mvn clean compile
echo.
echo 3. 检查网络连接，确保可以访问 Maven 中央仓库
echo.
goto end

:success
echo.
echo 🎉 修复完成！
echo.
echo SshConnectionHealthIndicator.java 现在应该可以正常编译了
echo 包含以下类现在应该可用:
echo   ✅ org.springframework.boot.actuator.health.Health
echo   ✅ org.springframework.boot.actuator.health.HealthIndicator
echo.

:end
echo.
echo 按任意键退出...
pause >nul