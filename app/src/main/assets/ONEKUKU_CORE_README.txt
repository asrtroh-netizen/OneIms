# OneKuku / OneBridge 内置 APK

## 唯一路径（Phase3）

将 OneBridge APK 命名为 `oneims-bridge.apk`，放到本目录。

硬性要求：

- `applicationId` = `com.oneims.bridge`
- 构建模块：仓内 `:bridge`
- 变更说明：`docs/changes/2026-07-15-onebridge-phase3-drop-shizuku.md`

未装桥时，`prepare()` / 「启动通道」会弹出安装此包。

## 已移除

- `onekuku-core.apk` 换皮 Core（不再内置、不再探测）
- 上游 `moe.shizuku.privileged.api` 安装引导
- `rikka.shizuku` 客户端依赖
