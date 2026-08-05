# 2026-08-05 · Temp Root 加速（对照 Root My Pixel）

## 对照结论

[XDA / Root My Pixel](https://xdaforums.com/t/get-root-on-bootloader-locked-pixels-using-root-my-pixel.4797178/) 是**手机 App**：Shizuku(UID 2000) 本机抽 so → IonStack → 可选 KernelSU late-load。几乎无 PC↔手机 adb 往返，所以体感快。

OneRoot（PC / Lite / UI）是 **adb 远程路径**，无法完全追上 App 本机一键；本轮只砍 PC 侧空等。

## 改动

| 项 | 旧 | 新 |
|---|---|---|
| attempts | 4 | 2 |
| per-attempt timeout | 180s | 90s |
| retry gap | 3s | 1s |
| so 解析 | 每次优先 GitHub | 本机 OneSo-assets → `.cache` → GitHub |
| LD_PRELOAD | 无设备侧超时 | `timeout 90s sh -c 'LD_PRELOAD=…'` |

涉及：`OneRoot/oneso.py`、`OneRoot/hub.py`、`scripts/temp-root-pc.ps1`；公开 ZIP 已重打并推 OneSo-assets。
