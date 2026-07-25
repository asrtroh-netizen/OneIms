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

# Read versionName from built APK via aapt if available; else stamp time.
$stamp = Get-Date -Format "yyyyMMdd-HHmm"
$out = Join-Path $dist "OneTools-debug-$stamp.apk"
Copy-Item -Force $apk $out
$latest = Join-Path $dist "OneTools-latest-debug.apk"
Copy-Item -Force $apk $latest

Write-Host ""
Write-Host "OK local product APK (not published):"
Write-Host "  $out"
Write-Host "  $latest"
Write-Host ""
Write-Host "Install on Pixel (USB debugging):"
Write-Host "  adb install -r `"$latest`""
