# 2026-07-25 · VoWiFi：软件开门、OEM 随缘

## 策略

产品确认：**只管软件侧把门打开**；联发科 / 非 Tensor 最终显不显示无法左右，不再硬拒绝写入。

## 变更

| 项 | 行为 |
|---|---|
| `ImsController.applyAll` | 去掉非 Tensor VoWiFi 硬失败；成功后附加 best-effort 提示 |
| `ImsController.setWfcMode` | 同上，允许尝试 |
| `VoWifiNameFormatManager` | 本就无 SoC 门，保持可点（含名称格式） |
| `DeviceInfo.supportsVowifiForceEnable` | 仅作「是否主推 Tensor 路径」提示，不硬拦 |
| Compat 文案 | 仍可提示风险，但不阻断操作 |

## 验证

- `compileOnelinkDebugKotlin` / 相关单测  
- 真机天玑是否改名：**NOT RUN**（OEM 决定）
