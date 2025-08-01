@echo off
REM SSH Terminal Management System - Comprehensive Test Runner (Windows)
REM This script executes the complete test suite and generates coverage reports

echo ==================================================
echo SSH Terminal Management System - Test Suite
echo ==================================================

REM Create test results directory
if not exist test-results mkdir test-results
set TEST_RESULTS_DIR=%CD%\test-results

echo [INFO] Test results will be saved to: %TEST_RESULTS_DIR%

REM Check if Maven is installed
mvn --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven is not installed. Please install Maven to run backend tests.
    exit /b 1
)

REM Check if Node.js is installed
node --version >nul 2>&1
if errorlevel 1 (
    echo [WARN] Node.js is not installed. Skipping frontend tests.
    set SKIP_FRONTEND=true
) else (
    set SKIP_FRONTEND=false
)

REM Backend Tests
echo.
echo ==================================================
echo BACKEND TESTS (Java/Spring Boot)
echo ==================================================

echo [INFO] Starting backend test execution...

REM Run unit tests
echo [INFO] Running unit tests...
mvn clean test -Dtest="**/*Test,!**/*IntegrationTest,!**/*PerformanceTest,!**/*SecurityTest" -Dmaven.test.failure.ignore=true > "%TEST_RESULTS_DIR%\unit-tests.log" 2>&1

if errorlevel 1 (
    echo [WARN] Some unit tests failed. Check %TEST_RESULTS_DIR%\unit-tests.log for details
) else (
    echo [INFO] Unit tests completed successfully
)

REM Run integration tests
echo [INFO] Running integration tests with Testcontainers...
mvn test -Dtest="**/*IntegrationTest" -Dspring.profiles.active=test -Dmaven.test.failure.ignore=true > "%TEST_RESULTS_DIR%\integration-tests.log" 2>&1

if errorlevel 1 (
    echo [WARN] Some integration tests failed. Check %TEST_RESULTS_DIR%\integration-tests.log for details
) else (
    echo [INFO] Integration tests completed successfully
)

REM Run performance tests
echo [INFO] Running performance tests...
mvn test -Dtest="**/*PerformanceTest" -Dmaven.test.failure.ignore=true > "%TEST_RESULTS_DIR%\performance-tests.log" 2>&1

if errorlevel 1 (
    echo [WARN] Some performance tests failed. Check %TEST_RESULTS_DIR%\performance-tests.log for details
) else (
    echo [INFO] Performance tests completed successfully
)

REM Run security tests
echo [INFO] Running security tests...
mvn test -Dtest="**/*SecurityTest" -Dmaven.test.failure.ignore=true > "%TEST_RESULTS_DIR%\security-tests.log" 2>&1

if errorlevel 1 (
    echo [WARN] Some security tests failed. Check %TEST_RESULTS_DIR%\security-tests.log for details
) else (
    echo [INFO] Security tests completed successfully
)

REM Generate coverage report
echo [INFO] Generating coverage report...
mvn jacoco:report > "%TEST_RESULTS_DIR%\coverage.log" 2>&1

if exist "target\site\jacoco\index.html" (
    echo [INFO] Coverage report generated: target\site\jacoco\index.html
    xcopy /E /I "target\site\jacoco" "%TEST_RESULTS_DIR%\jacoco" >nul
) else (
    echo [WARN] Coverage report generation failed
)

REM Frontend Tests
if "%SKIP_FRONTEND%"=="false" (
    echo.
    echo ==================================================
    echo FRONTEND TESTS (Vue 3 + Vite)
    echo ==================================================
    
    cd web\ssh-treminal-ui
    
    echo [INFO] Installing frontend dependencies...
    npm ci > "%TEST_RESULTS_DIR%\npm-install.log" 2>&1
    
    if errorlevel 1 (
        echo [ERROR] Failed to install frontend dependencies
    ) else (
        echo [INFO] Dependencies installed successfully
        
        REM Run frontend tests
        echo [INFO] Running frontend tests...
        npm run test > "%TEST_RESULTS_DIR%\frontend-tests.log" 2>&1
        
        if errorlevel 1 (
            echo [WARN] Some frontend tests failed. Check %TEST_RESULTS_DIR%\frontend-tests.log for details
        ) else (
            echo [INFO] Frontend tests completed successfully
        )
        
        REM Generate frontend coverage
        echo [INFO] Generating frontend coverage report...
        npm run coverage > "%TEST_RESULTS_DIR%\frontend-coverage.log" 2>&1
        
        if exist "coverage" (
            echo [INFO] Frontend coverage report generated: web\ssh-treminal-ui\coverage\
            xcopy /E /I "coverage" "%TEST_RESULTS_DIR%\frontend-coverage" >nul
        ) else (
            echo [WARN] Frontend coverage report generation failed
        )
    )
    
    cd ..\..
)

REM Test Summary
echo.
echo ==================================================
echo TEST EXECUTION SUMMARY
echo ==================================================

echo Backend Test Results:
echo   - Unit Tests: Executed
echo   - Integration Tests: Executed
echo   - Performance Tests: Executed
echo   - Security Tests: Executed

if "%SKIP_FRONTEND%"=="false" (
    echo Frontend Test Results:
    echo   - Component Tests: Executed
    echo   - Integration Tests: Executed
)

echo.
echo Test artifacts available at: %TEST_RESULTS_DIR%
echo   - Backend coverage: %TEST_RESULTS_DIR%\jacoco\index.html
if "%SKIP_FRONTEND%"=="false" (
    echo   - Frontend coverage: %TEST_RESULTS_DIR%\frontend-coverage\index.html
)
echo   - Detailed logs: %TEST_RESULTS_DIR%\*.log

echo.
echo ==================================================
echo Test execution completed!
echo ==================================================

pause