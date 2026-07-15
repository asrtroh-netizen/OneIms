# 2026-07-15 · OneBridge Phase3 卸 Shizuku 依赖

**类型**：架构 / 依赖卸载  
**版本策略**：不升 versionName / versionCode

## 做了什么

1. **编译依赖**
   - 删除 `dev.rikka.shizuku:api` / `provider`
   - 删除 Manifest `rikka.shizuku.ShizukuProvider`
   - 删除 `ShizukuPrivilegeBridge.kt`
2. **运行时**
   - `PrivilegeBridges.current` 仅挂 `OneBridgePrivilegeBridge`（不再 Fallback 到 Shizuku）
3. **安装/探测**
   - `CANDIDATE_PACKAGES` 仅 `com.oneims.bridge`
   - 删除 `assets/onekuku-core.apk`
   - 下载解析只认 OneBridge 命名资产
4. **文档 / 测试**
   - 立项 Phase3 标完成；本变更说明；单测契约更新

## 验收

- `:app:compileDebugKotlin`
- `OneKukuCoreComponentTest` + `PrivilegeBridgeTest`
- 全仓无 `import rikka.shizuku` / 无 `dev.rikka.shizuku` 依赖
- 真机：装 bridge → start → ping → 一键恢复（**NOT RUN 除非有设备**）

## 风险（必须知情）

Phase1 真机 binder 联调此前标 NOT RUN。硬卸后**没有** Shizuku 安全网；若 OneBridge 在某 OEM 上失败，需 git 回滚本提交或临时恢复依赖。

## 刻意未做

- 全库重命名 `shizuku*` 内部变量 / string key（无行为收益）
- 邻仓 OneKukuCore 物理删除（建议归档，本仓不管）
- `ShizukuManager` / `ShizukuSetupHelper` 类名保留作兼容门面（已不依赖 rikka）
