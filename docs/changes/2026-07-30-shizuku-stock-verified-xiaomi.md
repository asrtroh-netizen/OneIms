# 2026-07-30 · 原包 Shizuku 小米侧已验证

## 结论

用户用**新原包 Shizuku** 实测：此前小米上 OneLink / Shizuku「掉线、不稳」问题已解决。

## 对产品线的含义

1. **不归因于** OneIms `3.0.9` 血线（3.0.4 根 + 选拣好物 + Tensor 硬门禁）。
2. 优先保持 **stock 包名** `moe.shizuku.privileged.api`；fork / Drop-In / 并存多桥时先排除环境冲突。
3. OEM 省电仍建议无限制（见 `2026-07-30-shizuku-oem-background-kill.md`），原包能解不代表可以关掉省电白名单。
4. **OneKuku** 通道（OneBridge）与 OneLink（Shizuku）分开验收；原包就绪后可开 OneKuku 专项（binder 假活 / 文案包重装等）。

## 证据

- 来源：用户面板口述（2026-07-30）
- 本机未在本轮重跑 adb 对比（NOT RUN：无新 logcat 包）

## 相关文档

- `docs/changes/2026-07-30-shizuku-oem-background-kill.md`
- `docs/changes/2026-07-30-shizuku-local-vs-shizukuplus-r2185.md`
- `docs/architecture/2026-07-30-product-priority-pixel-first.md`
