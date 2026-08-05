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

# 先杀干净：重启后常见「进程还在但 binder 半死」→ 点「启动」一直转圈
& $Adb shell "/system/bin/killall -9 shizuku_server 2>/dev/null; true" | Out-Null
Start-Sleep -Milliseconds 400

Write-Host "==> start $lib --apk=$apk"
& $Adb shell "$lib --apk=$apk"
Start-Sleep -Seconds 2
& $Adb shell "ps -A | grep shizuku || true"

# 刷新 Manager UI，清掉「正在启动」转圈态（服务已由 ADB 拉起，勿再点无线「启动」）
& $Adb shell "am force-stop moe.shizuku.privileged.api"
Start-Sleep -Milliseconds 400
& $Adb shell "am start -n moe.shizuku.privileged.api/moe.shizuku.manager.MainActivity"
Write-Host "==> done. 首页应为 Shizuku Active / 已就绪。无线区「未激活」可忽略。"
Write-Host "不要再点无线调试里的「启动」（未配对会一直转）。需要无线：开发者选项 → 无线调试 → 配对码。"
