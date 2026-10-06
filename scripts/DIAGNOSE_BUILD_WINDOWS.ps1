$ErrorActionPreference = 'Continue'
Set-Location (Join-Path $PSScriptRoot '..')
$log = Join-Path $PWD 'BUILD_DIAGNOSTIC.log'
if (Test-Path $log) { Remove-Item -Force $log }
Write-Host '=== HORIZON BUILD DIAGNOSTIC ===' -ForegroundColor Cyan
& .\gradlew.bat :app:testDebugUnitTest --stacktrace --no-daemon *>&1 | Tee-Object -FilePath $log
$code = $LASTEXITCODE
Write-Host "`n=== RESULT ===" -ForegroundColor Yellow
if ($code -eq 0) {
    Write-Host 'testDebugUnitTest: PASS' -ForegroundColor Green
} else {
    Write-Host "testDebugUnitTest: FAIL (exit $code)" -ForegroundColor Red
    $lines = Get-Content $log
    $start = ($lines | Select-String -Pattern '^\* What went wrong:' | Select-Object -First 1).LineNumber
    if ($start) {
        $end = [Math]::Min($start + 45, $lines.Count)
        Write-Host "`n--- ROOT CAUSE ---" -ForegroundColor Red
        $lines[($start-1)..($end-1)] | ForEach-Object { Write-Host $_ }
    }
    Write-Host "`nFull log: $log" -ForegroundColor Yellow
}
exit $code
