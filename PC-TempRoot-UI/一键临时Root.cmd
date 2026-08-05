@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
cd /d "%~dp0"
title PC Temp Root UI

set "UI=%~dp0ui\TempRoot-UI.ps1"
set "DRY="

if /i "%~1"=="dry" set "DRY=1"
if /i "%~1"=="-dry" set "DRY=1"
if /i "%~1"=="--dry-run" set "DRY=1"
if /i "%~1"=="console" goto :console

if exist "%UI%" (
  if defined DRY (
    powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -STA -File "%UI%" -DryRun
  ) else (
    powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -STA -File "%UI%"
  )
  exit /b !ERRORLEVEL!
)

echo [WARN] UI missing, fallback console
goto :console

:console
call "%~dp0console\一键临时Root.cmd" %*
exit /b %ERRORLEVEL%
