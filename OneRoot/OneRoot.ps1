# OneRoot — local full pack (Python hub)
# Usage: double-click 一键启动.cmd  or  .\OneRoot.ps1
# so: GitHub OneSo-assets by default

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
& python (Join-Path $Here "oneso.py") hub
exit $LASTEXITCODE