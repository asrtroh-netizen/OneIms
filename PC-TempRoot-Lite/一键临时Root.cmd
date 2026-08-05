@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
cd /d "%~dp0"
title PC Temp Root Lite

set "ADB=%~dp0adb\adb.exe"
set "SO_DIR=%~dp0so"
set "SH_DIR=%~dp0sh"
set "REMOTE_SO=/data/local/tmp/preload-comet.so"
set "ATTEMPTS=4"
set "DRY=0"
set "NOPAUSE=0"
if /i "%~1"=="dry" set "DRY=1"
if /i "%~1"=="-dry" set "DRY=1"
if /i "%~1"=="--dry-run" set "DRY=1"
if /i "%~1"=="nopause" set "NOPAUSE=1"
if /i "%~2"=="nopause" set "NOPAUSE=1"
if /i "%PC_TEMPROOT_NOPAUSE%"=="1" set "NOPAUSE=1"

echo ==^> PC-TempRoot-Lite  (console only, no Python / no UI)
echo.

if not exist "%ADB%" (
  echo [FAIL] missing adb: "%ADB%"
  goto :fail
)

echo [1/6] adb devices
"%ADB%" start-server >nul 2>&1
"%ADB%" devices
set "SERIAL="
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
  if /i "%%B"=="device" if not defined SERIAL set "SERIAL=%%A"
)
if not defined SERIAL (
  echo [FAIL] no authorized device.
  goto :fail
)
echo     using serial=!SERIAL!

echo [2/6] read device / build
set "DEVICE="
set "BUILD="
for /f "usebackq delims=" %%A in (`"%ADB%" -s !SERIAL! shell getprop ro.product.device`) do set "DEVICE=%%A"
for /f "usebackq delims=" %%A in (`"%ADB%" -s !SERIAL! shell getprop ro.build.id`) do set "BUILD=%%A"
set "DEVICE=!DEVICE: =!"
set "BUILD=!BUILD: =!"
for /f "delims=" %%A in ("!DEVICE!") do set "DEVICE=%%A"
for /f "delims=" %%A in ("!BUILD!") do set "BUILD=%%A"
echo     device=!DEVICE!  build=!BUILD!

set "SOFILE=%SO_DIR%\preload-!DEVICE!-!BUILD!.so"
if not exist "!SOFILE!" set "SOFILE=%SO_DIR%\preload-comet.so"
if not exist "!SOFILE!" (
  echo [FAIL] no matching so
  goto :fail
)
echo [3/6] so=!SOFILE!

if "!DRY!"=="1" (
  echo.
  echo dry-run only.
  goto :ok
)

echo [4/6] adb push
"%ADB%" -s !SERIAL! push "!SOFILE!" "!REMOTE_SO!"
if errorlevel 1 goto :fail
"%ADB%" -s !SERIAL! shell chmod 644 !REMOTE_SO!

echo [5/6] exploit attempts
set "OK=0"
for /L %%I in (1,1,!ATTEMPTS!) do (
  if "!OK!"=="0" (
    echo     --- attempt %%I/!ATTEMPTS! ---
    "%ADB%" -s !SERIAL! push "%SH_DIR%\kill-stuck.sh" /data/local/tmp/oneroot-kill.sh >nul
    "%ADB%" -s !SERIAL! shell sh /data/local/tmp/oneroot-kill.sh
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

echo [6/7] getenforce
"%ADB%" -s !SERIAL! shell getenforce
if "!OK!"=="0" (
  echo [FAIL] no uid=0
  goto :fail
)

echo [7/7] rebind Shizuku as shell ^(NEVER su -c libshizuku^)
REM root 态 shizuku_server 会让 App binder 掉线；只允许 adb shell 拉起
"%ADB%" -s !SERIAL! shell "/data/local/tmp/su -c '/system/bin/killall -9 shizuku_server' 2>/dev/null; /system/bin/killall -9 shizuku_server 2>/dev/null; true" >nul 2>&1
timeout /t 1 /nobreak >nul
for /f "tokens=2 delims=:" %%P in ('"%ADB%" -s !SERIAL! shell pm path moe.shizuku.privileged.api 2^>nul') do set "APK=%%P"
set "APK=!APK: =!"
if defined APK (
  set "LIB=!APK:base.apk=lib/arm64/libshizuku.so!"
  echo     shell-start !LIB!
  "%ADB%" -s !SERIAL! shell "!LIB! --apk=!APK!"
  "%ADB%" -s !SERIAL! shell "ps -A | grep shizuku_server || true"
) else (
  echo     WARN: Shizuku not installed, skip rebind
)
echo SUCCESS
goto :ok

:ok
if "!NOPAUSE!"=="0" pause
exit /b 0
:fail
if "!NOPAUSE!"=="0" pause
exit /b 1
