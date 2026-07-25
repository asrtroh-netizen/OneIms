# Local product APK — no GitHub publish.
# Usage: powershell -File onetools/scripts/build-local-apk.ps1
$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $Root

Write-Host "==> :onetools:assembleDebug"
& .\gradlew :onetools:assembleDebug --quiet
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$apk = Join-Path $Root "onetools\build\outputs\apk\debug\onetools-debug.apk"
if (-not (Test-Path $apk)) {
    Write-Error "APK not found: $apk"
}

$dist = Join-Path $Root "onetools\dist"
New-Item -ItemType Directory -Force -Path $dist | Out-Null

# Distinct filename: include versionName so installs aren't confused with Pixel Meter / old builds.
$gradle = Get-Content (Join-Path $Root "onetools\build.gradle.kts") -Raw
$versionName = "0.0.0"
if ($gradle -match 'versionName\s*=\s*"([^"]+)"') {
    $versionName = $Matches[1]
}
$stamp = Get-Date -Format "yyyyMMdd-HHmm"
$out = Join-Path $dist "OneTools-v$versionName-debug-$stamp.apk"
$latest = Join-Path $dist "OneTools-v$versionName-latest-debug.apk"
# Stable pointer for adb scripts (always newest build).
$alias = Join-Path $dist "OneTools-latest-debug.apk"

Copy-Item -Force $apk $out
Copy-Item -Force $apk $latest
Copy-Item -Force $apk $alias

Write-Host ""
Write-Host "OK local product APK (not published):"
Write-Host "  $out"
Write-Host "  $latest"
Write-Host "  $alias"
Write-Host ""
Write-Host "Install on Pixel (USB debugging):"
Write-Host "  adb install -r `"$latest`""
