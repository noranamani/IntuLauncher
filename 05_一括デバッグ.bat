@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

start "IntuLauncher Logcat" cmd /k ".\04_Logcat_IntuLauncher.bat"
call .\03_起動.bat
if errorlevel 1 exit /b 1

echo [IntuLauncher] 一括デバッグの初期化が完了しました。
exit /b 0
