# 2.1.2 · 有配对口也先无码直连

## 问题

`activateLocked` 在扫到 `pairPort` 且未填码时立刻 `NeedPairingCode`，
导致系统「配对码配对」页开着时，即使本机已配对也每次逼填六位码。

## 改动

未填码时先 `connect` / `connectTls`；失败再 `NeedPairingCode`。

## 版本

- 2.1.2 / versionCode 47
