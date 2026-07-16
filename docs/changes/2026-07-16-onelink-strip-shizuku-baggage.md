# OneLink：Shizuku 后累赘清理（文案 P1）

日期：2026-07-16

## 产品定调

OneLink = 独立 IMS 产品线（官方 Shizuku），不是 OneKuku 全功能阉割版。

## 本轮已做（P1 · 用户感知）

1. 补全 `app/src/onelink/res/values/channel_branding.xml`：激活副文案、失败/配对相位、boot FGS、compat、home_adb_prep 兜底，全部去掉「六位码 / OneKuku」心智。
2. 新增 `app/src/onelink/res/values-en/channel_branding.xml` 英文 overlay。
3. `MainActivity`：
   - `oneKukuDetailOverride`：OneLink 不展示 WAITING_PAIR/PAIRING/CONNECTING 内嵌文案
   - `onRestoreCallConfig`：无通道时引导文案改为 Shizuku 准备，不再说「配对通知填码」
4. `OneKukuHomeTools.sanitizeUserText`：**OneLink 不再把 Shizuku 强行改写成 OneKuku**（旧逻辑对独立产品线是硬伤）。

## 仍属累赘、下轮可做（P2）

| 项 | 说明 |
|---|---|
| main 中 `OneBridge*` / `OneKukuAdbMdns` / `OneKukuPairingNotification` / `WirelessPairingCodeReceiver` | 运行时不可达，仍进 onelink dex → 下沉 `onekuku` 源集 |
| Manifest 权限如 `CHANGE_WIFI_MULTICAST_STATE` | OneLink 无 mDNS 可 overlay 剥离 |
| 命名 `OneKukuManager` / BootRestore | 共享门面，长期可 rename，非必须 |

## 验证

```text
.\gradlew.bat :app:compileOnekukuDebugKotlin :app:compileOnelinkDebugKotlin
```
