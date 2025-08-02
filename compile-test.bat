@echo off
echo Cleaning Maven target directory...
call mvnw.cmd clean

echo.
echo Compiling project...
call mvnw.cmd compile

echo.
echo Compilation complete. Check for errors above.
pause