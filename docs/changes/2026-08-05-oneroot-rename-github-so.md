# OneRoot：单窗改名 + so 从 GitHub 拉取

## 需求对齐

1. 软件只开一窗，名称 **OneRoot**
2. 定位文案：让**没有解锁**的 Pixel 也能**运营商配置持久化**
3. 所有 so **从 GitHub OneSo-assets** 获取（`raw.githubusercontent.com/asrtroh-netizen/OneSo-assets/`）

## 实现

- 窗口标题 / 品牌：`OneRoot`；入口 `scripts/OneRoot.ps1`（`oneso-hub.ps1` 转调）
- `resolve_temp_root_so(prefer_github=True)`：catalog + so HTTP 白名单拉取 → `.cache/so/`
- Hub UI：一键持久化；去掉本地打包入口

## 验证

- `oneso temp-root` dry-run：从 GitHub 拉到 `preload-comet-CP2A.260705.006.so`（220728 bytes）
- `HUB_COUNT=1`，窗口标题 `OneRoot`
