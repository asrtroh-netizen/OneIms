# 2026-08-05 · OneRoot App fork（Root-My-Pixel）+ 拓宽设备

## 结论

- GitHub fork：https://github.com/asrtroh-netizen/OneRoot（上游 `alex193a/Root-My-Pixel`）
- 本地工作副本：`E:/GQ/One/_forks/OneRoot`
- 产品名 / 包名：`OneRoot` / `com.oneroot.app`
- 设计语言：对齐 `OneRoot/web/app.css` 青绿深色（强制 dark，关闭 dynamicColor）
- so 特性：从 `OneSo-assets/catalog.json` 生成 **40** 条 profile，覆盖 8 机型 × 多 build；匹配改为 device+build 必选、kernel 软优先；APK 缺 so 时回落 OneSo-assets raw

## 拓宽点（相对上游）

上游 profiles ≈ 6 条且强依赖 kernel 精确匹配。  
OneRoot：`tokay/caiman/komodo/comet/blazer/frankel/mustang/rango` + `CP1A.*` / `CP2A.*` 多 OTA。

## 维护

```powershell
cd E:\GQ\One\_forks\OneRoot
.\scripts\sync-oneso-assets.ps1 -AssetsRoot E:\GQ\One\OneSo-assets
```

## 边界

- 未宣称 catalog 以外机型可用（如上游曾有的 `stallion` 若不在 OneSo catalog，则不在本版矩阵）
- Windows 全量 `build-all.sh` / 真机安装冒烟：按环境另验
- 上游 LICENSE 缺失；README 保留 Credits
