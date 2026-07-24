# 2026-07-25 · OneTools 通话录音软件调研（集成选型）

> 用户：查询市面通话录音软件，并考虑集成进 OneTools（拟售卖）。  
> 本轮：**只读调研 + 选型建议**，未并入任何录音源码。

## 0. 硬前提（先于选型）

1. **法律**：多数地区对通话录音有「单方/双方同意」要求；产品文案必须提示用户自担合规。本调研不构成法律意见。
2. **系统**：Android 9+ 对通话双方音频捕获极严；普通 App 用 `VOICE_COMMUNICATION` 往往只能录到本端。可靠双方录音通常需要：
   - 系统/拨号器内置能力，或
   - **Root / 特权权限**（`CAPTURE_AUDIO_OUTPUT` 等），或
   - **Shizuku/ADB shell 侧技巧**（近年出现，OEM 易碎）
3. **售卖与许可**：OneTools 若闭源/商用，**不能把 GPL-3 录音 App 源码嵌进 `:onetools`**（传染风险，同 Obtainium）。允许：更新中心安装对方 APK、外链、或自研干净室。

## 1. 主流可见方案对照

| 方案 | 类型 | 许可 | 技术路径 | 与 OneTools/Shizuku | 集成建议 |
|---|---|---|---|---|---|
| **BCR** ([chenxiaolong/BCR](https://github.com/chenxiaolong/BCR)) | FOSS · 口碑标杆 | **GPL-3.0** | Root / 系统特权；`InCallService`；自动后台录 | **明确不做 Shizuku**（作者：shell 拿不到完整权限且难 export InCallService） | ❌ 勿并源码；✅ 可通过更新中心一键安装 APK |
| **ShizuCallRecorder** ([kitsumed/ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder)) | FOSS · 非 Root | **GPL-3.0**（含额外商标条款） | **Shizuku + scrcpy-server** 思路；Android 11+ | **与 OneTools 通道最贴** | ❌ 勿并源码；✅ 外置安装 + 深链打开；或长期自研干净室学思路 |
| **Call Recorder · SKVALEX** | 闭源商业 | 专有（付费） | Root / Magisk 模块增强双方录音 | 无源码可合；功能强 | ❌ 不合；可选「推荐安装」外链（注意竞品/条款） |
| **Cube ACR** 等应用商店产品 | 闭源 | 专有 | Accessibility / 通知 / OEM 技巧，机型差异大 | 与 Shizuku 无绑定 | ❌ 不合；体验不稳定不宜当核心卖点 |
| **OEM/Google Phone 内置录音** | 系统能力 | N/A | 运营商/地区解锁 | 无法「集成」进三方 App | 仅文档提示「优先用系统拨号器录音」 |
| **axet Call Recorder**（历史 F-Droid） | FOSS 老牌 | GPL | 旧 API，新系统可用性差 | 不推荐新项目依赖 | ❌ |

## 2. 与 OneTools 产品契合度

OneTools 已定：**Shizuku 通道、不引入 `:bridge`、拟售卖、强调自研 IP**。

| 路径 | 描述 | 工作量 | 许可/卖点 | 推荐度 |
|---|---|---|---|---|
| **A. 外置工具卡** | 首页卡片 → 检查/安装 BCR 或 ShizuCall（走现有更新中心 GitHub/One Index）→ 打开对方 App | S～M | 不碰 GPL 源码；卖的是「一键编排」 | ⭐⭐⭐⭐ 短期首选 |
| **B. 自研干净室录音** | 参考公开技术事实（非抄源），用 Shizuku 做最小录音器 + One 文件管理 UI | L～XL | IP 全自有；OEM 碎、维护贵 | ⭐⭐⭐ 中长期差异化 |
| **C. 合并 GPL 源码进 APK** | 直接嵌 BCR/ShizuCall | — | **与商用冲突** | ⛔ 禁止 |
| **D. 只做通话记录元数据** | 读 CallLog（需权限），不录音频 | S | 许可干净但不是「录音」 | 仅作弱化备选 |

## 3. 推荐决策（给哥哥拍板）

1. **短期（可卖、可控）**：走 **路径 A**  
   - 默认推荐：**ShizuCallRecorder**（Shizuku 非 Root，和 OneTools 通道一致）  
   - Root 用户可选：**BCR**（成熟稳定）  
   - 用现有「类 Obtainium / One Index」安装与更新，不做源码合并。
2. **中期（自我 IP）**：若录音要成为付费卖点，再立项 **路径 B 干净室**，并配套地区合规开关与明示同意 UI。
3. **文案**：集成页必须有「请确认当地法律允许录音」勾选，不能默认静默全录。

## 4. 证据指针

- BCR 许可与 Root 定位：GitHub `chenxiaolong/BCR` README（GPL-3.0）  
- BCR 拒 Shizuku：Issue [#278](https://github.com/chenxiaolong/BCR/issues/278)  
- ShizuCallRecorder：F-Droid `com.kitsumed.shizucallrecorder` + GitHub GPL-3.0  
- SKVALEX：Play / Magisk 模块（闭源）

## 5. 本轮未做

- 未下载/合并任何录音项目源码  
- 未改 `:onetools` 业务代码（待你选型 A/B）
