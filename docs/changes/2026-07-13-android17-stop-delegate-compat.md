# Android 17：stopDelegateShellPermissionIdentity 兼容

## 现象
群反馈写入失败：`NoSuchMethodException: IActivityManager.stopDelegateShellPermissionIdentity []`，自动回滚同样失败。

## 根因
`BrokerInstrumentation.withDelegatedShellIdentity` 的 finally 调用 `stopShellPermissionDelegation()`；在部分 Android 17 / OEM 上反射找不到 stop。旧逻辑在无主错误时**直接抛出清理异常**，把可能已成功的写入判失败，并拖垮回滚。

## 修复
1. `SystemApiBroker`：start/stop 多路径解析（getMethod / HiddenApiBypass / 模糊名）；stop 缺失时 best-effort 返回，依赖 Instrumentation 退出回收委托。
2. `supportsDelegate` 不再强制要求 stop 方法存在。
3. finally：benign stop 失败只打日志，不冒充业务失败（`ShellDelegateCleanupPolicy`）。

## 验证
- 单测 `ShellDelegateCleanupPolicyTest`
- `compileDebugKotlin` + `packageNamedDebugApk` → `OneIms-2.0.2.apk`
- 真机 Android 17 写 CarrierConfig：NOT RUN（需用户回归）
