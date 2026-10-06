param(
    [string]$ProjectRoot = '',
    [int]$SoakSeconds = 300,
    [switch]$SkipInstall,
    [switch]$SkipAssemble
)
$ErrorActionPreference = 'Stop'

function Resolve-Project {
    param([string]$RequestedRoot)
    $candidates = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($RequestedRoot)) {
        if ([IO.Path]::IsPathRooted($RequestedRoot)) { $candidates.Add($RequestedRoot) }
        else {
            $candidates.Add((Join-Path (Get-Location).Path $RequestedRoot))
            $candidates.Add((Join-Path $PSScriptRoot '..' $RequestedRoot))
        }
    }
    $candidates.Add((Join-Path $PSScriptRoot '..'))
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (Test-Path -LiteralPath $candidate -PathType Container) {
            $project = (Resolve-Path -LiteralPath $candidate).Path
            if ((Test-Path (Join-Path $project 'settings.gradle.kts')) -or (Test-Path (Join-Path $project 'settings.gradle'))) {
                if (Test-Path (Join-Path $project 'gradlew.bat')) { return $project }
            }
        }
    }
    throw 'Could not resolve PROJECT.'
}

function Resolve-GradleLauncher {
    param([string]$Project)
    $wrapperBat = Join-Path $Project 'gradlew.bat'
    $wrapperJar = Join-Path $Project 'gradle\wrapper\gradle-wrapper.jar'
    if (Test-Path -LiteralPath $wrapperJar -PathType Leaf) {
        return $wrapperBat
    }

    # The packaged candidate intentionally has no wrapper JAR. Bootstrap the pinned
    # distribution once, then execute Gradle directly. This avoids a nested
    # gradlew.bat -> PowerShell -> gradlew.bat bootstrap ambiguity in the gate.
    $bootstrap = Join-Path $Project 'scripts\BOOTSTRAP_GRADLE_WINDOWS.ps1'
    $gradle = Join-Path $Project '.gradle-bootstrap\gradle-8.11.1\bin\gradle.bat'
    if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
        if (-not (Test-Path -LiteralPath $bootstrap -PathType Leaf)) {
            throw "Gradle wrapper JAR is missing and bootstrap script is unavailable: $bootstrap"
        }
        Write-Host 'Wrapper JAR absent; provisioning pinned Gradle 8.11.1 directly...' -ForegroundColor Cyan
        $bootstrapOutput = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $bootstrap 2>&1)
        $bootstrapExit = $LASTEXITCODE
        foreach ($line in $bootstrapOutput) { Write-Host ([string]$line) }
        if ($bootstrapExit -ne 0) { throw "Gradle bootstrap failed with exit code $bootstrapExit." }
    }
    if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
        throw "Gradle bootstrap completed without a usable launcher: $gradle"
    }
    return $gradle
}

function Resolve-Adb {
    param([string]$Project)
    $candidates = New-Object System.Collections.Generic.List[string]
    foreach ($envName in @('ANDROID_HOME','ANDROID_SDK_ROOT')) {
        $value = [Environment]::GetEnvironmentVariable($envName)
        if (-not [string]::IsNullOrWhiteSpace($value)) { $candidates.Add((Join-Path $value 'platform-tools\adb.exe')) }
    }
    $localProperties = Join-Path $Project 'local.properties'
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^	*sdk\.dir=' -or $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine -and $sdkLine -match '^sdk\.dir=(.*)$') {
            $sdk = $Matches[1].Trim() -replace '\\:', ':'
            if ($sdk) { $candidates.Add((Join-Path $sdk 'platform-tools\adb.exe')) }
        }
    }
    if ($env:LOCALAPPDATA) { $candidates.Add((Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')) }
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }
    $command = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    throw 'adb.exe not found. Set ANDROID_HOME/ANDROID_SDK_ROOT or provide local.properties sdk.dir.'
}

function Run-Step {
    param(
        [string]$Name,
        [string]$Launcher,
        [string[]]$Arguments,
        [string]$LogPath
    )

    Add-Content -LiteralPath $LogPath -Value "=== $Name ==="

    # PowerShell 5.1 can promote native stderr from a .bat/.cmd process into
    # a terminating NativeCommandError when $ErrorActionPreference='Stop'.
    # Execute the batch through cmd.exe and capture stdout/stderr at the process
    # level so compiler diagnostics can never be lost or turn into an empty catch.
    $stepDir = Split-Path -Parent $LogPath
    $stepBase = Join-Path $stepDir (".gate-step-{0}" -f ([guid]::NewGuid().ToString('N')))
    $cmdFile = "$stepBase.cmd"
    $outFile = "$stepBase.out.txt"

    function Quote-CmdArg {
        param([string]$Value)
        if ($null -eq $Value) { return '""' }
        return '"' + ($Value -replace '"','""') + '"'
    }

    $argText = (($Arguments | ForEach-Object { Quote-CmdArg ([string]$_) }) -join ' ')
    $cmdText = @(
        '@echo off'
        ('call {0} {1} > "{2}" 2>&1' -f (Quote-CmdArg $Launcher), $argText, $outFile.Replace('"','""'))
        'exit /b %ERRORLEVEL%'
    ) -join "`r`n"

    Set-Content -LiteralPath $cmdFile -Value $cmdText -Encoding ascii

    try {
        & $env:ComSpec /d /c call (Quote-CmdArg $cmdFile) | Out-Null
        $exitCode = $LASTEXITCODE

        if (Test-Path -LiteralPath $outFile) {
            $lines = @(Get-Content -LiteralPath $outFile -ErrorAction SilentlyContinue)
        } else {
            $lines = @()
        }

        foreach ($line in $lines) {
            $text = [string]$line
            Add-Content -LiteralPath $LogPath -Value $text
            Write-Host $text
        }

        return [pscustomobject]@{
            ExitCode = $exitCode
            Output = $lines
        }
    }
    finally {
        Remove-Item -LiteralPath $cmdFile -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $outFile -Force -ErrorAction SilentlyContinue
    }
}

function Get-DiagnosticLines {
    param([object[]]$Output)
    $patterns = @(
        '\* What went wrong:',
        '\* Try:',
        'FAILURE: Build failed',
        '(?i)error:',
        '(?i)e: .*\.kt:',
        '(?i)exception',
        '(?i)Caused by:',
        '(?i)Could not resolve',
        '(?i)unresolved reference'
    )
    $hits = foreach ($line in $Output) {
        $text = [string]$line
        foreach ($pattern in $patterns) {
            if ($text -match $pattern) { $text; break }
        }
    }
    @($hits | Select-Object -Unique | Select-Object -First 80)
}

$project = Resolve-Project $ProjectRoot
$reportDir = Join-Path $project 'gate-reports'
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$jsonPath = Join-Path $reportDir "FINAL_GATE_V3_$stamp.json"
$logPath = Join-Path $reportDir "FINAL_GATE_V3_$stamp.log"
$apk = Join-Path $project 'app\build\outputs\apk\debug\app-debug.apk'
$gates = [ordered]@{}
$errors = @()
$diagnostics = @{}
$final = 'FAIL'

try {
    Push-Location $project
    $gradle = Resolve-GradleLauncher $project
    Add-Content -LiteralPath $logPath -Value ("GradleLauncher={0}" -f $gradle)
    $version = Run-Step 'gradleVersion' $gradle @('--version','--no-daemon') $logPath
    $gates.gradleLauncher = if ($version.ExitCode -eq 0) { 'PASS' } else { 'FAIL' }
    if ($version.ExitCode -ne 0) { throw "Gradle launcher validation failed with exit code $($version.ExitCode)." }

    $compile = Run-Step 'compileDebugKotlin' $gradle @(':app:compileDebugKotlin','--no-daemon','--stacktrace','--console=plain') $logPath
    $gates.compileDebugKotlin = if ($compile.ExitCode -eq 0) { 'PASS' } else { 'FAIL' }
    if ($compile.ExitCode -ne 0) {
        $diagnostics.compileDebugKotlin = @(Get-DiagnosticLines $compile.Output)
        throw "compileDebugKotlin failed with exit code $($compile.ExitCode). See $logPath"
    }

    $tests = Run-Step 'testDebugUnitTest' $gradle @(':app:testDebugUnitTest','--no-daemon','--stacktrace','--console=plain') $logPath
    $gates.testDebugUnitTest = if ($tests.ExitCode -eq 0) { 'PASS' } else { 'FAIL' }
    if ($tests.ExitCode -ne 0) {
        $diagnostics.testDebugUnitTest = @(Get-DiagnosticLines $tests.Output)
        throw "testDebugUnitTest failed with exit code $($tests.ExitCode). See $logPath"
    }

    if (-not $SkipAssemble) {
        $assemble = Run-Step 'assembleDebug' $gradle @(':app:assembleDebug','--no-daemon','--stacktrace','--console=plain') $logPath
        $gates.assembleDebug = if ($assemble.ExitCode -eq 0) { 'PASS' } else { 'FAIL' }
        if ($assemble.ExitCode -ne 0) {
            $diagnostics.assembleDebug = @(Get-DiagnosticLines $assemble.Output)
            throw "assembleDebug failed with exit code $($assemble.ExitCode). See $logPath"
        }
        $gates.apkExists = if (Test-Path -LiteralPath $apk -PathType Leaf) { 'PASS' } else { 'FAIL' }
        if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw "APK missing after successful assemble: $apk" }
    } else {
        $gates.assembleDebug = 'SKIPPED'
    }

    if (-not $SkipInstall) {
        $adb = Resolve-Adb $project
        Add-Content -LiteralPath $logPath -Value ("ADB={0}" -f $adb)
        $devices = @(& $adb devices 2>&1)
        foreach ($line in $devices) { Add-Content -LiteralPath $logPath -Value ([string]$line); Write-Host $line }
        $serialLine = $devices | Where-Object { ([string]$_) -match '^\S+\s+device$' } | Select-Object -First 1
        if (-not $serialLine) { throw 'No Android device in adb device state.' }
        $serial = ([string]$serialLine -split '\s+')[0]
        $gates.adbDevice = 'PASS'

        $install = @(& $adb -s $serial install -r $apk 2>&1)
        foreach ($line in $install) { Add-Content -LiteralPath $logPath -Value ([string]$line); Write-Host $line }
        if (($install -join ' ') -notmatch '(?i)Success') { throw 'adb install did not report Success.' }
        $gates.install = 'PASS'

        & $adb -s $serial logcat -c | Out-Null
        & $adb -s $serial shell am force-stop horizon.observatory.debug | Out-Null
        & $adb -s $serial shell monkey -p horizon.observatory.debug 1 | Out-Null
        Start-Sleep -Seconds 5
        $gates.launch = 'PASS'

        $exitInfo = @(& $adb -s $serial shell dumpsys activity exit-info horizon.observatory.debug 2>&1)
        $mem = @(& $adb -s $serial shell dumpsys meminfo horizon.observatory.debug 2>&1)
        $log = @(& $adb -s $serial logcat -d -v time 2>&1)
        $exitInfo | Out-File (Join-Path $reportDir "EXIT_INFO_$stamp.txt") -Encoding utf8
        $mem | Out-File (Join-Path $reportDir "MEMINFO_$stamp.txt") -Encoding utf8
        $log | Out-File (Join-Path $reportDir "LOGCAT_$stamp.txt") -Encoding utf8

        $fatal = $log | Select-String 'FATAL EXCEPTION|AndroidRuntime|Illegal or concurrent session transition|LOW_MEMORY'
        $gates.runtimeCrashScan = if ($fatal) { 'FAIL' } else { 'PASS' }
        if ($fatal) { $errors += 'Runtime crash/low-memory/stop-race signature detected.' }

        if ($SoakSeconds -gt 0) {
            Start-Sleep -Seconds $SoakSeconds
            $afterExit = @(& $adb -s $serial shell dumpsys activity exit-info horizon.observatory.debug 2>&1)
            $afterMem = @(& $adb -s $serial shell dumpsys meminfo horizon.observatory.debug 2>&1)
            $afterLog = @(& $adb -s $serial logcat -d -v time 2>&1)
            $afterExit | Out-File (Join-Path $reportDir "EXIT_INFO_AFTER_SOAK_$stamp.txt") -Encoding utf8
            $afterMem | Out-File (Join-Path $reportDir "MEMINFO_AFTER_SOAK_$stamp.txt") -Encoding utf8
            $afterLog | Out-File (Join-Path $reportDir "LOGCAT_AFTER_SOAK_$stamp.txt") -Encoding utf8
            $fatal2 = $afterLog | Select-String 'FATAL EXCEPTION|AndroidRuntime|Illegal or concurrent session transition|LOW_MEMORY'
            $gates.soak = if ($fatal2) { 'FAIL' } else { 'PASS' }
            if ($fatal2) { $errors += 'Runtime signature detected during soak.' }
        } else {
            $gates.soak = 'SKIPPED'
        }
    } else {
        $gates.install = 'SKIPPED'
        $gates.launch = 'SKIPPED'
        $gates.runtimeCrashScan = 'SKIPPED'
        $gates.soak = 'SKIPPED'
    }

    $final = if ($errors.Count -eq 0 -and (($gates.Values -contains 'FAIL') -eq $false)) { 'PASS' } else { 'FAIL' }
}
catch {
    $final = 'FAIL'
    $message = $_.Exception.Message
    if ([string]::IsNullOrWhiteSpace($message)) {
        $message = (($_ | Out-String).Trim())
    }
    if ([string]::IsNullOrWhiteSpace($message)) {
        $message = 'Gate execution failed with an exception that did not expose a message.'
    }
    $errors += $message
}
finally {
    try { Pop-Location } catch {}
    $report = [ordered]@{
        timestamp = (Get-Date).ToString('o')
        projectRoot = $project
        package = 'horizon.observatory.debug'
        soakSeconds = $SoakSeconds
        gradleWrapperJarPresent = (Test-Path -LiteralPath (Join-Path $project 'gradle\wrapper\gradle-wrapper.jar') -PathType Leaf)
        gates = $gates
        diagnostics = $diagnostics
        final = $final
        errors = $errors
    }
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8
    Write-Host ''
    Write-Host '=== FINAL GATE V3 ==='
    Write-Host ("ProjectRoot: {0}" -f $project)
    Write-Host ("Report:      {0}" -f $jsonPath)
    Write-Host ("Final:       {0}" -f $final)
    foreach ($key in $gates.Keys) { Write-Host ("{0}: {1}" -f $key, $gates[$key]) }
    if ($errors.Count -gt 0) {
        Write-Host ''
        Write-Host '=== ERRORS ===' -ForegroundColor Red
        $errors | ForEach-Object { Write-Host $_ -ForegroundColor Red }
    }
}
if ($final -eq 'PASS') { exit 0 } else { exit 1 }
