# 2026-07-29 · 双版本首页紧凑 + V15.0.0 逻辑同步 + 高通兼容

## 背景

1. 截图：小屏/大字号下首页 `StatusHero` + 大标题过长，激活中遮住下方开关。
2. OneKuku 通道逻辑对齐库内 `HSSkyBoy-Shizuku-clean`（`V15.0.0`）冷启 binder 重试。
3. 双版本差异复核：`onekuku`=内嵌 OneBridge；`onelink`=外部 Shizuku（推荐 asrtroh V15）。
4. 国内高通开 VoWiFi：兼容体检不再把非 Tensor 标成「不支持」。

## 变更

| 项 | 说明 |
|---|---|
| `OneImsPage` / `StatusHero` | `screenHeightDp < 740` 或 `fontScale ≥ 1.1` 进入紧凑布局：缩小标题/内边距，阶段条只留圆点，隐藏副提示与「设备详情」胶囊 |
| `CompatChecker` | 非 Tensor → `DEGRADED`；仅 API&lt;31 → `UNSUPPORTED`；补充高通 SoC 行与文案 |
| `DeviceInfo.isQualcomm` | 识别 qcom / snapdragon / sm#### |
| `OneKukuEmbeddedAdbActivator` | binder 未就绪最多 3 次重试（对齐 V15 `awaitBinderReady`） |
| `ShizukuSetupHelper.openShizukuApp` | 未安装时回落 GitHub `V15.0.0` Release，而非商店官版 |
| `MembershipPaywallScreen.kt` | 补回缺失会员预览页，解除既有编译阻塞（支付仍仅预览） |

## 双版本差异（核对结论）

| 维度 | OneKuku（`onekuku`） | OneLink（`onelink`） |
|---|---|---|
| 包名 | `com.oneims.app` | `com.oneims.onelink` |
| 特权桥 | 内嵌 OneBridge（`:bridge`） | 外部官方/修缮版 Shizuku API |
| 激活 | 六位码通知 + 内嵌 ADB | 打开 Shizuku → 授权（无六位码） |
| Root 开机 | `onebridge_server` | `libshizuku.so` |
| 入口分叉 | `ChannelLine.usesEmbeddedBridge` / `usesShizuku` | 同上 |

入口证据：`MainActivity.prepareOneKukuCore` / `prepareOneLinkShizukuChannel`、`RootBootStarter`、`HomeScreen`。

## 验证

见交付总结中的命令与结果。
