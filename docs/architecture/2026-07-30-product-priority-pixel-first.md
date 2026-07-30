# 2026-07-30 · 产品优先级（Pixel 通信 + 开机自启优先）

## 优先级（硬）

**第一优先：Pixel** → **第二：其它机子（VoWIFI 容错）**。次序不可颠倒。

1. **【P0 · 第一权重】Pixel VoWIFI**：`KEY_VOICE_OVER_WIFI_ENABLED`(28) / `provision_vowifi` **硬失败可见**；CarrierConfig WFC 相关写入必须可用。不得因国产 soft 门控误伤 Pixel。
2. **【P0】Pixel 其它通信 + 开机自启**：VoLTE key=10 **硬**；`BootReceiver` / Guard / 开机重放不得被 OEM 兼容改弱（`isDomesticVowifiOem` 对 Google/Pixel 恒 false）。
3. **【P1 · 第二】其它机子 VoWIFI**：vivo / OPPO / 一加 / 小米 / 三星 / 荣耀等，对齐 pixel-volte-patch 式容错（拒持久降临时、异常不炸 UI、软键白名单）。国产另：**CarrierConfig temporary-first**、**不写 VoLTE/VoNR CC 大键集**。详见 `2026-07-30-pixelims-domestic-oem-alignment.md`。  
   **禁止**为 P1 削弱 P0。

## 本轮改动对照

| 能力 | 是否直接改文件 | Pixel 影响 |
|---|---|---|
| `BootReceiver` / `GuardService` / `RootBootStarter` / `OneKukuRestore*` / `ReapplyManager` | **未改** | 启动与重放控制流不变 |
| `ImsController` / `ProvisioningWritePolicy` | 有 | key=10 仍硬；68 软失败不整单失败（防假硬失败）；国产跳过通信 CC/provision |
| `BrokerInstrumentation` persistent→temporary | 有 | 对齐 pixel-volte-patch，防拒持久闪退；与既有 Writer 降级同向 |
| 国产回读 soft-timeout / VoWIFI·VoLTE soft / temporary-first | 有 | **仅** `OemDeviceCompat.isDomesticVowifiOem()`，Pixel 不进门 |

## 后续改代码铁律

- 新增 OEM 分支必须默认 **opt-in 国产机**，不得改 Pixel 默认路径。
- 开机路径改动必须单列验证：冷启 → 通道就绪 → 配置重放。
- 详细日志/Application 只允许观测，不得拦截 Boot 意图。
