$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

$version = '8.11.1'
$root = Join-Path $PWD '.gradle-bootstrap'
$zip = Join-Path $root "gradle-$version-bin.zip"
# Avoid PowerShell automatic variable $HOME; use a distinct project-local cache variable.
$gradleHome = Join-Path $root "gradle-$version"
$exe = Join-Path $gradleHome 'bin\gradle.bat'
$url = "https://services.gradle.org/distributions/gradle-$version-bin.zip"
$expectedSha256 = 'f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6'

New-Item -ItemType Directory -Force -Path $root | Out-Null
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}

if (-not (Test-Path $exe)) {
    if (-not (Test-Path $zip)) {
        Write-Host "Downloading Gradle $version..." -ForegroundColor Cyan
        Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $zip
    }
    $actualSha256 = (Get-FileHash -Algorithm SHA256 -Path $zip).Hash.ToLowerInvariant()
    if ($actualSha256 -ne $expectedSha256) {
        throw "Gradle $version distribution checksum mismatch. Expected $expectedSha256 but received $actualSha256."
    }

    $tmp = Join-Path $root '_extract'
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    Expand-Archive -Path $zip -DestinationPath $tmp -Force
    $src = Join-Path $tmp "gradle-$version"
    if (-not (Test-Path (Join-Path $src 'bin\gradle.bat'))) { throw 'Invalid Gradle distribution.' }
    if (Test-Path $gradleHome) { Remove-Item -Recurse -Force $gradleHome }
    Move-Item $src $gradleHome
    Remove-Item -Recurse -Force $tmp
}

& $exe --version
if ($LASTEXITCODE -ne 0) { throw "Gradle bootstrap validation failed with exit code $LASTEXITCODE." }
Write-Host "Gradle $version ready at $exe" -ForegroundColor Green
exit 0
