param(
    [switch]$InstallMissingSdk
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Resolve-SdkRoot {
    $candidates = New-Object System.Collections.Generic.List[string]
    foreach ($name in @('ANDROID_SDK_ROOT','ANDROID_HOME')) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if (-not [string]::IsNullOrWhiteSpace($value)) { $candidates.Add($value) }
    }
    if ($env:LOCALAPPDATA) { $candidates.Add((Join-Path $env:LOCALAPPDATA 'Android\Sdk')) }

    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Container)) {
            $platform = Join-Path $candidate 'platforms\android-36\android.jar'
            if (Test-Path -LiteralPath $platform -PathType Leaf) { return $candidate }
        }
    }
    return $null
}

$sdk = Resolve-SdkRoot
if (-not $sdk -and $InstallMissingSdk) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $ProjectRoot 'scripts\BOOTSTRAP_ANDROID_SDK_WINDOWS.ps1')
    if ($LASTEXITCODE -ne 0) { throw "Android SDK bootstrap failed with exit code $LASTEXITCODE." }
    $sdk = Resolve-SdkRoot
}
if (-not $sdk) {
    throw 'Android SDK API 36 was not found. Set ANDROID_SDK_ROOT/ANDROID_HOME or run this script with -InstallMissingSdk.'
}

$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_HOME = $sdk
$sdkForProperties = $sdk.Replace('\','/')
Set-Content -LiteralPath (Join-Path $ProjectRoot 'local.properties') -Value "sdk.dir=$sdkForProperties" -Encoding ASCII

Write-Host "SDK=$sdk" -ForegroundColor Green
Write-Host 'local.properties created for this machine only.' -ForegroundColor Green
Write-Host 'This file is intentionally not part of source control/package delivery.' -ForegroundColor DarkCyan
