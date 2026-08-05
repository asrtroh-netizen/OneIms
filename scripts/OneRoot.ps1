# Compat shim → independent OneRoot/ folder
$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
& powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File (Join-Path $RepoRoot "OneRoot\OneRoot.ps1") @args
exit $LASTEXITCODE