@echo off
setlocal
cd /d "%~dp0"
call "%~dp0OneRoot\OneRoot.cmd" %*
exit /b %ERRORLEVEL%