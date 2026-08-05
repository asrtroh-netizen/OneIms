# 2026-08-05 · 卸载 RootMyPixel / OneRoot App（真机）

## 结论

设备：Pixel 9 Pro Fold（`comet` / `CP2A.260705.006`，序列号 `47111FDKD0009J`）

| 包名 | 处置 |
|---|---|
| `com.alex193a.rootmypixel`（原版 Root My Pixel） | 任务开始时已不存在；`adb uninstall` → `DELETE_FAILED_INTERNAL_ERROR` |
| `com.oneroot.app`（上游 fork，桌面可见的「Root」类 App） | 多次 `adb uninstall` → **Success**；最后一次 20:22:11 卸掉后盯 20s+ 仍不在 |

用户说「卸不掉 RootMyPixel」时，手机上实际跑的是 **OneRoot**（`com.oneroot.app`，曾见 `1.0.3`→`1.0.4`），不是原版包名。

## 回弹原因（本轮证据）

卸掉后曾被 **ADB shell** 立刻重装（`initiatingPackageName=com.android.shell`，新 `codePath` / `firstInstallTime`），伴随本机其它 Cursor 会话对 `_forks/OneRoot` 的安装调试（logcat 见 `run-as com.oneroot.app …`）。  
不是系统「锁死卸载」，而是**并发 adb 重装**造成「怎么卸都还在」的体感。

## 残留

- 已跑 Lite `cleanup-residuals.sh` → `CLEAN_SHELL_OK`
- shell 可读的 `oneroot-*.sh` / `preload-comet.so` 已删
- 复验无 `temp_su.sock` / `preload-comet.so`；当时无可用 uid=0 su daemon

## 验证（最后一次）

```text
adb uninstall com.oneroot.app → Success（20:22:11）
hold-watch 约 20s：pm path 持续失败（GONE）
pm path com.alex193a.rootmypixel → exit 1
pm list packages | oneroot|rootmypixel|alex193a → 空
```
