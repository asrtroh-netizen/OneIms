# 2026-07-30 · Pixel 9 Pro Fold 装机 + 重启自启采证

## 设备

- `47111FDKD0009J` · Pixel 9 Pro Fold (`comet`)
- adb：`E:\GQ\One\_toolchain\android-sdk\platform-tools\adb.exe`

## 安装（均 Success）

| 制品 | 包名 | versionName |
|---|---|---|
| `app-onekuku-debug.apk` | `com.oneims.app` | `3.0.9-onekuku` (vc 79) |
| `app-onelink-debug.apk` | `com.oneims.onelink` | `3.0.9-onelink` (vc 79) |
| `shizuku-vV15.1.0-release.apk` | `moe.shizuku.privileged.api` | `V15.1.0` (vc 151000) |

## 重启后（boot_completed=1，约 +20s）

| 进程 | 结果 |
|---|---|
| `shizuku_server`（V15） | **有**（例：8172） |
| `moe.shizuku.privileged.api` | **有** |
| `com.oneims.app` / `com.oneims.onelink` | **有**（开机后起来） |
| `onekuku_server`（CARE_MIN 宿主内嵌） | **无** |
| `onebridge_server` | **无** |

App 日志：`ShizukuProvider: sendBinder … living binder` → `OneIMS-OneKuku: state=ACTIVE` / `wake: already activated (instant)`。

## 结论

1. **三包安装 + 重启流程完成。**
2. **开机自启（通道可用）**：依赖 **V15 外置 `shizuku_server`** 已起来，OneIMS 收到 binder 后秒 ACTIVE。
3. **CARE_MIN 内嵌 `onekuku_server` 冷启动未自起**：重启后无线调试通常未就绪，宿主无法用 adb shell 拉起内嵌 server——与「内循环独立版冷启仍需一次无线调试/Root 激活」一致。划掉保活 ≠ 冷启自启。
