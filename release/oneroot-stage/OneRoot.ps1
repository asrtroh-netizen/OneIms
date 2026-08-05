# OneRoot — local full pack (Python hub)
# Usage: double-click 一键启动.cmd  or  .\OneRoot.ps1
# so: GitHub OneSo-assets only (no local/assets embed; cache must match SHA256SUMS)

$ErrorActionPreference = "Stop"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Here

$AdbCandidates = @(
  "E:\GQ\One\_toolchain\android-sdk\platform-tools",
  "$env:LOCALAPPDATA\Android\Sdk\platform-tools",
  "$env:ANDROID_HOME\platform-tools"
)
foreach ($AdbDir in $AdbCandidates) {
  if ($AdbDir -and (Test-Path $AdbDir)) {
    $env:PATH = "$AdbDir;$env:PATH"
    break
  }
}

Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
  Where-Object { $_.CommandLine -match 'oneso\.py (hub|gui)' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
foreach ($name in @("oneroot-single.lock", "oneso-hub-single.lock")) {
  $Lock = Join-Path $env:TEMP $name
  if (Test-Path $Lock) { Remove-Item -Force $Lock -ErrorAction SilentlyContinue }
}

Write-Host "==> OneRoot (local full pack)"
Write-Host "【重要】请勿关闭本黑窗：关掉会导致本地 API 断开（Failed to fetch）。"
Write-Host "若他机无作者仓库路径，Hub 仍应能启动；so 可走本机缓存 / GitHub。"
& python (Join-Path $Here "oneso.py") hub
exit $LASTEXITCODE