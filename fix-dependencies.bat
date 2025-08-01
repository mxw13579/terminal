@echo off
echo 🔧 SSH Terminal System - Dependency Fix Script
echo ================================================
echo.

echo Step 1: Checking current directory...
cd /d "%~dp0"
echo Current directory: %CD%

echo.
echo Step 2: Cleaning Maven cache and target directory...
if exist "target" (
    rmdir /s /q target
    echo ✅ Cleaned target directory
) else (
    echo ℹ️  Target directory doesn't exist
)

echo.
echo Step 3: Checking Maven installation...
where mvn >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ❌ Maven not found in PATH!
    echo.
    echo Please install Maven or use one of these alternatives:
    echo 1. Download Maven from: https://maven.apache.org/download.cgi
    echo 2. Use your IDE to refresh and rebuild the project
    echo 3. Use Maven wrapper if available: ./mvnw clean compile
    echo.
    echo If using IntelliJ IDEA:
    echo   - Right-click on pom.xml → Maven → Reload Project
    echo   - Build → Rebuild Project
    echo.
    pause
    exit /b 1
)

echo ✅ Maven found at:
where mvn

echo.
echo Step 4: Refreshing Maven dependencies...
echo Running: mvn dependency:purge-local-repository -DmanualInclude="org.springframework.boot:spring-boot-starter-validation"
mvn dependency:purge-local-repository -DmanualInclude="org.springframework.boot:spring-boot-starter-validation" -q

echo.
echo Step 5: Downloading dependencies...
echo Running: mvn dependency:resolve
mvn dependency:resolve -q

echo.
echo Step 6: Compiling project...
echo Running: mvn clean compile
mvn clean compile

if %ERRORLEVEL% equ 0 (
    echo.
    echo ✅ SUCCESS! All compilation errors should be resolved.
    echo.
    echo The following dependencies are now available:
    echo   - jakarta.validation.Constraint
    echo   - jakarta.validation.Payload  
    echo   - org.springframework.boot.actuator.health.Health
    echo   - org.springframework.boot.actuator.health.HealthIndicator
    echo.
) else (
    echo.
    echo ❌ COMPILATION FAILED!
    echo.
    echo If you still see errors, try:
    echo 1. Refresh your IDE project
    echo 2. Check if you're using JDK 17
    echo 3. Clear IDE caches and restart
    echo.
)

echo.
echo Press any key to continue...
pause >nul