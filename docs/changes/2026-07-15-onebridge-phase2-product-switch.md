# 2026-07-15 · OneBridge Phase2 产品切换

**类型**：架构 / 产品面  
**版本策略**：不升 versionName / versionCode

## 做了什么

1. **生命周期统一**
   - `PrivilegeBridge` 补 binder received/dead + 授权结果监听 API
   - `ShizukuPrivilegeBridge` / `OneBridgePrivilegeBridge` / `FallbackPrivilegeBridge`（双源扇出）实现
   - `BridgeBinderHolder` 拆分 received / dead 回调
   - `GuardService`、`MainActivity` 去掉直连 `rikka.shizuku.Shizuku` listener，改听 `PrivilegeBridges.current`
2. **产品文案**
   - 首页「启动核心」→「启动通道」
   - 总控卡 / 准备 / 下载 / 内嵌 ADB 等用户可见串改为「通道」叙事
   - 去掉「过渡换皮核心」用户可见措辞（安装回落逻辑 Phase3 再卸）
3. **回落策略**
   - 明确：本轮只用运行时 `FallbackPrivilegeBridge`，不加 `BuildConfig` 开关
4. **触发枚举**
   - 新增 `ReapplyTrigger.BRIDGE_READY`；历史 `shizuku_ready` 存储值仍可解析

## 验收

- `:app:compileDebugKotlin`
- `:app:testDebugUnitTest --tests PrivilegeBridgeTest`（含 Fallback 扇出）
- 真机：仅 OneBridge 拉起时 Guard 应能收到 binder ready（**NOT RUN 除非有设备**）

## 刻意未做（Phase3）

- 删除 `rikka.shizuku` Maven / `ShizukuProvider`
- 删除 `onekuku-core.apk` / 换皮包安装回落
- Tile 改为 binder 订阅（现状轮询可接受）
