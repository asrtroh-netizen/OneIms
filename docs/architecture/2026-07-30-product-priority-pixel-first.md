# 2026-07-30 · 产品优先级（Pixel 通信 + 开机自启优先）

## 优先级（硬）

1. **Pixel 通信主链路**：VoLTE / IMS CarrierConfig / 应用与重放必须可用；VoLTE provisioning key=10 **永不 soft-fail**。
2. **Pixel 开机自启与恢复**：`BootReceiver` → 通道拉起 / `Guard` / OneKuku·OneLink 开机重放，语义不得被 OEM 兼容逻辑改弱。
3. **国产机非主功能兼容**：对齐 pixel-volte-patch 那类 OEM 容错（拒持久降临时、异常不炸 UI、软键白名单），详见 `2026-07-30-pixelims-domestic-oem-alignment.md`。**禁止**为兼容国产机而降低 Pixel 通信/开机保证。

## 本轮改动对照

| 能力 | 是否直接改文件 | Pixel 影响 |
|---|---|---|
| `BootReceiver` / `GuardService` / `RootBootStarter` / `OneKukuRestore*` / `ReapplyManager` | **未改** | 启动与重放控制流不变 |
| `ImsController` / `ProvisioningWritePolicy` | 有 | key=10 仍硬；68 软失败不整单失败（防假硬失败） |
| `BrokerInstrumentation` persistent→temporary | 有 | 对齐 pixel-volte-patch，防拒持久闪退；与既有 Writer 降级同向 |
| 国产回读 soft-timeout / VoWIFI·VoLTE soft | 有 | **仅** `OemDeviceCompat.isDomesticVowifiOem()`（vivo/OPPO/一加/小米/三星/荣耀等），Pixel 不进门 |

## 后续改代码铁律

- 新增 OEM 分支必须默认 **opt-in 国产机**，不得改 Pixel 默认路径。
- 开机路径改动必须单列验证：冷启 → 通道就绪 → 配置重放。
- 详细日志/Application 只允许观测，不得拦截 Boot 意图。
