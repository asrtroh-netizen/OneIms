# OneBridge Phase1 脚手架 + start 改指向

**日期**：2026-07-15  
**类型**：架构 / 新模块  
**版本策略**：不升 OneIMS versionName/versionCode

## 做了什么

1. 新增 `:bridge` 应用模块（`applicationId=com.oneims.bridge`）
   - `assets/start.sh` → 安装后写出到 `Android/data/com.oneims.bridge/start.sh`
   - `BridgeService.main`：`app_process` 入口 + 最小 Binder（ping/getUid/check/transactRemote）
   - 仅代理 MVP 四服务：activity / carrier_config / isub / phone
2. OneIMS 客户端
   - `BridgeBinderProvider`（`com.oneims.app.onebridge`）接收 binder
   - `OneBridgePrivilegeBridge` + `FallbackPrivilegeBridge`（优先桥，回落 Shizuku）
3. start 指向：`CANDIDATE_PACKAGES` / `adbStartCommand` / EmbeddedAdb 默认优先 `com.oneims.bridge`

## 验收（本轮）

- `:bridge:assembleDebug` + `:app:compileDebugKotlin` + 相关单测
- 真机：安装 bridge → 打开一次写出 start.sh → 无线调试执行 start → binder 送达 → ping（**真机 NOT RUN 除非有设备**）

## 刻意未做

- 完整授权 UI / 签名校验加固
- 把 bridge APK 打进 OneIMS assets（下一步）
- 移除 Shizuku 依赖
