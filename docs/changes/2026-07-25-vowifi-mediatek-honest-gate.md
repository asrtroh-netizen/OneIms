# 2026-07-25 · VoWiFi 联发科 / 非 Tensor 诚实门禁

## 背景

社区反馈（天玑等联发科机型）：VoWiFi 相关改动「改不了 / 不起作用」。  
`CompatChecker` 虽把非 Tensor 标为不支持，但 `ImsController.applyAll` / `setWfcMode` 此前仍会走写入，易出现假成功。

## 落地

| 项 | 行为 |
|---|---|
| `DeviceInfo.isMediaTek` / `supportsVowifiForceEnable` | 识别联发科/天玑；仅 Tensor 允许 VoWiFi 强开 |
| `ImsController.applyAll` | `enableVowifi=true` 且非 Tensor → 硬失败 + 明确文案（联发科单独文案） |
| `ImsController.setWfcMode` | 同上 |
| `CompatChecker` / 设备摘要 | 透出 SoC；联发科给 VoWiFi 专用建议 |

## 非目标（当时）

- 不宣称、不实现联发科 modem 侧 VoWiFi 强开（AOSP Provisioning 路径在 MTK 上通常无效）
- 不改 VoLTE / VoNR 其它写入语义；用户可关 VoWiFi 后继续尝试其它能力

## 后续修正（同日）

产品改为 **软件开门、OEM 随缘**：硬拒绝已撤销，见 `2026-07-25-vowifi-open-door-best-effort.md`。  
SoC 识别与兼容提示仍保留，仅作风险告知。

## 验证

- 单测：`DeviceInfoSocTest`
- 真机天玑：需人工确认关闭 VoWiFi 后提示文案；本环境 NOT RUN
