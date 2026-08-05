# Lite 一键临时 Root（经 Shizuku，方案 A）

## 行为

- Lite / OneLink 首页同样展示「一键临时 Root（实验）」。
- 通道：外置 Shizuku `newProcess` → `sh -c` 白名单命令（与 OneKuku 同源 `TempRootShellCommands`）。
- so：`app/src/main/assets/temproot/preload-comet.so`（双 flavor 共用）。
- 验活：优先 exploit 输出；再用本机 `ProcessBuilder(/data/local/tmp/su, -c, /system/bin/id)`，**不**经 Drop-In 对裸 `su`/`id` 的 mock。

## 非目标

- 不在 Shizuku Manager 内嵌 exploit。
- 不依赖关闭用户 SU Bridge（诚实探针绕开）。

## 验证

- `:app:compileOnekukuDebugKotlin` / `:app:compileOnelinkDebugKotlin` 通过。
- 真机：先激活 Shizuku → 点按钮 → ROOT 徽标（NOT RUN 直至装机）。
