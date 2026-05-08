@echo off
setlocal
cd /d "%~dp0"

start "IntuLauncher Logcat" cmd /k ".\04_Logcat_IntuLauncher.bat"
call .\03_起動.bat
if errorlevel 1 exit /b 1

echo IntuLauncher: debug bootstrap completed
exit /b 0
