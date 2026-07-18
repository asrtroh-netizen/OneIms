# 全局主色蓝 → 白（2026-07-18）

## 动机

用户要求：全局蓝色强调全部换成白色（不单首页「启动」钮）。

## 改动

- `Theme.kt`：`LightColors` / `DarkColors` 的 `primary` 改为 `Color.White`，`onPrimary` 为深色；去掉蓝系 `primaryContainer` / `secondaryContainer` 残留。
- `shizuku_home_colors.xml`：`app_color_*` 与 activating hero 蓝改为白。
- `ShizukuHomePalette`：accent 跟 `MaterialTheme.colorScheme.primary`。
- `OneImsComponents` 底栏：选中图标/文字用 `onSurface`，选中圆底用 `onSurface@14%`，避免浅色主题白-on-白不可见。

## 验证

- `:app:packageNamedOnekukuDebugApk` + `adb install -r` → Success。
