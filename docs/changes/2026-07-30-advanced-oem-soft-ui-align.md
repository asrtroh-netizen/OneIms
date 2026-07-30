# 2026-07-30 · 高级/一键：OneKuku 报 OEM 拒绝、Lite 不报

## 现象

同机小米：Lite（OneLink）高级选项不报 OEM 错；OneKuku 线路会弹出「OEM 拒绝了部分非关键 provisioning」。

## 证据

`session.log`（OneKuku）：

- 多次「高级 IMS 选项已应用」——高级开关本身两边都能成功
- 随后「配置已下发。OEM 拒绝了… provision_vowifi_roaming, provision_wfc_mode」——来自一键 `applyAll` 的 key=26/27 软拒文案，不是高级选项硬失败

OneLink 同路径若 26/27 返回 0，或用户只点高级应用，就不会看到该文案。

## 修复

国产 VoWIFI OEM：若软失败键仅属于全机型软键（26/27/68 对应 detail）且 `provision_vowifi` 已开门，UI 改为 `msg_apply_ok`（与 Lite 观感对齐）。日志仍保留 `OEM soft-reject`。

Pixel 路径不变，仍展示 soft 文案。
