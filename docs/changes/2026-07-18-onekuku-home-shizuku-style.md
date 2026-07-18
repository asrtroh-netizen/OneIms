# 2026-07-18 · 独立版首页对齐新作 Shizuku（状态卡 + 四小方块）

## 变更

独立版（`onekuku` / OneKuku-standalone）首页抛开旧布局，改为邻仓新作 Shizuku（`_forks/thedjchi-Shizuku`）同构：

1. **品牌**：页标题使用 `channel_display_name` → **OneKuku**
2. **状态卡**：两态（未激活 / 就绪），就绪时标题为 OneKuku + pill `Active`；无底部 CTA
3. **四小方块**：应用管理 / 终端 / Root 启动 / 电脑 ADB（2×2）
4. **移除**：运营商推荐、保存/恢复快捷操作、无 SIM 行、底部设备卡、首页 SIM 选择胶囊

OneLink（Lite-Shizuku）仍走原 `StatusHero` + 运营商 + 保存/恢复 + 设备卡路径（与改前一致；仅因 `HomeScreen` 拆函数而代码位置调整）。

## 文件

| 文件 | 说明 |
|---|---|
| `app/.../ui/HomeScreen.kt` | flavor 分支：独立版新首页 / OneLink 旧首页 |
| `app/.../ui/OneKukuShizukuStyleHome.kt` | 状态卡 + 四格 Compose |
| `app/.../res/values/strings.xml` | 独立版首页文案 |

## 交互

| 入口 | 行为 |
|---|---|
| 状态卡（未激活） | 走无线配对引导或直接激活 |
| 状态卡（就绪） | 状态检查弹窗 |
| 应用管理 | 状态检查弹窗 |
| 终端 / Root | 提示弹窗（能力占位，对齐 Shizuku 四格位） |
| 电脑 ADB | 启动通道（配对引导 / 激活） |

## 非目标

- 未切换特权通道（仍为 OneBridge）；本改仅为首页 UI/信息架构
- 未嵌入邻仓 Shizuku APK 进程
- 发热相关的 3s binder 重投未在本改动中处理

## 验证

```text
./gradlew :app:compileOnekukuDebugKotlin :app:compileOnelinkDebugKotlin
# EXIT 0
```
