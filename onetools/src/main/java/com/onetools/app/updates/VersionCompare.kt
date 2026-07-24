package com.onetools.app.updates

/**
 * Lightweight version compare for GitHub tags vs installed versionName.
 * Not a full SemVer library — good enough for update badges.
 */
object VersionCompare {
    enum class UpdateState {
        NOT_INSTALLED,
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        UNKNOWN,
    }

    fun state(installedVersion: String?, latestTag: String?): UpdateState {
        if (installedVersion.isNullOrBlank()) return UpdateState.NOT_INSTALLED
        if (latestTag.isNullOrBlank()) return UpdateState.UNKNOWN
        return when (compare(installedVersion, latestTag)) {
            in Int.MIN_VALUE until 0 -> UpdateState.UPDATE_AVAILABLE
            else -> UpdateState.UP_TO_DATE
        }
    }

    /** Negative if a < b (update available when installed < latest). */
    fun compare(a: String, b: String): Int {
        val left = tokenize(a)
        val right = tokenize(b)
        if (left.isEmpty() || right.isEmpty()) return 0
        val n = maxOf(left.size, right.size)
        for (i in 0 until n) {
            val lv = left.getOrElse(i) { 0 }
            val rv = right.getOrElse(i) { 0 }
            if (lv != rv) return lv.compareTo(rv)
        }
        return 0
    }

    fun tokenize(raw: String): List<Int> {
        val cleaned = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('_')
            .substringBefore('+')
        if (cleaned.isBlank()) return emptyList()
        return cleaned.split('.', ' ')
            .mapNotNull { part ->
                val digits = part.takeWhile { it.isDigit() }
                digits.toIntOrNull()
            }
    }
}
