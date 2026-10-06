param(
    [string]$ProjectRoot = ''
)
$ErrorActionPreference = 'Stop'
$root = if ([string]::IsNullOrWhiteSpace($ProjectRoot)) { (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path } else { (Resolve-Path $ProjectRoot).Path }
Set-Location $root

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = Join-Path $root "_HORIZON_BACKUP_$stamp"
New-Item -ItemType Directory -Path $backup -Force | Out-Null

$files = @(
    @{ Source = "$env:USERPROFILE\Downloads\HORIZON_MainActivity_NEXT_PHASE.kt"; Dest = '.\app\src\main\kotlin\horizon\observatory\ui\MainActivity.kt' },
    @{ Source = "$env:USERPROFILE\Downloads\HORIZON_CellInfoSource_NEXT_PHASE.kt"; Dest = '.\app\src\main\kotlin\horizon\observatory\acquisition\cellular\CellInfoSource.kt' },
    @{ Source = "$env:USERPROFILE\Downloads\HORIZON_GnssObservationSource_NEXT_PHASE.kt"; Dest = '.\app\src\main\kotlin\horizon\observatory\acquisition\gnss\GnssObservationSource.kt' },
    @{ Source = "$env:USERPROFILE\Downloads\HORIZON_SessionAnalysisEngine_NEXT_PHASE.kt"; Dest = '.\app\src\main\kotlin\horizon\observatory\analysis\SessionAnalysisEngine.kt' },
    @{ Source = "$env:USERPROFILE\Downloads\HORIZON_CapabilityScanner_NEXT_PHASE.kt"; Dest = '.\app\src\main\kotlin\horizon\observatory\core\capability\CapabilityScanner.kt' },
    @{ Source = "$env:USERPROFILE\Downloads\HORIZON_SessionAnalysisEngineTest.kt"; Dest = '.\app\src\test\kotlin\horizon\observatory\analysis\SessionAnalysisEngineTest.kt' }
)

foreach ($f in $files) {
    $dest = Join-Path $root $f.Dest.TrimStart('.\')
    $destBackup = Join-Path $backup $f.Dest.TrimStart('.\')
    $destBackupDir = Split-Path $destBackup -Parent
    New-Item -ItemType Directory -Path $destBackupDir -Force | Out-Null
    if (Test-Path $dest) { Copy-Item $dest $destBackup -Force }
    if (-not (Test-Path $f.Source)) { throw "Missing source file: $($f.Source)" }
    New-Item -ItemType Directory -Path (Split-Path $dest -Parent) -Force | Out-Null
    Copy-Item $f.Source $dest -Force
}

Write-Host "Applied HORIZON next phase."
Write-Host "Backup: $backup"
Write-Host "Next: .\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain"
