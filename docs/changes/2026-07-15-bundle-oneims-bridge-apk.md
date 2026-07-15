# 2026-07-15 · 内置 OneBridge APK 到 assets

**类型**：架构 / 打包  
**版本策略**：不升 versionName / versionCode（按「改 bug / 工程补齐不升版」约定；本轮亦非发版）

## 做了什么

1. 将 `:bridge` Debug 产物拷入 `app/src/main/assets/oneims-bridge.apk`
   - `applicationId` = `com.oneims.bridge`
   - 显示名 = `OneKuku 通道`
   - versionName = `0.1.0-mvp`
2. `OneKukuCoreComponent` 内置安装顺序改为：
   - 优先 `oneims-bridge.apk`
   - 缺失时回落 `onekuku-core.apk`（过渡）
3. `.gitignore` 增加 `!app/src/main/assets/oneims-bridge.apk`
4. 更新 `ONEKUKU_CORE_README.txt` 与单测契约

## 验收

- `aapt dump badging`：`package: name='com.oneims.bridge'`
- `:app:compileDebugKotlin` + `OneKukuCoreComponentTest`
- 真机：未装桥时点「启动核心」应弹出安装 `OneKuku 通道`（**真机 NOT RUN 除非有设备**）

## 刻意未做

- 未删除 `onekuku-core.apk`（保留回落）
- 未做 bridge Release 签名正式分发
- 真机 binder ping 联调仍待下一刀
