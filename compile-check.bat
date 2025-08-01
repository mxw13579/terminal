@echo off
echo Checking Maven installation...
where mvn >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Maven not found in PATH. Please install Maven or use your IDE to compile.
    echo.
    echo You can download Maven from: https://maven.apache.org/download.cgi
    echo Or compile using your IDE (IntelliJ IDEA, Eclipse, etc.)
    pause
    exit /b 1
)

echo Maven found. Compiling project...
cd /d "%~dp0"
mvn clean compile -DskipTests

if %ERRORLEVEL% equ 0 (
    echo.
    echo ✅ Compilation successful!
) else (
    echo.
    echo ❌ Compilation failed. Check the error messages above.
)

pause