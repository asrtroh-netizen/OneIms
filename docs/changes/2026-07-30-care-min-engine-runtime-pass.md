# 2026-07-30 · CARE_MIN 引擎真机跑通（onekuku_server）

## 结论

**PASS**：默认 `CHANNEL_ENGINE=CARE_MIN` 时，宿主内嵌 `onekuku_server` 可拉起、binder 可投递、划掉近似后进程存活并秒级 `state=ACTIVE`。

## 根因链（此前秒退 / inactive）

| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| 1 | `UnsatisfiedLinkError: librish.so` | `:care-min` 未编 native；boot 未传 library path | CMake + `org.lsposed.libcxx`；`CareMinBootShell` 传 `-Dshizuku.library.path`；`useLegacyPackaging` |
| 2 | `NoClassDefFoundError: ActivityManagerHidden` | 缺 refine 变换 | `dev.rikka.tools.refine` 挂到 `:care-min` / `:app` |
| 3 | binder 已投递仍 `wake: inactive` | 宿主被当成 Manager 时 `bindApplication` 不带 `PERMISSION_GRANTED` | `ShizukuService.attachApplication` 对 `com.oneims.app` 显式 `allowed=true` + grant |

## 真机证据（`c0b76e3b` / 22061218C）

| 步 | 结果 |
|---|---|
| 装机后拉起 | `pidof onekuku_server` 有 pid；`librish.so` 在 `lib/arm64/` |
| 激活 | log `OneIMS-OneKuku: state=ACTIVE` |
| `am force-stop` | **server pid 不变** |
| 重开 MainActivity | 同 server pid；再次 `state=ACTIVE` |

## 关键改动

- `app/build.gradle.kts`：`CHANNEL_ENGINE=CARE_MIN`；jni legacy packaging；refine
- `care-min/build.gradle.kts`：CMake/rish、libcxx、refine
- `CareMinBootShell`：library.path + `__OB_BOOT_OK__` 标记对齐
- `OneKukuCoreComponent.bridgeBootShellCommand`：CARE_MIN 委托 `CareMinBootShell`
- `ShizukuService.attachApplication`：宿主 Manager=Client 授权

## 回滚

`app/build.gradle.kts` onekuku flavor：`CHANNEL_ENGINE` 改回 `"ONEBRIDGE"` 后重装。
