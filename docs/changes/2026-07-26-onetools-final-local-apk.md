# 变更说明 · OneTools 最终本地产物对齐

## 背景

用户索取 OneTools「最后的产物」。源码已为 **v0.3.4（versionCode 16）**，且电池模块已剥离；`dist` 指针此前仍停在 2026-07-25 22:27，与 07-26 01:00 后的 build 输出不一致。

## 动作

- 执行 `onetools/scripts/build-local-apk.ps1`（`:onetools:assembleDebug` → 复制到 `onetools/dist`）
- 不推 GitHub（沿用本地产品包约定）

## 产物

| 文件 | 说明 |
|---|---|
| `onetools/dist/OneTools-v0.3.4-debug-20260726-0306.apk` | 带时间戳归档 |
| `onetools/dist/OneTools-v0.3.4-latest-debug.apk` | 版本 latest 指针 |
| `onetools/dist/OneTools-latest-debug.apk` | 稳定别名（adb 脚本用） |

- 包名：`com.onetools.app`
- 版本：`0.3.4` / `16`
- 体积：29625619 bytes
- SHA256：`A342337F1257733780B1427EA194ABD88AC2C7E96337705A320DAE8656F41532`

## 安装

```text
adb install -r "onetools/dist/OneTools-v0.3.4-latest-debug.apk"
```

## 当前能力边界（底栏）

HOME / CALLER / METER / LAB / UPDATES / SETTINGS（无 BATTERY）
