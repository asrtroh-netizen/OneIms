# OneIMS · PC 按需临时 Root（替代手机首页「一键临时 Root」）
# 默认只探测机型 / 匹配 so / 打印计划；加 -Run 才真正 push + LD_PRELOAD + 验 su。
#
# 用法：
#   .\scripts\temp-root-pc.ps1
#   .\scripts\temp-root-pc.ps1 -Run
#   .\scripts\temp-root-pc.ps1 -Run -So E:\Down\TEMP\preload-comet.so
#   .\scripts\temp-root-pc.ps1 -Run -Attempts 4 -TimeoutSec 180
#
# 前提：adb 在 PATH；设备已 USB/无线调试授权；对应 so 已在
# app/src/main/assets/temproot（或 -So 指定）。

param(
    [switch]$Run,
    [string]$So = "",
    [int]$Attempts = 4,
    [int]$TimeoutSec = 180,
    [double]$RetryGapSec = 3.0
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RepoRoot

$Py = Get-Command python -ErrorAction SilentlyContinue
if (-not $Py) {
    Write-Error "python not found in PATH"
}

$Onesopy = Join-Path $RepoRoot "tools\oneso\oneso.py"
if (-not (Test-Path $Onesopy)) {
    Write-Error "missing $Onesopy"
}

$argv = @($Onesopy, "temp-root", "--attempts", "$Attempts", "--timeout-sec", "$TimeoutSec", "--retry-gap-sec", "$RetryGapSec")
if ($Run) { $argv += "--run" }
if ($So) { $argv += @("--so", $So) }

Write-Host "==> OneIMS PC temp-root  (phone one-tap UI is off)"
& python @argv
exit $LASTEXITCODE
