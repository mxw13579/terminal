@echo off
echo 🔧 修复 InteractiveScriptExecutor 类型转换错误
echo =============================================
echo.

cd /d "%~dp0"

echo ✅ 已修复的问题:
echo.
echo 第117行错误: java.lang.String无法转换为ScriptExecutionSession
echo.
echo 原因: createInteraction方法调用参数类型不匹配
echo   错误: session.getId(), script.getId()  (String, Long)
echo   正确: session, script                   (ScriptExecutionSession, AtomicScript)
echo.
echo 🔧 修复内容:
echo   - 更改了 interactionService.createInteraction() 的调用参数
echo   - 传递对象而不是ID
echo.

echo 🚀 测试编译:
echo.

if exist "mvnw.cmd" (
    echo 使用 Maven Wrapper 编译...
    .\mvnw.cmd compile -q
    if %ERRORLEVEL% equ 0 (
        echo ✅ 编译成功！类型转换错误已修复
    ) else (
        echo ❌ 还有其他编译错误，请检查控制台输出
        echo.
        echo 重新编译显示详细错误:
        .\mvnw.cmd compile
    )
) else (
    where mvn >nul 2>&1
    if %ERRORLEVEL% equ 0 (
        echo 使用系统 Maven 编译...
        mvn compile -q
        if %ERRORLEVEL% equ 0 (
            echo ✅ 编译成功！类型转换错误已修复
        ) else (
            echo ❌ 还有其他编译错误，请检查控制台输出
            echo.
            echo 重新编译显示详细错误:
            mvn compile
        )
    ) else (
        echo ⚠️  无法找到 Maven，请使用 IDE 编译测试
        echo.
        echo IDE 编译方法:
        echo   IntelliJ IDEA: Build → Build Project (Ctrl+F9)
        echo   Eclipse: Project → Build Project
        echo   VS Code: 使用 Java Extension Pack
    )
)

echo.
echo 📝 修复说明:
echo   InteractiveScriptExecutor.java 第117行的参数类型已修正
echo   现在 createInteraction 方法接收正确的对象类型
echo.

echo 按任意键退出...
pause >nul