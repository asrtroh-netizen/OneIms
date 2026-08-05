# OneSo × ONE：一点临时 Root 核实结论

## 一句话

**能，但不是「任意手机一点就成」；是「目录命中的机型 + 通道就绪 + exploit so 真提权」时，App 一键链路会自动取 so 并跑 LD_PRELOAD，再用诚实 su 验活。**

## 角色分工

| 组件 | 干什么 | 不干什么 |
|---|---|---|
| **OneSo**（`tools/oneso` + 私仓 factory） | 编译/导入 preload.so，推到公开 `OneSo-assets` | 不是手机上的一键 Root App |
| **OneSo-assets** | 公开 `catalog.json` + `so/` + `SHA256SUMS` | 不含 exploit 源码 |
| **ONE / OneIMS**（OneKuku / Lite） | 首页「一键临时 Root」：取 so → stage → `LD_PRELOAD` → 验 `uid=0` | 不能覆盖未进 catalog 的机型 |

## 一键链路（代码真源）

`HomeScreen` → `onTempRootOneClick` → `OneKukuTempRootActivator.runExperimental`：

1. `TempRootSoProvider.ensure`：**远端 OneSo-assets 优先** → 缓存 → APK assets  
2. shell 通道：OneKuku=内嵌无线 ADB；Lite=Shizuku（不改 Manager）  
3. so → 公共 Download → `/data/local/tmp/preload-comet.so`  
4. `LD_PRELOAD=… /system/bin/id`  
5. 诚实验活：绝对路径 su + `/system/bin/id`，防 Drop-In mock

## 何时会成功 / 失败

| 条件 | 成功 | 常见失败 |
|---|---|---|
| `Build.DEVICE` + `Build.ID` 在远端/本地 catalog | 有匹配 so | `UnsupportedDevice` |
| OneKuku 已无线 ADB 配对 / Lite 已授权 Shizuku | 可执行白名单 shell | `NeedPairing` / `NeedShizuku` |
| 该 Build 的 exploit so 有效 | `uid=0(root)` / `root=1` | `exploit_ran_but_su_missing` |
| 网络拉 so（远端优先） | 首次可自动下载 | 离线且无 assets/缓存则失败 |

## 本轮证据（2026-08-05）

- 远端 catalog HTTP 200；**8 设备 / 34 device×build**（含 tokay/caiman/komodo/comet/blazer/…）  
- `TempRootSoProviderTest`（onekukuDebug）：**BUILD SUCCESS**  
- 本机 **adb NOT RUN**（PATH 无 `adb`）：未做真机一点冒烟

## 文档漂移

`docs/changes/2026-08-05-temproot-remote-so-fetch.md` 写「assets 优先」；现行 `TempRootSoProvider.ensure` 为**远端强制优先**，以代码为准。
