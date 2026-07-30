# 2026-07-30 · Pixel 主链路安全审计（回应「别弄坏以前研究」）

## 产品边界（用户确认）

1. **Pixel 通信相关必须保证**（IMS/VoLTE 等）
2. **Pixel 开机自启 / 恢复必须保证**
3. **国产机**：只做非主功能兼容兜底，不得反噬 1/2

详见 `docs/architecture/2026-07-30-product-priority-pixel-first.md`。

## 两笔 commit 影响面

| 改动 | Pixel | 非 Pixel | 结论 |
|---|---|---|---|
| `DiagFileLogger` / `OneImsApp` | 仅观测 | 仅观测 | 不改业务语义 |
| Broker 内 persistent→temporary | 与 pixel-volte-patch 同款 | 同 | **增益**（防 QPR2 闪退） |
| 回读 5s soft-timeout | **不触发**（仅小米门控） | 小米软 | Pixel 仍硬验真 |
| key 26/27 soft | 原有 | 原有 | 未动语义 |
| key 68 soft（本审计收窄后：**全局**） | 拒写不整单失败 | 同 | **保护 Pixel**，避免 `==0` 判成硬失败 |
| key 28 soft | **不触发** | 仅小米 | 符合「非 Pixel 管 VoWIFI」 |
| VoLTE key=10 | **始终硬** | 始终硬 | 未软化 |
| `provision_volte` 改 `==0` | 去掉假成功 | 同 | 正确性修复，不降硬 |

## 结论

有改动，但 **没有把 Pixel VoLTE 主研究改软**；相对上游 Pixel IMS 的 Instr 降级是对齐而非偏离。  
若仍不放心：可只保留日志 + Broker 降级，回滚小米 key=28 soft（用户未要求回滚则保留）。
