package com.oneims.app.core

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.oneims.app.core.privilege.PrivilegeBridges
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 尽量屏蔽系统 OTA / 自动更新（Pixel 常见路径）。
 *
 * - 需要特权通道（OneKuku / Shizuku）包装 `package` 服务
 * - **不保证**挡死所有更新渠道；可能影响 Google Play 系统更新
 * - 关闭开关时按清单恢复为默认启用状态
 */
object SystemUpdateShield {
    private const val TAG = "OneIMS-UpdateShield"

    private const val STATE_DEFAULT = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    private const val STATE_DISABLED_USER = PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
    private const val STATE_ENABLED = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    private const val DONT_KILL_APP = PackageManager.DONT_KILL_APP

    /** 整包禁用（存在才动手）。 */
    private val PACKAGES = listOf(
        "com.google.android.factoryota",
    )

    /** 组件禁用（GMS / GSF 更新相关；不存在则跳过）。 */
    private val COMPONENTS = listOf(
        ComponentName(
            "com.google.android.gms",
            "com.google.android.gms.update.SystemUpdateService",
        ),
        ComponentName(
            "com.google.android.gms",
            "com.google.android.gms.update.SystemUpdateService\$ActiveReceiver",
        ),
        ComponentName(
            "com.google.android.gms",
            "com.google.android.gms.update.SystemUpdateService\$Receiver",
        ),
        ComponentName(
            "com.google.android.gsf",
            "com.google.android.gsf.update.SystemUpdateService",
        ),
        ComponentName(
            "com.google.android.gsf",
            "com.google.android.gsf.update.SystemUpdateService\$Receiver",
        ),
    )

    data class Result(
        val ok: Boolean,
        val message: String,
        val touched: Int = 0,
        val skipped: Int = 0,
    )

    fun isEnabled(context: Context): Boolean =
        ConfigStore.isSystemUpdateShield(context)

    fun setPreference(context: Context, enabled: Boolean) {
        ConfigStore.setSystemUpdateShield(context, enabled)
    }

    /** 按偏好施加或撤销；通道未就绪时返回失败。 */
    fun applyPreference(context: Context): Result {
        return if (isEnabled(context)) apply(context) else clear(context)
    }

    fun apply(context: Context): Result = mutate(context, shield = true)

    fun clear(context: Context): Result = mutate(context, shield = false)

    @SuppressLint("PrivateApi")
    private fun mutate(context: Context, shield: Boolean): Result {
        val bridge = PrivilegeBridges.current
        if (!bridge.isRunning() || !bridge.isGranted()) {
            return Result(false, "需要先激活特权通道")
        }
        return runCatching {
            HiddenApiBypass.addHiddenApiExemptions("")
            val pm = packageManagerProxy(bridge.wrapSystemService("package"))
            var touched = 0
            var skipped = 0
            for (pkg in PACKAGES) {
                when (setPackageEnabled(pm, context, pkg, enabled = !shield)) {
                    true -> touched++
                    false -> skipped++
                    null -> skipped++
                }
            }
            for (component in COMPONENTS) {
                when (setComponentEnabled(pm, component, enabled = !shield)) {
                    true -> touched++
                    false -> skipped++
                    null -> skipped++
                }
            }
            val action = if (shield) "已尽量屏蔽" else "已恢复"
            Result(
                ok = touched > 0 || !shield,
                message = "$action（改动 $touched，跳过 $skipped）",
                touched = touched,
                skipped = skipped,
            )
        }.getOrElse { error ->
            Log.w(TAG, "mutate failed: ${error.message}")
            Result(false, error.message ?: error.javaClass.simpleName)
        }
    }

    private fun packageManagerProxy(binder: IBinder): Any {
        val stub = Class.forName("android.content.pm.IPackageManager\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("IPackageManager null")
    }

    /**
     * @return true=已改；false=目标不存在；null=调用失败
     */
    private fun setPackageEnabled(
        pm: Any,
        context: Context,
        packageName: String,
        enabled: Boolean,
    ): Boolean? {
        if (!packageInstalled(context, packageName)) return false
        val state = if (enabled) STATE_ENABLED else STATE_DISABLED_USER
        return runCatching {
            val method = pm.javaClass.methods.firstOrNull { candidate ->
                candidate.name == "setApplicationEnabledSetting" &&
                    candidate.parameterTypes.size >= 3
            } ?: error("setApplicationEnabledSetting missing")
            when (method.parameterTypes.size) {
                3 -> method.invoke(pm, packageName, state, DONT_KILL_APP)
                4 -> method.invoke(pm, packageName, state, DONT_KILL_APP, context.userIdSafe())
                5 -> method.invoke(
                    pm,
                    packageName,
                    state,
                    DONT_KILL_APP,
                    context.userIdSafe(),
                    context.packageName,
                )
                else -> method.invoke(pm, packageName, state, DONT_KILL_APP)
            }
            Log.i(TAG, "package $packageName -> $state")
            true
        }.getOrElse {
            Log.w(TAG, "package $packageName failed: ${it.message}")
            null
        }
    }

    private fun setComponentEnabled(
        pm: Any,
        component: ComponentName,
        enabled: Boolean,
    ): Boolean? {
        val state = if (enabled) STATE_DEFAULT else STATE_DISABLED_USER
        return runCatching {
            val method = pm.javaClass.methods.firstOrNull { candidate ->
                candidate.name == "setComponentEnabledSetting" &&
                    candidate.parameterTypes.isNotEmpty() &&
                    candidate.parameterTypes[0] == ComponentName::class.java
            } ?: error("setComponentEnabledSetting missing")
            when (method.parameterTypes.size) {
                3 -> method.invoke(pm, component, state, DONT_KILL_APP)
                4 -> method.invoke(pm, component, state, DONT_KILL_APP, 0)
                5 -> method.invoke(pm, component, state, DONT_KILL_APP, 0, null)
                else -> method.invoke(pm, component, state, DONT_KILL_APP)
            }
            Log.i(TAG, "component ${component.flattenToShortString()} -> $state")
            true
        }.getOrElse {
            // 组件不存在时多数抛 NameNotFound / RemoteException，当作跳过
            Log.i(TAG, "component skip ${component.flattenToShortString()}: ${it.message}")
            false
        }
    }

    private fun packageInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)

    private fun Context.userIdSafe(): Int =
        runCatching {
            javaClass.getMethod("getUserId").invoke(this) as Int
        }.getOrDefault(0)
}
