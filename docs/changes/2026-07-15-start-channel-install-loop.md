# 2026-07-15 · 启动通道「已装仍提示未装」死循环

**版本策略**：不升 versionName / versionCode

## 现象

点「启动通道」→ 系统安装 → 进入空白「OneKuku 通道」页 → 返回再点又弹「还没装 OneKuku 通道」。

## 根因

1. **P0**：`targetSdk=36` 下 App Manifest **未声明** `<queries>` 包 `com.oneims.bridge`，`PackageManager.getPackageInfo` 对已安装包也失败 → `isInstalled==false` 死循环。
2. **P1**：安装器「打开」进入通道 LAUNCHER，旧页几乎空白，用户不知道要回 OneIMS。

## 修复

- App `AndroidManifest.xml`：`<queries>` 声明 `com.oneims.bridge`
- `MainActivity`：`awaitingCoreInstall` + `ON_RESUME` 检测到已装则自动续跑配对
- `BridgeStatusActivity`：明确文案 +「打开 OneIMS」按钮
- Bridge Manifest：`<queries>` 声明 `com.oneims.app`

## 验证

- 编译 `:app:assembleDebug` / `:bridge:assembleDebug`
- 真机：装好通道后不再弹「还没装」→ **待用户验证**
