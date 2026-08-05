# OneIms dual public release (OneKuku + OneLink + README must ship together)
# Usage: .\scripts\publish-dual-readme-release.ps1 -Version 3.3.0 [-SkipBuild] [-SkipApkUpload]
# Requires: gh logged in; JAVA_HOME / Android SDK (see local.properties)
#
# Hard rule (2026-08-05): README sync to origin/main is MANDATORY.
# Do not use -SkipReadmePush unless emergency; it requires -IKnowReadmeIsMandatoryAnyway.

param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [string]$ReleaseTag = "",
    [switch]$SkipBuild,
    [switch]$SkipApkUpload,
    # Emergency only — must pair with -IKnowReadmeIsMandatoryAnyway
    [switch]$SkipReadmePush,
    [switch]$IKnowReadmeIsMandatoryAnyway
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RepoRoot

if (-not $ReleaseTag) { $ReleaseTag = "v$Version" }

if ($SkipReadmePush -and -not $IKnowReadmeIsMandatoryAnyway) {
    throw @"
README sync is mandatory for dual releases (user rule: 以后连 README).
Refusing -SkipReadmePush without -IKnowReadmeIsMandatoryAnyway.
"@
}

$Jdk = "E:\GQ\One\_toolchain\jdk\jdk-17.0.19+10"
if (Test-Path $Jdk) {
    $env:JAVA_HOME = $Jdk
    $env:PATH = "$Jdk\bin;$env:PATH"
}

$KukuApk = "OneIms-OneKuku-standalone-$Version.apk"
$LinkApk = "OneIms-Lite-Shizuku-$Version.apk"
$ReadmePath = Join-Path $RepoRoot "README.md"

Write-Host "==> OneIms dual publish $Version ($ReleaseTag)"

# Gate: local README must already mention this version before we upload/push.
$readmeText = Get-Content -Raw -Path $ReadmePath
if ($readmeText -notmatch [regex]::Escape($Version)) {
    throw "README.md does not mention version '$Version'. Update What's New + download links first."
}
if ($readmeText -notmatch [regex]::Escape($ReleaseTag)) {
    throw "README.md does not mention tag '$ReleaseTag'. Fix download URLs before publish."
}
Write-Host "==> README preflight OK (contains $Version / $ReleaseTag)"

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
    Write-Host "==> Push README-only to origin/main (worktree) [MANDATORY]"
    git fetch origin
    $Wt = ".worktree-readme-push"
    if (Test-Path $Wt) { git worktree remove --force $Wt 2>$null }
    git worktree add $Wt origin/main
    Copy-Item -Force "README.md" "$Wt\README.md"
    Push-Location $Wt
    git add README.md
    $pending = git status --porcelain -- README.md
    if (-not $pending) {
        Write-Host "==> README on main already matches; no commit needed"
    } else {
        git commit -m "docs: README dual $Version sync (README only)"
        git push origin HEAD:main
        if ($LASTEXITCODE -ne 0) { throw "README push to origin/main failed (exit $LASTEXITCODE)" }
    }
    Pop-Location
    git worktree remove --force $Wt 2>$null

    # Postflight: remote main README must contain this version.
    $remoteRaw = gh api "repos/asrtroh-netizen/OneIms/contents/README.md?ref=main" --jq .content
    $remoteText = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String(($remoteRaw -replace "`n", "")))
    if ($remoteText -notmatch [regex]::Escape($Version)) {
        throw "Postflight FAIL: origin/main README still missing '$Version'"
    }
    Write-Host "==> README postflight OK on origin/main"
} else {
    Write-Host "==> WARNING: skipped README push (emergency override)"
}

Write-Host "==> Done. Verify: gh release view $ReleaseTag"
