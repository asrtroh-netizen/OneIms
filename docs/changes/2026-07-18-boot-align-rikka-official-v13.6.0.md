# V15.0.3：开机自启对齐 Rikka 官方 v13.6.0

此前误把 Boy@`424254b` 当「原版」。用户明确要对齐 **RikkaApps/Shizuku 官方**。

- `BootCompleteReceiver` 按官方 v13.6.0：ROOT 或（Android 13+ + WRITE_SECURE_SETTINGS + 上次 LaunchMethod.ADB）→ 进程内开无线调试 + mDNS + AdbClient 执行 Starter
- **不再**走 Boy `WirelessBootStartWorker` / WorkManager 开机入口
- 制品：`Shizuku-V15.0.3-release.apk`
