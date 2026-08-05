@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
cd /d "%~dp0"
title PC-TempRoot-UI

set "UI=%~dp0ui\TempRoot-UI.ps1"
set "DRY="

if /i "%~1"=="dry" set "DRY=1"
if /i "%~1"=="-dry" set "DRY=1"
if /i "%~1"=="--dry-run" set "DRY=1"
if /i "%~1"=="console" goto :console_help

if not exist "%UI%" (
  echo [FAIL] missing UI script: "%UI%"
  echo Lite pure-CMD pack: ..\PC-TempRoot-Lite
  pause
  exit /b 2
)

rem UI pack = PowerShell WinForms (progress + monitor + OneIMS recommend + sponsor QR)
if defined DRY (
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -STA -File "%UI%" -DryRun
) else (
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -STA -File "%UI%"
)
exit /b %ERRORLEVEL%

:console_help
echo PC-TempRoot-UI is the PowerShell window edition.
echo For pure CMD, use: ..\PC-TempRoot-Lite\一键临时Root.cmd
pause
exit /b 0
