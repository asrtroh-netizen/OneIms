# OneIMS · PC 按需临时 Root（替代手机首页「一键临时 Root」）
# 默认只探测机型 / 匹配 so / 打印计划；加 -Run 才真正 push + LD_PRELOAD + 验 su。
# 加 -Hub 打开 OneAE 风格启动页（推荐日常使用）。
#
# 用法：
#   .\scripts\temp-root-pc.ps1 -Hub
#   .\scripts\temp-root-pc.ps1
#   .\scripts\temp-root-pc.ps1 -Run
#   .\scripts\temp-root-pc.ps1 -Run -So E:\Down\TEMP\preload-comet.so
#
# 前提：adb 在 PATH（脚本会优先挂本地 SDK platform-tools）；设备已授权。

param(
    [switch]$Hub,
    [switch]$Run,
    [string]$So = "",
    [int]$Attempts = 4,
    [int]$TimeoutSec = 180,
    [double]$RetryGapSec = 3.0
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RepoRoot

$AdbDir = "E:\GQ\One\_toolchain\android-sdk\platform-tools"
if (Test-Path $AdbDir) { $env:PATH = "$AdbDir;$env:PATH" }

if ($Hub) {
    & "$PSScriptRoot\OneRoot.ps1"
    exit $LASTEXITCODE
}

$Onesopy = Join-Path $RepoRoot "OneRoot\oneso.py"
if (-not (Test-Path $Onesopy)) {
    Write-Error "missing $Onesopy"
}

$argv = @($Onesopy, "temp-root", "--attempts", "$Attempts", "--timeout-sec", "$TimeoutSec", "--retry-gap-sec", "$RetryGapSec")
if ($Run) { $argv += "--run" }
if ($So) { $argv += @("--so", $So) }

Write-Host "==> OneIMS PC temp-root  (phone one-tap UI is off)"
& python @argv
exit $LASTEXITCODE
