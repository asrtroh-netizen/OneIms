package com.onetools.app.export

import android.content.Context
import android.content.Intent
import com.onetools.app.R
import com.onetools.app.channel.ChannelCardState
import com.onetools.app.device.DeviceSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Assembles a plain-text diagnostic summary for share / paste.
 * Local-only; does not upload.
 */
object DiagExport {
    fun formatMarkdown(snapshot: DeviceSnapshot): String {
        val time = formatUtc(snapshot.capturedAtEpochMs)
        return buildString {
            appendLine("# OneTools diagnostic")
            appendLine()
            appendLine("- capturedAt: `$time`")
            appendLine("- app: `${snapshot.appPackage}` ${snapshot.appVersionName} (${snapshot.appVersionCode})")
            appendLine(
                "- device: `${snapshot.manufacturer}` / `${snapshot.brand}` / " +
                    "`${snapshot.model}` (`${snapshot.device}`)",
            )
            appendLine("- android: `${snapshot.androidRelease}` (API ${snapshot.sdkInt})")
            appendLine("- shizukuInstalled: `${snapshot.shizukuInstalled}`")
            appendLine("- shizukuRunning: `${snapshot.shizukuRunning}`")
            appendLine("- shizukuGranted: `${snapshot.shizukuGranted}`")
            appendLine("- channelState: `${channelLabel(snapshot.channelState)}`")
            appendLine()
            appendLine("> OneTools does not write carrier / IMS config. Export is local share only.")
        }
    }

    fun share(context: Context, body: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_share_subject))
            putExtra(Intent.EXTRA_TEXT, body)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.export_share_chooser))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun channelLabel(state: ChannelCardState): String = when (state) {
        ChannelCardState.INACTIVE -> "INACTIVE"
        ChannelCardState.ACTIVATING -> "ACTIVATING"
        ChannelCardState.READY -> "READY"
        ChannelCardState.SLEEPING -> "SLEEPING"
    }

    private fun formatUtc(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(epochMs))
    }
}
