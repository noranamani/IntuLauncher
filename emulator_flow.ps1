param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("build", "install", "launch", "debug", "logcat")]
    [string]$Mode
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$AppPackage = "jp.co.cssservice.intulauncher"
$HomeActivity = "$AppPackage/.MainActivity"
$AvdName = if ($env:INTULAUNCHER_AVD) { $env:INTULAUNCHER_AVD } else { "EdgeGestureN_Pixel9_API35" }
$EmulatorSerial = if ($env:INTULAUNCHER_EMULATOR_SERIAL) { $env:INTULAUNCHER_EMULATOR_SERIAL } else { "emulator-5554" }

function Write-Step([string]$Message) {
    Write-Host "IntuLauncher: $Message"
}

function Get-SdkRoot {
    if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) { return $env:ANDROID_SDK_ROOT }
    if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) { return $env:ANDROID_HOME }
    $localSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $localSdk) { return $localSdk }
    throw "Android SDK was not found."
}

function Get-ToolPaths {
    $sdkRoot = Get-SdkRoot
    $adbPath = Join-Path $sdkRoot "platform-tools\adb.exe"
    $emulatorPath = Join-Path $sdkRoot "emulator\emulator.exe"
    if (-not (Test-Path $adbPath)) { throw "adb.exe was not found." }
    if (-not (Test-Path $emulatorPath)) { throw "emulator.exe was not found." }
    return @{
        SdkRoot = $sdkRoot
        AdbPath = $adbPath
        EmulatorPath = $emulatorPath
    }
}

function Invoke-GradleBuild {
    Write-Step "start debug build"
    $appBuild = Join-Path $ProjectRoot "app\build"
    Push-Location $ProjectRoot
    try {
        & ".\gradlew.bat" --stop | Out-Null
    }
    finally {
        Pop-Location
    }
    if (Test-Path $appBuild) {
        try {
            Write-Step "remove previous app\build"
            Remove-Item -LiteralPath $appBuild -Recurse -Force
        }
        catch {
            Write-Step "skip full cleanup because build artifacts are locked"
        }
    }
    Push-Location $ProjectRoot
    try {
        & ".\gradlew.bat" assembleDebug --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed." }
    }
    finally {
        Pop-Location
    }
    Write-Step "build completed"
}

function Get-RunningDeviceSerial {
    param([string]$AdbPath)
    $lines = & $AdbPath devices
    foreach ($line in $lines) {
        if ($line -match '^(emulator-\d+)\s+device$') {
            return $matches[1]
        }
    }
    return $null
}

function Ensure-Emulator {
    param(
        [string]$AdbPath,
        [string]$EmulatorPath
    )
    $runningSerial = Get-RunningDeviceSerial -AdbPath $AdbPath
    if (-not $runningSerial) {
        Write-Step "start emulator $AvdName"
        Start-Process -FilePath $EmulatorPath -ArgumentList @("-avd", $AvdName)
        $runningSerial = $EmulatorSerial
    }
    Write-Step "wait for emulator boot"
    & $AdbPath -s $runningSerial wait-for-device | Out-Null
    do {
        Start-Sleep -Seconds 2
        $bootValue = (& $AdbPath -s $runningSerial shell getprop sys.boot_completed 2>$null).Trim()
    } while ($bootValue -ne "1")
    Write-Step "emulator boot completed"
    return $runningSerial
}

function Install-Apk {
    param(
        [string]$AdbPath,
        [string]$Serial
    )
    Write-Step "install debug apk"
    $apkPath = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
    & $AdbPath -s $Serial install -r $apkPath
    if ($LASTEXITCODE -ne 0) { throw "APK install failed." }
    Write-Step "install completed"
}

function Set-HomeAndLaunch {
    param(
        [string]$AdbPath,
        [string]$Serial
    )
    Write-Step "force-stop previous launcher instance"
    & $AdbPath -s $Serial shell am force-stop $AppPackage
    Write-Step "set default home activity"
    & $AdbPath -s $Serial shell cmd package set-home-activity --user 0 $HomeActivity
    if ($LASTEXITCODE -ne 0) { throw "Failed to set default home activity." }
    Write-Step "press home"
    & $AdbPath -s $Serial shell input keyevent KEYCODE_HOME | Out-Null
    Start-Sleep -Seconds 5
    Write-Step "home launched and widget setup window elapsed"
}

function Watch-Logcat {
    param(
        [string]$AdbPath,
        [string]$Serial
    )
    Write-Step "watch logcat. Press Ctrl+C to stop."
    & $AdbPath -s $Serial logcat -s IntuLauncher ActivityManager AndroidRuntime WorkManager
}

$toolPaths = Get-ToolPaths

switch ($Mode) {
    "build" {
        Invoke-GradleBuild
    }
    "install" {
        $serial = Ensure-Emulator -AdbPath $toolPaths.AdbPath -EmulatorPath $toolPaths.EmulatorPath
        Invoke-GradleBuild
        Install-Apk -AdbPath $toolPaths.AdbPath -Serial $serial
    }
    "launch" {
        $serial = Ensure-Emulator -AdbPath $toolPaths.AdbPath -EmulatorPath $toolPaths.EmulatorPath
        Invoke-GradleBuild
        Install-Apk -AdbPath $toolPaths.AdbPath -Serial $serial
        Set-HomeAndLaunch -AdbPath $toolPaths.AdbPath -Serial $serial
    }
    "debug" {
        $serial = Ensure-Emulator -AdbPath $toolPaths.AdbPath -EmulatorPath $toolPaths.EmulatorPath
        Start-Process powershell -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $ProjectRoot "emulator_flow.ps1"), "-Mode", "logcat")
        Invoke-GradleBuild
        Install-Apk -AdbPath $toolPaths.AdbPath -Serial $serial
        Set-HomeAndLaunch -AdbPath $toolPaths.AdbPath -Serial $serial
        Write-Step "debug bootstrap completed"
    }
    "logcat" {
        $serial = Ensure-Emulator -AdbPath $toolPaths.AdbPath -EmulatorPath $toolPaths.EmulatorPath
        Watch-Logcat -AdbPath $toolPaths.AdbPath -Serial $serial
    }
}
