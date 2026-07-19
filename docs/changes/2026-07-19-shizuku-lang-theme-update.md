# Shizuku 首页：Lang / 日月主题开关 / GitHub 检测下载

日期：2026-07-19  
工作树：`E:\GQ\One\_forks\HSSkyBoy-Shizuku-clean`

## 需求

1. 「检查更新」对齐 OneIMS：点一下先检测 GitHub Release，有新版 APK 则系统下载并拉起安装  
2. 右上角语言胶囊文案改为固定 `Lang`（不再显示「语言」）  
3. 主题改为左右滑动日月开关（点一下滑到另一侧）

## 实现

| 项 | 位置 |
|---|---|
| UpdateChecker / UpdateInfo | `manager/.../update/` → `asrtroh-netizen/shizuku` releases/latest |
| 检查更新按钮 | `LibrarySkinHome.CheckUpdateRow`：IO 检测 → 有则 `DownloadManager` |
| Lang 芯片 | `home_lang_chip`（`translatable="false"`） |
| 主题滑块 | `ThemeSlideToggle`（LightMode / DarkMode + 白圆钮动画） |
| 权限 | `REQUEST_INSTALL_PACKAGES` |

## 验证

- `:manager:compileReleaseKotlin`（本轮执行）
