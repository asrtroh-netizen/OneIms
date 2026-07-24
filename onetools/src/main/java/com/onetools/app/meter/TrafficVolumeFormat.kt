package com.onetools.app.meter

/**
 * Formats cumulative byte volumes (not rates) for app-traffic UI.
 * Clean-room helper — not derived from any third-party traffic app.
 */
object TrafficVolumeFormat {
    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "0 B"
        val k = bytes / 1024.0
        val m = k / 1024.0
        val g = m / 1024.0
        return when {
            g >= 1.0 -> String.format("%.2f GB", g)
            m >= 1.0 -> String.format("%.1f MB", m)
            k >= 1.0 -> String.format("%.0f KB", k)
            else -> "$bytes B"
        }
    }
}
