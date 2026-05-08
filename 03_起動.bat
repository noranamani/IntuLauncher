@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

call .\02_インストール.bat
if errorlevel 1 exit /b 1

call :resolve_adb
if errorlevel 1 exit /b 1

echo [IntuLauncher] アプリを起動します。
"%ADB_PATH%" shell am start -n jp.co.cssservice.intulauncher/.MainActivity
if errorlevel 1 (
    echo [IntuLauncher] 起動に失敗しました。
    exit /b 1
)

echo [IntuLauncher] 起動が完了しました。
exit /b 0

:resolve_adb
set "ADB_PATH="
if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB_PATH=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB_PATH if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB_PATH=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not defined ADB_PATH if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB_PATH (
    echo [IntuLauncher] adb.exe が見つかりません。ANDROID_HOME または ANDROID_SDK_ROOT を設定してください。
    exit /b 1
)
exit /b 0
