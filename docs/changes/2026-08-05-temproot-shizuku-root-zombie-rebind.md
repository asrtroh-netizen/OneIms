# 2026-08-05 · 临时 Root 后 Shizuku root 僵尸掉线

## 现象

临时 Root 成功、黑标出现后，OneLink / Shizuku 立刻掉线。

## 根因

1. `su -c libshizuku.so`（含 OneLink「Root 开机启动」旧路径）会把 `shizuku_server` 拉成 **root**，SELinux 常为 `u:r:kernel:s0`。
2. App（`untrusted_app`）对 root/kernel 态 server 的 binder 被拒 → 客户端掉线。
3. 同时 `Shizuku.getUid()==0` 会让首页黑标亮起，形成「有 Root、无通道」。

## 修复

- `RootBootStarter`（OneLink）：禁止 `su -c libshizuku`；改为 `rebindShellServerAfterTempRoot`。
- `ShizukuSetupHelper.rebindShellServerAfterTempRoot`：临时 su **只杀** server → 无线 SelfStarter（shell uid）重绑。
- 一键临时 Root 成功后与首页徽标轮询：掉线时限频自动重绑。

## 验证

- `installOnelinkDebug`：Installed on 1 device
- 真机：`su -c libshizuku` → `ps` 为 root；kill + adb shell 拉起 → `ps` 为 shell；AVC binder deny 消失

## PC OneRoot（追加）

- `oneso.rebind_shell_shizuku()`：成功后 kill + **adb shell** 拉起；代码注释标明 **FORBIDDEN: su -c libshizuku**
- `hub` status 增加 Shizuku shell/root 指示
- `PC-TempRoot-Lite` / `PC-TempRoot-UI` 成功路径同样重绑
- 已同步并 push `OneSo-assets` `oneroot/`（`b782870`）
