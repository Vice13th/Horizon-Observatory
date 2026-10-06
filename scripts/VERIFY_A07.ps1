$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

Write-Host '=== HORIZON A07 DEVICE VERIFICATION ===' -ForegroundColor Cyan
$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) { throw 'adb.exe not found on PATH.' }

$devices = @(adb devices | Select-String '\tdevice$')
if ($devices.Count -lt 1) { throw 'No authorized Android device found.' }

$serial = ($devices[0].ToString() -split '\s+')[0]
$model = (adb -s $serial shell getprop ro.product.model).Trim()
$manufacturer = (adb -s $serial shell getprop ro.product.manufacturer).Trim()
$release = (adb -s $serial shell getprop ro.build.version.release).Trim()
$sdk = (adb -s $serial shell getprop ro.build.version.sdk).Trim()

Write-Host "Device: $manufacturer $model / Android $release / API $sdk"
if ($model -ne 'SM-A075F') { throw "Unexpected model: $model" }
if ($sdk -ne '36') { throw "Expected API 36, got $sdk" }

$apk = Join-Path $PWD 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw "APK not found: $apk. Run BUILD_WINDOWS.ps1 first." }

adb -s $serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'adb install failed.' }

Write-Host '--- Package permissions ---'
adb -s $serial shell dumpsys package horizon.observatory.debug | Select-String 'android.permission.(ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION|READ_PHONE_STATE|POST_NOTIFICATIONS|FOREGROUND_SERVICE_LOCATION)'

Write-Host '--- Recent Horizon logs ---'
adb -s $serial logcat -d -t 250 | Select-String 'Horizon|Observatory' | Select-Object -Last 120

Write-Host 'Physical device identity verified. Capability/runtime results must be collected from the app Diagnostics and a real observation session.' -ForegroundColor Green
