# OneSo · 单窗 PC 一键临时 Root（so 工厂在 GitHub，本脚本不打包）
# 用法：.\scripts\oneso-hub.ps1
# 已运行则不会再开第二扇（hub 单实例锁）。

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RepoRoot

$AdbDir = "E:\GQ\One\_toolchain\android-sdk\platform-tools"
if (Test-Path $AdbDir) { $env:PATH = "$AdbDir;$env:PATH" }

# 清掉残留多开（仅 oneso hub/gui）+ 失效单实例锁
Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
  Where-Object { $_.CommandLine -match 'oneso\.py (hub|gui)' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
$Lock = Join-Path $env:TEMP "oneso-hub-single.lock"
if (Test-Path $Lock) { Remove-Item -Force $Lock -ErrorAction SilentlyContinue }

Write-Host "==> OneSo Temp Root (single window)"
& python (Join-Path $RepoRoot "tools\oneso\oneso.py") hub
exit $LASTEXITCODE
