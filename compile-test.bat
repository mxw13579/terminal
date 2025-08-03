@echo off
chcp 65001 >nul
echo Starting Maven compilation test...
echo.

echo [1/3] Cleaning previous build...
call mvnw.cmd clean -q
if %ERRORLEVEL% neq 0 (
    echo ERROR: Clean failed
    exit /b 1
)

echo [2/3] Compiling sources...
call mvnw.cmd compile -DskipTests
if %ERRORLEVEL% equ 0 (
    echo.
    echo ✅ SUCCESS: Compilation completed successfully!
    echo.
    echo All refactored components compiled:
    echo - ScriptExecutionController with sa-token integration
    echo - RefactoredScriptExecutionService
    echo - 4-type script classification system
    echo - Built-in script registry
    echo - DTO classes and models
    echo.
) else (
    echo.
    echo ❌ COMPILATION FAILED
    echo Check the error messages above for details.
    echo.
)

echo [3/3] Test complete.
pause