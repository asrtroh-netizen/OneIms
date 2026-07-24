# 2026-07-25 · 自研干净室通话录音（Shizuku UserService）

## 立项

用户选路径 B：自研干净室，尽情用 Shizuku；不并 BCR / ShizuCallRecorder（GPL）。

## 技术事实（公开）

- Rikka：UserService 以 shell UID 运行，可使用 Shell 已有的特权能力（含通话相关音频权限语境）。
- AudioFlinger 常校验 `(uid, package)`；实现侧尝试 `createPackageContext("com.android.shell")` + `AudioRecord.Builder.setContext`（API 31+）。

## 本轮交付

| 组件 | 作用 |
|---|---|
| `IShellRecorder` + `ShellRecorderService` | Shizuku UserService，WAV 落盘 |
| `ShellRecorderClient` | bindUserService |
| `CallStateMonitor` / `CallRecorderController` | 通话态自动录 |
| `RecorderScreen` + 首页卡 | 法律勾选、手动/自动、列表 |
| 权限 | RECORD_AUDIO / READ_PHONE_STATE |

## 边界（诚实）

- OEM / Android 大版本可能失败或只能录到 MIC
- 非系统拨号器内置录音，稳定性 < 厂商方案
- 法律合规由用户勾选确认，产品需持续明示

## 验证

`./gradlew :onetools:assembleDebug`；真机通话录音 **NOT RUN**（需 Shizuku + 实呼）。
