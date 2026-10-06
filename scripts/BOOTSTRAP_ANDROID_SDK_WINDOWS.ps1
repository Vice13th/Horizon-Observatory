$ErrorActionPreference = 'Stop'

Write-Host '=== HORIZON ANDROID SDK SETUP ===' -ForegroundColor Cyan

$projectRoot = Split-Path $PSScriptRoot -Parent
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_HOME = $sdkRoot

function Refresh-ProcessPath {
    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    $machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $parts = @($env:Path, $userPath, $machinePath) |
        Where-Object { $_ } |
        ForEach-Object { $_ -split ';' } |
        Where-Object { $_ } |
        Select-Object -Unique
    $env:Path = ($parts -join ';')
}

function Resolve-SdkManager {
    Refresh-ProcessPath

    $candidates = @(
        (Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'),
        (Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.cmd'),
        (Join-Path $sdkRoot 'tools\bin\sdkmanager.bat')
    )

    $fromPath = Get-Command sdkmanager.bat -ErrorAction SilentlyContinue
    if ($fromPath) { $candidates += $fromPath.Source }
    $fromPathCmd = Get-Command sdkmanager.cmd -ErrorAction SilentlyContinue
    if ($fromPathCmd) { $candidates += $fromPathCmd.Source }

    foreach ($candidate in $candidates | Select-Object -Unique) {
        if ($candidate -and (Test-Path $candidate)) { return $candidate }
    }
    return $null
}

function Install-OfficialCommandLineTools {
    $archive = Join-Path $env:TEMP 'horizon-commandlinetools-win.zip'
    $extractRoot = Join-Path $env:TEMP 'horizon-commandlinetools-extract'
    $url = 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip'
    $expectedSha256 = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'

    if (-not (Test-Path $archive)) {
        Write-Host "Downloading official Android command-line tools: $url" -ForegroundColor DarkCyan
        try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}
        Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $archive
    }

    Write-Host 'Verifying command-line tools SHA-256...' -ForegroundColor DarkCyan
    $actual = (Get-FileHash -Algorithm SHA256 -Path $archive).Hash.ToLowerInvariant()
    if ($actual -ne $expectedSha256) {
        throw "Command-line tools checksum mismatch. Expected $expectedSha256 but received $actual."
    }

    if (Test-Path $extractRoot) { Remove-Item -Recurse -Force $extractRoot }
    New-Item -ItemType Directory -Force -Path $extractRoot | Out-Null
    Expand-Archive -Path $archive -DestinationPath $extractRoot -Force

    $sourceRoot = Join-Path $extractRoot 'cmdline-tools'
    if (-not (Test-Path (Join-Path $sourceRoot 'bin\sdkmanager.bat'))) {
        throw 'Official command-line tools archive did not contain cmdline-tools\bin\sdkmanager.bat.'
    }

    $targetRoot = Join-Path $sdkRoot 'cmdline-tools\latest'
    New-Item -ItemType Directory -Force -Path (Join-Path $sdkRoot 'cmdline-tools') | Out-Null
    if (Test-Path $targetRoot) { Remove-Item -Recurse -Force $targetRoot }
    Move-Item -Path $sourceRoot -Destination $targetRoot
    Remove-Item -Recurse -Force $extractRoot

    return (Join-Path $targetRoot 'bin\sdkmanager.bat')
}

New-Item -ItemType Directory -Force -Path $sdkRoot | Out-Null

$sdkManager = Resolve-SdkManager
if (-not $sdkManager) {
    Write-Host 'sdkmanager not found; using official Android Command-line Tools instead of Android CLI.' -ForegroundColor Yellow
    $sdkManager = Install-OfficialCommandLineTools
}

if (-not (Test-Path $sdkManager)) { throw "sdkmanager not found: $sdkManager" }

$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) { throw 'Java is required. Install JDK 17+ and ensure java.exe is on PATH.' }

Write-Host "Using sdkmanager: $sdkManager" -ForegroundColor DarkCyan
Write-Host "SDK root: $sdkRoot" -ForegroundColor DarkCyan

# Accept licenses non-interactively where possible.
$yesInput = (1..50 | ForEach-Object { 'y' }) -join "`n"
$yesInput | & $sdkManager --sdk_root=$sdkRoot --licenses | Out-Host
if ($LASTEXITCODE -ne 0) {
    Write-Warning "sdkmanager --licenses returned exit code $LASTEXITCODE; continuing to package installation."
}

Write-Host 'Installing API 36 platform, Build Tools 36.0.0 and Platform Tools...' -ForegroundColor DarkCyan
& $sdkManager --sdk_root=$sdkRoot 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'
if ($LASTEXITCODE -ne 0) {
    throw "sdkmanager package installation failed (exit code $LASTEXITCODE)."
}

$required = @(
    (Join-Path $sdkRoot 'platform-tools\adb.exe'),
    (Join-Path $sdkRoot 'platforms\android-36\android.jar'),
    (Join-Path $sdkRoot 'build-tools\36.0.0\aapt2.exe')
)
foreach ($path in $required) {
    if (-not (Test-Path $path)) { throw "Required SDK component is missing: $path" }
}

$sdkForProperties = $sdkRoot.Replace('\','/')
Set-Content -Path (Join-Path $projectRoot 'local.properties') -Value "sdk.dir=$sdkForProperties" -Encoding ASCII

Write-Host "SDK ready: $sdkRoot" -ForegroundColor Green
Write-Host 'Now run: powershell -ExecutionPolicy Bypass -File .\scripts\BUILD_WINDOWS.ps1' -ForegroundColor Green
