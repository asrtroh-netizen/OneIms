package com.onetools.app.meter

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import java.util.Calendar

enum class TrafficPeriod {
    TODAY,
    DAYS_7,
    DAYS_30,
}

enum class TrafficNetwork {
    ALL,
    WIFI,
    MOBILE,
}

data class AppTrafficRow(
    val uid: Int,
    val packageName: String,
    val label: String,
    val rxBytes: Long,
    val txBytes: Long,
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

/**
 * Per-app usage via [NetworkStatsManager] (clean-room).
 * Requires [UsageAccess] (PACKAGE_USAGE_STATS / AppOps).
 */
class AppTrafficReader(
    private val context: Context,
) {
    fun load(
        period: TrafficPeriod,
        network: TrafficNetwork,
        nowMillis: Long = System.currentTimeMillis(),
        limit: Int = 40,
    ): List<AppTrafficRow> {
        val (start, end) = TrafficWindow.range(period, nowMillis)
        val byUid = mutableMapOf<Int, LongArray>()
        when (network) {
            TrafficNetwork.WIFI -> merge(byUid, queryType(ConnectivityManager.TYPE_WIFI, start, end))
            TrafficNetwork.MOBILE -> merge(byUid, queryType(ConnectivityManager.TYPE_MOBILE, start, end))
            TrafficNetwork.ALL -> {
                merge(byUid, queryType(ConnectivityManager.TYPE_WIFI, start, end))
                merge(byUid, queryType(ConnectivityManager.TYPE_MOBILE, start, end))
            }
        }
        val pm = context.packageManager
        return byUid.entries
            .asSequence()
            .filter { (uid, bytes) ->
                uid > 0 &&
                    uid != NetworkStats.Bucket.UID_REMOVED &&
                    uid != NetworkStats.Bucket.UID_TETHERING &&
                    (bytes[0] + bytes[1]) > 0L
            }
            .map { (uid, bytes) ->
                val (pkg, label) = resolveUid(pm, uid)
                AppTrafficRow(
                    uid = uid,
                    packageName = pkg,
                    label = label,
                    rxBytes = bytes[0],
                    txBytes = bytes[1],
                )
            }
            .sortedByDescending { it.totalBytes }
            .take(limit)
            .toList()
    }

    @Suppress("DEPRECATION")
    private fun queryType(networkType: Int, start: Long, end: Long): Map<Int, LongArray> {
        val nsm = context.getSystemService(NetworkStatsManager::class.java) ?: return emptyMap()
        val subscriberId = when (networkType) {
            ConnectivityManager.TYPE_MOBILE -> null
            else -> ""
        }
        val map = mutableMapOf<Int, LongArray>()
        nsm.querySummary(networkType, subscriberId, start, end).use { stats ->
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                if (uid == NetworkStats.Bucket.UID_ALL) continue
                val arr = map.getOrPut(uid) { longArrayOf(0L, 0L) }
                arr[0] += bucket.rxBytes.coerceAtLeast(0L)
                arr[1] += bucket.txBytes.coerceAtLeast(0L)
            }
        }
        return map
    }

    private fun merge(target: MutableMap<Int, LongArray>, source: Map<Int, LongArray>) {
        source.forEach { (uid, bytes) ->
            val arr = target.getOrPut(uid) { longArrayOf(0L, 0L) }
            arr[0] += bytes[0]
            arr[1] += bytes[1]
        }
    }

    private fun resolveUid(pm: PackageManager, uid: Int): Pair<String, String> {
        val packages = pm.getPackagesForUid(uid)
        val pkg = packages?.firstOrNull()
        if (pkg.isNullOrEmpty()) {
            val fallback = "uid:$uid"
            return fallback to fallback
        }
        val label = runCatching {
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(pkg)
        return pkg to label
    }
}

object TrafficWindow {
    fun range(period: TrafficPeriod, nowMillis: Long): Pair<Long, Long> {
        val end = nowMillis.coerceAtLeast(0L)
        val start = when (period) {
            TrafficPeriod.TODAY -> startOfLocalDay(nowMillis)
            TrafficPeriod.DAYS_7 -> end - 7L * 24L * 60L * 60L * 1000L
            TrafficPeriod.DAYS_30 -> end - 30L * 24L * 60L * 60L * 1000L
        }
        return start.coerceAtMost(end) to end
    }

    fun startOfLocalDay(nowMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
