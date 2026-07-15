# 2026-07-15 · OneKuku 九段进度 / 设备详情常显 / 状态检查弹窗（2.0.25）

## 背景

真机反馈三件事：

1. 希望参考 Shizuku 式 `adb tcpip`：配对一次后，不重启、无 Wi‑Fi 仍可用。
2. 状态卡进度「还是四个」——应对齐九态，做成九段。
3. 「状态检查」不要跳排障页，改成与「配置快照」同级的 AlertDialog。

另：设备详情不要下拉折叠，改为常显。

## 方案

| 点 | 做法 |
|---|---|
| 九段进度 | `OneKukuCardPolicy.litStageCount` 1–9；`stageLabelRes()` 九短标签；`OneKukuStageProgress` 横向滚动 |
| 设备详情 | `StatusHero` 去掉展开/收起，有 `deviceInfo` 即常显 |
| 状态检查 | `HomeToolDialog.Status` + `buildStatusCheckLines`；快捷入口打开弹窗 |
| tcpip | 激活链路已有 `persistTcpip5555`（`adb tcpip 5555`）；本版不改协议，沿用既有持久化 |

## 版本

- `versionName`：`2.0.25`
- `versionCode`：`34`

## 验证

- `:app:testDebugUnitTest` + `:app:packageNamedDebugApk` → BUILD SUCCESSFUL
- 真机安装 `OneIms-2.0.25.apk`，`dumpsys` 核对 versionName
