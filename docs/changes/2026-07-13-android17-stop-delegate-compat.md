# Android 17：stopDelegateShellPermissionIdentity 兼容（2.0.3）

## 现象
真机截图（Pixel 10 Pro Fold / Android 17 API 37）：
- Snackbar：`配置写入与自动回滚均失败…`
- 详情：`BrokerExecutionException: override_config: NoSuchMethodException: IActivityManager.stopDelegateShellPermissionIdentity []`
- 设备详情同时出现「权限代理: 不支持」与「上次写入策略: instrumentation-shell-delegate」

## 根因
1. 旧 Broker 在 CarrierConfig 写入后的 `finally` 清理里，把 `stopDelegateShellPermissionIdentity` 反射失败**抬升为业务失败**，并触发自动回滚；回滚复用同一路径再次死在 stop。
2. `HiddenApiBypass 4.3`（2022）对 Android 17 hidden-API 表面过旧；标准 `Class.getMethod` 在 API 37 上会把受限成员伪装成 `NoSuchMethodException`。
3. 诊断文案把「代理探测失败」误写成「走 Shizuku 直调」，与非 root 实际仍走 Instrumentation 矛盾。

## 修复（2.0.3 / versionCode 12）
1. 升级 `hiddenapibypass` **4.3 → 6.1**（官方覆盖 Android 17），并关闭 `dependenciesInfo` 上报。
2. `resolveStart/StopDelegateMethod`：**优先 HiddenApiBypass**，再回退 getMethod / 模糊扫描；stop 仍缺失时 best-effort 返回，依赖 Instrumentation 退出回收委托。
3. `ShellDelegateCleanupPolicy`：benign stop 失败只打日志，不冒充写入失败、不误触发回滚（既有）。
4. 诊断 UX：修正「权限代理 / 绕过策略」文案；设备详情增加**应用版本号**便于确认制品。

## 验证
- 单测 `ShellDelegateCleanupPolicyTest`
- `compileDebugKotlin` / `testDebugUnitTest` / `packageNamedDebugApk` → `OneIms-2.0.3.apk`
- 真机 Android 17 写 CarrierConfig：需用户安装 2.0.3 后回归（看设备详情版本行）
