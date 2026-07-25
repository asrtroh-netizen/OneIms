# 2026-07-25 · 独家功能：尽量屏蔽系统更新

## 做什么

独家页开关「尽量屏蔽系统更新」：在特权通道就绪时，通过 `package` 服务禁用 Pixel 常见 OTA 包/组件。

## 边界

- **尽量**，不保证挡死所有更新
- 可能影响 Google Play 系统更新
- 需 OneKuku / Shizuku 通道；关闭开关可恢复
- OneBridge / PrivilegeBridge MVP 白名单新增 `package`

## 入口

`ExperimentalScreen` · `SystemUpdateShield` · `ConfigStore.system_update_shield`
