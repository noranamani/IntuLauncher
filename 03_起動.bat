@echo off
setlocal
cd /d "%~dp0"

call .\02_インストール.bat
if errorlevel 1 exit /b 1

call :resolve_adb
if errorlevel 1 exit /b 1

echo IntuLauncher: start app
"%ADB_PATH%" shell am start -n jp.co.cssservice.intulauncher/.MainActivity
if errorlevel 1 (
    echo IntuLauncher: app start failed
    exit /b 1
)

echo IntuLauncher: app started
exit /b 0

:resolve_adb
set "ADB_PATH="
if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB_PATH=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB_PATH if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB_PATH=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not defined ADB_PATH if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB_PATH (
    echo IntuLauncher: adb.exe was not found. Set ANDROID_HOME or ANDROID_SDK_ROOT.
    exit /b 1
)
exit /b 0
