# 2026-07-30 · 对齐 PixelIMS（pixel-volte-patch）对国产/OEM 的容错

> 用户澄清：要对齐的是 **PixelIMS 处理国产机那一类东西**，不是改 Pixel 通信/开机主战场。  
> 上游：https://github.com/kyujin-cho/pixel-volte-patch

## PixelIMS「国产/OEM 容错」到底是什么

上游**几乎没有** `if (Xiaomi)` 品牌分支。所谓「国产机也更稳」，实质是这几条**通用 OEM 苛刻环境**处理：

| # | PixelIMS 做法 | 解决什么 | OneIMS 状态 |
|---|---|---|---|
| 1 | `BrokerInstrumentation` 内 `persistent=true` 遇 `SecurityException` → 同会话改 `false` | QPR2/OEM 拒持久导致 **toggle 闪退**（Issue #398） | **DONE**（`BrokerInstrumentation` + `ok-temporary`） |
| 2 | Writer/调用链外层不把拒写直接炸死进程 | UI 可继续用 | **DONE**（`runOperation`+`runCatching`；Writer 降级） |
| 3 | 写集偏小（主 CarrierConfig；少碰多键 provisioning） | 接触面小 | **部分**：主路径仍多键；国产侧 soft 26/27/68 + 小米 28 |
| 4 | 订阅 API `NoSuchMethodError` 多签名回退 | OEM/API 漂移 | **既有**（telephony 多处 `runCatching`/探测） |
| 5 | 无嵌入式 ADB/重 Boot 通道 | 冷启爆炸半径小 | **产品差异**：OneIMS 必须保留 Pixel 开机自启（硬保证） |
| 6 | 异常打 Log | 可排障 | **增强 DONE**：`DiagFileLogger` 落盘+导出 |

## 明确不对齐 / 禁止用对齐伤主链路

- **不对齐**「砍掉开机自启」——Pixel 开机恢复是硬保证。
- **不对齐**「VoLTE key=10 soft」——通信主链路硬保证。
- **国产分支**只能加在 `OemDeviceCompat` 门控或 soft key 白名单，默认不进 Pixel。

## 验证

- 单测：`ProvisioningWritePolicyTest` / `CarrierConfigOverrideWriterTest` / `OemDeviceCompatTest`
- 编译：双 flavor
- 小米真机闪退：需导出详细日志（现场）
