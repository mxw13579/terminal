@echo off
echo ============================================
echo SSH Terminal Management System - Run Script
echo ============================================
echo.

echo Step 1: Cleaning Maven target directory...
call mvnw.cmd clean
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Maven clean failed!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Step 2: Compiling project...
call mvnw.cmd compile
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Maven compile failed!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Step 3: Running Spring Boot application...
call mvnw.cmd spring-boot:run

pause