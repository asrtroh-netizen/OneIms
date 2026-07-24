# 2026-07-25 · 录音双声道 + 播放分享 + OEM 探测

- ShellRecorder：优先 UPLINK+DOWNLINK 立体声交织 WAV
- 列表：播放 / 分享 / 删除；FileProvider 增加 `call_recordings`
- `probeSources()` + UI 按钮；OEM 矩阵文档模板

验证：`./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug`
