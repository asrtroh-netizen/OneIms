# 2.1.5 · 开机静默打开无线调试（WRITE_SECURE_SETTINGS）

## 动机

Pixel 等机型重启后系统常会关掉 `adb_wifi_enabled`，配对身份仍在；仅打开设置页（2.1.4）仍偏「半自动」。对齐 Shizuku/Tasker：激活成功后留下 `WRITE_SECURE_SETTINGS`，开机已配对时直接写回开关再无码直连。

## 改动

1. **激活成功后**：经已建立的 ADB shell 执行 `pm grant <pkg> WRITE_SECURE_SETTINGS`，并立刻尝试写 `adb_wifi_enabled=1`。
2. **开机已配对**：`ensureOneKukuReadyForBoot` 先 `tryEnableAdbWifi`；成功则短等 3s 再 `activateExistingOrNeedPair`。
3. **降级**：无权限时仍打开无线调试设置页 + 8s 重试（保留 2.1.4 行为）。

## 版本

- `versionName=2.1.5` / `versionCode=50`
- 产物：`OneIms-2.1.5.apk`

## 验证

- `:app:packageNamedDebugApk`
- 真机：首次激活成功后 `dumpsys package` 应见 `WRITE_SECURE_SETTINGS`；重启后应尽量无码就绪（adb 设备未连时标 NOT RUN）
