> ⚠️ **已废止现行方案（2026-07-30）**：见 `docs/changes/2026-07-30-abolish-onekuku-mini-care-min.md`。下文仅考古。

# 2026-07-30 · 为啥还是 OneBridge？换 CARE_MIN 图啥？

（主 Agent 复核采纳子代理 7B 证据稿）

## 为啥产品说新 OneKuku，真机仍是 `onebridge_server`

默认 `CHANNEL_ENGINE=ONEBRIDGE`（`app/build.gradle.kts`）；`:care-min` 仅有契约常量，**server 源码尚未进 APK**（白名单 §4）。  
划掉三连 PASS 观测的也是 `onebridge_server`（`2026-07-30-onekuku-debug-swipe-triad-pass.md`）。

换的是路线与插座；插头（MINI server）还在搬家——故意未半截拉闸。

## 换 CARE_MIN 的意义

| 维度 | OneBridge（现状） | CARE_MIN（目标） |
|---|---|---|
| 协议 | 自研 `*.onebridge` | 与 Lite 同构 Shizuku `*.shizuku` |
| 进程 | `onebridge_server` | `onekuku_server` |
| 长期 | 双协议债 | P3c 可退役 `:bridge` |
| 用户路径 | 单 APK | 仍单 APK，不装 Care |

价值：协议同源、可退役自研桥、吃进 MINI server 刚需。稳定性须在 P3b 用同等划掉矩阵重新举证。

