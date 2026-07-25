# Shizuku 13.7.1-asrtroh：开机自启对齐 OneKuku + 状态卡小方块

## 背景

用户反馈：开机仍不能自启、授权列表为空；希望状态卡学 OneIms（检查式小方块弹层）；并考虑回基官方 13.6.0。

## 结论

- **不回基 13.6.0**：官方缺 thedjchi 无线调试开机链路；在现有分叉移植 OneKuku 特性成本更低、风险更小。
- **授权列表空**：服务未运行时 binder 不可用 → 列表读不出；已改为明确文案，不再像「0 个授权」。
- **自启**：去掉 WorkManager `UNMETERED` 门闩；worker 内 `waitForWifiClient`；`WifiReadyMonitor` 晚到再试；开机自启联动 Watchdog。

## 产物

- 仓：https://github.com/asrtroh-netizen/shizuku
- Release：https://github.com/asrtroh-netizen/shizuku/releases/tag/v13.7.1-asrtroh
- APK：`shizuku-v13.7.1-asrtroh-release.apk`

## 真机验收（人工）

1. 设置里打开「开机自启」（会联动 Watchdog）
2. 忽略电池优化
3. 冷重启，确认 Wi‑Fi 连上后 Shizuku 自动 Running
4. 点状态卡看 2×2 小方块与详情弹层
5. 服务起来后再看授权列表
