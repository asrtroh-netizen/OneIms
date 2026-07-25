package com.onetools.app.special.display

import android.content.Context
import android.content.SharedPreferences

/**
 * OneTools 特色功能本地偏好（与 OneIMS ConfigStore 解耦）。
 */
object SpecialFeatureStore {
    private const val PREFS = "onetools_special_features"

    enum class SignalBarMode {
        AUTO, FOUR_BARS, FIVE_BARS;

        companion object {
            fun fromStored(value: String?): SignalBarMode =
                entries.firstOrNull { it.name == value } ?: AUTO
        }
    }

    data class FiveGConfig(
        val enabled: Boolean = false,
        val mode: String = Mode.CN_SPEED,
        val systemIconConfigString: String = DEFAULT_SYSTEM_ICON_CONFIG,
    ) {
        object Mode {
            const val CONSERVATIVE = "CONSERVATIVE"
            const val CN_SPEED = "CN_SPEED"
            const val COOL = "COOL"
            const val CUSTOM = "CUSTOM"
        }

        companion object {
            const val DEFAULT_SYSTEM_ICON_CONFIG =
                "connected_mmwave:5G_PLUS,connected:5G_PLUS,not_restricted_rrc_idle:5G"
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun signalBarMode(context: Context, subId: Int): SignalBarMode {
        if (subId < 0) return SignalBarMode.AUTO
        return SignalBarMode.fromStored(prefs(context).getString("signal_bar_$subId", null))
    }

    fun setSignalBarMode(context: Context, subId: Int, mode: SignalBarMode) {
        require(subId >= 0)
        prefs(context).edit().putString("signal_bar_$subId", mode.name).apply()
    }

    fun fiveGConfig(context: Context): FiveGConfig {
        val p = prefs(context)
        return FiveGConfig(
            enabled = p.getBoolean("five_g_enabled", false),
            mode = p.getString("five_g_mode", FiveGConfig.Mode.CN_SPEED)
                ?: FiveGConfig.Mode.CN_SPEED,
            systemIconConfigString = p.getString(
                "five_g_icon",
                FiveGConfig.DEFAULT_SYSTEM_ICON_CONFIG,
            ) ?: FiveGConfig.DEFAULT_SYSTEM_ICON_CONFIG,
        )
    }

    fun setFiveGConfig(context: Context, config: FiveGConfig) {
        prefs(context).edit()
            .putBoolean("five_g_enabled", config.enabled)
            .putString("five_g_mode", config.mode)
            .putString("five_g_icon", config.systemIconConfigString)
            .apply()
    }

    fun lastFiveGSubId(context: Context): Int =
        prefs(context).getInt("five_g_last_sub", -1)

    fun setLastFiveGSubId(context: Context, subId: Int) {
        prefs(context).edit().putInt("five_g_last_sub", subId).apply()
    }

    // --- baseline for restore ---

    fun hasSignalBaseline(context: Context, subId: Int): Boolean =
        prefs(context).getBoolean("sig_base_has_$subId", false)

    fun saveSignalBaseline(
        context: Context,
        subId: Int,
        inflate: Boolean,
        lte: IntArray,
        nr: IntArray,
        parameters: Int,
    ) {
        prefs(context).edit()
            .putBoolean("sig_base_has_$subId", true)
            .putBoolean("sig_base_inflate_$subId", inflate)
            .putString("sig_base_lte_$subId", lte.joinToString(","))
            .putString("sig_base_nr_$subId", nr.joinToString(","))
            .putInt("sig_base_params_$subId", parameters)
            .apply()
    }

    fun readSignalBaseline(context: Context, subId: Int): SignalBaseline? {
        if (!hasSignalBaseline(context, subId)) return null
        val p = prefs(context)
        return SignalBaseline(
            inflate = p.getBoolean("sig_base_inflate_$subId", false),
            lte = parseIntArray(p.getString("sig_base_lte_$subId", null)),
            nr = parseIntArray(p.getString("sig_base_nr_$subId", null)),
            parameters = p.getInt("sig_base_params_$subId", 1),
        )
    }

    fun fiveGBaseline(context: Context, subId: Int): String? =
        prefs(context).getString("five_g_base_$subId", null)

    fun setFiveGBaseline(context: Context, subId: Int, value: String) {
        prefs(context).edit().putString("five_g_base_$subId", value).apply()
    }

    data class SignalBaseline(
        val inflate: Boolean,
        val lte: IntArray,
        val nr: IntArray,
        val parameters: Int,
    )

    private fun parseIntArray(raw: String?): IntArray =
        raw.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toIntArray()
}
