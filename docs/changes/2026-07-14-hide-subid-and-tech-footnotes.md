# 独家页：隐藏 subId、去掉技术小字、澄清 5G-A

日期：2026-07-14

## 变更

1. **用户面不再展示 `subId=N`**  
   目标预览、写入成功文案、APN 目标、切卡行、`formatTargetLabel` 均改为「卡槽 · 运营商」。

2. **去掉图二类技术小字**  
   移除信号格 CarrierConfig/SystemUI 说明、能力页信号阈值/VoWiFi SystemUI 说明、独家页系统图标配置副文案。

3. **5G-A 说明与应用内映射**  
   - 系统状态栏 CarrierConfig 常见 token 只有 `5G` / `5G_PLUS`，多数 ROM 会显示成 **5G+**，这不是网络坏了。  
   - 应用内「5G-A」：在内地速率/酷炫模式下，**NR Advanced 直接显示 5G-A**；SA 仍需达到 5G-A 下行阈值。

## 未改

- 不伪造系统状态栏「5Ga」图标（无通用 AOSP token）  
- 日志/诊断内部仍可用 subId
