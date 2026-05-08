@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

echo [IntuLauncher] デバッグビルドを開始します。
call .\gradlew.bat assembleDebug --no-daemon --console=plain
if errorlevel 1 (
    echo [IntuLauncher] ビルドに失敗しました。
    exit /b 1
)

echo [IntuLauncher] ビルドが完了しました。
exit /b 0
