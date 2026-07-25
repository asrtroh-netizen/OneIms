package com.onetools.app.device

import android.content.Context
import android.os.Build
import com.onetools.app.channel.ChannelCardState
import com.onetools.app.channel.ShizukuChannel

/**
 * Read-only device / channel snapshot for local diagnostic export (architecture F4).
 * No carrier config writes; no privileged probing beyond public APIs + Shizuku ping.
 */
data class DeviceSnapshot(
    val capturedAtEpochMs: Long,
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val sdkInt: Int,
    val appPackage: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val shizukuInstalled: Boolean,
    val shizukuRunning: Boolean,
    val shizukuGranted: Boolean,
    val channelState: ChannelCardState,
)

object DeviceSnapshotReader {
    fun capture(
        context: Context,
        channelState: ChannelCardState,
        nowMillis: Long = System.currentTimeMillis(),
    ): DeviceSnapshot {
        val pm = context.packageManager
        val pkg = context.packageName
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(pkg, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
        }.getOrNull()
        val versionCode = when {
            info == null -> -1L
            Build.VERSION.SDK_INT >= 28 -> info.longVersionCode
            else -> {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }
        return DeviceSnapshot(
            capturedAtEpochMs = nowMillis,
            brand = Build.BRAND.orEmpty(),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            appPackage = pkg,
            appVersionName = info?.versionName.orEmpty().ifEmpty { "?" },
            appVersionCode = versionCode,
            shizukuInstalled = ShizukuChannel.isShizukuInstalled(context),
            shizukuRunning = ShizukuChannel.isRunning(),
            shizukuGranted = ShizukuChannel.isGranted(),
            channelState = channelState,
        )
    }
}
