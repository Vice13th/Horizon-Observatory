param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$failures = @()
$warnings = @()

$trackedExt = @('.kt','.kts','.gradle','.xml','.pro','.properties','.ps1','.cmd','.bat','.md','.json','.txt')
$files = Get-ChildItem -LiteralPath $ProjectRoot -Recurse -File | Where-Object {
    $trackedExt -contains $_.Extension.ToLowerInvariant() -and
    $_.FullName -notmatch '\\(build|\.gradle|\.idea)\\'
}

$patterns = @(
    '(?i)[A-Z]:\\Users\\',
    '(?i)[A-Z]:\\(Android|AndroidSDK|SDK)\\',
    '(?i)[A-Z]:\\New folder',
    '(?i)/mnt/data/',
    '(?i)/home/oai/',
    '(?i)/workspace/'
)
foreach ($file in $files) {
    $content = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
    foreach ($pattern in $patterns) {
        if ($content -match $pattern) {
            $failures += "Machine-specific path in $($file.FullName): $pattern"
            break
        }
    }
}

if (Test-Path -LiteralPath (Join-Path $ProjectRoot 'local.properties')) {
    $failures += 'local.properties must not be included in a portable source package.'
}

$src = Join-Path $ProjectRoot 'app\src'
$resourceNames = @{}
foreach ($dir in @('drawable','mipmap','layout','menu','raw','xml','font')) {
    $resourceNames[$dir] = [System.Collections.Generic.HashSet[string]]::new()
}
$resRoot = Join-Path $src 'main\res'
if (Test-Path $resRoot) {
    Get-ChildItem $resRoot -Recurse -File | ForEach-Object {
        $folder = Split-Path $_.DirectoryName -Leaf
        if ($resourceNames.ContainsKey($folder)) { [void]$resourceNames[$folder].Add($_.BaseName) }
    }
}
foreach ($kt in Get-ChildItem (Join-Path $src 'main\kotlin') -Recurse -Filter '*.kt') {
    $text = Get-Content $kt.FullName -Raw
    foreach ($m in [regex]::Matches($text, 'R\.(drawable|mipmap|layout|menu|raw|xml|font)\.([A-Za-z0-9_]+)')) {
        if (-not $resourceNames[$m.Groups[1].Value].Contains($m.Groups[2].Value)) {
            $failures += "Missing app resource: $($m.Groups[1].Value)/$($m.Groups[2].Value) referenced by $($kt.FullName)"
        }
    }
}

foreach ($required in @('settings.gradle.kts','build.gradle.kts','gradle.properties','gradle\libs.versions.toml','gradlew','gradlew.bat','gradle\wrapper\gradle-wrapper.properties','app\build.gradle.kts','app\src\main\AndroidManifest.xml')) {
    if (-not (Test-Path -LiteralPath (Join-Path $ProjectRoot $required) -PathType Leaf)) {
        $failures += "Missing required build file: $required"
    }
}

$mainKotlin = @(Get-ChildItem (Join-Path $ProjectRoot 'app\src\main\kotlin') -Recurse -Filter '*.kt')
$testKotlin = @(Get-ChildItem (Join-Path $ProjectRoot 'app\src\test\kotlin') -Recurse -Filter '*.kt')
if ($mainKotlin.Count -lt 1) { $failures += 'No main Kotlin source files found.' }
if ($testKotlin.Count -lt 1) { $warnings += 'No JVM unit tests found.' }

$result = [ordered]@{
    result = if ($failures.Count -eq 0) { 'PASS' } else { 'FAIL' }
    projectRoot = $ProjectRoot
    mainKotlinFiles = $mainKotlin.Count
    testKotlinFiles = $testKotlin.Count
    failures = $failures
    warnings = $warnings
}
$result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $ProjectRoot 'PORTABILITY_AUDIT.json') -Encoding UTF8
if ($failures.Count) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 2
}
Write-Host "PORTABILITY AUDIT PASS: $($mainKotlin.Count) main Kotlin files, $($testKotlin.Count) tests." -ForegroundColor Green
