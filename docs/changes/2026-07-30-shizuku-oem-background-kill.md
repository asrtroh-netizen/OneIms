# 2026-07-30 · Shizuku 一会儿就掉（小米实测）

## 结论

**不是 OneIMS 杀 Shizuku。** 小米对 `moe.shizuku.privileged.api` 限后台：

- `appops RUN_ANY_IN_BACKGROUND: ignore`（后已 adb 改为 allow）
- `standbyBucket` 长时间停在 **40 (RARE)**，用户点开才升到 10 (ACTIVE)
- 代码侧无 force-stop / kill Shizuku

次要因素：无线调试关闭时，经无线调试拉起的 `shizuku_server` 也会一起死。

## 对比

| 通道 | 特权进程 | OEM 杀后台影响 |
|---|---|---|
| OneKuku | `onebridge_server`（shell） | 相对扛打 |
| OneLink | 官方 `shizuku_server` + Manager | 小米/一加省电易杀 |

## 用户侧（MIUI/HyperOS）

1. 设置 → 应用 → Shizuku → 省电策略 **无限制**
2. 自启动 / 后台运行 允许
3. 锁定 Shizuku 最近任务
4. 用无线调试启动时：勿随手关「无线调试」

## 本机已做

- `cmd appops set moe.shizuku.privileged.api RUN_ANY_IN_BACKGROUND allow`
- `dumpsys deviceidle whitelist +moe.shizuku.privileged.api`（及 OneLink）
- OneLink 文案补充国产机省电提示

## 2026-07-30 晚 · 用户验证（原包 Shizuku）

**用户结论**：换用**新到货的原包 Shizuku** 实测后，此前小米侧「一会儿就掉 / OneLink 不稳」问题已解决。

架构收口（本轮写入，证据等级 = 用户真机口述）：

| 项 | 状态 |
|---|---|
| OneIms 3.0.9 血线（Pixel 优先 + Tensor 门禁） | **不必为该问题回退** |
| 嫌疑重心 | 更偏向 **非原包 / fork 环境 / 省电策略**，而非 OneLink 业务逻辑本身 |
| 假 ADB | 仍未就绪；满血免无线路径继续搁置 |
| OneKuku | 此前约定「等 Shizuku 就绪再换血」——原包已通，可进入专项；与 OneLink 通道勿混诊 |

推荐验证姿势（人工）：仅装原包 `moe.shizuku.privileged.api`，勿与 `onebridge_server` / 二手 fork 并存；省电仍建议「无限制」。
