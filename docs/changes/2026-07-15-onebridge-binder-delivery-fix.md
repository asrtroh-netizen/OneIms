# 2026-07-15 · OneBridge binder 投递修复（真机 Pixel 9 Pro Fold）

## 现象
USB 真机：`onebridge_server` 能起来，但 OneIms 一直「通道未激活」。log：
`send binder failed` / `provider binder missing` → 后续 `IContentProvider$Stub` / `authority unknown`。

## 根因链
1. **缺桥包**：设备原先无 `com.oneims.bridge`（UI「未安装」）。
2. **`as? IBinder` 误判**：`ContentProviderHolder.provider` 类型是 `IContentProvider`，强制转 `IBinder` 恒 null → 假报 `provider binder missing`。
3. **`Looper` 顺序**：`systemMain` 在 `prepareMainLooper` 之前调用会失败。
4. **调用入口**：现代 Android 无 `IContentProvider$Stub`，应走 `ContentProviderNative.asInterface`。
5. **call 参数**：须用 `AttributionSource.Builder(SHELL_UID).setPackageName("com.android.shell")` + `call(attr, authority, method, arg, extras)`；乱序会导致 `authority unknown`。

## 修复
- `bridge/.../BridgeService.kt`：Looper 顺序、`extractProviderBinder`、`ContentProviderNative`、AttributionSource.Builder、按签名匹配 call。
- 同步更新 `app/src/main/assets/oneims-bridge.apk`。
- 文案澄清（2.0.17）仍保留：热点下「IP 不可用」可忽略。

## 真机验证（本轮）
- 设备：Pixel 9 Pro Fold `47111FDKD0009J`
- `OneBridgeClient: OneBridge binder received` PASS
- `binder sent to com.oneims.app.onebridge` PASS
- UI：`OneKuku 通道已激活` / `设备已就绪` PASS
- 无线调试配对路径：本轮用 USB adb 拉起验证 binder；出门无电脑路径仍依赖内嵌 ADB（未在本轮重跑）

## 注意
先保证 OneIms 进程在前台/存活，再拉起 bridge，否则 force-stop 后 binder 会丢，需再 start 一次。
