# OneRoot — 单窗：仅 PC 一键临时 Root（不做运营商持久化）
# so 从 GitHub OneSo-assets 获取。
# 用法：仓库根 OneRoot.bat  或  .\scripts\OneRoot.ps1

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RepoRoot

$AdbDir = "E:\GQ\One\_toolchain\android-sdk\platform-tools"
if (Test-Path $AdbDir) { $env:PATH = "$AdbDir;$env:PATH" }

Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
  Where-Object { $_.CommandLine -match 'oneso\.py (hub|gui)' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
foreach ($name in @("oneroot-single.lock", "oneso-hub-single.lock")) {
  $Lock = Join-Path $env:TEMP $name
  if (Test-Path $Lock) { Remove-Item -Force $Lock -ErrorAction SilentlyContinue }
}

Write-Host "==> OneRoot (single window)"
& python (Join-Path $RepoRoot "tools\oneso\oneso.py") hub
exit $LASTEXITCODE
