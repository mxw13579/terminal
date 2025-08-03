@echo off
echo Testing compilation...
mvnw.cmd clean compile -DskipTests -q
if %ERRORLEVEL% equ 0 (
    echo SUCCESS: Compilation completed without errors
) else (
    echo ERROR: Compilation failed
)