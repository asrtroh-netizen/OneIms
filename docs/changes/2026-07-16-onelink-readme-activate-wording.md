# 2026-07-16 · OneLink README「激活」措辞纠正

## 用户质疑

> OneLink 为什么还要跳转 Shizuku？Shizuku 开了点一下不就激活么？以前也是。README 为啥还写要激活？

## 代码事实（`prepareOneLinkShizukuChannel`）

| 条件 | 行为 |
|---|---|
| `isGranted()` | 安装桥 + `wake()`，**不跳转** Shizuku |
| `isRunning()` 未授权 | `requestPermission`，系统授权框 |
| 未 Running | 才 `openShizukuApp` 跳转官方 Shizuku |

与 2.0.8/2.0.9 一致；「跳转」只覆盖冷启动/未 Start。

## 文案修正

README 选购表 / 升级说明 / What's New：区分「已 Start 一点唤醒」vs「未 Start 才跳转」；换线「重新激活」改为「对本包再授权一次」（非无线调试配对）。
