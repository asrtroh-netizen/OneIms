package com.onetools.app.updates

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.IBinder
import com.onetools.app.channel.ShizukuChannel
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Privileged PackageInstaller session via Shizuku (shell/root identity).
 * Falls back is caller's responsibility when Result fails.
 */
object ShizukuApkInstaller {
    private const val INSTALL_REPLACE_EXISTING = 0x00000002
    private const val INSTALL_ALLOW_DOWNGRADE = 0x00000080
    private const val INSTALL_FULL_APP = 0x00004000

    fun isAvailable(): Boolean = ShizukuChannel.isServiceReady()

    fun install(context: Context, apk: File): Result<String> = runCatching {
        require(isAvailable()) { "Shizuku 未就绪" }
        require(apk.isFile && apk.length() > 0L) { "APK 无效" }

        val appContext = context.applicationContext
        val iPackageInstaller = resolvePackageInstaller()
        val userId = android.os.Process.myUid() / 100_000
        val packageInstaller = createPackageInstaller(
            appContext,
            iPackageInstaller,
            appContext.packageName,
            userId,
        )

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        setInstallFlags(
            params,
            getInstallFlags(params) or INSTALL_REPLACE_EXISTING or INSTALL_ALLOW_DOWNGRADE or INSTALL_FULL_APP,
        )

        val sessionId = packageInstaller.createSession(params)
        val action = "${appContext.packageName}.SHIZUKU_INSTALL_$sessionId"
        val statusCode = AtomicInteger(PackageInstaller.STATUS_FAILURE)
        val statusMessage = AtomicReference("")
        val confirmIntent = AtomicReference<Intent?>(null)
        val latch = CountDownLatch(1)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                statusCode.set(intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE))
                statusMessage.set(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty())
                @Suppress("DEPRECATION")
                val pending = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmIntent.set(pending)
                latch.countDown()
            }
        }

        registerReceiver(appContext, receiver, action)
        try {
            packageInstaller.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { input -> input.copyTo(out) }
                    session.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pi = PendingIntent.getBroadcast(
                    appContext,
                    sessionId,
                    Intent(action).setPackage(appContext.packageName),
                    flags,
                )
                session.commit(pi.intentSender)
            }

            if (!latch.await(180, TimeUnit.SECONDS)) {
                error("Shizuku 安装超时")
            }

            when (val code = statusCode.get()) {
                PackageInstaller.STATUS_SUCCESS ->
                    statusMessage.get().ifBlank { "silent ok" }
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirm = confirmIntent.get()
                        ?: error("需要确认安装，但未返回确认 Intent")
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(confirm)
                    "pending_user_action"
                }
                else -> error("安装失败 status=$code ${statusMessage.get()}")
            }
        } finally {
            runCatching { appContext.unregisterReceiver(receiver) }
        }
    }

    private fun registerReceiver(context: Context, receiver: BroadcastReceiver, action: String) {
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    private fun resolvePackageInstaller(): Any {
        val pmBinder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package"))
        val ipmStub = Class.forName("android.content.pm.IPackageManager\$Stub")
        val ipm = ipmStub.getMethod("asInterface", IBinder::class.java).invoke(null, pmBinder)
            ?: error("IPackageManager null")
        val rawInstaller = ipm.javaClass.methods
            .first { it.name == "getPackageInstaller" && it.parameterCount == 0 }
            .invoke(ipm)
            ?: error("getPackageInstaller null")
        val asBinder = rawInstaller.javaClass.methods.first { it.name == "asBinder" && it.parameterCount == 0 }
        val installerBinder = asBinder.invoke(rawInstaller) as IBinder
        val installerStub = Class.forName("android.content.pm.IPackageInstaller\$Stub")
        return installerStub.getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(installerBinder))
            ?: error("IPackageInstaller null")
    }

    private fun createPackageInstaller(
        context: Context,
        iPackageInstaller: Any,
        installerPackageName: String,
        userId: Int,
    ): PackageInstaller {
        val clazz = PackageInstaller::class.java
        val constructors = clazz.declaredConstructors
        // API 31+: (IPackageInstaller, String, String, int)
        constructors.firstOrNull { it.parameterTypes.size == 4 }?.let { c ->
            c.isAccessible = true
            return c.newInstance(iPackageInstaller, installerPackageName, null, userId) as PackageInstaller
        }
        // API 26+: (IPackageInstaller, String, int)
        constructors.firstOrNull {
            it.parameterTypes.size == 3 && it.parameterTypes[0].name.contains("IPackageInstaller")
        }?.let { c ->
            c.isAccessible = true
            return c.newInstance(iPackageInstaller, installerPackageName, userId) as PackageInstaller
        }
        // Older: (Context, PackageManager, IPackageInstaller, String, int)
        val legacy = constructors.firstOrNull { it.parameterTypes.size == 5 }
            ?: error("PackageInstaller constructor not found")
        legacy.isAccessible = true
        return legacy.newInstance(
            context,
            context.packageManager,
            iPackageInstaller,
            installerPackageName,
            userId,
        ) as PackageInstaller
    }

    private fun getInstallFlags(params: PackageInstaller.SessionParams): Int {
        val field = PackageInstaller.SessionParams::class.java.getDeclaredField("installFlags")
        field.isAccessible = true
        return field.getInt(params)
    }

    private fun setInstallFlags(params: PackageInstaller.SessionParams, flags: Int) {
        val field = PackageInstaller.SessionParams::class.java.getDeclaredField("installFlags")
        field.isAccessible = true
        field.setInt(params, flags)
    }
}
