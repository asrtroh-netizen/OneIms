# OneIms 双版本公开发布脚本（OneKuku + OneLink 必须一起更新）
# 用法：.\scripts\publish-dual-readme-release.ps1 -Version 2.2.0 [-SkipBuild] [-SkipReadmePush]
# 前提：gh 已登录；JAVA_HOME / Android SDK 可用（见 local.properties）

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

$KukuApk = "OneIms-OneKuku-$Version.apk"
$LinkApk = "OneIms-OneLink-$Version.apk"

Write-Host "==> OneIms dual publish $Version ($ReleaseTag)"

if (-not $SkipBuild) {
    Write-Host "==> Build dual APKs"
    .\gradlew.bat :app:packageDualDebugApks --no-daemon
    Copy-Item -Force "OneIms-OneKuku-$Version-debug.apk" $KukuApk
    Copy-Item -Force "OneIms-OneLink-$Version-debug.apk" $LinkApk
}

if (-not $SkipApkUpload) {
    Write-Host "==> Upload APKs to GitHub Release $ReleaseTag"
    gh release upload $ReleaseTag $KukuApk $LinkApk --clobber
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
    git commit -m "docs: README 双版本 $Version 同步（仅 README）"
    git push origin HEAD:main
    Pop-Location
    git worktree remove --force $Wt 2>$null
}

Write-Host "==> Done. Verify: gh release view $ReleaseTag"
