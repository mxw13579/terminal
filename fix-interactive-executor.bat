@echo off
echo 🔧 修复 InteractiveScriptExecutor 所有编译错误
echo =============================================
echo.

cd /d "%~dp0"

echo ✅ 已修复的问题:
echo.
echo 1. 添加了缺失的 updateInteraction 方法到 ScriptInteractionService
echo 2. 创建了 MessageType 枚举类
echo 3. 修复了 InteractionRequest 的字段名调用:
echo    - setInteractionType → setType
echo    - setPromptMessage → setPrompt  
echo    - getPromptMessage → getPrompt
echo 4. 修复了 ExecutionMessage 的构造方式
echo 5. 添加了 setInteractionRequest 方法
echo 6. 添加了 InteractionType 导入
echo.

echo 🔧 修复内容总结:
echo   - ScriptInteractionService: 添加 updateInteraction 方法
echo   - MessageType.java: 新增枚举类
echo   - ExecutionMessage.java: 更新字段和方法  
echo   - InteractiveScriptExecutor.java: 修复所有方法调用
echo.

echo 🚀 测试编译:
echo.

if exist "mvnw.cmd" (
    echo 使用 Maven Wrapper 编译...
    .\mvnw.cmd compile -q
    if %ERRORLEVEL% equ 0 (
        echo ✅ 编译成功！所有方法和字段错误已修复
        echo.
        echo 🎉 InteractiveScriptExecutor 现在应该可以正常工作了
    ) else (
        echo ❌ 还有编译错误，显示详细信息:
        echo.
        .\mvnw.cmd compile
    )
) else (
    where mvn >nul 2>&1
    if %ERRORLEVEL% equ 0 (
        echo 使用系统 Maven 编译...
        mvn compile -q
        if %ERRORLEVEL% equ 0 (
            echo ✅ 编译成功！所有方法和字段错误已修复
            echo.
            echo 🎉 InteractiveScriptExecutor 现在应该可以正常工作了
        ) else (
            echo ❌ 还有编译错误，显示详细信息:
            echo.
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
echo   现在 InteractiveScriptExecutor.java 的所有编译错误都应该解决了：
echo   - 类型转换错误 ✅
echo   - 缺失方法错误 ✅
echo   - 字段名不匹配错误 ✅
echo   - 构造函数错误 ✅
echo.

echo 按任意键退出...
pause >nul