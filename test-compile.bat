@echo off
echo ============================================
echo Testing Compilation
echo ============================================
echo.

echo Attempting Maven compilation...
mvnw.cmd compile -DskipTests -q

if %ERRORLEVEL% equ 0 (
    echo.
    echo ✅ SUCCESS: Compilation completed without errors!
    echo.
    echo Key components verified:
    echo - ScriptExecutionController (with sa-token)
    echo - RefactoredScriptExecutionService
    echo - DTO classes
    echo - Entity enums
    echo.
) else (
    echo.
    echo ❌ COMPILATION FAILED
    echo.
    echo Running detailed compilation to show errors...
    mvnw.cmd compile -DskipTests
)

echo ============================================
pause