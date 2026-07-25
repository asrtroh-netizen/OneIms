# 变更说明 · Battery Guru 2.5.0.3（derrin 高级版样本）反编译

**日期**：2026-07-26  
**用户样本路径**：`Desktop/Battery Guru电池大师 2.5.0.3高级版-derrin.apk(1).1`  
**工作副本**：`.tmp_batteryguru_re/v2503_derrin/work/battery-guru-2.5.0.3-derrin.apk`  
**SHA-256**：`115EC4DAB5E6035FBEE7DAEA097D5005EA3D28B18ED371D35B1DF7405ACACDF0`  
**versionName / versionCode**：`2.5.0.3` / `711`  
**工具**：jadx 1.5.5（61 errors）+ apktool 3.0.2（exit 0）

## 样本定性（事实）

| 证据 | 结论 |
|---|---|
| META-INF 为 `EDITOR.RSA` / `EDITOR.SF` | **重签名改包**，非 Play 原签 |
| 证书 `CN=editor`，有效期 2016→2115 | 常见 APK 编辑器默认调试证书 |
| 文件名「高级版-derrin」 | 第三方改包标识；**不作为产品授权依据** |
| 包名仍为 `com.paget96.batteryguru` | 未改 applicationId |
| 与社区 2.5.0.5 组件表一致 | 结构同源、版本略旧 |

> 本轮只做结构取料与干净室映射；**不**分析/复现付费解锁补丁，**不**把改包逻辑带进 OneBattery。

## 反编译结果

| 产物 | 路径 |
|---|---|
| jadx | `.tmp_batteryguru_re/v2503_derrin/jadx_out/`（~13742 Java） |
| apktool | `.tmp_batteryguru_re/v2503_derrin/apktool_out/` |

清晰组件（与 2.5.0.5 取料一致）：

- `services.BatteryInfoService`（`:battery_service` FGS）
- `AODOverlayActivity` + `aod.NotificationService`
- Widget×2 / `BatteryTileService` / `BluetoothLeService`
- Workers：`AnomalyAlertWorker`、`ChargingSummaryWorker`、`OvernightReportWorker`、`SummaryNotificationWorker`、`StartBatteryInfoServiceWorker`、`StreakMilestoneWorker`

## 相对 2.5.0.5 社区包

| 项 | 2.5.0.3 derrin | 2.5.0.5 社区 |
|---|---|---|
| SHA-256 | `115EC4…ACDF0` | `1C50D0…8163` |
| 体积 | 14,933,221 | 14,885,876 |
| 签名 | `CN=editor` 重签 | 社区镜像原样 |
| 架构取料价值 | 同骨架，可交叉核对 | 略新，优先作公开能力对照 |

## 对 OneBattery 的含义

沿用既有取料表（`2026-07-26-batteryguru-decompile-takeaways.md`）：P0=功率W/多闹钟/常驻通知；P1=充电器档案/异常耗电/Overlay/Tile；AOD 首期 OUT。  
**新增纪律**：改包样本仅作结构旁证，落地实现仍走官方公开能力 + 自有干净室代码。

## 验证

```text
Get-FileHash .tmp_batteryguru_re\v2503_derrin\work\battery-guru-2.5.0.3-derrin.apk -Algorithm SHA256
# 115EC4DAB5E6035FBEE7DAEA097D5005EA3D28B18ED371D35B1DF7405ACACDF0

apktool d → exit 0
jadx → exit 1 / errors 61（可接受）
certutil → Subject CN=editor
```
