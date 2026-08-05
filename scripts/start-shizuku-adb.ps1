# 用当前 USB/无线 adb 拉起 Shizuku 服务（临时 Root 后若 root 版 shizuku_server 卡住，先杀掉再启）
$ErrorActionPreference = "Stop"
$Adb = "E:\GQ\One\_toolchain\android-sdk\platform-tools\adb.exe"
if (-not (Test-Path $Adb)) { $Adb = "adb" }

& $Adb wait-for-device | Out-Null
$apkLine = & $Adb shell "pm path moe.shizuku.privileged.api"
$apk = ($apkLine -replace "package:", "").Trim()
if (-not $apk) { throw "Shizuku 未安装 (moe.shizuku.privileged.api)" }

# 清掉可能卡死的 root 版 server
& $Adb shell "/data/local/tmp/su -c '/system/bin/killall -9 shizuku_server' 2>/dev/null; /system/bin/killall -9 shizuku_server 2>/dev/null; true" | Out-Null

$lib = ($apk -replace "base.apk$", "") + "lib/arm64/libshizuku.so"
$exists = (& $Adb shell "test -f $lib && echo YES || echo NO").Trim()
if ($exists -ne "YES") {
  $lib = "/data/local/tmp/libshizuku.so"
  & $Adb shell "chmod 755 $lib"
}

Write-Host "==> start $lib --apk=$apk"
& $Adb shell "$lib --apk=$apk"
Start-Sleep -Seconds 2
& $Adb shell "ps -A | grep shizuku || true"
& $Adb shell "am start -n moe.shizuku.privileged.api/moe.shizuku.manager.MainActivity"
Write-Host "==> done. 首页服务区应为 Active。无线调试「未激活」可忽略（USB 直拉不依赖配对）。"
Write-Host "若一定要手机内无线配对：系统设置 → 开发者选项 → 无线调试 → 使用配对码配对设备。"
