# OneKuku 换皮核心包（隐藏上游名）

**日期**：2026-07-14  
**目标包名**：`com.oneims.onekuku.core`  
**显示名**：OneKuku 核心  

## 为什么不能只改文案

系统应用列表显示的是**服务端 APK 的 `applicationId` / `label` / 图标**。  
OneIMS 客户端即使全程说 OneKuku，只要装的是 `moe.shizuku.privileged.api`，设置里仍可能看到上游名。

真·隐藏 = **自建/Fork 服务端 APK**，并优先安装它。

## 本仓已就绪的客户端侧

| 项 | 状态 |
|---|---|
| 双包探测：换皮优先，上游回落 | ✅ `OneKukuCoreComponent.CANDIDATE_PACKAGES` |
| start.sh / ADB 路径随已装包解析 | ✅ |
| 内置 `assets/onekuku-core.apk` 安装 | ✅（需你放入换皮产物） |
| 完整 Shizuku 工程 Fork 与签名发包 | ❌ 另立项（本仓库不内嵌上游完整源码树） |

## 建议构建步骤（在独立目录）

1. Clone 上游 Shizuku（遵守其许可证：Apache-2.0 等，保留 NOTICE）。  
2. 修改 manager 模块：  
   - `applicationId` → `com.oneims.onekuku.core`  
   - `app_name` / 图标 → OneKuku 核心  
   - 启动器名、通知文案去掉上游品牌  
3. 确认 starter / `start.sh` 输出目录落在  
   `/Android/data/com.oneims.onekuku.core/`  
4. 签名打 release APK，复制为：  
   `app/src/main/assets/onekuku-core.apk`  
5. 重编 OneIMS，首页「启动核心」应优先识别换皮包。  
6. 真机验收：设置 → 应用列表 **应出现 OneKuku 核心**，不应再依赖商店页的上游 App 名。

## 兼容策略

- 用户已装上游包：仍可激活（回落）。  
- 同时装两包：优先换皮包。  
- 下载回落仍可能拉到上游 Release（过渡）；换皮产物就绪后应改为自有 Release URL。

## 风险

- Fork 维护成本高（跟进上游安全补丁）。  
- 错误改包名可能导致 rikka 客户端收不到 binder——必须以真机 `pingBinder` / 写 CarrierConfig 验收。  
- 许可证与署名：分发前核对上游 LICENSE/NOTICE。
