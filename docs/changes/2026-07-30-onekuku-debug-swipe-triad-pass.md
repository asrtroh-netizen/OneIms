# 2026-07-30 · onekuku Debug 划掉三连真机验收

## 制品

- APK：`app/build/outputs/apk/onekuku/debug/app-onekuku-debug.apk`（约 50.2 MB，2026-07-30 18:42）
- 设备：`c0b76e3b` · `22061218C`（小米）
- 安装：`adb install -r` → Success

## 步骤与证据

| 步 | 操作 | 结果 |
|---|---|---|
| 1 就绪 | 已激活态拉起 App | `onebridge_server` pid=**1851** |
| 2 划掉近似 | `am force-stop com.oneims.app` | App pid 清空；**bridge 仍为 1851**（`survived=True`） |
| 3 重开 | `am start …MainActivity` | 新 App pid=32697；bridge **仍为 1851**；约 1s 内 log `OneIMS-OneKuku: state=ACTIVE` |

## 结论

**PASS**（本机 force-stop 强近似划掉收进程）：通道进程未死，重开秒级 ACTIVE。

## 命令摘要

```bat
E:\GQ\One\_toolchain\android-sdk\platform-tools\adb.exe install -r app\build\outputs\apk\onekuku\debug\app-onekuku-debug.apk
adb shell pidof onebridge_server
adb shell am force-stop com.oneims.app
adb shell am start -n com.oneims.app/.MainActivity
adb logcat -d | findstr OneIMS-OneKuku
```
