package com.oneims.app.core

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.oneims.app.core.privilege.PrivilegeBridges
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 尽量屏蔽系统 OTA / 自动更新（Pixel 常见路径）——**能用则用、不能则跳过**。
 *
 * 层：
 * 1. `package`：禁用 factoryota / GMS·GSF 更新组件
 * 2. Settings：`ota_disable_automatic_update=1`（开发者选项「自动系统更新」）
 * 3. hosts：有 Root/`su` 时写入 Magisk/KSU 模块挡 Google OTA 域名
 *
 * 不保证挡死所有渠道；可能影响 Google Play 系统更新。
 */
object SystemUpdateShield {
    private const val TAG = "OneIMS-UpdateShield"

    private const val STATE_DEFAULT = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    private const val STATE_DISABLED_USER = PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
    private const val STATE_ENABLED = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    private const val DONT_KILL_APP = PackageManager.DONT_KILL_APP

    private const val PERM_WRITE_SECURE = "android.permission.WRITE_SECURE_SETTINGS"
    /** AOSP Settings.Global：关闭「自动系统更新」。 */
    private const val KEY_OTA_DISABLE = "ota_disable_automatic_update"
    private const val MAGISK_MODULE_ID = "oneims_ota_block"

    private val PACKAGES = listOf(
        "com.google.android.factoryota",
    )

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

    /** Pixel / Google OTA 常见域名（hosts 层）。 */
    private val OTA_HOSTS = listOf(
        "ota.googlezip.net",
        "ota-cache1.googlezip.net",
        "ota-cache2.googlezip.net",
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
            val layers = mutableListOf<String>()

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
            if (touched > 0) layers += "组件"

            when (tryOtaSettings(context, pm, shield)) {
                true -> {
                    touched++
                    layers += "设置"
                }
                false -> skipped++
                null -> skipped++
            }

            when (tryHostsLayer(shield)) {
                true -> {
                    touched++
                    layers += "hosts"
                }
                false -> skipped++
                null -> { /* 无 su / 无 Magisk，不算失败 */ }
            }

            val action = if (shield) "已尽量屏蔽" else "已恢复"
            val layerText = if (layers.isEmpty()) "（无有效层）" else "（${layers.joinToString("+")}）"
            Result(
                ok = touched > 0 || !shield,
                message = "$action$layerText 改动 $touched，跳过 $skipped",
                touched = touched,
                skipped = skipped,
            )
        }.getOrElse { error ->
            Log.w(TAG, "mutate failed: ${error.message}")
            Result(false, error.message ?: error.javaClass.simpleName)
        }
    }

    /**
     * @return true 成功；false 尝试失败；null 无权限且无法授予（跳过）
     */
    private fun tryOtaSettings(context: Context, pm: Any, shield: Boolean): Boolean? {
        tryGrantWriteSecure(pm, context)
        val value = if (shield) 1 else 0
        return runCatching {
            val ok = Settings.Global.putInt(context.contentResolver, KEY_OTA_DISABLE, value)
            if (ok) {
                Log.i(TAG, "$KEY_OTA_DISABLE=$value")
                true
            } else {
                Log.i(TAG, "settings put returned false")
                false
            }
        }.getOrElse {
            Log.i(TAG, "settings skip: ${it.message}")
            null
        }
    }

    private fun tryGrantWriteSecure(pm: Any, context: Context) {
        runCatching {
            val method = pm.javaClass.methods.firstOrNull { candidate ->
                candidate.name == "grantRuntimePermission" &&
                    candidate.parameterTypes.size >= 2
            } ?: return
            when (method.parameterTypes.size) {
                2 -> method.invoke(pm, context.packageName, PERM_WRITE_SECURE)
                3 -> method.invoke(pm, context.packageName, PERM_WRITE_SECURE, context.userIdSafe())
                else -> method.invoke(pm, context.packageName, PERM_WRITE_SECURE, 0)
            }
            Log.i(TAG, "granted $PERM_WRITE_SECURE")
        }.onFailure {
            Log.i(TAG, "grant $PERM_WRITE_SECURE skip: ${it.message}")
        }
    }

    /**
     * Magisk/KSU 模块写 hosts；无 `su` 或无 `/data/adb` 则跳过（null）。
     * @return true 成功；false 执行失败；null 环境不具备
     */
    private fun tryHostsLayer(shield: Boolean): Boolean? {
        val adb = java.io.File("/data/adb")
        val hasSuHint = java.io.File("/system/bin/su").exists() ||
            java.io.File("/system/xbin/su").exists() ||
            java.io.File("/sbin/su").exists()
        if (!hasSuHint && !adb.exists()) {
            Log.i(TAG, "hosts skip: no su / Magisk path")
            return null
        }
        val cmd = if (shield) buildHostsInstallScript() else buildHostsRemoveScript()
        val ok = RootBootStarter.execSu(cmd)
        return if (ok) {
            Log.i(TAG, "hosts layer ok shield=$shield")
            true
        } else {
            Log.i(TAG, "hosts layer failed (su missing or denied)")
            false
        }
    }

    private fun buildHostsInstallScript(): String {
        // 单行 su -c：写 Magisk/KSU 模块，重启后挂载；已有则覆盖
        val hostEcho = (listOf("127.0.0.1 localhost", "::1 localhost") +
            OTA_HOSTS.map { "127.0.0.1 $it" })
            .joinToString("\\n")
        return listOf(
            "MOD=/data/adb/modules/$MAGISK_MODULE_ID",
            "mkdir -p \$MOD/system/etc || exit 1",
            "printf 'id=$MAGISK_MODULE_ID\\nname=OneIMS OTA Hosts Block\\nversion=1.0\\nversionCode=1\\nauthor=OneIMS\\ndescription=Block Google OTA hosts\\n' > \$MOD/module.prop || exit 1",
            "printf '$hostEcho\\n' > \$MOD/system/etc/hosts || exit 1",
            "touch \$MOD/update 2>/dev/null",
            "exit 0",
        ).joinToString("; ")
    }

    private fun buildHostsRemoveScript(): String =
        "rm -rf /data/adb/modules/$MAGISK_MODULE_ID; exit 0"

    private fun packageManagerProxy(binder: IBinder): Any {
        val stub = Class.forName("android.content.pm.IPackageManager\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("IPackageManager null")
    }

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
