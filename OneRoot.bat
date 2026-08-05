@echo off
setlocal
cd /d "%~dp0"
title OneRoot
echo.
echo   ========================================
echo    OneRoot  ?  one-click start
echo   ========================================
echo.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\OneRoot.ps1" %*
set "EXITCODE=%ERRORLEVEL%"
if not "%EXITCODE%"=="0" (
  echo.
  echo OneRoot failed with exit code %EXITCODE%
  pause
)
exit /b %EXITCODE%
