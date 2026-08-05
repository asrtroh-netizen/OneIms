# OneSo Hub — OneAE 风格启动页（pywebview）
# 用法：.\scripts\oneso-hub.ps1

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RepoRoot

$env:PATH = "E:\GQ\One\_toolchain\android-sdk\platform-tools;" + $env:PATH

Write-Host "==> OneSo Hub (OneAE splash)"
& python (Join-Path $RepoRoot "tools\oneso\oneso.py") hub
exit $LASTEXITCODE
