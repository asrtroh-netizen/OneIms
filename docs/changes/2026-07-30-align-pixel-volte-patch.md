# 2026-07-30 · 对照 kyujin-cho/pixel-volte-patch（真正的 Pixel IMS）

## 澄清

用户所指 PixelIMS = [kyujin-cho/pixel-volte-patch](https://github.com/kyujin-cho/pixel-volte-patch)（包名 `dev.bluehouse.enablevolte`）。  
此前本地 `.tmp_vvb2060_ims` 是南宫/vvb2060 旁路参考，**不是**该仓库。

## 上游行为（采证自 raw API，未整仓 clone）

| 点 | pixel-volte-patch | OneIMS（改前） | 本次对齐 |
|---|---|---|---|
| 写路径 | 2025-10 后走 `BrokerInstrumentation`；更早直调 `ICarrierConfigLoader` | 一律 Broker / root | 已有 |
| persistent 拒写 | **Instr 内** catch `SecurityException` → `persistent=false`（Issue #398） | 主要靠 Writer 跨进程重试 | **Instr 内同会话降级** + 返回 `ok-temporary` |
| Provisioning | 几乎不做 `setImsProvisioningInt` | 多键 10/26/27/28/68 | 小米 soft 28/68（上轮） |
| 进程面 | 轻 UI + Shizuku，无嵌入式 ADB/Boot 重通道 | OneKuku/Lite + Boot/FGS | 日志落盘取证（上轮） |
| 许可证 | GPL-3.0 | 独立实现 | **只对齐行为，不抄源码** |

## 为何「它在小米上不闪、我们闪」更说得通

1. 上游把 `persistent=true` 拒写吞在 Instrumentation 里，UI 进程不吃未捕获异常。  
2. 上游写集几乎只有 CarrierConfig 开关，无 OneKuku 激活/开机恢复爆炸半径。  
3. 群友「PixelIMS 正常」≠ 小米官方支持；README 主推 Tensor Pixel，但崩溃点多在 toggle 写配置（与 OEM 无关的 SecurityException 路径）。

## 验证

- `CarrierConfigOverrideWriterTest` + 既有 soft-fail 单测  
- 双 flavor 编译  
- 小米真机：NOT RUN（请导出详细日志）
