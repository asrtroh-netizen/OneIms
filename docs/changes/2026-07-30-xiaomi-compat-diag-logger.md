# 2026-07-30 · 小米兼容软失败 + 详细诊断日志

## 背景

群友反馈：小米 / HyperOS 上 OneIMS 闪退，PixelIMS 正常。  
**澄清（后续）**：用户所指 PixelIMS = [kyujin-cho/pixel-volte-patch](https://github.com/kyujin-cho/pixel-volte-patch)；本地 `.tmp_vvb2060_ims` 仅作南宫旁路参考。详见 `2026-07-30-align-pixel-volte-patch.md`。

## 改动

1. **`OneImsApp` + `DiagFileLogger`**
   - Manifest 挂自定义 Application
   - 未捕获异常落盘 `filesDir/diag_logs/crash-*.log`
   - 环形 `session.log`；UI 日志同步写入
   - 排障页「导出详细日志」→ FileProvider 分享诊断包

2. **`OemDeviceCompat`**
   - 识别小米 / Redmi / POCO / MIUI·HyperOS 属性

3. **兼容软失败（对齐一加先例，不抄 GPL）**
   - 全机型：key **26 / 27 / 68** 软失败不抛（68 保护 Pixel 主路径）
   - 小米系额外：key **28**（VoWIFI）软失败；CarrierConfig 5s 回读软放行
   - **VoLTE key=10 永不软化**；`ImsController` 按 `== 0` 判成功

## 验证

- 单测：`OemDeviceCompatTest`、`ProvisioningWritePolicyTest`
- 双 flavor 编译
- 小米真机闪退复现：需群友导出详细日志（NOT RUN 本机）

## 非目标

- 不复制 PixelIMS GPL 源码
- 不把 VoLTE key=10 改成软失败（invoke 软化仅限已声明 soft keys）
- 无现场 logcat 前不宣称根因已钉死
