param([string]$Serial)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$latestApk = Get-ChildItem -Path (Join-Path $projectRoot 'dist\EyeShorts-*-debug.apk') -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $latestApk) { throw '먼저 scripts/build.ps1로 APK를 빌드해주세요.' }
$apk = $latestApk.FullName
$sdkRoot = $env:ANDROID_HOME
if (-not $sdkRoot) { $sdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $sdkRoot) {
    foreach ($candidate in @((Join-Path $env:LOCALAPPDATA 'Android\Sdk'), (Join-Path $env:USERPROFILE 'Documents\Codex\.toolchains\android-sdk'))) {
        if (Test-Path -LiteralPath (Join-Path $candidate 'platform-tools\adb.exe')) { $sdkRoot = $candidate; break }
    }
}
if (-not $sdkRoot) { throw 'ANDROID_HOME을 Android SDK 경로로 설정해주세요.' }
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$env:ANDROID_USER_HOME = Join-Path $projectRoot '.build-tools\android-home'
if (-not $Serial) {
    $devices = @(& $adb -P 5038 devices | Where-Object { $_ -match '^\S+\s+device$' })
    if ($devices.Count -ne 1) { throw 'USB 디버깅을 허용한 기기 한 대를 연결하거나 -Serial을 지정해주세요.' }
    $Serial = ($devices[0] -split '\s+')[0]
}
& $adb -P 5038 -s $Serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'APK 설치에 실패했습니다.' }
& $adb -P 5038 -s $Serial shell am start -n 'com.eyeshorts.app/.MainActivity'
if ($LASTEXITCODE -ne 0) { throw '앱 실행에 실패했습니다.' }
