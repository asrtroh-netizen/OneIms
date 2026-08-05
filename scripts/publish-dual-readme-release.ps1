# OneIms dual public release (OneKuku + OneLink must ship together)
# Usage: .\scripts\publish-dual-readme-release.ps1 -Version 3.2.0 [-SkipBuild] [-SkipReadmePush]
# Requires: gh logged in; JAVA_HOME / Android SDK (see local.properties)

param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [string]$ReleaseTag = "",
    [switch]$SkipBuild,
    [switch]$SkipReadmePush,
    [switch]$SkipApkUpload
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RepoRoot

if (-not $ReleaseTag) { $ReleaseTag = "v$Version" }

$Jdk = "E:\GQ\One\_toolchain\jdk\jdk-17.0.19+10"
if (Test-Path $Jdk) {
    $env:JAVA_HOME = $Jdk
    $env:PATH = "$Jdk\bin;$env:PATH"
}

$KukuApk = "OneIms-OneKuku-standalone-$Version.apk"
$LinkApk = "OneIms-Lite-Shizuku-$Version.apk"

Write-Host "==> OneIms dual publish $Version ($ReleaseTag)"

if (-not $SkipBuild) {
    Write-Host "==> Build dual APKs"
    .\gradlew.bat :app:packageDualDebugApks --no-daemon
    if (-not (Test-Path $KukuApk)) {
        Copy-Item -Force "OneIms-OneKuku-standalone-$Version-debug.apk" $KukuApk
    }
    if (-not (Test-Path $LinkApk)) {
        Copy-Item -Force "OneIms-Lite-Shizuku-$Version-debug.apk" $LinkApk
    }
}

if (-not $SkipApkUpload) {
    Write-Host "==> Ensure GitHub Release $ReleaseTag then upload APKs"
    if (-not (Test-Path $KukuApk) -or -not (Test-Path $LinkApk)) {
        throw "Missing APK(s): $KukuApk / $LinkApk"
    }
    # gh writes "release not found" to stderr; with $ErrorActionPreference=Stop that aborts.
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    gh release view $ReleaseTag 1>$null 2>$null
    $releaseExists = ($LASTEXITCODE -eq 0)
    $ErrorActionPreference = $prevEap
    if (-not $releaseExists) {
        gh release create $ReleaseTag $KukuApk $LinkApk --title "OneIms $Version" --generate-notes
    } else {
        gh release upload $ReleaseTag $KukuApk $LinkApk --clobber
    }
    if ($LASTEXITCODE -ne 0) { throw "gh release create/upload failed (exit $LASTEXITCODE)" }
}

if (-not $SkipReadmePush) {
    Write-Host "==> Push README-only to origin/main (worktree)"
    git fetch origin
    $Wt = ".worktree-readme-push"
    if (Test-Path $Wt) { git worktree remove --force $Wt 2>$null }
    git worktree add $Wt origin/main
    Copy-Item -Force "README.md" "$Wt\README.md"
    Push-Location $Wt
    git add README.md
    git commit -m "docs: README dual $Version sync (README only)"
    git push origin HEAD:main
    Pop-Location
    git worktree remove --force $Wt 2>$null
}

Write-Host "==> Done. Verify: gh release view $ReleaseTag"
