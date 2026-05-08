@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

call :resolve_adb
if errorlevel 1 exit /b 1

echo [IntuLauncher] IntuLauncher 関連のログを監視します。終了は Ctrl+C です。
"%ADB_PATH%" logcat -s IntuLauncher ActivityManager AndroidRuntime WorkManager
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
