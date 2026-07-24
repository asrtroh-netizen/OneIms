# 2026-07-25 · OneTools 脚手架（Shizuku + 四态）

> 状态：脚手架可组装  
> 关联：`docs/architecture/2026-07-25-onetools-architecture-blueprint.md` v0.3

## 落地

| 项 | 内容 |
|---|---|
| Module | `:onetools` · `applicationId=com.onetools.app` |
| 主题 | `OneToolsTheme` 对齐 OneIMS `Theme.kt`（primary 白） |
| 首页 | 四态 `StatusHero` + OneLink 节奏分区（无写配入口） |
| 通道 | `ShizukuChannel` + `ShizukuProvider`（禁 `:bridge`） |
| 生命周期 | `ON_STOP`→休眠 · `ON_START`→就绪（已授权时） |

## 验证

```powershell
./gradlew :onetools:testDebugUnitTest :onetools:assembleDebug
```

- `ChannelCardPolicyTest` · PASS  
- APK：`onetools/build/outputs/apk/debug/onetools-debug.apk`

## 明确未做（后续）

- 完整逐文件移植 `OneLinkHome` 全部依赖（Root 开机卡、写配 ActionGrid 已按 Out 墙排除）
- 设备摘要实采 / 导出 Markdown
- 与 OneIMS 深链
