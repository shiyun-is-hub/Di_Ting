# manage.ps1
param (
    [switch]$Build,
    [switch]$Run,
    [switch]$Install,
    [switch]$Clean,
    [switch]$All
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8
$env:LC_ALL = "C.UTF-8"

# 动态推导 SDK 和 JDK 路径
$SDK_PATH = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Android\Android Studio\jbr" }
$ADB = "$SDK_PATH\platform-tools\adb.exe"

if (-not (Test-Path $SDK_PATH)) { Write-Error "❌ 未找到 Android SDK"; pause; exit }
if (-not (Test-Path $JAVA_HOME)) { Write-Error "❌ 未找到 JDK"; pause; exit }

$env:JAVA_HOME = $JAVA_HOME
$env:ANDROID_HOME = $SDK_PATH

function Invoke-Build {
    Write-Host "--- [ PHASE: BUILD ] ---" -ForegroundColor Cyan
    if (-not (Test-Path ".\gradle\wrapper\gradle-wrapper.jar")) { Write-Error "❌ 缺失 gradle-wrapper.jar"; pause; exit }
    
    cmd.exe /c "chcp 65001 >nul & .\gradlew.bat assembleDebug -q --console plain"
    if ($LASTEXITCODE -eq 0) { Write-Host "`n✅ Build Successful!" -ForegroundColor Green }
    else { Write-Error "❌ Build Failed!"; pause; exit }
}

function Invoke-Install {
    if (-not (Test-Path $ADB)) { Write-Error "❌ 未找到 adb.exe"; pause; exit }
    Write-Host "--- [ PHASE: INSTALL ] ---" -ForegroundColor Cyan

    $ApkFile = Get-ChildItem -Path ".\app\build\outputs\apk\debug\*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $ApkFile) { Write-Error "❌ 未找到 APK，请先执行 -Build"; pause; exit }

    Write-Host ">> Uninstalling old version..." -ForegroundColor Gray
    & $ADB uninstall com.diting.recorder 2>$null | Out-Null

    Write-Host ">> Installing $($ApkFile.Name)..." -ForegroundColor Gray
    & $ADB install -r "$($ApkFile.FullName)"
    if ($LASTEXITCODE -eq 0) { Write-Host "`n✅ Install Successful!" -ForegroundColor Green }
    else { Write-Error "❌ Install Failed!"; pause; exit }
}

function Invoke-Run {
    if (-not (Test-Path $ADB)) { Write-Error "❌ 未找到 adb.exe"; pause; exit }
    Write-Host "--- [ PHASE: DEPLOY & RUN ] ---" -ForegroundColor Cyan
    
    $MainFile = Get-ChildItem -Path ".\app\src" -Recurse -Include *.java, *.kt | Where-Object { (Get-Content $_.FullName) -match "main\s*\(" } | Select-Object -First 1
    if (-not $MainFile) { Write-Error "❌ 无法定位 main 函数！"; pause; exit }

    $Package = (Get-Content $MainFile.FullName | Select-String -Pattern "^package\s+([\w\.]+)").Matches.Groups[1].Value
    $TargetClass = "$Package.$($MainFile.BaseName)"
    $ApkFile = Get-ChildItem -Path ".\app\build\outputs\apk\debug\*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $ApkFile) { Write-Error "❌ 未找到 APK，请先执行 -Build"; pause; exit }

    $RemoteApk = "/data/local/tmp/diting_recorder.apk"
    Write-Host ">> Deploying $($ApkFile.Name)..." -ForegroundColor Gray
    & $ADB shell "pkill -f 'com.diting.recorder.core.RecordingOrchestrator'" 2>$null
    & $ADB push $ApkFile.FullName $RemoteApk | Out-Null

    $LogDir = ".\logs"
    if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }
    $Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $ConsoleOutFile = "$LogDir\console_$Timestamp.txt"

    Write-Host ">> RUNNING: $TargetClass" -ForegroundColor Green
    & $ADB logcat -c
    & $ADB shell "export CLASSPATH=$RemoteApk; app_process -Xmx512m / $TargetClass" 2>&1 | Tee-Object -FilePath $ConsoleOutFile
    
    & $ADB logcat -d -v threadtime ZR.Core:V ZR.Display:V ZR.GL:V *:S | Out-File "$LogDir\logcat_zero_$Timestamp.txt" -Encoding UTF8
    Write-Host ">> DONE! 日志保存至 $LogDir" -ForegroundColor Green
}

if ($Clean) {
    Write-Host "🧹 清理构建缓存..." -ForegroundColor Yellow
    Remove-Item -Recurse -Force build, app\build, .gradle-tmp, logs -ErrorAction SilentlyContinue
    if (Test-Path "clean.py") { python clean.py }
}

if ($Build -or $All) { Invoke-Build }
if ($Install -or $All) { Invoke-Install }
if ($Run -or $All) { Invoke-Run }

if (-not ($Build -or $Run -or $Install -or $Clean -or $All)) {
    Write-Host "用法: .\manage.ps1 -Build | -Install | -Run | -All | -Clean"
}
