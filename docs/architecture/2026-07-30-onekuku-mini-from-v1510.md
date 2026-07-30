# 2026-07-30 · OneKuku Care / MINI（V15.1.0 缩减独立包）

**仓**：`E:\GQ\One\_forks\ShizukuDropIn-Local`  
**Flavor**：`onekukuMini`  
**包名**：`com.onekuku.care` · `versionName=OneKuku-MINI` · `versionCode=151010`

## 目标

把 V15.1.0 DropIn 收成 **OneIMS 专用迷你通道包**，为撤内嵌 OneBridge 铺路。

## 已砍（onekukuMini）

| 项 | 做法 |
|---|---|
| 自动化卡 + automation | Settings 硬关；Manifest remove AutomationService / AICore+ / Locale 插件 |
| Samsung UID1000 | Settings getter MINI 恒 false |
| 终端 / 活动日志 / 了解更多 | Settings 硬关；首页去入口；Manifest remove 对应 Activity |
| Service Doctor | 设置隐藏；Manifest remove |
| Root Compatibility Hub | 菜单/设置隐藏；Manifest remove；`experimental_root_compat` 恒 false |

## 构建

```bat
cd /d E:\GQ\One\_forks\ShizukuDropIn-Local
gradlew :manager:assembleOnekukuMiniRelease
```

## 与 OneIMS 衔接（纠偏后路径）

> 冻结内循环后：**不要**把「探测外置 `com.onekuku.care` + 当第二 App」当主路径。  
> 正确衔接 = **把本 MINI 的 server 最小面融进宿主**（`ChannelEngine.CARE_MIN`），替换旧 OneBridge。

| 步 | 状态 | 说明 |
|---|---|---|
| P0 `ChannelEngine` + 迁入白名单 | **已开**（OneIMS） | 默认仍 `ONEBRIDGE` |
| P3a 白名单迁入 server | 未做 | `2026-07-30-care-min-server-import-whitelist.md` |
| P3b 旗标切换 + 宿主 Provider | 未做 | 进程名 `onekuku_server` |
| P3c 撤 `:bridge` | 未做 | 验收后 |
| 物理删 Plus 模块减体积 | 邻仓继续 | `2026-07-30-v1510-strip-candidates.md` |

## 注意

- **不是** stock `moe.shizuku.privileged.api`；邻仓 Care 可与官方/DropIn 同机并存作对照  
- 宿主融合后 Manager/API 指向 **`com.oneims.app`**，不要求用户安装 Care  

