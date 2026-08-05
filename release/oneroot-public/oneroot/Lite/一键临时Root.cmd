@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
cd /d "%~dp0"
title PC-TempRoot-Lite
color 0B

rem Enable ANSI colors when supported
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
set "REMOTE_SO=/data/local/tmp/preload-comet.so"
set "ATTEMPTS=2" & rem FAST defaults vs Root My Pixel on-device feel
set "DRY=0"
set "NOPAUSE=0"
set "CLEANUP=0"
set "AGGRESSIVE=0"
if /i "%~1"=="dry" set "DRY=1"
if /i "%~1"=="-dry" set "DRY=1"
if /i "%~1"=="--dry-run" set "DRY=1"
if /i "%~1"=="cleanup" set "CLEANUP=1"
if /i "%~1"=="-cleanup" set "CLEANUP=1"
if /i "%~1"=="--cleanup" set "CLEANUP=1"
if /i "%~1"=="cleanup-aggressive" (
  set "CLEANUP=1"
  set "AGGRESSIVE=1"
)
if /i "%~1"=="--cleanup-aggressive" (
  set "CLEANUP=1"
  set "AGGRESSIVE=1"
)
if /i "%~2"=="aggressive" set "AGGRESSIVE=1"
if /i "%~1"=="nopause" set "NOPAUSE=1"
if /i "%~2"=="nopause" set "NOPAUSE=1"
if /i "%PC_TEMPROOT_NOPAUSE%"=="1" set "NOPAUSE=1"

cls
echo.
echo %C_TITLE%  ============================================================%C_RST%
echo %C_TITLE%   PC-TempRoot-Lite                                          %C_RST%
echo %C_MUTE%   portable console temp root · no PIPE deadlock · no Python · bundled adb/so   %C_RST%
echo %C_TITLE%  ============================================================%C_RST%
echo.

if not exist "%ADB%" (
  echo %C_BAD%  [FAIL]%C_RST% missing adb: "%ADB%"
  goto :fail
)

if "!CLEANUP!"=="1" (
  call :step 1 4 "adb devices"
) else (
  call :step 1 7 "adb devices"
)
"%ADB%" start-server >nul 2>&1
"%ADB%" devices
set "SERIAL="
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
  if /i "%%B"=="device" if not defined SERIAL set "SERIAL=%%A"
)
if not defined SERIAL (
  echo %C_BAD%  [FAIL]%C_RST% no authorized device. plug USB and allow debugging.
  goto :fail
)
echo %C_OK%  [ok]%C_RST% serial=!SERIAL!
call :bar 18

if "!CLEANUP!"=="1" goto :do_cleanup

call :step 2 7 "read device / build"
set "DEVICE="
set "BUILD="
for /f "usebackq delims=" %%A in (`"%ADB%" -s !SERIAL! shell getprop ro.product.device`) do set "DEVICE=%%A"
for /f "usebackq delims=" %%A in (`"%ADB%" -s !SERIAL! shell getprop ro.build.id`) do set "BUILD=%%A"
set "DEVICE=!DEVICE: =!"
set "BUILD=!BUILD: =!"
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

call :step 3 7 "match preload so"
echo %C_OK%  [ok]%C_RST% so=!SOFILE!
echo %C_MUTE%       remote=!REMOTE_SO!%C_RST%
call :bar 45

if "!DRY!"=="1" (
  echo.
  echo %C_ACC%  [dry-run]%C_RST% plan ready · no exploit executed
  echo %C_MUTE%  re-run without "dry" to push + LD_PRELOAD + su verify%C_RST%
  call :bar 100
  goto :ok
)

call :step 4 7 "pre-clean residuals"
echo %C_MUTE%  cleanup-residuals + best-effort su sock clean%C_RST%
"%ADB%" -s !SERIAL! push "%SH_DIR%\cleanup-residuals.sh" /data/local/tmp/oneroot-cleanup.sh >nul
"%ADB%" -s !SERIAL! shell sh /data/local/tmp/oneroot-cleanup.sh
"%ADB%" -s !SERIAL! shell "/data/local/tmp/su -c 'rm -f /data/local/tmp/temp_su.sock /dev/socket/temp_su.sock; echo SU_CLEAN'" >nul 2>&1
call :bar 50

call :step 5 7 "adb push"
"%ADB%" -s !SERIAL! push "!SOFILE!" "!REMOTE_SO!"
if errorlevel 1 (
  echo %C_BAD%  [FAIL]%C_RST% adb push
  goto :fail
)
"%ADB%" -s !SERIAL! shell chmod 644 !REMOTE_SO!
echo %C_OK%  [ok]%C_RST% pushed
call :bar 58

call :step 6 7 "exploit attempts x!ATTEMPTS!"
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
    "%ADB%" -s !SERIAL! shell "timeout 90s sh -c 'LD_PRELOAD=!REMOTE_SO! /system/bin/id'" > "%TEMP%\pc-temproot-out.txt" 2>&1
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
    if "!OK!"=="0" if %%I LSS !ATTEMPTS! (
      echo %C_MUTE%  no uid=0 yet, retry in 1s...%C_RST%
      timeout /t 1 /nobreak >nul
    )
  )
)

call :step 7 7 "getenforce + summary"
"%ADB%" -s !SERIAL! shell getenforce
if "!OK!"=="1" (
  call :bar 96
  echo.
  echo %C_OK%  ************************************************************%C_RST%
  echo %C_OK%   SUCCESS  temporary root looks good                         %C_RST%
  echo %C_OK%  ************************************************************%C_RST%
  call :rebind_shizuku
  call :bar 100
  goto :ok
)
echo.
echo %C_BAD%  [FAIL]%C_RST% no uid=0 after !ATTEMPTS! attempt^(s^)
goto :fail

:do_cleanup
rem Mirror Hub cleanup_temp_root_residuals (no Python)
call :step 2 4 "push cleanup script"
"%ADB%" -s !SERIAL! push "%SH_DIR%\cleanup-residuals.sh" /data/local/tmp/oneroot-cleanup.sh
if errorlevel 1 (
  echo %C_BAD%  [FAIL]%C_RST% push cleanup script
  goto :fail
)
call :bar 40

call :step 3 4 "shell cleanup ^(kill + best-effort rm^)"
"%ADB%" -s !SERIAL! shell sh /data/local/tmp/oneroot-cleanup.sh
call :bar 65

call :step 4 4 "su cleanup if uid=0"
set "ROOT_OK=0"
"%ADB%" -s !SERIAL! shell "/data/local/tmp/su -c /system/bin/id" > "%TEMP%\pc-temproot-clean.txt" 2>&1
findstr /C:"uid=0(root)" "%TEMP%\pc-temproot-clean.txt" >nul && set "ROOT_OK=1"
if "!ROOT_OK!"=="0" (
  "%ADB%" -s !SERIAL! shell "/apex/com.android.virt/bin/su -c /system/bin/id" > "%TEMP%\pc-temproot-clean.txt" 2>&1
  findstr /C:"uid=0(root)" "%TEMP%\pc-temproot-clean.txt" >nul && set "ROOT_OK=1"
)
if "!ROOT_OK!"=="1" (
  if "!AGGRESSIVE!"=="1" (
    echo %C_ACC%  [aggressive]%C_RST% teardown sock + su binary together
    "%ADB%" -s !SERIAL! shell "/data/local/tmp/su -c 'pkill -9 -f preload-comet.so 2>/dev/null; killall -9 id 2>/dev/null; rm -f /data/local/tmp/temp_su.sock /dev/socket/temp_su.sock /data/local/tmp/su; echo TEARDOWN_OK'"
    call :bar 100
    echo.
    echo %C_OK%  CLEANUP OK ^(mode=su-teardown^)%C_RST%
    goto :ok
  )
  echo %C_OK%  [ok]%C_RST% uid=0 live — safe cleanup keeps daemon ^(no sock/su rm^)
  "%ADB%" -s !SERIAL! shell "pkill -9 -f preload-comet.so 2>/dev/null; killall -9 id 2>/dev/null; echo KILL_OK"
  call :bar 100
  echo.
  echo %C_OK%  CLEANUP OK ^(mode=su-keep^)%C_RST%
  echo %C_MUTE%  tip: cleanup-aggressive to fully remove temp root%C_RST%
  goto :ok
)
echo %C_ACC%  [warn]%C_RST% no uid=0 — shell cannot delete root-owned residuals
echo %C_MUTE%  tip: re-run one-click Root to overwrite; then cleanup-aggressive to remove%C_RST%
call :bar 100
echo.
echo %C_ACC%  CLEANUP PARTIAL ^(mode=blocked^)%C_RST%
goto :ok

:ok
echo.
echo %C_MUTE%  tip: cleanup = 一键临时Root.cmd cleanup  ^|^|  清理残留.cmd%C_RST%
echo %C_MUTE%  tip: PowerShell UI pack = PC-TempRoot-UI%C_RST%
echo.
if "!NOPAUSE!"=="0" pause
exit /b 0

:fail
echo.
echo %C_BAD%  ended with errors%C_RST%
echo.
if "!NOPAUSE!"=="0" pause
exit /b 1

:rebind_shizuku
rem After temp root: kill possible root zombie, then start Shizuku as SHELL.
rem FORBIDDEN: su -c libshizuku.so  (root/kernel server → App binder dies)
echo.
echo %C_TITLE%  [rebind]%C_RST% Shizuku as shell uid %C_MUTE%^(FORBIDDEN: su -c libshizuku^)%C_RST%
"%ADB%" -s !SERIAL! shell "/data/local/tmp/su -c '/system/bin/killall -9 shizuku_server' 2>/dev/null; /system/bin/killall -9 shizuku_server 2>/dev/null; true" >nul 2>&1
timeout /t 1 /nobreak >nul
set "APK="
for /f "usebackq delims=" %%A in (`"%ADB%" -s !SERIAL! shell pm path moe.shizuku.privileged.api`) do (
  set "LINE=%%A"
  set "LINE=!LINE: =!"
  if /i "!LINE:~0,8!"=="package:" set "APK=!LINE:package:=!"
)
if not defined APK (
  echo %C_MUTE%  [skip] Shizuku not installed%C_RST%
  exit /b 0
)
set "LIB=!APK:base.apk=lib/arm64/libshizuku.so!"
echo %C_MUTE%  start !LIB! --apk=!APK!%C_RST%
"%ADB%" -s !SERIAL! shell "!LIB! --apk=!APK!"
"%ADB%" -s !SERIAL! shell "ps -A | grep shizuku_server || true"
echo %C_OK%  [ok]%C_RST% rebind attempted via adb shell ^(not su^)
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
