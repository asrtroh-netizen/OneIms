# OneKuku 核心组件（可选内置）

将**换皮后的**核心服务 APK 命名为 `onekuku-core.apk`，放到：

```
app/src/main/assets/onekuku-core.apk
```

要求：

- `applicationId` = `com.oneims.onekuku.core`（优先）
- 应用显示名 = OneKuku 核心（或同等品牌，不出现上游商店名）
- 构建说明见 `docs/changes/2026-07-14-onekuku-branded-core-package.md`

若未放置该文件：

1. 运行时若已装换皮包或上游兼容包，仍可激活；  
2. 否则会尝试从公开 Release 下载兼容核心（过渡期，系统列表可能仍见上游名）。

许可与归属以该 APK 上游/Fork 许可证为准；分发前请自行核对。
