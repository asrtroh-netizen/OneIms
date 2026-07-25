# OneTools 当前能力总表（产品）

> 版本锚定：本地产品包 **0.2.0**（versionCode 2）· 包名 `com.onetools.app`  
> 定位：**Pixel 专属** · OneIMS 轻度配套 · 不替代写配  
> 本地包：`onetools/dist/OneTools-latest-debug.apk`（不发 GitHub）

## 0. 产品边界（硬）

| 做 | 不做 |
|---|---|
| Pixel / 类原生 Phone 验收 Caller | 国产 ROM 拨号适配表 |
| Directory 原生归属行 + CallScreening | 来电悬浮窗当验收 |
| 离线 geo + 自有 OneBlock/onespam | 默认商业查号 / 绑第三方云骚扰库主路径 |
| Meter 对标 Pixel Meter 观感 | 各家 OEM SystemUI 专项 |
| 电池对齐 Accu 思路（干净室） | 非 root「完全等于 Accu」内核账本 |
| One 自主更新中心 | 嵌入第三方 GPL 更新器源码 |

---

## 1. 首页与通道

| 能力 | 状态 | 说明 |
|---|---|---|
| Shizuku 四态英雄卡 | ✅ | 未激活 / 激活中 / 就绪 / 休眠 |
| One 系列 UI 骨架 | ✅ | Page / 顶栏 / 20dp 卡 / 白胶囊主按钮 / 动态色 |
| 工具分区入口 | ✅ | 来电·网速 / 电池 / 录音 / 更新 / 导出诊断 |
| 诊断摘要导出 | ✅ | 设备+通道，本地分享不上传 |

---

## 2. 来电（Caller）

| 能力 | 状态 | 说明 |
|---|---|---|
| Contacts Directory 归属 | ✅ | 系统拨号/通话记录原生行验收 |
| CallScreening 拦截 | ✅ | 默认筛选角色 |
| 本地 geo.dat 归属 | ✅ | MIT 数据集；默认仅离线 |
| OneBlock + onespam 骚扰库 | ✅ | 精确/前缀；自有 Release 更新路径 |
| 本机举报 Phase1 | ✅ | 立刻进本地规则/onespam |
| 社区导出+ingest Phase2 | ✅ | 脚本回灌；expand 保留 community 行 |
| 云查号默认关 | ✅ | `noNetworkQuery=true`；不买商业 API |
| Phase3 Worker 自动收举报 | ○ | 可选，未做 |

---

## 3. 网速（Meter）

| 能力 | 状态 | 说明 |
|---|---|---|
| 通知栏实时上下行 | ✅ | 忽略 VPN 虚接口 |
| 悬浮窗 / 贴顶观感 | ✅ | 对标 Pixel Meter 思路 |
| 分应用流量 | ✅ | 需使用情况访问 |
| QS Tile | ✅ | 通知/悬浮快捷开关 |
| 与 Caller 同页 Hub | ✅ | Tab 切换；电池单独入口 |

---

## 4. 电池（Battery）

| 能力 | 状态 | 说明 |
|---|---|---|
| 实时仪表 | ✅ | 电量/温度/电流/状态等 |
| 充电会话跟踪 + 闹钟 | ✅ | 默认 80% |
| 容量/健康估算 | ✅ | 有效充电样本 |
| Pixel 设计容量预设 | ✅ | 检测本机 / 芯片手选 |
| 分应用耗电 | ✅ | 前台归因；需用量权限 |
| 放电曲线 + Deep sleep 估算 | ✅ | 历史 Tab；Deep sleep 为启发式 |
| BatteryStats 账本 | ✅ | Shizuku UserService dumpsys（回退 newProcess） |
| 桌面 Widget | ✅ | 电量/状态/温度；点击进电池页 |
| Widget / Accu 级细交互再磨 | ○ | 可用，非 Accu 克隆 |

---

## 5. 通话录音（Recorder）

| 能力 | 状态 | 说明 |
|---|---|---|
| 干净室自研录音 | ✅ | Shizuku UserService 优先双声道 |
| OEM 阶梯回退 | ✅ | 失败再降级 |
| 同意与列表播放分享 | ✅ | 本机文件 |

---

## 6. 应用更新（One 自主更新中心）

| 能力 | 状态 | 说明 |
|---|---|---|
| 可增删源目录 | ✅ | DataStore 持久化 |
| GitHub / GitLab / Forgejo | ✅ | Release APK |
| F-Droid / Direct / HTML | ✅ | HTML 为页面抽链回退 |
| One Index（可签名/Token） | ✅ | 自有协议 |
| ABI 选包 + 版本状态 | ✅ | 可更新优先排序 |
| APK 正则 / 预发布开关 | ✅ | 加源时可配 |
| 下载安装 / Shizuku 静默装 | ✅ | 失败回退系统安装 |
| 后台定时检查+通知 | ✅ | WorkManager，默认可关 |
| 目录导入导出 | ✅ | JSON |
| 本机自更新真源 | △ | 需自备索引/APK；默认不依赖发 GitHub |

---

## 7. 工程与分发

| 项 | 状态 |
|---|---|
| 本地产品包脚本 | ✅ `onetools/scripts/build-local-apk.ps1` |
| 当前测试 APK | ✅ `onetools/dist/OneTools-latest-debug.apk` |
| GitHub Release / push | ❌ 按产品决策：本地测，不发 |

---

## 8. 诚实上限（勿当漏做）

- Deep sleep / wakelock：估算或 best-effort，非内核真值  
- 非 root 精确系统 BatteryStats 全账本做不到  
- Caller 成功标准 = Pixel 上 Directory 原生行，不是悬浮窗  
- 更新中心不嵌入第三方 GPL 更新器源码  
