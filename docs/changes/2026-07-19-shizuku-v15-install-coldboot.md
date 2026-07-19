# Shizuku V15.0.0 装机与冷启验收

日期：2026-07-19  
设备：`47111FDKD0009J` Pixel 9 Pro Fold  
制品：`shizuku-vV15.0.0-release.apk`（versionName=`V15.0.0`，versionCode=`150000`）

## 装机

| 步骤 | 结果 |
|---|---|
| `:manager:assembleRelease` | BUILD SUCCESSFUL |
| `adb install -r` | Success |
| `WRITE_SECURE_SETTINGS` | granted=true |
| 安装后版本 | versionName=V15.0.0 / versionCode=150000 |

## 冷启（无手点窗口内）

| 观测 | 结果 |
|---|---|
| `BootCompleteReceiver` 拉起 manager | PASS（t≈boot+1s） |
| `SelfStarterService` FGS | 拉起后很快 `STOP_SERVICE`（LOCKED_BOOT ~631ms；BOOT_COMPLETED ~163ms） |
| ≤66s 内出现 `shizuku_server` | **FAIL** |
| 打开 App / `StarterActivity` 后 | `shizuku_server` 出现（手点路径可用） |

结论：**装机成功，可供查看 UI；冷启无手点自启本轮未过**（对比此前 r11 PASS）。
