# OneTools 通话录音 · OEM 兼容矩阵（持续填写）

探测入口：录音页「探测 OEM 音频源矩阵」→ 返回 `SOURCE=ok|fail` 串。

| 设备 / Android | Shizuku | STEREO_UPLINK+DOWNLINK | VOICE_CALL | VOICE_COMM | MIC | 备注 |
|---|---|---|---|---|---|---|
| （模板）Pixel 8 / 15 | ADB | ? | ? | ? | ok | 请真机补 |
| （模板）小米 / HyperOS | ADB | ? | ? | ? | ? | 可能限制通话源 |
| （模板）三星 / OneUI | ADB | ? | ? | ? | ? |  |

## 客户端阶梯（代码真源）

1. 同时开 `VOICE_UPLINK` + `VOICE_DOWNLINK` → 立体声 WAV（L=近端, R=远端）
2. 失败则单路：`VOICE_CALL` → `DOWNLINK` → `UPLINK` → `VOICE_COMMUNICATION` → `MIC`
3. `AudioRecord.Builder.setContext(com.android.shell)`（API 31+）尽量满足 AudioFlinger 包名校验

## 验证

- 单元：assemble + 现有单测
- 真机：探测矩阵 + 短通话听立体声左右声道
