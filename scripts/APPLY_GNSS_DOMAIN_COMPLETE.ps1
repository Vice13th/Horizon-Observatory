param(
    [string]$TargetRoot = ''
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root

if ([string]::IsNullOrWhiteSpace($TargetRoot)) {
    $targetRoot = $root
} elseif ([IO.Path]::IsPathRooted($TargetRoot)) {
    $targetRoot = (Resolve-Path $TargetRoot).Path
} else {
    $targetRoot = (Resolve-Path (Join-Path $root $TargetRoot)).Path
}
if (-not (Test-Path $targetRoot)) { throw "Target project not found: $targetRoot" }

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = Join-Path $targetRoot "_HORIZON_BACKUP_GNSS_COMPLETE_$stamp"
New-Item -ItemType Directory -Path $backup -Force | Out-Null

$files = @(
    'app\src\main\kotlin\horizon\observatory\domain\gnss\GnssEvidenceModels.kt',
    'app\src\main\kotlin\horizon\observatory\analysis\gnss\GnssCorrelationEngine.kt',
    'app\src\main\kotlin\horizon\observatory\analysis\SessionAnalysisEngine.kt',
    'app\src\main\kotlin\horizon\observatory\ui\MainActivity.kt',
    'app\src\main\kotlin\horizon\observatory\export\ExportEngine.kt',
    'app\src\test\kotlin\horizon\observatory\analysis\gnss\GnssCorrelationEngineTest.kt'
)

foreach ($relative in $files) {
    $source = Join-Path $root $relative
    $dest = Join-Path $targetRoot $relative
    $backupDest = Join-Path $backup $relative
    if (-not (Test-Path $source)) { throw "Missing source file in checkpoint: $source" }
    New-Item -ItemType Directory -Path (Split-Path $backupDest -Parent) -Force | Out-Null
    New-Item -ItemType Directory -Path (Split-Path $dest -Parent) -Force | Out-Null
    if (Test-Path $dest) { Copy-Item $dest $backupDest -Force }
    Copy-Item $source $dest -Force
}

Write-Host "Applied HORIZON GNSS domain completion source changes."
Write-Host "Backup: $backup"
Write-Host "Next gate: .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain"
