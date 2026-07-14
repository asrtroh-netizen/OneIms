# OneKuku 核心组件（内置）

将**换皮后的**核心服务 APK 命名为 `onekuku-core.apk`，放到本目录。

硬性要求：

- `applicationId` = `com.oneims.onekuku.core`
- 应用显示名 = OneKuku 核心（不得出现上游商店品牌名）
- 构建说明：`docs/changes/2026-07-14-onekuku-branded-core-package.md`

禁止：把上游 `moe.shizuku.privileged.api` 原包改名冒充换皮核心。

若未放置该文件：仅当设备已安装换皮包（或过渡期兼容包）时可激活；
**不会**再自动从上游公开仓库下载充数。
