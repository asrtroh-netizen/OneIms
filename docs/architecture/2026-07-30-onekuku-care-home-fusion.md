# 2026-07-30 · OneIMS 内循环：MINI 能力融进首页（单 APK）

**规模**：L  
**状态**：方案已按用户澄清纠偏（原「外置 Care 双 APK」作废）  
**澄清原话**：总之就是相当于一个**内循环**  
**产品定调（更新）**：见 `2026-07-30-onekuku-mirrors-lite-shizuku.md` ——  
OneIMS 内循环 = Lite⊕Shizuku 的分工镜像，通道侧是**增强旧 OneKuku**（非外置第二 App）。

## 0. 产品真源（冻结）

| 要 | 不要 |
|---|---|
| 用户只在 **OneIMS 首页**完成：启动 → 授权/就绪 → 写配置 → 划掉/冷启复连 | 再装/再开第二个 `com.onekuku.care` App |
| 单 APK 闭环（`com.oneims.app`） | 外置双 APK 薄壳（上拍方案 A，已否） |
| 把 MINI/Care **已验证能力**（冷启/热路径/首页心智）吃进内循环 | 把 DropIn 全家桶源码整仓塞进 `:app` |
| 对外仍叫 OneKuku 通道 | 「再装一套 Shizuku/Care」叙事 |

邻仓 `com.onekuku.care` = **能力对照与编包试验田**，不是用户必经路径。

## 1. 「放弃特权通道」怎么读

不是「OneIMS 不再有 shell binder」，而是：

- 放弃**旧叙事/旧双轨**（独立桥包、外置 Manager、第二套 UI）  
- 内循环仍需要等价特权进程（今天是 `onebridge_server`；可逐步换成 Care 同源 starter，但**仍由主包拉起、首页驱动**）

## 2. 推荐工程形态（纠偏后）

**S2′（选定）**：保留 `PrivilegeBridge` 门面 + 宿主内嵌 server，把 Care/V15 **冷启精华与首页体验**迁入 OneIMS；不把整包 Care Manager 嵌进主包。

| 对比 | 结论 |
|---|---|
| S1 整仓迁 Care server/UI 进主包 | 体量大、权限/Provider/进程名冲突多；与「最小特权桥」立项冲突 |
| **S2′ 内循环增强（选）** | 复用现网 OneBridge 写入路径；补 SelfStarter/UserPresent/WifiReady 级韧性；首页继续 Hero 三态 |
| 外置 Care（旧 A） | 与「内循环」冲突 → **否** |

## 3. 数据流（目标态）

```
OneIMS 首页 Hero
  → 内嵌 ADB（可选）pair/connect
  → app_process 拉起宿主 CLASSPATH 内 server（现 onebridge_server）
  → Binder → PrivilegeBridge → SystemApiBroker
       (activity / carrier_config / isub / phone)
  → 冷启：Boot/UserPresent/WifiReady/Watchdog（对齐 MINI 已迁能力）
```

用户全程不离开 OneIMS。

## 4. 阶段计划

| 阶段 | 内容 | 完成标准 |
|---|---|---|
| **P0** 纠偏 | 本文档；探测列表可保留 Care 作实验室回落，但产品路径不依赖 | 文档冻结 |
| **P1** 首页内循环体验 | Hero/文案/引导统一「通道在 App 内」；去掉「去开 Shizuku/Care」主路径 | 文案+状态机自洽 |
| **P2** 冷启对齐 MINI | 对照 Care oem1/oem6：解锁重试、WifiReady、SelfStarter 级热路径补进 onekuku | 冷启/划掉矩阵（真机） |
| **P3** 实现收敛 | 若 OneBridge 协议够用则留门面换引擎；不够再评估迁 Care server **最小面** | 写配置 PASS；无第二 App |

## 5. 与上拍脚手架的关系

| 项 | 处理 |
|---|---|
| `ShizukuSetupHelper.CARE_PACKAGE` | 可留作实验室/对照；**内循环主路径不走 open Care** |
| `CANDIDATE_PACKAGES` Care 首位 | 将改回**宿主优先**（内循环真源=主包） |
| `CHANNEL_USES_EMBEDDED_BRIDGE` | **保持 true**（内循环=内嵌），直到有可证明的单包替换引擎 |

## 6. 验收（内循环）

1. 只装 OneIMS（onekuku），不装 Care  
2. 首页完成激活 → READY → 写 `carrier_config`  
3. 划掉 / 冷启后能回到 READY（或明确需点一次）  
4. 全程无强制跳转第二 App  

## 7. 本拍

- 纠偏文档（本文件）  
- 候选包序改回宿主优先（见代码）  
