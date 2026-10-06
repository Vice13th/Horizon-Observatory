$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = Join-Path $root "_HORIZON_BACKUP_GNSS_DOMAIN_$stamp"
New-Item -ItemType Directory -Path $backup -Force | Out-Null

$files = @(
    @{ Source = "$PSScriptRoot\..\app\src\main\kotlin\horizon\observatory\domain\gnss\GnssEvidenceModels.kt"; Dest = '.\app\src\main\kotlin\horizon\observatory\domain\gnss\GnssEvidenceModels.kt' },
    @{ Source = "$PSScriptRoot\..\app\src\main\kotlin\horizon\observatory\analysis\gnss\GnssCorrelationEngine.kt"; Dest = '.\app\src\main\kotlin\horizon\observatory\analysis\gnss\GnssCorrelationEngine.kt' },
    @{ Source = "$PSScriptRoot\..\app\src\test\kotlin\horizon\observatory\analysis\gnss\GnssCorrelationEngineTest.kt"; Dest = '.\app\src\test\kotlin\horizon\observatory\analysis\gnss\GnssCorrelationEngineTest.kt' }
)

foreach ($f in $files) {
    $dest = Join-Path $root $f.Dest.TrimStart('.\')
    $relative = $f.Dest.TrimStart('.\')
    $backupDest = Join-Path $backup $relative
    New-Item -ItemType Directory -Path (Split-Path $backupDest -Parent) -Force | Out-Null
    if (Test-Path $dest) { Copy-Item $dest $backupDest -Force }
    New-Item -ItemType Directory -Path (Split-Path $dest -Parent) -Force | Out-Null
    Copy-Item $f.Source $dest -Force
}

Write-Host "Applied HORIZON GNSS Domain Foundation."
Write-Host "Backup: $backup"
Write-Host "Next: .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain"
