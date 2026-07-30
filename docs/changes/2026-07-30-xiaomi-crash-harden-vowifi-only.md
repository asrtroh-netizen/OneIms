# 2026-07-30 · 小米闪退再加固（国产 VoWIFI-only）

## 背景

群友反馈 OneIMS 小米闪退；3.0.8 已有 soft-fail / 详细日志 / Broker persistent→temporary。
真机栈仍未到。按产品边界再收一层爆炸半径：

- **P0 Pixel**：通信 + 开机 + VoWIFI 硬路径不变
- **P1 国产**：只要 VoWIFI，不推通信大键集

## 改动

1. **国产 CarrierConfig temporary-first**（`OemDeviceCompat.preferTemporaryCarrierOverride`）
   - 跳过 persistent / 沙盒旁路，直接 temporary，避开 HyperOS 拒持久杀进程面
2. **国产跳过 VoLTE/VoNR CarrierConfig + provisioning 通信写**（`ImsController.applyAll`）
   - 只写 VoWIFI CC / provisioning；CC 整批失败不中断
3. **`carrier_config_override` 纳入国产 softKeys**（`ProvisioningWritePolicy`）
4. **UI 防崩**：`refreshAll` / 特权轮询 `runCatching`；Compose `SupervisorJob` + `CoroutineExceptionHandler` 落盘
5. **BrokerInstrumentation.onCreate** 尽早挂 `DiagFileLogger`

## 验证

- `OemDeviceCompatTest` / `ProvisioningWritePolicyTest`
- 双 flavor compile
- **小米真机（2026-07-30 adb）**：Xiaomi 22061218C / HyperOS OS3.0 (V816) / Android 15
  - 覆盖安装 onekuku+onelink debug（含 `fde9d5391`）
  - 冷启双包进程存活；`crash-*.log` = 0
  - 写路径日志：`temporary-first ... domestic-vowifi-oem` + Broker `ok-temporary`
  - **用户确认**：VoWIFI 正常、不闪退

## 非目标

- 不削弱 Pixel persistent 优先
- 完整「按推荐一键」自动化未作唯一验收门（用户体感 + temporary-first 写路径已闭环）
