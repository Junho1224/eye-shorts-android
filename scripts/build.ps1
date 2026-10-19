param([string[]]$Tasks = @('testDebugUnitTest', 'lintDebug', 'assembleDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.build-tools'
New-Item -ItemType Directory -Path $toolRoot -Force | Out-Null

if (-not $env:JAVA_HOME) {
    $candidate = Join-Path $env:USERPROFILE 'Documents\Codex\.toolchains\jdk-17'
    if (Test-Path -LiteralPath (Join-Path $candidate 'bin\java.exe')) { $env:JAVA_HOME = $candidate }
}
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw 'JDK 17 이상을 설치하고 JAVA_HOME을 설정해주세요.'
}
$sdkRoot = $env:ANDROID_HOME
if (-not $sdkRoot) { $sdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $sdkRoot) {
    foreach ($candidate in @((Join-Path $env:LOCALAPPDATA 'Android\Sdk'), (Join-Path $env:USERPROFILE 'Documents\Codex\.toolchains\android-sdk'))) {
        if (Test-Path -LiteralPath (Join-Path $candidate 'platforms\android-36')) { $sdkRoot = $candidate; break }
    }
}
if (-not $sdkRoot) { throw 'Android SDK 36과 Build Tools 36.0.0을 설치하고 ANDROID_HOME을 설정해주세요.' }
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_USER_HOME = Join-Path $toolRoot 'android-home'
$env:GRADLE_USER_HOME = Join-Path ([IO.Path]::GetTempPath()) 'eyeshorts-gradle'
[IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), ('sdk.dir=' + $sdkRoot.Replace('\','/').Replace(':','\:') + "`n"), [Text.UTF8Encoding]::new($false))
$keystore = Join-Path $toolRoot 'debug.keystore'
if (-not (Test-Path -LiteralPath $keystore)) {
    & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -keystore $keystore -storepass android -alias androiddebugkey -keypass android -dname 'CN=EyeShorts Debug,O=Development,C=KR' -keyalg RSA -keysize 2048 -validity 10000
    if ($LASTEXITCODE -ne 0) { throw '디버그 서명 키 생성 실패' }
}
$gradle = Join-Path $projectRoot 'gradlew.bat'
$cached = Get-ChildItem -Path (Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-9.1.0-*\*\gradle-9.1.0\bin\gradle.bat') -ErrorAction SilentlyContinue | Select-Object -First 1
if ($cached) { $gradle = $cached.FullName }
Push-Location $projectRoot
try {
    & $gradle @Tasks --console=plain --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed ($LASTEXITCODE)" }
    if ($Tasks -contains 'assembleDebug') {
        $dist = Join-Path $projectRoot 'dist'
        New-Item -ItemType Directory -Path $dist -Force | Out-Null
        $metadata = Get-Content -LiteralPath (Join-Path $projectRoot 'app\build\outputs\apk\debug\output-metadata.json') -Raw | ConvertFrom-Json
        $apkName = 'EyeShorts-' + $metadata.elements[0].versionName + '-debug.apk'
        Copy-Item -LiteralPath (Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk') -Destination (Join-Path $dist $apkName) -Force
        Write-Output (Join-Path $dist $apkName)
    }
} finally { Pop-Location }
