# 2026-07-25 · OneCaller 对齐 Telo 离线骚扰库全路数（干净室）

## 目标

用户要求「走 Telo 库的全部路数」：空包 → 清单检查 → SHA-256 校验安装 → 本地精确命中 → 可选联网 → CallScreening / Directory 接入。

## 落地（不抄 Telo 源码、不用 pixeltelo API）

| 路数 | 实现 |
|---|---|
| 空包启动 | APK 不捆绑 `onespam.db` |
| 清单检查 | `SpamSyncRepository.checkUpdate` + Telo 兼容 JSON 字段 |
| 校验安装 | 下 zip → size + SHA-256 → 解出 `onespam_*.db`/`mast_*.db` → 替换 |
| 本地命中 | Room/`SpamOfflineDatabase` 精确 `phone_number` |
| 用户规则优先 | 白名单 → 黑名单 → 离线库 → 联网 |
| 仅提示 | `CallerPrefs.notifyOnly`（默认 true） |
| 仅离线 | `CallerPrefs.noNetworkQuery` |
| 联网查号 | `CallerNetworkQuery`（`ONE_CALLER_QUERY_URL` 空则跳过） |
| 无 CDN 时 | UI「从 OneBlock JSON 灌入离线库」 |

## 配置

- `ONE_SPAM_SYNC_MANIFEST_URL`（默认 `https://cdn.oneims.app/onetools/caller/spam-sync.json`）
- `ONE_CALLER_QUERY_URL`（默认空）

## 明确不做

- 不合并 Telo 源码
- 不调用 `pixeltelo.api.mystery0.vip`
- 不以来电悬浮作为归属地验收（沿用 Directory 原生行 + MIT `geo.dat`）

## 验证

`./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug`
