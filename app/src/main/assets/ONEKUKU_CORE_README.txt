# OneKuku / OneBridge 内置 APK

## 主路径（优先）

将 OneBridge Debug/Release APK 命名为 `oneims-bridge.apk`，放到本目录。

硬性要求：

- `applicationId` = `com.oneims.bridge`
- 构建模块：仓内 `:bridge`
- 变更说明：`docs/changes/2026-07-15-bundle-oneims-bridge-apk.md`

未装桥时，`prepare()` / 「启动核心」会优先弹出安装此包。

## 过渡回落（可选）

换皮核心仍可放 `onekuku-core.apk`：

- `applicationId` = `com.oneims.onekuku.core`
- 仅当 `oneims-bridge.apk` 缺失时才会被内置安装逻辑使用

禁止：把上游 `moe.shizuku.privileged.api` 原包改名冒充换皮核心。
