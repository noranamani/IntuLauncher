@echo off
setlocal
cd /d "%~dp0"
powershell -ExecutionPolicy Bypass -File "%~dp0emulator_flow.ps1" -Mode install
exit /b %errorlevel%
