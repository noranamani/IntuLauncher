@echo off
setlocal
cd /d "%~dp0"

echo IntuLauncher: start debug build
if exist ".\app\build" (
    echo IntuLauncher: remove previous app\build
    rmdir /s /q ".\app\build"
)
call .\gradlew.bat assembleDebug --no-daemon --console=plain
if errorlevel 1 (
    echo IntuLauncher: build failed
    exit /b 1
)

echo IntuLauncher: build completed
exit /b 0
