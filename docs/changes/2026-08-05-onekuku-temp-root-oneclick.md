# OneKuku 一键临时 Root（实验）

## 行为

- OneKuku 首页在「持久性 VoLTE/NR」下方增加 **一键临时 Root（实验）**，**不依赖当前已有 Root**（重启后可点）。
- Lite / OneLink：不展示该入口；逻辑返回 `UnsupportedChannel`。
- 流程：内嵌无线 ADB 白名单 shell → 探测 `/data/local/tmp/preload-comet.so` → 缺失则从 assets 落到公共 Download 再 `cp` → `LD_PRELOAD=… /system/bin/id` → `su -c id` 验 `uid=0`。
- 前置：已完成 OneKuku 无线调试配对；so 须匹配设备构建（当前打包 comet / CP2A.260705.006）。

## 非目标

- 不实现假 `setenforce` 恢复；不把 exploit so 打进 Lite 包。
- 不替代电脑 adb 排障；超时可达约 10 分钟。

## 验证

- `:app:compileOnekukuDebugKotlin` / `:app:compileOnelinkDebugKotlin` 通过。
- 真机：配对后点按钮，观察 ROOT 徽标与 Root 功能区出现（需匹配 so）。
