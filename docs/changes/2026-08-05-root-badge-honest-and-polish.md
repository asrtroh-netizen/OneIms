# ROOT 徽标：去假阳性 + 临时/永久分色

## 问题

重启后 `/data/local/tmp/su` 文件常残留，旧探测把「文件存在」当成临时 Root → **没 Root 也显示黑金徽标**。

## 修复

- `RootPresenceProbe`：临时 Root 仅认特权桥 uid=0 或临时 `su` **可执行且输出 uid=0**
- 徽标：永久=炭黑+香槟金「ROOT」；临时=深蓝灰+暖琥珀「临时 ROOT」

## 验证

`RootPresenceProbeTest` + `compileOnekukuDebugKotlin`
