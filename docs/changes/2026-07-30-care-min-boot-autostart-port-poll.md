> ⚠️ **已废止现行方案（2026-07-30）**：见 `docs/changes/2026-07-30-abolish-onekuku-mini-care-min.md`。下文仅考古。

# 2026-07-30 · CARE_MIN 重启自启：等 adbd 口，勿误报要码

## 现象

Pixel 重启解锁后：`has_paired_once=true`、`WRITE_SECURE_SETTINGS` 已授、`adb_wifi_enabled=1`，但仍报 `wireless needs pairing code`，`onekuku_server` 未起，能力重放跳过。

## 根因

1. 重启后 setting 已开 ≠ tcpip/TLS 口已 LISTEN；冷窗内连 `5555` 得 `ECONNREFUSED`。
2. `ALREADY_ON` 路径原先直接 skip wait，过早 `NeedPairingCode`。
3. `NEED_USER` 仍占「本开机已尝试」，`USER_PRESENT` 重试无法再重放。

## 修复

- `OneKukuHostServerBootstrap`：已配对误报要码时轮询端口再激活；`pingBinder` 计存活
- `OneKukuEmbeddedAdbActivator`：已配对 connect 失败后最长约 40s 等口
- `OneKukuBootRestoreCoordinator`：`ALREADY_ON` 也轮询口；多轮重试；`NEED_USER` 清 attempted

