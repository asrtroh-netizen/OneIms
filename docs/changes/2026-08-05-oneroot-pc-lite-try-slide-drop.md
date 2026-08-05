# OneRoot PC-Lite 试跑：slide 阶段 adb 掉线（2026-08-05）

## 环境

- 设备：`comet` / Pixel 9 Pro Fold / `47111FDKD0009J`
- Build：`CP2A.260705.006`
- 入口：`release/OneRoot-Lite/OneRoot-Lite/一键临时Root.cmd nopause`
- so：`preload-comet-CP2A.260705.006.so`
- SHA256：`e74cbc7d2e5a941691568500a24a77d92a6decd175e6b56090e99d047f847245`（原版；App 1.1.6 热补丁为 `64ed9d74…`）

## 结果

- **失败**：未拿到 `uid=0`；无 `/data/local/tmp/su`
- 脚本退出码：`1`（`[FAIL] no uid=0 after 2 attempt(s)`）
- Attempt 1：KernelSnitch 前 3 次 `mm_struct leak failed`，第 4 次 leak 成功 → 进入 `slide attempt 1 uses pselect` → 随后 `adb: device not found`
- Attempt 2：设备仍离线，直接失败
- 重连后：`sys.boot_completed=1`，`sys.boot.reason.last=reboot,shell`（与先前 shell reboot 一致；本次未见明确 `kernel_panic` dmesg 摘录）
- 残留：`preload-comet.so` 已清理

## 结论

PC 通路与 App 一样，在 **comet + 0705 原版 so** 上不可靠；本轮卡点偏前（slide / USB 掉线），尚未进入此前 App 热补丁后的 `install_android_root` task-walk 长刷 configfs。

## 产物

- 控制台输出：`release/_tmp/pc-run-out.txt`
- logcat 抓取：`release/_tmp/pc-temproot-try-215847.log`（体积大，勿入库）
