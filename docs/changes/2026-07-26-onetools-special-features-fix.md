# 变更说明 · OneTools 特色功能「都不中用」修复

## 现象

三项 UI 已迁入，但信号格 / 5G / 切卡实际不可用。

## 根因（证据）

1. **电话权限缺口（P0）**：`SpecialFeaturesScreen` 只检查 `READ_PHONE_STATE`，从不请求；未授权时 SIM 列表为空，切卡与目标卡 UI 全空。权限仅在录音页索要过。
2. **整包 CarrierConfig 回读过严（P0）**：多键一次写入后 `awaitOverrideReadback` 要求全部键匹配；Pixel 上阈值数组常不完整回读，导致 inflate/5G 写入被误判失败。OneIMS 有 `CarrierConfigOverrideWriter` 逐键补写，R1 移植时漏掉。
3. **Instrumentation stop 误伤（P1）**：`stopDelegateShellPermissionIdentity` 缺失时不应冒充业务失败（对齐 OneIMS `ShellDelegateCleanupPolicy`）。

## 修复

- 进入特色页主动申请电话权限 + 明确 CTA / 文案
- `SpecialBroker.applyOverridesResilient`：批量失败后逐键补写
- Instrumentation stop 良性失败只打日志
- 版本 → `0.3.6` / `18`

## 验证

```text
.\gradlew :onetools:compileDebugKotlin
powershell -File onetools/scripts/build-local-apk.ps1
```

真机（Pixel + Shizuku）：授权电话权限 → 应用信号格/5G → 切卡。
