$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

Write-Host '=== HORIZON BUILD ===' -ForegroundColor Cyan

function Resolve-HorizonAndroidSdk {
    $candidates = New-Object System.Collections.Generic.List[string]
    foreach ($value in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)) {
        if ($value -and -not $candidates.Contains($value)) { $candidates.Add($value) }
    }

    $localProperties = Join-Path $PWD 'local.properties'
    if (Test-Path $localProperties) {
        $line = Get-Content $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($line) {
            $candidate = ($line -replace '^sdk\.dir=','').Trim()
            $candidate = $candidate -replace '\\\\','\\'
            if ($candidate) { $candidates.Add($candidate) }
        }
    }

    foreach ($candidate in @(
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
        (Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk')
    )) {
        if ($candidate -and -not $candidates.Contains($candidate)) { $candidates.Add($candidate) }
    }

    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate 'platforms\android-36')) { return (Resolve-Path $candidate).Path }
    }

    $defaultSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    $bootstrap = Join-Path $PSScriptRoot 'BOOTSTRAP_ANDROID_SDK_WINDOWS.ps1'
    if (Test-Path $bootstrap) {
        Write-Host 'Android SDK API 36 not found; bootstrapping with official sdkmanager...' -ForegroundColor Yellow
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $bootstrap
        if ($LASTEXITCODE -eq 0 -and (Test-Path (Join-Path $defaultSdk 'platforms\android-36\android.jar'))) {
            return (Resolve-Path $defaultSdk).Path
        }
    }

    throw @"
Android SDK API 36 was not found.

Use the bundled BOOTSTRAP_ANDROID_SDK_WINDOWS.ps1 script, which installs the
official Android Command-line Tools and uses sdkmanager for API 36, Build Tools
and Platform Tools.
"@
}

$sdkPath = Resolve-HorizonAndroidSdk
$env:ANDROID_SDK_ROOT = $sdkPath
$env:ANDROID_HOME = $sdkPath
$sdkForProperties = $sdkPath.Replace('\','/')
Set-Content -Path (Join-Path $PWD 'local.properties') -Value "sdk.dir=$sdkForProperties" -Encoding ASCII
Write-Host "Android SDK: $sdkPath" -ForegroundColor Green


$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) { throw 'Java is required. Install JDK 17+ and ensure java.exe is on PATH.' }

$expectedGradleVersion = '8.11.1'
$wrapperJar = Join-Path $PWD 'gradle\wrapper\gradle-wrapper.jar'

# The clean-build path deliberately avoids an arbitrary system Gradle. When the
# wrapper JAR is absent from a source archive, provision the exact pinned Gradle
# distribution and use it to regenerate the standard wrapper files locally.
if (-not (Test-Path $wrapperJar)) {
    $bootstrap = Join-Path $PSScriptRoot 'BOOTSTRAP_GRADLE_WINDOWS.ps1'
    if (-not (Test-Path $bootstrap)) { throw "Missing Gradle bootstrap script: $bootstrap" }

    Write-Host "Wrapper JAR missing; provisioning pinned Gradle $expectedGradleVersion..." -ForegroundColor Yellow
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $bootstrap
    if ($LASTEXITCODE -ne 0) { throw "Gradle bootstrap failed (exit code $LASTEXITCODE)." }

    $bootstrappedGradle = Join-Path $PWD ".gradle-bootstrap\gradle-$expectedGradleVersion\bin\gradle.bat"
    if (-not (Test-Path $bootstrappedGradle)) { throw "Pinned Gradle launcher not found: $bootstrappedGradle" }

    & $bootstrappedGradle wrapper --gradle-version $expectedGradleVersion
    if ($LASTEXITCODE -ne 0) { throw "Pinned Gradle failed to regenerate the standard wrapper (exit code $LASTEXITCODE)." }
}

if (-not (Test-Path '.\gradlew.bat')) { throw 'gradlew.bat not found.' }
if (-not (Test-Path $wrapperJar)) { throw 'gradle-wrapper.jar is still missing after wrapper generation.' }

& .\gradlew.bat --version
if ($LASTEXITCODE -ne 0) { throw "Gradle Wrapper version check failed (exit code $LASTEXITCODE)." }

& .\gradlew.bat clean testDebugUnitTest assembleDebug --stacktrace
if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }

Write-Host 'BUILD + UNIT TESTS PASSED' -ForegroundColor Green
