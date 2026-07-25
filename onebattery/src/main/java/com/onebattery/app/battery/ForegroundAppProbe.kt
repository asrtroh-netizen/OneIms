package com.onebattery.app.battery

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.onebattery.app.meter.UsageAccess

data class ForegroundApp(
    val packageName: String,
    val label: String,
)

/**
 * Best-effort foreground app via [UsageStatsManager] events (AccuBattery-style attribution input).
 * Requires usage access; returns null if denied or unknown.
 */
object ForegroundAppProbe {
    fun current(context: Context, lookbackMs: Long = 60_000L): ForegroundApp? {
        if (!UsageAccess.hasPermission(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val end = System.currentTimeMillis()
        val start = end - lookbackMs
        val events = usm.queryEvents(start, end) ?: return null
        val event = UsageEvents.Event()
        var lastPkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPkg = event.packageName
            }
        }
        val pkg = lastPkg?.takeIf { it.isNotBlank() } ?: return null
        val label = resolveLabel(context.packageManager, pkg)
        return ForegroundApp(pkg, label)
    }

    private fun resolveLabel(pm: PackageManager, pkg: String): String {
        return runCatching {
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        }.getOrDefault(pkg)
    }
}
