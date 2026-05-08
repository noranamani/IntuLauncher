@echo off
setlocal
cd /d "%~dp0"

start "IntuLauncher Logcat" cmd /k ".\logcat_intu.bat"
call .\launch_debug.bat
if errorlevel 1 exit /b 1

echo IntuLauncher: debug bootstrap completed
exit /b 0
