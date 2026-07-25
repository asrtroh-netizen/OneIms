# 尽量屏蔽系统更新：叠 Settings + hosts

日期：2026-07-25  
版本：3.0.3（覆盖发布）

## 变更

`SystemUpdateShield` 在原有 `package` 组件禁用之外，增加两层「能用则用」：

1. **Settings**：授予 `WRITE_SECURE_SETTINGS` 后写入 `ota_disable_automatic_update=1`
2. **hosts**：有 `su` / Magisk·KSU 时写入模块 `oneims_ota_block`，挡  
   `ota.googlezip.net` / `ota-cache1.googlezip.net` / `ota-cache2.googlezip.net`  
   无 Root 则跳过，不把整次操作判失败

关闭开关时：恢复组件默认态、清 settings 值、删除 Magisk 模块目录。

## 后续（同日）

- hosts 增补：`googlezip.net` / `www` / `ota-cache3` / `proxy.googlezip.net`
- 偏好默认改为**开启**（`ConfigStore` / UI 默认；已手动关过的用户保持关）

## 验证

- `./gradlew :app:compileOnekukuDebugKotlin :app:compileOnelinkDebugKotlin`
- `./gradlew :app:packageDualDebugApks`
