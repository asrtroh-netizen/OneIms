@echo off
setlocal
cd /d "%~dp0"
title OneRoot
echo.
echo   ========================================
echo    OneRoot  ?  ????
echo   ========================================
echo.
call "%~dp0OneRoot.bat" %*
exit /b %ERRORLEVEL%
