@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
cd /d "%~dp0"
title PC-TempRoot-UI
color 0B

rem Pure CMD only — no PowerShell
for /f %%A in ('echo prompt $E^| cmd') do set "ESC=%%A"
set "C_TITLE=%ESC%[38;2;94;210;180m"
set "C_MUTE=%ESC%[38;2;140;150;160m"
set "C_OK=%ESC%[38;2;80;200;120m"
set "C_BAD=%ESC%[38;2;230;90;90m"
set "C_ACC=%ESC%[38;2;255;200;90m"
set "C_RST=%ESC%[0m"

set "ADB=%~dp0adb\adb.exe"
set "SO_DIR=%~dp0so"
set "SH_DIR=%~dp0sh"
set "SPONSOR=%~dp0assets\sponsor_wechat.jpg"
set "REMOTE_SO=/data/local/tmp/preload-comet.so"
set "ATTEMPTS=4"
set "ONEIMS=https://github.com/asrtroh-netizen/OneIms"
set "RELEASES=https://github.com/asrtroh-netizen/OneIms/releases"
set "DRY=0"
set "NOPAUSE=0"
set "ACTION="

if /i "%~1"=="dry" set "DRY=1" & set "ACTION=run"
if /i "%~1"=="-dry" set "DRY=1" & set "ACTION=run"
if /i "%~1"=="--dry-run" set "DRY=1" & set "ACTION=run"
if /i "%~1"=="run" set "ACTION=run"
if /i "%~1"=="nopause" set "NOPAUSE=1"
if /i "%~2"=="nopause" set "NOPAUSE=1"
if /i "%PC_TEMPROOT_NOPAUSE%"=="1" set "NOPAUSE=1"
if /i "%~1"=="github" goto :open_github
if /i "%~1"=="releases" goto :open_releases
if /i "%~1"=="sponsor" goto :open_sponsor

cls
call :banner
call :recommend

if defined ACTION goto :do_run

echo %C_TITLE%  ---------------- menu (pure CMD) ----------------%C_RST%
echo %C_MUTE%  1^) Start temp root     2^) Dry-run only%C_RST%
echo %C_MUTE%  3^) Open OneIMS GitHub  4^) Open Releases ^(APK^)%C_RST%
echo %C_MUTE%  5^) Show WeChat sponsor QR%C_RST%
echo %C_MUTE%  0^) Exit%C_RST%
echo.
choice /C 123450 /N /M "Select: "
set "SEL=!ERRORLEVEL!"
if "!SEL!"=="1" set "DRY=0" & goto :do_run
if "!SEL!"=="2" set "DRY=1" & goto :do_run
if "!SEL!"=="3" goto :open_github
if "!SEL!"=="4" goto :open_releases
if "!SEL!"=="5" goto :open_sponsor
goto :eof

:do_run
if not exist "%ADB%" (
  echo %C_BAD%  [FAIL]%C_RST% missing adb
  goto :fail
)

call :step 1 6 "adb devices"
"%ADB%" start-server >nul 2>&1
"%ADB%" devices
set "SERIAL="
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
  if /i "%%B"=="device" if not defined SERIAL set "SERIAL=%%A"
)
if not defined SERIAL (
  echo %C_BAD%  [FAIL]%C_RST% no authorized device
  goto :fail
)
echo %C_OK%  [ok]%C_RST% serial=!SERIAL!
call :bar 18

call :step 2 6 "read device / build"
set "DEVICE=" & set "BUILD="
for /f "usebackq delims=" %%A in (`"%ADB%" -s !SERIAL! shell getprop ro.product.device`) do set "DEVICE=%%A"
for /f "usebackq delims=" %%A in (`"%ADB%" -s !SERIAL! shell getprop ro.build.id`) do set "BUILD=%%A"
set "DEVICE=!DEVICE: =!" & set "BUILD=!BUILD: =!"
for /f "delims=" %%A in ("!DEVICE!") do set "DEVICE=%%A"
for /f "delims=" %%A in ("!BUILD!") do set "BUILD=%%A"
echo %C_OK%  [ok]%C_RST% device=%C_ACC%!DEVICE!%C_RST%  build=%C_ACC%!BUILD!%C_RST%
call :bar 32

set "SOFILE=%SO_DIR%\preload-!DEVICE!-!BUILD!.so"
if not exist "!SOFILE!" set "SOFILE=%SO_DIR%\preload-comet.so"
if not exist "!SOFILE!" (
  echo %C_BAD%  [FAIL]%C_RST% no matching so
  goto :fail
)

call :step 3 6 "match preload so"
echo %C_OK%  [ok]%C_RST% so=!SOFILE!
echo %C_MUTE%       remote=!REMOTE_SO!%C_RST%
call :bar 45

if "!DRY!"=="1" (
  echo.
  echo %C_ACC%  [dry-run]%C_RST% plan ready · no exploit executed
  call :bar 100
  goto :ok
)

call :step 4 6 "adb push"
"%ADB%" -s !SERIAL! push "!SOFILE!" "!REMOTE_SO!"
if errorlevel 1 goto :fail
"%ADB%" -s !SERIAL! shell chmod 644 !REMOTE_SO!
echo %C_OK%  [ok]%C_RST% pushed
call :bar 58

call :step 5 6 "exploit attempts x!ATTEMPTS!"
set "OK=0"
for /L %%I in (1,1,!ATTEMPTS!) do (
  if "!OK!"=="0" (
    set /a PCT=58 + %%I * 8
    echo.
    echo %C_ACC%  --- attempt %%I/!ATTEMPTS! ---%C_RST%
    call :bar !PCT!
    "%ADB%" -s !SERIAL! push "%SH_DIR%\kill-stuck.sh" /data/local/tmp/oneroot-kill.sh >nul
    "%ADB%" -s !SERIAL! shell sh /data/local/tmp/oneroot-kill.sh
    echo %C_MUTE%  LD_PRELOAD running...%C_RST%
    "%ADB%" -s !SERIAL! shell "LD_PRELOAD=!REMOTE_SO! /system/bin/id" > "%TEMP%\pc-temproot-out.txt" 2>&1
    type "%TEMP%\pc-temproot-out.txt"
    findstr /C:"uid=0(root)" /C:"root=1" "%TEMP%\pc-temproot-out.txt" >nul && set "OK=1"
    if "!OK!"=="0" (
      "%ADB%" -s !SERIAL! shell "/data/local/tmp/su -c /system/bin/id" > "%TEMP%\pc-temproot-out.txt" 2>&1
      type "%TEMP%\pc-temproot-out.txt"
      findstr /C:"uid=0(root)" /C:"root=1" "%TEMP%\pc-temproot-out.txt" >nul && set "OK=1"
    )
    if "!OK!"=="0" (
      "%ADB%" -s !SERIAL! shell "/apex/com.android.virt/bin/su -c /system/bin/id" > "%TEMP%\pc-temproot-out.txt" 2>&1
      type "%TEMP%\pc-temproot-out.txt"
      findstr /C:"uid=0(root)" /C:"root=1" "%TEMP%\pc-temproot-out.txt" >nul && set "OK=1"
    )
    if "!OK!"=="0" if %%I LSS !ATTEMPTS! timeout /t 3 /nobreak >nul
  )
)

call :step 6 6 "getenforce + summary"
"%ADB%" -s !SERIAL! shell getenforce
if "!OK!"=="1" (
  call :bar 100
  echo.
  echo %C_OK%  ************************************************************%C_RST%
  echo %C_OK%   SUCCESS  temporary root looks good                         %C_RST%
  echo %C_OK%  ************************************************************%C_RST%
  goto :ok
)
echo %C_BAD%  [FAIL]%C_RST% no uid=0 after !ATTEMPTS! attempt^(s^)
goto :fail

:ok
echo.
call :recommend
echo %C_MUTE%  tip: menu item 5 / "一键临时Root.cmd sponsor" opens WeChat QR%C_RST%
echo.
if "!NOPAUSE!"=="0" pause
exit /b 0

:fail
echo.
echo %C_BAD%  ended with errors%C_RST%
echo.
if "!NOPAUSE!"=="0" pause
exit /b 1

:open_github
start "" "%ONEIMS%"
echo %C_OK%  opened OneIMS GitHub%C_RST%
if "!NOPAUSE!"=="0" pause
exit /b 0

:open_releases
start "" "%RELEASES%"
echo %C_OK%  opened OneIMS Releases%C_RST%
if "!NOPAUSE!"=="0" pause
exit /b 0

:open_sponsor
if not exist "%SPONSOR%" (
  echo %C_BAD%  [FAIL]%C_RST% missing "%SPONSOR%"
  if "!NOPAUSE!"=="0" pause
  exit /b 1
)
start "" "%SPONSOR%"
echo %C_OK%  opened WeChat sponsor QR ^(same as OneIMS App^)%C_RST%
if "!NOPAUSE!"=="0" pause
exit /b 0

:banner
echo.
echo %C_TITLE%  ============================================================%C_RST%
echo %C_TITLE%   PC-TempRoot-UI   ^(pure CMD · no PowerShell^)              %C_RST%
echo %C_MUTE%   progress bar · OneIMS recommend · WeChat sponsor QR       %C_RST%
echo %C_TITLE%  ============================================================%C_RST%
echo.
exit /b 0

:recommend
echo %C_TITLE%  [OneIMS Recommend]%C_RST%
echo %C_MUTE%  After temp root, use OneIMS App for carrier / VoLTE tools.%C_RST%
echo %C_ACC%  GitHub  : %ONEIMS%%C_RST%
echo %C_ACC%  Releases: %RELEASES%%C_RST%
echo %C_ACC%  Sponsor : assets\sponsor_wechat.jpg%C_RST%
echo.
exit /b 0

:step
echo.
echo %C_TITLE%  [%~1/%~2]%C_RST% %~3
exit /b 0

:bar
setlocal EnableDelayedExpansion
set /a N=%~1
if !N! GTR 100 set N=100
if !N! LSS 0 set N=0
set /a FILL=N/5
set /a EMPTY=20-FILL
set "BAR="
for /L %%i in (1,1,!FILL!) do set "BAR=!BAR!#"
for /L %%i in (1,1,!EMPTY!) do set "BAR=!BAR!-"
echo %C_MUTE%  progress [%C_TITLE%!BAR!%C_MUTE%] !N!%%%C_RST%
endlocal
exit /b 0
